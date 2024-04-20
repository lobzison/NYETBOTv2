package nyetbot.functionality

import canoe.api.Scenario
import canoe.api.TelegramClient
import cats.MonadThrow
import cats.effect.std.Random
import cats.implicits.*
import cats.*
import cats.effect.*
import canoe.api.*
import canoe.models.*
import canoe.models.messages.*
import canoe.syntax.*
import nyetbot.model.{given, *}
import cats.effect.kernel.syntax.resource
import scala.util.Try
import nyetbot.service.LlmService
import cats.effect.kernel.GenConcurrent
import cats.effect.std.Queue
import nyetbot.Config.LlmConfig
import cats.effect.std.Console
import nyetbot.service.TranslationService
import nyetbot.service.TransliterationService
import cats.effect.std.Mutex
import concurrent.duration.DurationInt
import cats.effect.implicits.*
import cats.implicits.*

trait LlmFunctionality[F[_]]:
    def reply: Scenario[F, Unit]

class LlmFunctionalityImpl[F[_]: MonadCancelThrow: TelegramClient: Console: Random: Temporal](
    service: LlmService[F],
    translationService: TranslationService[F],
    queue: Queue[F, LlmContextMessage],
    mutex: Mutex[F],
    config: LlmConfig
) extends LlmFunctionality[F]:

    def predictReply(msg: TextMessage): F[Unit] =
        for
            _ <- queue.offer(LlmContextMessage.fromTextMessage(msg, config))
            _ <- triggerReplyWithChance(msg)
        yield ()

    def triggerReplyWithChance(msg: TextMessage): F[Unit] =
        for
            c <- Random[F].betweenInt(0, config.llmMessageEvery)
            _ <- if c == 0 || msg.text.contains("@nyetterbot") then triggerReply(msg)
                 else Monad[F].unit
        yield ()

    def triggerReply(msg: TextMessage): F[Unit] =
        def sendIfNotEmpty(s: String) =
            if s.nonEmpty then
                msg.chat
                    .send(
                      s,
                      replyToMessageId = Some(msg.messageId)
                    )
                    .void
            else Monad[F].unit

        def typing: F[Unit] =
            msg.chat.setAction[F](ChatAction.Typing).void >> Temporal[F].sleep(4.seconds) >> typing

        val translateAndReply = for
            // Amazing efficiency 🤦‍♂️
            msgs           <- queue.tryTakeN(None)
            translatedMsgs <-
                translationService.translateMessageBatch(msgs, TranslationService.TargetLang.EN)
            replyEng       <- service.predict(translatedMsgs)
            reply          <- translationService.translate(replyEng, TranslationService.TargetLang.RU)
            _              <- queue.tryOfferN(msgs)
            _              <- queue.offer(LlmContextMessage(config.userPrefix + config.botName, reply))
            _              <- sendIfNotEmpty(reply.trim)
        yield ()

        translateAndReply.race(typing).void

    override def reply: Scenario[F, Unit] =
        for
            msg <- Scenario.expect(textMessage)
            _   <- Scenario.eval(mutex.lock.surround(predictReply(msg)))
        yield ()

object LlmFunctionalityImpl:
    def mk[F[_]: Concurrent: TelegramClient: Console: Random: Temporal](
        service: LlmService[F],
        translationService: TranslationService[F],
        config: LlmConfig
    ): F[LlmFunctionalityImpl[F]] =
        for
            m <- Mutex[F]
            q <- Queue.circularBuffer[F, LlmContextMessage](20)
        yield LlmFunctionalityImpl[F](service, translationService, q, m, config)
