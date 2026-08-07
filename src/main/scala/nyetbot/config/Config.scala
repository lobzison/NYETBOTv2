package nyetbot.config

import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import pureconfig.ConfigReader
import pureconfig.ConfigSource

import java.net.URI

case class Config(
    botToken: String,
    ollamaDomain: String,
    dbConfig: Config.DbConfig,
    llmConfig: Config.LlmConfig,
    ollamaConfig: Config.OllamaConfig
)

object Config:
    final case class ReplyConfig(
        minChars: Int,
        meanFactor: Double,
        spread: Double,
        maxChars: Int
    ) derives ConfigReader

    final case class LlmConfig(
        botName: String,
        botAlias: String,
        userPrefix: String,
        inputPrefix: String,
        messageEvery: Int,
        chatBufferSize: Int,
        replyContextWindow: Int,
        topicContextWindow: Int,
        recentUserMessages: Int,
        profileMaxChars: Int,
        summaryMaxChars: Int,
        reply: ReplyConfig
    ) derives ConfigReader

    final case class OllamaConfig(
        port: Int,
        replyModel: String,
        utilityModel: String,
        replyTemperature: Double,
        utilityTemperature: Double,
        replyNumPredict: Int,
        summaryNumPredict: Int,
        rewriteNumPredict: Int,
        intentNumPredict: Int,
        topicNumPredict: Int,
        registerNumPredict: Int,
        numCtx: Int,
        think: Boolean,
        requestTimeoutMinutes: Int,
        idleTimeoutMinutes: Int
    ) derives ConfigReader:
        def uri(domain: String): String = s"http://$domain:$port"

    final case class RawConfig(
        botToken: String,
        databaseUrl: String,
        ollamaDomain: String,
        llm: LlmConfig,
        ollama: OllamaConfig
    ) derives ConfigReader

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
            "jdbc:postgresql://" + dbHost + ':' + dbPort + '/' + dbName + "?sslmode=require"

    def build[F[_]: Sync]: F[Config] = Sync[F].delay {
        val settings = ConfigSource.default.at("nyetbot").loadOrThrow[RawConfig]

        Config(
          botToken = settings.botToken,
          ollamaDomain = settings.ollamaDomain,
          dbConfig = buildDbConfig(settings.databaseUrl),
          llmConfig = settings.llm,
          ollamaConfig = settings.ollama
        )
    }

    def buildDbConfig(fullDbUrl: String): DbConfig =
        val dbUri = new URI(fullDbUrl)

        val username = dbUri.getUserInfo.split(":")(0)
        val password = dbUri.getUserInfo.split(":")(1)
        val host     = dbUri.getHost
        val port     = dbUri.getPort
        val dbName   = dbUri.getPath.stripPrefix("/")
        DbConfig(host, port, dbName, username, password, "flyway", List("db"))

    def configResource[F[_]: Sync]: Resource[F, Config] =
        Resource.eval(build[F])
