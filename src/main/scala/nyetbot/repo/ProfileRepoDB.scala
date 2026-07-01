package nyetbot.repo

import cats.effect.*
import cats.implicits.*
import nyetbot.model.*
import skunk.*
import skunk.codec.all.*
import skunk.implicits.*

class ProfileRepoDB(s: Session[IO]) extends ProfileRepo:

    def getProfile(userId: Long): IO[Option[Profile]] =
        val query =
            sql"""select user_id, display_name, description, updated_at
                  from user_profile
                  where user_id = $int8""".query(Profile.codec)
        s.prepareR(query).use(_.option(userId))

    def upsertProfile(userId: Long, displayName: String, description: String): IO[Unit] =
        val cmd =
            sql"""insert into user_profile (user_id, display_name, description, updated_at)
                  values ($int8, $text, $text, now())
                  on conflict (user_id) do update
                    set display_name = excluded.display_name,
                        description  = excluded.description,
                        updated_at   = now()""".command
        s.prepareR(cmd).use(_.execute(userId, displayName, description)).void
