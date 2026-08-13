package nyetbot.lab

import io.circe.Json
import nyetbot.model.ProfileModels.UserId

object Preprocess:

    def message(json: Json, botId: BotId): Option[CorpusMessage] =
        val c    = json.hcursor
        val text = flattenedText(json)
        for
            id   <- c.get[Long]("id").toOption
            tpe  <- c.get[String]("type").toOption
            if tpe == "message"
            date <- c.get[String]("date").toOption
            if text.nonEmpty
        yield CorpusMessage(
          id = MessageId(id),
          date = date,
          userId = fromId(json)
              .filter(_.startsWith("user"))
              .flatMap(_.drop(4).toLongOption)
              .map(UserId(_)),
          userName = c.get[String]("from").toOption.fold("user")(_.replace(' ', '_')),
          isBot = fromId(json).contains(botId.value),
          text = text,
          replyToMessageId = c.get[Long]("reply_to_message_id").toOption.map(MessageId(_))
        )

    def fromId(json: Json): Option[String] =
        json.hcursor.get[String]("from_id").toOption

    def flattenedText(json: Json): String =
        json.hcursor.downField("text").focus.fold("")(flatten)

    private def flatten(text: Json): String =
        text.asString
            .orElse(text.asArray.map(_.map(flattenPart).mkString))
            .getOrElse("")

    private def flattenPart(part: Json): String =
        part.asString
            .orElse(part.hcursor.get[String]("text").toOption)
            .getOrElse("")
