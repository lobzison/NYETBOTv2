package nyetbot.lab

import cats.effect.IO
import fs2.Stream
import fs2.data.json.tokens
import io.circe.Json
import munit.CatsEffectSuite

class TelegramExportPipeSpec extends CatsEffectSuite:

    private val dump = """{
      "name": "Gleb",
      "type": "personal_chat_list",
      "chats": {
        "about": "This page lists all chats from this export.",
        "list": [
          {
            "name": "Decoy",
            "type": "personal_chat",
            "id": 111,
            "messages": [
              {
                "id": 222,
                "type": "message",
                "date": "2025-01-01T10:00:00",
                "from": "Decoy User",
                "from_id": "user1",
                "text": "decoy",
                "text_entities": [{"type": "plain", "text": "decoy"}],
                "reactions": [
                  {
                    "type": "emoji",
                    "count": 1,
                    "emoji": "👍",
                    "id": 222,
                    "recent": [{"from": "X", "from_id": "user2", "date": "2025-01-01T11:00:00"}]
                  }
                ]
              }
            ]
          },
          {
            "name": "Target",
            "type": "private_supergroup",
            "id": 222,
            "messages": [
              {
                "id": 10,
                "type": "message",
                "date": "2025-02-01T10:00:00",
                "from": "Милежик",
                "from_id": "user21564513",
                "text": "hello",
                "text_entities": []
              },
              {
                "id": 11,
                "type": "service",
                "date": "2025-02-01T11:00:00",
                "actor": "Someone",
                "actor_id": "user3",
                "action": "pin_message",
                "message_id": 10,
                "text": "",
                "text_entities": []
              }
            ]
          },
          {
            "name": "After",
            "type": "personal_chat",
            "id": 333,
            "messages": [
              {
                "id": 20,
                "type": "message",
                "date": "2025-03-01T10:00:00",
                "from": "Later",
                "from_id": "user4",
                "text": "later",
                "text_entities": []
              }
            ]
          }
        ]
      }
    }"""

    private def messagesOf(target: Long): IO[List[Json]] =
        Stream
            .emit(dump)
            .through(tokens[IO, String])
            .through(TelegramExportPipe.messages[IO](ChatId(target)))
            .compile
            .toList

    test("extracts only the target chat's messages") {
        messagesOf(222L).map { messages =>
            assertEquals(messages.flatMap(_.hcursor.get[Long]("id").toOption), List(10L, 11L))
            assertEquals(
              messages.flatMap(_.hcursor.get[String]("from").toOption),
              List("Милежик")
            )
        }
    }

    test("message-level and reaction-level id fields do not select a chat") {
        messagesOf(222L).map { messages =>
            assertEquals(
              messages.flatMap(_.hcursor.get[String]("text").toOption).contains("decoy"),
              false
            )
        }
    }

    test("a chat whose message ids collide with another chat id still extracts fully") {
        messagesOf(111L).map { messages =>
            assertEquals(messages.size, 1)
            assertEquals(
              messages.headOption.flatMap(_.hcursor.get[Long]("id").toOption),
              Some(222L)
            )
            assertEquals(
              messages.headOption.flatMap(_.hcursor.get[String]("text").toOption),
              Some("decoy")
            )
        }
    }

    test("unknown chat id extracts nothing") {
        messagesOf(999L).map(messages => assertEquals(messages, Nil))
    }
