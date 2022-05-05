package nyetbot.service

import nyetbot.model.*
import cats.Show

trait SwearService[F[_]]:
    def showSwearGroups(using Show[Chance]): F[String]
    def showSwears(groupId: SwearGroupId): F[String]
    def getSwear: F[Option[Swear]]
    def addSwearGroup(groupChance: Chance): F[Unit]
    def addSwear(groupId: SwearGroupId, swear: Swear, weight: Int): F[Unit]
    def deleteSwearGroup(id: SwearGroupId): F[Unit]
    def deleteSwear(id: SwearId): F[Unit]
    def swearGroupExists(groupId: SwearGroupId): F[Boolean]
