package nyetbot.repo

import nyetbot.model.*

trait SwearRepo[F[_]]:
    def getSwears: F[List[SwearRow]]
    def addSwearGroup(groupChance: Chance): F[Unit]
    def addSwear(groupId: SwearGroupId, swear: Swear, weight: Int): F[Unit]
    def deleteSwearGroup(id: SwearGroupId): F[Unit]
    def deleteSwear(id: SwearId): F[Unit]
