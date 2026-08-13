package nyetbot.lab

import io.circe.literal.*
import munit.FunSuite
import nyetbot.model.ProfileModels.UserId

class PreprocessSpec extends FunSuite:

    private val botId = BotId("user467782420")

    test("plain string text is flattened") {
        val message = json"""{
          "id": 312765,
          "type": "message",
          "date": "2025-06-01T12:34:56",
          "from": "Милежик",
          "from_id": "user21564513",
          "text": "привет",
          "text_entities": [{"type": "plain", "text": "привет"}]
        }"""
        val result  = Preprocess.message(message, botId)
        assertEquals(
          result,
          Some(
            CorpusMessage(
              id = MessageId(312765L),
              date = "2025-06-01T12:34:56",
              userId = Some(UserId(21564513L)),
              userName = "Милежик",
              isBot = false,
              text = "привет",
              replyToMessageId = None
            )
          )
        )
    }

    test("mixed array text is flattened") {
        val message = json"""{
          "id": 1,
          "type": "message",
          "date": "2025-06-01T12:34:56",
          "from": "Gleb Lobov",
          "from_id": "user1",
          "text": ["hello ", {"type": "mention", "text": "@nyetterbot"}, " bye"],
          "text_entities": []
        }"""
        assertEquals(Preprocess.message(message, botId).map(_.text), Some("hello @nyetterbot bye"))
    }

    test("photo-only message is dropped") {
        val message = json"""{
          "id": 2,
          "type": "message",
          "date": "2025-06-01T12:34:56",
          "from": "Gleb Lobov",
          "from_id": "user1",
          "photo": "photos/photo_1.jpg",
          "width": 800,
          "height": 600,
          "text": "",
          "text_entities": []
        }"""
        assertEquals(Preprocess.message(message, botId), None)
    }

    test("service message is dropped") {
        val message = json"""{
          "id": 3,
          "type": "service",
          "date": "2025-06-01T12:34:56",
          "actor": "Gleb Lobov",
          "actor_id": "user1",
          "action": "pin_message",
          "message_id": 2,
          "text": "",
          "text_entities": []
        }"""
        assertEquals(Preprocess.message(message, botId), None)
    }

    test("bot message maps to isBot") {
        val message = json"""{
          "id": 4,
          "type": "message",
          "date": "2025-06-01T12:34:56",
          "from": "NYETBOT",
          "from_id": "user467782420",
          "text": "ну и что",
          "text_entities": []
        }"""
        val result  = Preprocess.message(message, botId)
        assertEquals(result.map(_.isBot), Some(true))
        assertEquals(result.flatMap(_.userId), Some(UserId(467782420L)))
    }

    test("channel from_id yields no userId and no isBot") {
        val message = json"""{
          "id": 5,
          "type": "message",
          "date": "2025-06-01T12:34:56",
          "from": "Some Channel",
          "from_id": "channel1234567",
          "text": "post",
          "text_entities": []
        }"""
        val result  = Preprocess.message(message, botId)
        assertEquals(result.flatMap(_.userId), None)
        assertEquals(result.map(_.isBot), Some(false))
    }

    test("display name spaces become underscores") {
        val message = json"""{
          "id": 6,
          "type": "message",
          "date": "2025-06-01T12:34:56",
          "from": "Gleb Lobov",
          "from_id": "user1",
          "text": "hi",
          "text_entities": []
        }"""
        assertEquals(Preprocess.message(message, botId).map(_.userName), Some("Gleb_Lobov"))
    }

    test("missing from falls back to user") {
        val message = json"""{
          "id": 7,
          "type": "message",
          "date": "2025-06-01T12:34:56",
          "from": null,
          "from_id": "user1",
          "text": "hi",
          "text_entities": []
        }"""
        assertEquals(Preprocess.message(message, botId).map(_.userName), Some("user"))
    }

    test("reply_to_message_id is carried over") {
        val message = json"""{
          "id": 312765,
          "type": "message",
          "date": "2025-06-01T12:34:56",
          "from": "Милежик",
          "from_id": "user21564513",
          "reply_to_message_id": 312763,
          "text": "ага",
          "text_entities": []
        }"""
        assertEquals(
          Preprocess.message(message, botId).flatMap(_.replyToMessageId),
          Some(MessageId(312763L))
        )
    }
