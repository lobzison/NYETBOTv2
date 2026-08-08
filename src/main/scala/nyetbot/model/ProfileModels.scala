package nyetbot.model

import canoe.models.User
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.all.MaxLength
import io.github.iltotore.iron.constraint.any.Pure
import nyetbot.util.Text
import skunk.*
import skunk.codec.all.*

import java.time.OffsetDateTime

object ProfileModels:

    type UserId = UserId.T

    object UserId extends RefinedType[Long, Pure]

    type DisplayName = DisplayName.T

    object DisplayName extends RefinedType[String, Pure]

    type ProfileDescription = ProfileDescription.T

    object ProfileDescription extends RefinedType[String, MaxLength[300]]:
        def truncate(s: String): ProfileDescription =
            either(Text.truncate(s, 300)).getOrElse(ProfileDescription(""))

    final case class UserRef(id: UserId, displayName: DisplayName)

    object UserRef:
        def fromUser(u: User): UserRef =
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
                ProfileDescription
                    .either(desc)
                    .map(d => Profile(UserId(id), DisplayName(dn), d, ts))
            }
