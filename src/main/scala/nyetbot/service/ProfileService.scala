package nyetbot.service

import cats.effect.IO
import cats.effect.std.Random
import nyetbot.Config
import nyetbot.model.LlmContextMessage
import nyetbot.model.UserRef
import nyetbot.repo.ProfileRepo
import nyetbot.util.Text

enum Trigger:
    case Random
    case Tagged(question: String, replyToText: String)

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

class ProfileServiceImpl(repo: ProfileRepo, llm: LlmService, config: Config.LlmConfig)(using Random[IO])
    extends ProfileService:

    override def generateReply(
        target: UserRef,
        triggerText: String,
        recentUserMsgs: List[LlmContextMessage],
        recentChat: List[LlmContextMessage],
        trigger: Trigger
    ): IO[GeneratedReply] =
        for
            oldProfile <- repo.getProfile(target.id).map(_.map(_.description).getOrElse(""))
            summary    <- llm.summarizeUser(recentUserMsgs, target)
            intent     <- trigger match
                              case Trigger.Tagged(q, r) => llm.classifyTagIntent(q, r, recentChat)
                              case Trigger.Random       => IO.pure(TagIntent.Contextual)
            minChars   <- targetMinChars(triggerText)
            text       <- llm.generateReply(
                            ReplyContext(target, oldProfile, summary, recentChat, intent, minChars, triggerText)
                          )
        yield GeneratedReply(text, summary, oldProfile)

    override def rewriteProfile(target: UserRef, gen: GeneratedReply): IO[Unit] =
        for
            merged <- llm.rewriteProfile(gen.oldProfile, gen.recentSummary, target)
            _      <- repo.upsertProfile(target.id, target.displayName, Text.truncate(merged, config.profileMaxChars))
        yield ()

    private def targetMinChars(triggerText: String): IO[Int] =
        val base = if triggerText.nonEmpty then triggerText.length else config.replyMinChars
        Random[IO].betweenDouble(-config.replySpread, config.replySpread).map { jitter =>
            val target = (config.replyMeanFactor * base * (1.0 + jitter)).toInt
            target.max(config.replyMinChars).min(config.replyMaxChars)
        }
