package nyetbot.service

import nyetbot.model.*
import nyetbot.vault.SwearVault
import cats.Show
import cats.implicits.*
import cats.MonadThrow
import cats.effect.std.Random

class SwearServiceImpl[F[_]: MonadThrow: Random](vault: SwearVault[F]) extends SwearService[F]:
    def showSwearGroups(using Show[Chance]): F[String] =
        val header = List("id", "chance")
        for
            swears <- vault.getSwears
            body    = swears.map(s => List(s.groupId.value.toString, s.groupChance.show))
            drawer <- TableDrawer.create[F](header.length, header :: body)
        yield drawer.buildHtmlCodeTable
    def showSwears(groupId: SwearGroupId): F[String]   =
        val header = List("id", "swear", "weight")
        for
            swears <- vault.getSwears
            body    = swears.map(s => List(s.id.value.toString, s.swear.value, s.weight.toString))
            drawer <- TableDrawer.create[F](header.length, header :: body)
        yield drawer.buildHtmlCodeTable

    def getSwear: F[Option[Swear]] = 
        // for
        //     r           <- Random[F].betweenFloat(0f, 1f)
        //     randomSwear <- Random[F].betweenInt(0, swears.size).map(swears(_))
        // yield Option.when(r < 1f / swearEveryNMessage) { randomSwear }
        ???

    def addSwearGroup(groupChance: Chance): F[Unit]                         =
        vault.addSwearGroup(groupChance)
    def addSwear(groupId: SwearGroupId, swear: Swear, weight: Int): F[Unit] =
        vault.addSwear(groupId, swear, weight)
    def deleteSwearGroup(id: SwearGroupId): F[Unit]                         =
        vault.deleteSwearGroup(id)
    def deleteSwear(id: SwearId): F[Unit]                                   =
        vault.deleteSwear(id)
