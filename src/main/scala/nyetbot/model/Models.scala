package nyetbot.model

import canoe.models.Animation
import canoe.models.PhotoSize
import canoe.models.Sticker
import canoe.models.messages.AnimationMessage
import canoe.models.messages.PhotoMessage
import canoe.models.messages.StickerMessage
import canoe.models.messages.TelegramMessage
import canoe.models.messages.TextMessage
import cats.MonadThrow
import cats.implicits.toFunctorOps
import cats.implicits.toTraverseOps
import io.circe.Json
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import nyetbot.Config.LlmConfig
import nyetbot.util.Text
import skunk.*
import skunk.circe.codec.json.json
import skunk.codec.all.*

import java.time.OffsetDateTime
import scala.util.matching.Regex

opaque type MemeId = Int
object MemeId:
    inline def apply(id: Int): MemeId           = id
    extension (x: MemeId) inline def value: Int = x

opaque type SwearId = Int
object SwearId:
    inline def apply(id: Int): SwearId           = id
    extension (x: SwearId) inline def value: Int = x

type Swear = Swear.T
object Swear extends RefinedType[String, Not[Empty]]

type Chance = Chance.T
object Chance extends RefinedType[Int, Positive]

type Weight = Weight.T
object Weight extends RefinedType[Int, Positive]

type ProfileDescription = ProfileDescription.T
object ProfileDescription extends RefinedType[String, MaxLength[300]]:
    def truncate(s: String): ProfileDescription =
        either(Text.truncate(s, 300)).getOrElse(ProfileDescription(""))

opaque type UserId = Long
object UserId:
    inline def apply(id: Long): UserId           = id
    extension (x: UserId) inline def value: Long = x

opaque type DisplayName = String
object DisplayName:
    inline def apply(s: String): DisplayName            = s
    extension (x: DisplayName) inline def value: String = x

opaque type SwearGroupId = Int
object SwearGroupId:
    inline def apply(id: Int): SwearGroupId           = id
    extension (x: SwearGroupId) inline def value: Int = x

opaque type MemeTrigger = Regex
object MemeTrigger:
    inline def apply(s: Regex): MemeTrigger = s
    extension (x: MemeTrigger)
        inline def value: Regex                                   = x
        inline def toMemeTriggerUserSyntax: MemeTriggerUserSyntax =
            MemeTriggerUserSyntax(x.toString.replaceAll(raw"\.\*", "%").replaceAll(raw"\.", "_"))

opaque type MemeTriggerUserSyntax = String
object MemeTriggerUserSyntax:
    def apply(s: String): MemeTriggerUserSyntax = s
    extension (x: MemeTriggerUserSyntax)
        inline def value: String                = x
        inline def toMemeTriggered: MemeTrigger =
            MemeTrigger(x.replaceAll("%", ".*").replaceAll("_", ".").r)

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

case class MemeCreationRequest(trigger: String, body: SupportedMemeType, chance: Chance):
    def toPersisted(id: MemeId): MemeRow                 =
        MemeRow(id, MemeTriggerUserSyntax(trigger), body.asJson, chance)
    def toPersistedRequest: MemeCreationRequestPersisted =
        MemeCreationRequestPersisted(trigger, body.asJson, chance.value)

case class MemeCreationRequestPersisted(trigger: String, body: Json, chance: Int)

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

case class SwearRow(
    groupId: SwearGroupId,
    groupChance: Chance,
    id: SwearId,
    swear: Swear,
    weight: Weight
)

object SwearRow:
    val swearRow: Decoder[SwearRow] =
        (int4 ~ int4 ~ int4 ~ text ~ int4).emap {
            case groupId ~ groupChance ~ id ~ swear ~ weight =>
                for
                    chance <- Chance.either(groupChance)
                    sw     <- Swear.either(swear)
                    wt     <- Weight.either(weight)
                yield SwearRow(SwearGroupId(groupId), chance, SwearId(id), sw, wt)
        }

case class SwearMemoryStorage(
    swearRows: List[SwearRow],
    swearGroupsOrdered: List[(SwearGroupId, Chance)],
    groupedSwears: Map[SwearGroupId, SwearGroup]
)

case class SwearGroup(totalWeight: Int, swears: List[SwearRow])

final case class LlmContextMessage(userId: Option[UserId], userName: String, text: String)

object LlmContextMessage:
    def fromTextMessage(t: TextMessage, config: LlmConfig): LlmContextMessage =
        val user = t.from
            .map(u => s"${config.userPrefix}${u.firstName}_${u.lastName.getOrElse("")}")
            .getOrElse("user")
        LlmContextMessage(t.from.map(u => UserId(u.id)), user, t.text)

final case class UserRef(id: UserId, displayName: DisplayName)
object UserRef:
    def fromUser(u: canoe.models.User): UserRef =
        val last   = u.lastName.map(" " + _).getOrElse("")
        val handle = u.username.map(n => s" (@$n)").getOrElse("")
        UserRef(UserId(u.id), DisplayName(s"${u.firstName}$last$handle"))

final case class Profile(
    userId: UserId,
    displayName: DisplayName,
    description: ProfileDescription,
    updatedAt: OffsetDateTime
)
object Profile:
    val codec: Decoder[Profile] =
        (int8 ~ text ~ text ~ timestamptz).emap { case id ~ dn ~ desc ~ ts =>
            ProfileDescription.either(desc).map(d => Profile(UserId(id), DisplayName(dn), d, ts))
        }
