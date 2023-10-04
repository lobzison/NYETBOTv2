package nyetbot.vault

import skunk.Session
import nyetbot.model.*

import cats.effect.*
import skunk.*
import skunk.implicits.*
import skunk.codec.all.*
import skunk.circe.codec.json.json
import cats.Functor
import cats.implicits.*
import cats.Monad

class MemeVaultDB[F[_]: Concurrent](s: Session[F]) extends MemeVault[F]:
    def getAllMemes: F[List[MemeRow]]         =
        val query =
            sql"select id, trigger, body, chance from memes order by id".query(
              MemeRow.memePersisted
            )
        s.execute(query)
    def addMeme(meme: MemeCreationRequest): F[Unit] =
        val query =
            sql"insert into memes (trigger, body, chance) values ($text, $json, $int4)".command
                .to[MemeCreationRequestPersisted]
        s.prepareR(query).use(_.execute(meme.toPersistedRequest)).void
    def deleteMeme(memeId: MemeId): F[Unit]         =
        val query = sql"delete from memes where id = $int4".command
        s.prepareR(query).use(_.execute(memeId.value)).void
