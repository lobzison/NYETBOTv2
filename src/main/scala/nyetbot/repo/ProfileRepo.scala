package nyetbot.repo

import cats.effect.IO
import nyetbot.model.*

trait ProfileRepo:
    def getProfile(userId: UserId): IO[Option[Profile]]
    def upsertProfile(
        userId: UserId,
        displayName: DisplayName,
        description: ProfileDescription
    ): IO[Unit]
