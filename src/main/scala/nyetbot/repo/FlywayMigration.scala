package nyetbot.repo

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import fly4s.*
import fly4s.data.*
import nyetbot.Config

def fly4sRes[F[_]: Async](config: Config.DbConfig): Resource[F, Fly4s[F]] = Fly4s.make[F](
  url = config.jdbcUrl,
  user = Some(config.dbUser),
  password = Some(config.dbPassword.toArray),
  config =
      Fly4sConfig(table = config.migrationsTable, locations = Locations(config.migrationsLocations))
)
