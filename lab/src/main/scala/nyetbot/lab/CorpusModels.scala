package nyetbot.lab

import io.circe.Codec
import io.circe.Decoder
import io.circe.Encoder
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import nyetbot.model.ProfileModels.UserId

type MessageId = MessageId.T

object MessageId extends RefinedType[Long, Pure]

type ChatId = ChatId.T

object ChatId extends RefinedType[Long, Pure]

final case class BotId(value: String)

given Encoder[MessageId] = Encoder.encodeLong.contramap(_.value)
given Decoder[MessageId] = Decoder.decodeLong.map(MessageId(_))
given Encoder[ChatId]    = Encoder.encodeLong.contramap(_.value)
given Decoder[ChatId]    = Decoder.decodeLong.map(ChatId(_))
given Encoder[UserId]    = Encoder.encodeLong.contramap(_.value)
given Decoder[UserId]    = Decoder.decodeLong.map(UserId(_))

final case class CorpusMessage(
    id: MessageId,
    date: String,
    userId: Option[UserId],
    userName: String,
    isBot: Boolean,
    text: String,
    replyToMessageId: Option[MessageId]
) derives Codec.AsObject

final case class WindowMeta(
    sourceDump: String,
    chatId: ChatId,
    seed: Long,
    windowIndex: Int,
    startDate: String,
    endDate: String,
    messageCount: Int
) derives Codec.AsObject

final case class WindowTrigger(replyToBot: Boolean, replyToText: String) derives Codec.AsObject

final case class CorpusWindow(
    meta: WindowMeta,
    trigger: WindowTrigger,
    messages: List[CorpusMessage]
) derives Codec.AsObject
