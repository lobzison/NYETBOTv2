package nyetbot.model

import canoe.models.PrivateChat
import canoe.models.User
import canoe.models.messages.TextMessage
import munit.FunSuite
import nyetbot.Fixtures

class LlmContextMessageSpec extends FunSuite:

    private def user(id: Long, first: String, last: Option[String], handle: Option[String]) =
        User(id, isBot = false, first, last, handle, None, None, None, None)

    private val chat = PrivateChat(1L, None, None, None)

    test("fromTextMessage captures the stable user id and display name") {
        val msg = TextMessage(
          messageId = 1,
          chat = chat,
          date = 0,
          text = "привет",
          from = Some(user(42L, "Гоша", Some("Петров"), Some("gosha")))
        )
        val m   = LlmContextMessage.fromTextMessage(msg, Fixtures.llmConfig)
        assertEquals(m.userId, Some(UserId(42L)))
        assertEquals(m.text, "привет")
        assert(m.userName.contains("Гоша"))
    }

    test("fromTextMessage falls back to 'user' when there is no sender") {
        val msg = TextMessage(messageId = 1, chat = chat, date = 0, text = "привет", from = None)
        val m   = LlmContextMessage.fromTextMessage(msg, Fixtures.llmConfig)
        assertEquals(m.userId, None)
        assertEquals(m.userName, "user")
    }

    test("UserRef.fromUser builds id and a display name that includes the handle") {
        val ref = UserRef.fromUser(user(7L, "Аня", None, Some("anya")))
        assertEquals(ref.id, UserId(7L))
        assert(ref.displayName.value.contains("Аня"))
        assert(ref.displayName.value.contains("@anya"))
    }
