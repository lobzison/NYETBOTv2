package nyetbot.service

import cats.Show
import cats.effect.IO
import nyetbot.model.*

trait SwearService:
    def showSwearGroups(using Show[Chance]): IO[String]
    def showSwears(groupId: SwearGroupId): IO[String]
    def getSwear: IO[Option[Swear]]
    def addSwearGroup(groupChance: Chance): IO[Unit]
    def addSwear(groupId: SwearGroupId, swear: Swear, weight: Int): IO[Unit]
    def deleteSwearGroup(id: SwearGroupId): IO[Unit]
    def deleteSwear(id: SwearId): IO[Unit]
    def swearGroupExists(groupId: SwearGroupId): IO[Boolean]
