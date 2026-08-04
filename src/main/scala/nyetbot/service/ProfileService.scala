package nyetbot.service

import cats.effect.Clock
import cats.effect.IO
import cats.effect.std.Random
import io.github.iltotore.iron.*
import nyetbot.Config
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileDescription
import nyetbot.model.UserRef
import nyetbot.repo.ProfileRepo

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum Trigger:
    case Random(replyToText: String)
    case Tagged(question: String, replyToText: String, replyToBot: Boolean)

final case class GeneratedReply(text: String, recentSummary: String, oldProfile: String)

trait ProfileService:
    def generateReply(
        target: UserRef,
        triggerText: String,
        recentUserMsgs: List[LlmContextMessage],
        recentChat: List[LlmContextMessage],
        trigger: Trigger
    ): IO[GeneratedReply]

    def rewriteProfile(target: UserRef, gen: GeneratedReply): IO[Unit]

class ProfileServiceImpl(repo: ProfileRepo, llm: LlmService, config: Config.LlmConfig)(using
    Random[IO]
) extends ProfileService:

    override def generateReply(
        target: UserRef,
        triggerText: String,
        recentUserMsgs: List[LlmContextMessage],
        recentChat: List[LlmContextMessage],
        trigger: Trigger
    ): IO[GeneratedReply] =
        for
            oldProfile                       <- repo.getProfile(target.id).map(_.map(_.description.value).getOrElse(""))
            summary                          <- llm.summarizeUser(recentUserMsgs, target)
            topic                            <- llm
                                                    .summarizeThread(
                                                      recentChat.takeRight(config.topicContextWindow)
                                                    )
                                                    .handleError(_ => "")
            register                         <- llm
                                                    .classifyRegister(triggerText, recentChat)
                                                    .handleError(_ => Register.Byt)
            details                          <- trigger match
                                                    case Trigger.Tagged(q, r, replyToBot) =>
                                                        llm.classifyTagIntent(q, r, recentChat).map((_, r, replyToBot))
                                                    case Trigger.Random(replyToText)      =>
                                                        IO.pure((TagIntent.Contextual, replyToText, false))
            (intent, replyToText, replyToBot) = details
            minChars                         <- targetMinChars(triggerText)
            date                             <- currentDate
            text                             <- llm.generateReply(
                                                  ReplyContext(
                                                    target = target,
                                                    profile = oldProfile,
                                                    recentSummary = summary,
                                                    topic = topic,
                                                    recentChat = recentChat,
                                                    intent = intent,
                                                    register = register,
                                                    minChars = minChars,
                                                    triggerText = triggerText,
                                                    currentDate = date,
                                                    replyToText = replyToText,
                                                    replyToBot = replyToBot
                                                  )
                                                )
        yield GeneratedReply(text, summary, oldProfile)

    override def rewriteProfile(target: UserRef, gen: GeneratedReply): IO[Unit] =
        for
            merged <- llm.rewriteProfile(gen.oldProfile, gen.recentSummary, target)
            _      <- repo.upsertProfile(
                        target.id,
                        target.displayName,
                        ProfileDescription.truncate(merged)
                      )
        yield ()

    private val currentDateFormatter =
        DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru"))

    private def currentDate: IO[String] =
        Clock[IO].realTimeInstant.map(_.atZone(ZoneId.systemDefault()).format(currentDateFormatter))

    private def targetMinChars(triggerText: String): IO[Int] =
        val base = if triggerText.nonEmpty then triggerText.length else config.replyMinChars
        Random[IO].betweenDouble(-config.replySpread, config.replySpread).map { jitter =>
            val target = (config.replyMeanFactor * base * (1.0 + jitter)).toInt
            target.max(config.replyMinChars).min(config.replyMaxChars)
        }
