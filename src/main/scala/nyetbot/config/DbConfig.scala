package nyetbot.config

import java.net.URI

case class DbConfig(
    dbHost: String,
    dbPort: Int,
    dbName: String,
    dbUser: String,
    dbPassword: String,
    migrationsTable: String,
    migrationsLocations: List[String]
):
    val jdbcUrl =
        "jdbc:postgresql://" + dbHost + ':' + dbPort + '/' + dbName + "?ssl=false"

object DbConfig:
    def apply(fullDbUrl: String): DbConfig =
        val dbUri = new URI(fullDbUrl)

        val username = dbUri.getUserInfo.split(":")(0)
        val password = dbUri.getUserInfo.split(":")(1)
        val host     = dbUri.getHost
        val port     = dbUri.getPort
        val dbName   = dbUri.getPath.stripPrefix("/")
        DbConfig(host, port, dbName, username, password, "flyway", List("db"))
