package nyetbot.model

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.all.Empty
import io.github.iltotore.iron.constraint.all.Not
import io.github.iltotore.iron.constraint.all.Positive
import io.github.iltotore.iron.constraint.any.Pure
import skunk.*
import skunk.codec.all.*

type Swear = Swear.T

object Swear extends RefinedType[String, Not[Empty]]

type SwearId = SwearId.T

object SwearId extends RefinedType[Int, Pure]

type SwearGroupId = SwearGroupId.T

object SwearGroupId extends RefinedType[Int, Pure]

type Weight = Weight.T

object Weight extends RefinedType[Int, Positive]

case class SwearGroup(totalWeight: Int, swears: List[SwearRow])

case class SwearMemoryStorage(
    swearRows: List[SwearRow],
    swearGroupsOrdered: List[(SwearGroupId, Chance)],
    groupedSwears: Map[SwearGroupId, SwearGroup]
)

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
