package nyetbot.service

import cats.Show
import cats.effect.IO
import cats.effect.kernel.Ref
import cats.effect.std.Random
import cats.implicits.*
import nyetbot.model.*
import nyetbot.repo.SwearRepo

class SwearServiceCached(
    vault: SwearRepo,
    inMemory: Ref[IO, SwearMemoryStorage]
)(using Random[IO])
    extends SwearService:
    def showSwearGroups(using Show[Chance]): IO[String] =
        val header = List("id", "chance")
        for
            swearStorage <- inMemory.get
            body          =
                swearStorage.swearRows
                    .map(s => List(s.groupId.value.toString, s.groupChance.show))
                    .distinct
            drawer       <- TableDrawer.create[IO](header.length, header :: body)
        yield drawer.buildHtmlCodeTable
    def showSwears(groupId: SwearGroupId): IO[String]   =
        val header = List("id", "swear", "weight")
        for
            swearStorage <- inMemory.get
            body          = swearStorage.swearRows.map(s =>
                                List(s.id.value.toString, s.swear.value, s.weight.toString)
                            )
            drawer       <- TableDrawer.create[IO](header.length, header :: body)
        yield drawer.buildHtmlCodeTable

    def getSwear: IO[Option[Swear]] =
        for
            swearStorage <- inMemory.get
            groupId      <-
                swearStorage.swearGroupsOrdered.traverse(g => rollForOneGroup(g._1, g._2))
            swearGroup    = groupId.headOption.flatten
            swear        <- swearGroup.traverse(rollSwearInGroup)
        yield swear

    private def rollForOneGroup(id: SwearGroupId, chance: Chance): IO[Option[SwearGroupId]] =
        Random[IO].betweenInt(0, chance.value).map(r => Option.when(r == 0)(id))

    private def rollSwearInGroup(id: SwearGroupId): IO[Swear] =
        for
            swearStorage <- inMemory.get
            group         = swearStorage.groupedSwears(id)
            r            <- Random[IO].betweenInt(0, group.totalWeight)
        yield selectSwearWeighted(group.swears, r)

    private def selectSwearWeighted(swears: List[SwearRow], roll: Int): Swear =
        // technically can be replaced with a binary search
        // in practice the search will be over quite small list
        // and triggered quire rarely
        val (swear, _) = swears.foldLeft((None: Option[Swear], 0)) {
            case ((maybeSwear, currentWeight), currentSwear) =>
                val nextWeight = currentWeight + currentSwear.weight
                if roll >= currentWeight && roll < nextWeight then
                    (Some(currentSwear.swear), nextWeight)
                else (maybeSwear, nextWeight)
        }
        swear.getOrElse(swears.head.swear)

    def addSwearGroup(groupChance: Chance): IO[Unit] =
        vault.addSwearGroup(groupChance) >> updateInMemoryRepresentation

    def addSwear(groupId: SwearGroupId, swear: Swear, weight: Int): IO[Unit] =
        val performUpdate = vault.addSwear(groupId, swear, weight) >> updateInMemoryRepresentation
        val raiseError    = IO.raiseError[Unit](
          new IllegalArgumentException(s"Swear group with id $groupId does not exist")
        )
        for
            swearGroupExists <- swearGroupExists(groupId)
            _                <- if swearGroupExists then performUpdate else raiseError
        yield ()

    def swearGroupExists(groupId: SwearGroupId): IO[Boolean] =
        for swearStorage <- inMemory.get
        yield swearStorage.groupedSwears.contains(groupId)

    def deleteSwearGroup(id: SwearGroupId): IO[Unit] =
        vault.deleteSwearGroup(id) >> updateInMemoryRepresentation

    def deleteSwear(id: SwearId): IO[Unit] =
        vault.deleteSwear(id) >> updateInMemoryRepresentation

    private val updateInMemoryRepresentation: IO[Unit] =
        for
            swearRows <- vault.getSwears
            _         <- inMemory.set(SwearServiceCached.createInMemoryRepresentation(swearRows))
        yield ()

object SwearServiceCached:

    def apply(vault: SwearRepo)(using Random[IO]): IO[SwearServiceCached] =
        for
            swearRows <- vault.getSwears
            inMemory  <-
                Ref.of[IO, SwearMemoryStorage](
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
