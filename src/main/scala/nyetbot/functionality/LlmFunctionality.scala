package nyetbot.functionality

import canoe.api.*
import canoe.models.*
import canoe.models.messages.*
import canoe.syntax.*
import cats.*
import cats.effect.*
import cats.effect.std.Mutex
import cats.effect.Ref
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
    contextRef: Ref[IO, Vector[LlmContextMessage]],
    mutex: Mutex[IO],
    config: LlmConfig
)(using TelegramClient[IO], Random[IO])
    extends LlmFunctionality:

    def predictReply(msg: TextMessage): IO[Unit] =
        for
            newMsg <- IO.pure(LlmContextMessage.fromTextMessage(msg, config))
            _      <- contextRef.update(msgs => (msgs :+ newMsg).takeRight(20))
            _      <- triggerReplyWithChance(msg)
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
            msgs           <- contextRef.get
            translatedMsgs <-
                translationService.translateMessageBatch(
                  msgs.toList,
                  TranslationService.TargetLang.EN
                )
            replyEng       <- service.predict(translatedMsgs, tagged)
            _               = println(s"predicted bot message: $replyEng")
            reply          <- translationService.translate(replyEng, TranslationService.TargetLang.RU)
            replyMsg       <- IO.pure(LlmContextMessage(config.userPrefix + config.botName, reply))
            _              <- contextRef.update(msgs => (msgs :+ replyMsg).takeRight(20))
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
            r <- Ref.of[IO, Vector[LlmContextMessage]](Vector.empty)
        yield LlmFunctionalityImpl(service, translationService, r, m, config)
