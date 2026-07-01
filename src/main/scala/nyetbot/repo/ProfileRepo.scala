package nyetbot.repo

import cats.effect.IO
import nyetbot.model.Profile

// Persistence of the global per-user behavioural profile. Kept minimal on purpose:
// the orchestration (when to read/rewrite) lives in ProfileService.
trait ProfileRepo:
    def getProfile(userId: Long): IO[Option[Profile]]
    def upsertProfile(userId: Long, displayName: String, description: String): IO[Unit]
