package nyetbot.vault

import nyetbot.model.*
import skunk.*
import skunk.implicits.*
import skunk.codec.all.*
import cats.effect.*
import cats.implicits.*

class SwearVaultImpl[F[_]: Concurrent](s: Session[F]) extends SwearVault[F]:
    def getSwears: F[List[SwearRow]]                                        =
        val query =
            sql"""select sg.id, sg.chance, s.id, s.swear, s.weight from
                swear_group sg join swear s on sg.id = s.group_id 
                order by sg.id, s.id""".query(SwearRow.swearRow)
        s.execute(query)
    def addSwearGroup(groupChance: Chance): F[Unit]                         =
        val query =
            sql"""insert into swear_group (chance) values ($int4)""".command
        s.prepare(query).use(_.execute(groupChance.value)).void
    def addSwear(groupId: SwearGroupId, swear: Swear, weight: Int): F[Unit] =
        val query =
            sql"""insert into swear (group_id, swear, weight) values ($int4, $text, $int4)""".command
        s.prepare(query).use(_.execute((groupId.value, swear.value), weight)).void
    def deleteSwearGroup(id: SwearGroupId): F[Unit]                         =
        val query =
            sql"""delete from swear_group where id = $int4""".command
        s.prepare(query).use(_.execute(id.value)).void
    def deleteSwear(id: SwearId): F[Unit]                                   =
        val query =
            sql"""delete from swear where id = $int4""".command
        s.prepare(query).use(_.execute(id.value)).void
