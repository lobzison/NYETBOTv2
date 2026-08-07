package nyetbot.repo

import cats.effect.*
import cats.effect.std.Console
import fs2.io.net.Network
import nyetbot.config.DbConfig
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.trace.Tracer
import skunk.*

def buildSessionResource[F[_]: Temporal: Tracer: Meter: Network: Console](
    config: DbConfig
): Resource[F, Session[F]] =
    Session
        .Builder[F]
        .withHost(config.dbHost)
        .withPort(config.dbPort)
        .withUserAndPassword(config.dbUser, config.dbPassword)
        .withDatabase(config.dbName)
        .withSSL(SSL.None)
        .single
