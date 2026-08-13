package nyetbot.functionality

import canoe.api.*
import canoe.models.*
import canoe.models.messages.*
import canoe.syntax.*
import cats.effect.*
import cats.effect.std.Mutex
import cats.effect.std.Random
import nyetbot.config.LlmFunctionalityConfig
import nyetbot.model.*
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.*
import nyetbot.service.llm.LlmService.Trigger

import concurrent.duration.DurationInt

trait LlmFunctionality:
    def reply: Scenario[IO, Unit]
    def isReplyToBot(msg: TextMessage): Boolean

object LlmFunctionality:
    def apply(llmService: LlmService, config: LlmFunctionalityConfig)(using
        TelegramClient[IO],
        Random[IO]
    ): IO[LlmFunctionality] =
        for
            mutex  <- Mutex[IO]
            memory <- ChatMemory(config)
        yield new LlmFunctionality:
            override def isReplyToBot(msg: TextMessage): Boolean =
                msg.replyToMessage.exists {
                    case t: TextMessage =>
                        t.from.exists(u =>
                            u.isBot && u.username.exists(un =>
                                ("@" + un).equalsIgnoreCase(config.botAlias)
                            )
                        ) && msg.quote.isEmpty
                    case _              => false
                }

            def maybeReply(msg: TextMessage): IO[Unit] =
                val tagged     = msg.text.contains(config.botAlias)
                val replyToBot = isReplyToBot(msg)
                val fire       =
                    mutex.lock
                        .surround(triggerReply(msg, tagged, replyToBot))
                        .handleErrorWith(e => IO.println(s"LLM reply failed: ${e.getMessage}"))
                for
                    roll <- Random[IO].betweenInt(0, config.messageEvery)
                    _    <- if roll == 0 || tagged || replyToBot then fire else IO.unit
                yield ()

            def triggerReply(msg: TextMessage, tagged: Boolean, replyToBot: Boolean): IO[Unit] =
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
                            if replyToBot then Trigger.Reply(msg.text, replyToText)
                            else if tagged then Trigger.Tagged(msg.text, replyToText)
                            else Trigger.Random(msg.text)

                        val produce =
                            for
                                recentChat <- memory.replyContext
                                recentUser <- memory.recentUser(UserId(user.id))
                                gen        <- llmService.generateReply(
                                                ReplyInputs(
                                                  target = target,
                                                  triggerText = triggerText,
                                                  trigger = trigger,
                                                  recentChat = recentChat,
                                                  recentUserMsgs = recentUser
                                                )
                                              )
                                _          <- memory.ingest(
                                                LlmContextMessage(None, config.botName, gen.text)
                                              )
                                _          <- sendIfNotEmpty(gen.text.trim)
                            yield gen

                        produce.race(typing).flatMap {
                            case Left(gen) => llmService.rewriteProfile(gen)
                            case Right(_)  => IO.unit
                        }

            override def reply: Scenario[IO, Unit] =
                for
                    msg <- Scenario.expect(textMessage)
                    _   <- Scenario.eval(
                             IO.whenA(config.enabled)(
                               memory.ingest(LlmContextMessage.fromTextMessage(msg)) *>
                                   maybeReply(msg)
                             )
                           )
                yield ()
