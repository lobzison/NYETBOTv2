package nyetbot.model

import canoe.models.{Sticker, PhotoSize, Animation}
import io.circe.Json
import io.circe.syntax.*
import io.circe.generic.auto.*
import skunk.codec.all.*
import skunk.circe.codec.json.json
import skunk.*
import cats.Show
import skunk.Decoder
import cats.MonadThrow
import cats.implicits.toFunctorOps
import cats.implicits.toTraverseOps

opaque type MemeId = Int
object MemeId:
    def apply(id: Int): MemeId           = id
    extension (x: MemeId) def value: Int = x

enum SupportedMemeType:
    case Sticker(sticker: canoe.models.Sticker)
    case PhotoSize(photo: canoe.models.PhotoSize)
    case Animation(animation: canoe.models.Animation)

case class Meme(id: MemeId, trigger: String, body: SupportedMemeType)

case class MemeCreationRequest(trigger: String, body: SupportedMemeType):
    def toPersisted(id: MemeId): MemePersisted           =
        MemePersisted(id, trigger, body.asJson)
    def toPersistedRequest: MemeCreationRequestPersisted =
        MemeCreationRequestPersisted(trigger, body.asJson)

case class MemeCreationRequestPersisted(trigger: String, body: Json)

case class Memes(triggers: Set[String], memes: Map[String, Meme])

case class MemePersisted(id: MemeId, trigger: String, body: Json):
    def toMeme[F[_]: MonadThrow]: F[Meme] =
        for parsedBody <- MonadThrow[F].fromEither(body.as[SupportedMemeType])
        yield Meme(
          id,
          trigger,
          parsedBody
        )

object MemePersisted:
    val memePersisted: Decoder[MemePersisted] =
        (int4 ~ text ~ json).map { case id ~ trigger ~ body =>
            MemePersisted(MemeId(id), trigger, body)
        }

case class MemesPersisted(memes: List[MemePersisted]):
    def toMemes[F[_]: MonadThrow]: F[Memes] =
        val memesF = memes.traverse(x => x.toMeme[F])
        memesF.map { memes =>
            Memes(
              triggers = memes.map(_.trigger).toSet,
              memes = memes.map(x => x.trigger -> x).toMap
            )
        }
