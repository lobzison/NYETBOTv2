package nyetbot.service

import nyetbot.model.*
import nyetbot.vault.SwearVault
import cats.Show
import cats.implicits.*
import cats.MonadThrow
import cats.effect.std.Random
import cats.effect.kernel.Ref
import cats.effect.kernel.Concurrent

class SwearServiceCached[F[_]: MonadThrow: Random](
    vault: SwearVault[F],
    inMemory: Ref[F, SwearMemoryStorage]
) extends SwearService[F]:
    def showSwearGroups(using Show[Chance]): F[String] =
        val header = List("id", "chance")
        for
            swearStorage <- inMemory.get
            body          =
                swearStorage.swearRows
                    .map(s => List(s.groupId.value.toString, s.groupChance.show))
                    .distinct
            drawer       <- TableDrawer.create[F](header.length, header :: body)
        yield drawer.buildHtmlCodeTable
    def showSwears(groupId: SwearGroupId): F[String]   =
        val header = List("id", "swear", "weight")
        for
            swearStorage <- inMemory.get
            body          = swearStorage.swearRows.map(s =>
                                List(s.id.value.toString, s.swear.value, s.weight.toString)
                            )
            drawer       <- TableDrawer.create[F](header.length, header :: body)
        yield drawer.buildHtmlCodeTable

    def getSwear: F[Option[Swear]] =
        for
            swearStorage <- inMemory.get
            groupId      <-
                swearStorage.swearGroupsOrdered.traverse(g => rollForOneGroup(g._1, g._2))
            swearGroup    = groupId.headOption.flatten
            swear        <- swearGroup.traverse(rollSwearInGroup)
        yield swear

    private def rollForOneGroup(id: SwearGroupId, chance: Chance): F[Option[SwearGroupId]] =
        Random[F].betweenInt(0, chance.value).map(r => Option.when(r == 0)(id))

    private def rollSwearInGroup(id: SwearGroupId): F[Swear] =
        for
            swearStorage <- inMemory.get
            group         = swearStorage.groupedSwears(id)
            r            <- Random[F].betweenInt(0, group.totalWeight)
        yield selectSwearWeighted(group.swears, r)

    private def selectSwearWeighted(swears: List[SwearRow], roll: Int): Swear =
        // technically can be replaced with a binary search
        // in practice the search will be over quite small list
        // and triggered quire rarely
        val (swear, _) = swears.foldLeft((None: Option[Swear], 0)) {
            case ((maybeSwear, currentWeight), currentSwear) =>
                val nextWeight = currentWeight + currentSwear.weight
                if (roll >= currentWeight && roll < nextWeight) then
                    (Some(currentSwear.swear), nextWeight)
                else (maybeSwear, nextWeight)
        }
        swear.getOrElse(swears.head.swear)

    def addSwearGroup(groupChance: Chance): F[Unit] =
        vault.addSwearGroup(groupChance) >> updateInMemoryRepresentation

    def addSwear(groupId: SwearGroupId, swear: Swear, weight: Int): F[Unit] =
        vault.addSwear(groupId, swear, weight) >> updateInMemoryRepresentation

    def deleteSwearGroup(id: SwearGroupId): F[Unit] =
        vault.deleteSwearGroup(id) >> updateInMemoryRepresentation

    def deleteSwear(id: SwearId): F[Unit] =
        vault.deleteSwear(id) >> updateInMemoryRepresentation

    private val updateInMemoryRepresentation: F[Unit] =
        for
            swearRows <- vault.getSwears
            _         <- inMemory.set(SwearServiceCached.createInMemoryRepresentation(swearRows))
        yield ()

object SwearServiceCached:

    def apply[F[_]: Concurrent: Random](vault: SwearVault[F]): F[SwearServiceCached[F]] =
        for
            swearRows <- vault.getSwears
            inMemory  <-
                Ref.of[F, SwearMemoryStorage](
                  SwearServiceCached.createInMemoryRepresentation(swearRows)
                )
        yield new SwearServiceCached(vault, inMemory)

    def createInMemoryRepresentation(swearRows: List[SwearRow]): SwearMemoryStorage =
        val swearGroupsOrdered =
            swearRows.map(s => (s.groupId, s.groupChance)).distinct.sortWith {
                case ((_, chance1), (_, chance2)) => chance1.value < chance2.value
            }
        val swearsById         = swearRows.groupBy(_.groupId).map { case id -> swears =>
            id -> SwearGroup(swears.map(_.weight).sum, swears)
        }
        SwearMemoryStorage(swearRows, swearGroupsOrdered, swearsById)
