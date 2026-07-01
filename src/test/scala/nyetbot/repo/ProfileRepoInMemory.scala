package nyetbot.repo

import cats.effect.IO
import cats.effect.Ref
import nyetbot.model.Profile

import java.time.OffsetDateTime

class ProfileRepoInMemory(state: Ref[IO, Map[Long, Profile]]) extends ProfileRepo:
    def getProfile(userId: Long): IO[Option[Profile]] =
        state.get.map(_.get(userId))

    def upsertProfile(userId: Long, displayName: String, description: String): IO[Unit] =
        state.update(
          _.updated(userId, Profile(userId, displayName, description, OffsetDateTime.MIN))
        )

object ProfileRepoInMemory:
    def create: IO[ProfileRepoInMemory] =
        Ref.of[IO, Map[Long, Profile]](Map.empty).map(ProfileRepoInMemory(_))
