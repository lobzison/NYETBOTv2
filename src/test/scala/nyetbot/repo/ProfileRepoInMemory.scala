package nyetbot.repo

import cats.effect.IO
import cats.effect.Ref
import nyetbot.model.*

import java.time.OffsetDateTime

class ProfileRepoInMemory(state: Ref[IO, Map[UserId, Profile]]) extends ProfileRepo:
    def getProfile(userId: UserId): IO[Option[Profile]] =
        state.get.map(_.get(userId))

    def upsertProfile(
        userId: UserId,
        displayName: DisplayName,
        description: ProfileDescription
    ): IO[Unit] =
        state.update(
          _.updated(userId, Profile(userId, displayName, description, OffsetDateTime.MIN))
        )

object ProfileRepoInMemory:
    def create: IO[ProfileRepoInMemory] =
        Ref.of[IO, Map[UserId, Profile]](Map.empty).map(ProfileRepoInMemory(_))
