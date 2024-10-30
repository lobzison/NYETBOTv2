package nyetbot.functionality

import canoe.api.*
import canoe.models.*
import canoe.models.messages.*
import canoe.syntax.*
import cats.*
import cats.effect.*
import cats.effect.std.Mutex
import cats.effect.std.Queue
import cats.effect.std.Random
import nyetbot.Config.LlmConfig
import nyetbot.model.*
import nyetbot.service.LlmService
import nyetbot.service.TranslationService

import concurrent.duration.DurationInt

trait LlmFunctionality:
    def reply: Scenario[IO, Unit]

class LlmFunctionalityImpl(
    service: LlmService,
    translationService: TranslationService,
    queue: Queue[IO, LlmContextMessage],
    mutex: Mutex[IO],
    config: LlmConfig
)(using TelegramClient[IO], Random[IO])
    extends LlmFunctionality:

    def predictReply(msg: TextMessage): IO[Unit] =
        for
            _ <- queue.offer(LlmContextMessage.fromTextMessage(msg, config))
            _ <- triggerReplyWithChance(msg)
        yield ()

    def triggerReplyWithChance(msg: TextMessage): IO[Unit] =
        for
            c     <- Random[IO].betweenInt(0, config.llmMessageEvery)
            tagged = msg.text.contains(config.botAlias)
            _     <- if c == 0 || tagged then triggerReply(msg, tagged)
                     else IO.unit
        yield ()

    def triggerReply(msg: TextMessage, tagged: Boolean): IO[Unit] =
        def sendIfNotEmpty(s: String) =
            if s.nonEmpty then
                msg.chat
                    .send(
                      s,
                      replyToMessageId = Some(msg.messageId)
                    )
                    .void
            else IO.unit

        def typing: IO[Unit] =
            msg.chat.setAction[IO](ChatAction.Typing).void >> IO.sleep(4.seconds) >> typing

        val translateAndReply = for
            // Amazing efficiency 🤦‍♂️
            msgs           <- queue.tryTakeN(None)
            translatedMsgs <-
                translationService.translateMessageBatch(msgs, TranslationService.TargetLang.EN)
            replyEng       <- service.predict(translatedMsgs, tagged)
            reply          <- translationService.translate(replyEng, TranslationService.TargetLang.RU)
            _              <- queue.tryOfferN(msgs)
            _              <- queue.offer(LlmContextMessage(config.userPrefix + config.botName, reply))
            _              <- sendIfNotEmpty(reply.trim)
        yield ()

        translateAndReply.race(typing).void

    override def reply: Scenario[IO, Unit] =
        for
            msg <- Scenario.expect(textMessage)
            _   <- Scenario.eval(mutex.lock.surround(predictReply(msg)))
        yield ()

object LlmFunctionalityImpl:
    def mk(
        service: LlmService,
        translationService: TranslationService,
        config: LlmConfig
    )(using TelegramClient[IO], Random[IO]): IO[LlmFunctionalityImpl] =
        for
            m <- Mutex[IO]
            q <- Queue.circularBuffer[IO, LlmContextMessage](20)
        yield LlmFunctionalityImpl(service, translationService, q, m, config)
