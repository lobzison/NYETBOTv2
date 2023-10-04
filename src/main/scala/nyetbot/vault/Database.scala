package nyetbot.vault

import cats.effect.*
import skunk.*
import skunk.implicits.*
import skunk.codec.all.*
import natchez.Trace.Implicits.noop
import skunk.SSL
import nyetbot.Config
import cats.*
import cats.implicits.*
import fs2.io.net.Network
import cats.effect.std.Console

def buildSessionResource[F[_]: Concurrent: Network: Console](
    config: Config
): Resource[F, Session[F]] =
    Session.single(
      host = config.dbHost,
      port = config.dbPort,
      user = config.dbUser,
      database = config.dbName,
      password = Some(config.dbPassword),
      ssl = SSL.Trusted
    )
