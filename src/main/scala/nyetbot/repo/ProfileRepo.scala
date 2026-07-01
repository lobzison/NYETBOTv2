package nyetbot.repo

import cats.effect.IO
import nyetbot.model.Profile

trait ProfileRepo:
    def getProfile(userId: Long): IO[Option[Profile]]
    def upsertProfile(userId: Long, displayName: String, description: String): IO[Unit]
