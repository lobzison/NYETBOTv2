package nyetbot.functionality

import canoe.api.TelegramClient
import canoe.methods.Method
import canoe.models.PrivateChat
import canoe.models.User
import canoe.models.messages.TextMessage
import cats.effect.IO
import cats.effect.std.Random
import munit.CatsEffectSuite
import nyetbot.Fixtures
import nyetbot.service.llm.LlmService
import nyetbot.service.llm.LlmService.GeneratedReply
import nyetbot.service.llm.ReplyInputs

class LlmFunctionalitySpec extends CatsEffectSuite:

    private val client = new TelegramClient[IO]:
        override def execute[Req, Res](request: Req)(implicit method: Method[Req, Res]): IO[Res] =
            IO.raiseError(new IllegalStateException("Telegram client must not be called"))

    private val llmService = new LlmService:
        override def generateReply(in: ReplyInputs): IO[GeneratedReply] =
            IO.raiseError(new IllegalStateException("LLM service must not be called"))

        override def rewriteProfile(gen: GeneratedReply): IO[Unit] = IO.unit

    private val chat = PrivateChat(1L, None, None, None)

    private def user(isBot: Boolean, username: Option[String]): User =
        User(1L, isBot, "NYETBOT", None, username, None, None, None, None)

    private def incomingReply(repliedFrom: User): TextMessage =
        val replied = TextMessage(
          messageId = 1,
          chat = chat,
          date = 0,
          text = "прошлая позиция бота",
          from = Some(repliedFrom)
        )
        TextMessage(
          messageId = 2,
          chat = chat,
          date = 0,
          text = "возражение",
          replyToMessage = Some(replied)
        )

    test("reply to the configured bot username is detected case-insensitively") {
        Random.scalaUtilRandom[IO].flatMap { random =>
            LlmFunctionality(llmService, Fixtures.llmConfig)(using client, random)
                .map { functionality =>
                    assert(
                      functionality.isReplyToBot(
                        incomingReply(user(isBot = true, username = Some("NyEtTeRbOt")))
                      )
                    )
                    assert(
                      !functionality.isReplyToBot(
                        incomingReply(user(isBot = false, username = Some("nyetterbot")))
                      )
                    )
                    assert(
                      !functionality.isReplyToBot(
                        incomingReply(user(isBot = true, username = Some("another_bot")))
                      )
                    )
                }
        }
    }
