package nyetbot.model

import canoe.models.messages.AnimationMessage
import canoe.models.messages.PhotoMessage
import canoe.models.messages.StickerMessage
import canoe.models.messages.TelegramMessage
import cats.MonadThrow
import cats.implicits.toFunctorOps
import cats.implicits.toTraverseOps
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.any.Pure
import skunk.*
import skunk.circe.codec.json.json
import skunk.codec.all.*

import scala.util.matching.Regex

object MemeModels:

    type MemeId = MemeId.T

    object MemeId extends RefinedType[Int, Pure]

    type MemeTrigger = MemeTrigger.T

    object MemeTrigger extends RefinedType[Regex, Pure]:
        extension (x: MemeTrigger)
            inline def toMemeTriggerUserSyntax: MemeTriggerUserSyntax =
                MemeTriggerUserSyntax(
                  x.value.toString.replaceAll(raw"\.\*", "%").replaceAll(raw"\.", "_")
                )

    type MemeTriggerUserSyntax = MemeTriggerUserSyntax.T

    object MemeTriggerUserSyntax extends RefinedType[String, Pure]:
        extension (x: MemeTriggerUserSyntax)
            inline def toMemeTriggered: MemeTrigger =
                MemeTrigger(x.value.replaceAll("%", ".*").replaceAll("_", ".").r)

    enum SupportedMemeType:
        case Sticker(sticker: canoe.models.Sticker)
        case PhotoSize(photo: canoe.models.PhotoSize)
        case Animation(animation: canoe.models.Animation)

        def toMessageContent =
            import canoe.syntax.*
            this match
                case Sticker(s)   => stickerMessageContent(s)
                case PhotoSize(p) => photoMessageContent(p)
                case Animation(a) => animationMessageContent(a)

    object SupportedMemeType:
        def fromTelegramMessage(m: TelegramMessage): Option[SupportedMemeType] =
            m match
                case stickerMessage: StickerMessage     =>
                    Some(SupportedMemeType.Sticker(stickerMessage.sticker))
                case imageMessage: PhotoMessage         =>
                    Some(SupportedMemeType.PhotoSize(imageMessage.photo.head))
                case animationMessage: AnimationMessage =>
                    Some(SupportedMemeType.Animation(animationMessage.animation))
                case _                                  => None

    case class Meme(id: MemeId, trigger: MemeTrigger, body: SupportedMemeType, chance: Chance)

    case class MemeRow(id: MemeId, trigger: MemeTriggerUserSyntax, body: Json, chance: Chance):
        def toMeme[F[_]: MonadThrow]: F[Meme] =
            for parsedBody <- MonadThrow[F].fromEither(body.as[SupportedMemeType])
            yield Meme(
              id,
              trigger.toMemeTriggered,
              parsedBody,
              chance
            )

    object MemeRow:
        val memePersisted: Decoder[MemeRow] =
            (int4 ~ text ~ json ~ int4).emap { case id ~ trigger ~ body ~ chance =>
                Chance
                    .either(chance)
                    .map(c => MemeRow(MemeId(id), MemeTriggerUserSyntax(trigger), body, c))
            }

        extension (memes: List[MemeRow])
            def toMemes[F[_]: MonadThrow]: F[List[Meme]] =
                memes.traverse(_.toMeme[F])

    case class MemeCreationRequestPersisted(trigger: String, body: Json, chance: Int)

    case class MemeCreationRequest(trigger: String, body: SupportedMemeType, chance: Chance):
        def toPersisted(id: MemeId): MemeRow                 =
            MemeRow(id, MemeTriggerUserSyntax(trigger), body.asJson, chance)
        def toPersistedRequest: MemeCreationRequestPersisted =
            MemeCreationRequestPersisted(trigger, body.asJson, chance.value)
