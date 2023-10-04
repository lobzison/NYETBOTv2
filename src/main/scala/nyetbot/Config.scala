package nyetbot

import cats.effect.kernel.Sync
import cats.implicits.*
import cats.*
import java.net.URI
import cats.effect.kernel.Resource

case class Config(
    botToken: String,
    dbHost: String,
    dbPort: Int,
    dbName: String,
    dbUser: String,
    dbPassword: String,
    migrationsTable: String,
    migrationsLocations: List[String]
):
    val jdbcUrl = "jdbc:postgresql://" + dbHost + ':' + dbPort + '/' + dbName + "?sslmode=require"

object Config:
    def build[F[_]: Sync]: F[Config] =
        for
            botToken <- Sync[F].delay(sys.env("NYETBOT_KEY"))
            fullUrl  <- Sync[F].delay(sys.env("DATABASE_URL"))
        yield buildConfig(botToken, fullUrl)

    def buildConfig(botToken: String, fullDbUrl: String) =
        val dbUri = new URI(fullDbUrl)

        val username = dbUri.getUserInfo.split(":")(0)
        val password = dbUri.getUserInfo.split(":")(1)
        val host     = dbUri.getHost
        val port     = dbUri.getPort
        val dbName   = dbUri.getPath.stripPrefix("/")
        Config(
          botToken,
          host,
          port,
          dbName,
          username,
          password,
          "flyway",
          List("db")
        )

    def configResource[F[_]: Sync]: Resource[F, Config] =
        Resource.make(build[F])(_ => Sync[F].unit)
