package nyetbot.functionality

import canoe.api.*
import canoe.models.*
import canoe.models.messages.*
import canoe.syntax.*
import cats.effect.*
import cats.effect.std.Mutex
import cats.effect.std.Random
import cats.syntax.all.*
import nyetbot.Config.LlmConfig
import nyetbot.model.*
import nyetbot.service.*

import concurrent.duration.DurationInt

trait LlmFunctionality:
    def reply: Scenario[IO, Unit]

class LlmFunctionalityImpl(
    profileService: ProfileService,
    contextRef: Ref[IO, Vector[LlmContextMessage]],
    userHistoryRef: Ref[IO, Map[Long, Vector[LlmContextMessage]]],
    mutex: Mutex[IO],
    config: LlmConfig
)(using TelegramClient[IO], Random[IO])
    extends LlmFunctionality:

    def ingest(msg: TextMessage): IO[Unit] =
        val newMsg = LlmContextMessage.fromTextMessage(msg, config)
        for
            _ <- contextRef.update(m => (m :+ newMsg).takeRight(config.chatBufferSize))
            _ <- msg.from.traverse_ { u =>
                     userHistoryRef.update { map =>
                         val buf =
                             (map.getOrElse(u.id, Vector.empty) :+ newMsg)
                                 .takeRight(config.recentUserMessages)
                         map.updated(u.id, buf)
                     }
                 }
        yield ()

    def maybeReply(msg: TextMessage): IO[Unit] =
        val fire =
            mutex.lock
                .surround(triggerReply(msg, msg.text.contains(config.botAlias)))
                .handleErrorWith(e => IO.println(s"LLM reply failed: ${e.getMessage}"))
        for
            roll  <- Random[IO].betweenInt(0, config.llmMessageEvery)
            tagged = msg.text.contains(config.botAlias)
            _     <- if roll == 0 || tagged then fire else IO.unit
        yield ()

    def triggerReply(msg: TextMessage, tagged: Boolean): IO[Unit] =
        def sendIfNotEmpty(s: String) =
            if s.nonEmpty then msg.chat.send(s, replyToMessageId = Some(msg.messageId)).void
            else IO.unit

        def typing: IO[Unit] =
            msg.chat.setAction[IO](ChatAction.Typing).void >> IO.sleep(4.seconds) >> typing

        def replyToText: String =
            msg.replyToMessage match
                case Some(t: TextMessage) => t.text
                case _                    => ""

        msg.from match
            case None       =>
                IO.unit
            case Some(user) =>
                val target      = UserRef.fromUser(user)
                val triggerText = msg.text.replace(config.botAlias, config.botName)
                val trigger     =
                    if tagged then Trigger.Tagged(msg.text, replyToText) else Trigger.Random

                val produce =
                    for
                        recentChat <-
                            contextRef.get.map(_.takeRight(config.replyContextWindow).toList)
                        recentUser <-
                            userHistoryRef.get.map(_.getOrElse(user.id, Vector.empty).toList)
                        gen        <- profileService.generateReply(
                                        target,
                                        triggerText,
                                        recentUser,
                                        recentChat,
                                        trigger
                                      )
                        _          <- contextRef.update(m =>
                                          (m :+ LlmContextMessage(None, config.botName, gen.text))
                                              .takeRight(config.chatBufferSize)
                                      )
                        _          <- sendIfNotEmpty(gen.text.trim)
                    yield gen

                produce.race(typing).flatMap {
                    case Left(gen) => profileService.rewriteProfile(target, gen)
                    case Right(_)  => IO.unit
                }

    override def reply: Scenario[IO, Unit] =
        for
            msg <- Scenario.expect(textMessage)
            _   <- Scenario.eval(ingest(msg) *> maybeReply(msg))
        yield ()

object LlmFunctionalityImpl:
    def mk(profileService: ProfileService, config: LlmConfig)(using
        TelegramClient[IO],
        Random[IO]
    ): IO[LlmFunctionalityImpl] =
        for
            m       <- Mutex[IO]
            r       <- Ref.of[IO, Vector[LlmContextMessage]](Vector.empty)
            perUser <- Ref.of[IO, Map[Long, Vector[LlmContextMessage]]](Map.empty)
        yield LlmFunctionalityImpl(profileService, r, perUser, m, config)
