package nyetbot.repo

import cats.effect.IO
import nyetbot.model.*

trait SwearRepo:
    def getSwears: IO[List[SwearRow]]
    def addSwearGroup(groupChance: Chance): IO[Unit]
    def addSwear(groupId: SwearGroupId, swear: Swear, weight: Int): IO[Unit]
    def deleteSwearGroup(id: SwearGroupId): IO[Unit]
    def deleteSwear(id: SwearId): IO[Unit]
