package nyetbot.vault

import skunk.Session
import nyetbot.model.*

import cats.effect.*
import skunk.*
import skunk.implicits.*
import skunk.codec.all.*
import natchez.Trace.Implicits.noop
import skunk.circe.codec.json.json
import cats.Functor
import cats.implicits.*
import cats.Monad

class MemeVaultDB[F[_]: Concurrent](s: Session[F]) extends MemeVault[F]:
    def getAllMemes: F[MemesPersisted]              =
        val query = sql"select id, trigger, body from memes".query(MemePersisted.memePersisted)
        s.execute(query).map(MemesPersisted.apply)
    def addMeme(meme: MemeCreationRequest): F[Unit] =
        val query = sql"insert into memes (trigger, body) values ($text, $json)".command
            .gcontramap[MemeCreationRequestPersisted]
        s.prepare(query).use(_.execute(meme.toPersistedRequest)).void
    def deleteMeme(memeId: MemeId): F[Unit]         =
        val query = sql"delete from memes where id = $int4".command
        s.prepare(query).use(_.execute(memeId.value)).void
