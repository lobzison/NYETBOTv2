package nyetbot.repo

import cats.effect.*
import cats.implicits.*
import nyetbot.model.*
import skunk.*
import skunk.circe.codec.json.json
import skunk.codec.all.*
import skunk.implicits.*

class MemeRepoDB(s: Session[IO]) extends MemeRepo:
    def getAllMemes: IO[List[MemeRow]]               =
        val query =
            sql"select id, trigger, body, chance from memes order by id".query(
              MemeRow.memePersisted
            )
        s.execute(query)
    def addMeme(meme: MemeCreationRequest): IO[Unit] =
        val query =
            sql"insert into memes (trigger, body, chance) values ($text, $json, $int4)".command
                .to[MemeCreationRequestPersisted]
        s.prepareR(query).use(_.execute(meme.toPersistedRequest)).void
    def deleteMeme(memeId: MemeId): IO[Unit]         =
        val query = sql"delete from memes where id = $int4".command
        s.prepareR(query).use(_.execute(memeId.value)).void
