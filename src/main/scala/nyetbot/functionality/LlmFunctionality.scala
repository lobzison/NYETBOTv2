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

trait LlmFunctionality[F[_]]:
    def reply: Scenario[F, Unit]

class LlmFunctionalityImpl[F[_]: MonadCancelThrow: TelegramClient: Console: Random](
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
            _ <- if c == 0 then triggerReply(msg) else Monad[F].unit
        yield ()

    def triggerReply(msg: TextMessage): F[Unit] =
        def sendIfNotEmpty(s: String) =
            if s.nonEmpty then
                msg.chat
                    .send(
                      TransliterationService.transliterate(s).toLowerCase,
                      replyToMessageId = Some(msg.messageId)
                    )
                    .void
            else Monad[F].unit

        for
            // Amazing efficiency 🤦‍♂️
            msgs           <- queue.tryTakeN(None)
            translatedMsgs <-
                translationService.translateMessageBatch(msgs, TranslationService.TargetLang.EN)
            _              <- Console[F].println(msgs)
            _              <- Console[F].println(translatedMsgs)
            replyEng       <- service.predict(translatedMsgs)
            reply          <- translationService.translate(replyEng, TranslationService.TargetLang.RU)
            _              <- queue.tryOfferN(msgs)
            _              <- queue.offer(LlmContextMessage(config.userPrefix + config.botName, reply))
            _              <- sendIfNotEmpty(reply.trim)
        yield ()

    override def reply: Scenario[F, Unit] =
        for
            msg <- Scenario.expect(textMessage)
            _   <- Scenario.eval(mutex.lock.surround(predictReply(msg)))
        yield ()

object LlmFunctionalityImpl:
    def mk[F[_]: Concurrent: TelegramClient: Console: Random](
        service: LlmService[F],
        translationService: TranslationService[F],
        config: LlmConfig
    ): F[LlmFunctionalityImpl[F]] =
        for
            m <- Mutex[F]
            q <- Queue.circularBuffer[F, LlmContextMessage](15)
        yield LlmFunctionalityImpl[F](service, translationService, q, m, config)
