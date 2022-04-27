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
import scala.util.matching.Regex

opaque type MemeId = Int
object MemeId:
    def apply(id: Int): MemeId           = id
    extension (x: MemeId) def value: Int = x

opaque type SwearId = Int
object SwearId:
    def apply(id: Int): SwearId           = id
    extension (x: SwearId) def value: Int = x

opaque type Swear = String
object Swear:
    def apply(s: String): Swear            = s
    extension (x: Swear) def value: String = x

opaque type Chance = Int
object Chance:
    def apply(id: Int): Chance           = id
    extension (x: Chance) def value: Int = x

opaque type SwearGroupId = Int
object SwearGroupId:
    def apply(id: Int): SwearGroupId           = id
    extension (x: SwearGroupId) def value: Int = x

opaque type MemeTrigger = Regex
object MemeTrigger:
    def apply(s: Regex): MemeTrigger = s
    extension (x: MemeTrigger)
        def value: Regex                                   = x
        def toMemeTriggerUserSyntax: MemeTriggerUserSyntax =
            MemeTriggerUserSyntax(x.toString.replaceAll(raw"\.\*", "%").replaceAll(raw"\.", "_"))

opaque type MemeTriggerUserSyntax = String
object MemeTriggerUserSyntax:
    def apply(s: String): MemeTriggerUserSyntax = s
    extension (x: MemeTriggerUserSyntax)
        def value: String                = x
        def toMemeTriggered: MemeTrigger =
            MemeTrigger(x.replaceAll("%", ".*").replaceAll("_", ".").r)

enum SupportedMemeType:
    case Sticker(sticker: canoe.models.Sticker)
    case PhotoSize(photo: canoe.models.PhotoSize)
    case Animation(animation: canoe.models.Animation)

case class Meme(id: MemeId, trigger: MemeTrigger, body: SupportedMemeType, chance: Chance)

case class MemeCreationRequest(trigger: String, body: SupportedMemeType, chance: Int):
    def toPersisted(id: MemeId): MemePersisted           =
        MemePersisted(id, MemeTriggerUserSyntax(trigger), body.asJson, chance)
    def toPersistedRequest: MemeCreationRequestPersisted =
        MemeCreationRequestPersisted(trigger, body.asJson, chance)

case class MemeCreationRequestPersisted(trigger: String, body: Json, chance: Int)

case class Memes(memes: List[Meme])

case class MemePersisted(id: MemeId, trigger: MemeTriggerUserSyntax, body: Json, chance: Int):
    def toMeme[F[_]: MonadThrow]: F[Meme] =
        for parsedBody <- MonadThrow[F].fromEither(body.as[SupportedMemeType])
        yield Meme(
          id,
          trigger.toMemeTriggered,
          parsedBody,
          Chance(chance)
        )

object MemePersisted:
    val memePersisted: Decoder[MemePersisted] =
        (int4 ~ text ~ json ~ int4).map { case id ~ trigger ~ body ~ chance =>
            MemePersisted(MemeId(id), MemeTriggerUserSyntax(trigger), body, chance)
        }

case class MemesPersisted(memes: List[MemePersisted]):
    def toMemes[F[_]: MonadThrow]: F[Memes] =
        memes.traverse(x => x.toMeme[F]).map(Memes.apply)

case class SwearRow(
    groupId: SwearGroupId,
    groupChance: Chance,
    id: SwearId,
    swear: Swear,
    weight: Int
)

object SwearRow:
    val swearRow: Decoder[SwearRow] =
        (int4 ~ int4 ~ int4 ~ text ~ int4).map { case groupId ~ groupChance ~ id ~ swear ~ weight =>
            SwearRow(
              SwearGroupId(groupId),
              Chance(groupChance),
              SwearId(id),
              Swear(swear),
              weight
            )
        }
