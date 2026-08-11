package nyetbot.service.llm

import cats.effect.IO
import cats.effect.std.Random
import nyetbot.config.ReplyLengthConfig
import nyetbot.service.llm.ReplyGenerator.ReplyContext
import nyetbot.service.llm.context.ContextFeatures
import nyetbot.service.llm.context.DossierFeature.Dossier

trait LlmService:
    def generateReply(in: ReplyInputs): IO[LlmService.GeneratedReply]
    def rewriteProfile(gen: LlmService.GeneratedReply): IO[Unit]

object LlmService:
    enum Trigger:
        case Random(replyToText: String)
        case Tagged(question: String, replyToText: String)
        case Reply(question: String, replyToText: String)

    final case class GeneratedReply(text: String, dossier: Option[Dossier])

    def apply(
        features: ContextFeatures,
        reply: ReplyGenerator,
        rewriter: ProfileRewriter,
        config: ReplyLengthConfig
    )(using Random[IO]): LlmService =
        new LlmService:
            override def generateReply(in: ReplyInputs): IO[GeneratedReply] =
                for
                    dossier     <- features.dossier.get(in)
                    topic       <- features.topic.get(in)
                    register    <- features.register.get(in)
                    intent      <- features.intent.get(in)
                    chatLog     <- features.chatLog.get(in)
                    replyTarget <- features.replyTarget.get(in)
                    userTrigger <- features.userTrigger.get(in)
                    date        <- features.date.get(in)
                    minChars    <- targetMinChars(in.triggerText)
                    text        <- reply.generate(
                                     ReplyContext(
                                       dossier = dossier,
                                       topic = topic,
                                       chatLog = chatLog,
                                       replyTarget = replyTarget,
                                       userTrigger = userTrigger,
                                       register = register,
                                       intent = intent,
                                       date = date,
                                       minChars = minChars
                                     )
                                   )
                yield GeneratedReply(text, dossier)

            override def rewriteProfile(gen: GeneratedReply): IO[Unit] =
                gen.dossier.fold(IO.unit)(rewriter.rewrite)

            private def targetMinChars(triggerText: String): IO[Int] =
                val base = if triggerText.nonEmpty then triggerText.length else config.minChars
                Random[IO].betweenDouble(-config.spread, config.spread).map { jitter =>
                    val target = (config.meanFactor * base * (1.0 + jitter)).toInt
                    target.max(config.minChars).min(config.maxChars)
                }
