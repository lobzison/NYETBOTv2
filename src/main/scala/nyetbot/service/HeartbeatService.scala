package nyetbot.service

import cats.effect.{IO, Ref, Temporal}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

trait HeartbeatService:
    def beat: IO[Unit]
    def isAlive: IO[Boolean]

object HeartbeatService:
    def apply(): IO[HeartbeatService] =
        for
            now <- Temporal[IO].monotonic
            r   <- Ref.of[IO, FiniteDuration](now)
        yield new HeartbeatService:
            def beat: IO[Unit] =
                for
                    now <- Temporal[IO].monotonic
                    _   <- r.set(now)
                yield ()

            def isAlive: IO[Boolean] =
                for
                    now <- Temporal[IO].monotonic
                    res <- r.get.map(lastBeat => now - lastBeat < 10.minutes)
                yield res
