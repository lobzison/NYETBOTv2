package nyetbot.vault

import fly4s.core.*
import fly4s.core.data.*
import nyetbot.Config
import cats.effect.kernel.Resource
import cats.effect.kernel.Async

def fly4sRes[F[_]: Async](config: Config): Resource[F, Fly4s[F]] = Fly4s.make[F](
  url = config.jdbcUrl,
  user = Some(config.dbUser),
  password = Some(config.dbPassword.toArray),
  config = Fly4sConfig(
    table = config.migrationsTable,
    locations = Location.of(config.migrationsLocations)
  )
)
