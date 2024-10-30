package nyetbot.repo

import cats.*
import cats.effect.*
import cats.effect.std.Console
import fs2.io.net.Network
import nyetbot.Config
import org.typelevel.otel4s.trace.Tracer
import skunk.*

def buildSessionResource[F[_]: Temporal: Tracer: Network: Console](
    config: Config.DbConfig
): Resource[F, Session[F]] =
    Session.single(
      host = config.dbHost,
      port = config.dbPort,
      user = config.dbUser,
      database = config.dbName,
      password = Some(config.dbPassword),
      ssl = SSL.Trusted
    )
