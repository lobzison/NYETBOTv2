package nyetbot

import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import com.typesafe.config.ConfigFactory

import java.net.URI

// Secrets come from the environment (NYETBOT_KEY, DATABASE_URL, OLLAMA_DOMAIN); every
// non-secret tunable comes from src/main/resources/application.conf (overridable via
// -Dconfig.file=...). LLM_MESSAGE_EVERY is still honoured as an env override.
case class Config(
    botToken: String,
    dbConfig: Config.DbConfig,
    llmConfig: Config.LlmConfig,
    ollamaConfig: Config.OllamaConfig
)

object Config:
    final case class LlmConfig(
        botName: String,
        botAlias: String,
        userPrefix: String,
        inputPrefix: String,
        llmMessageEvery: Int,
        chatBufferSize: Int,
        replyContextWindow: Int,
        recentUserMessages: Int,
        profileMaxChars: Int,
        summaryMaxChars: Int,
        replyMinChars: Int,
        replyMeanFactor: Double,
        replySpread: Double,
        replyMaxChars: Int
    )

    final case class OllamaConfig(
        uri: String,
        replyModel: String,
        utilityModel: String,
        replyTemperature: Double,
        utilityTemperature: Double,
        replyNumPredict: Int,
        summaryNumPredict: Int,
        rewriteNumPredict: Int,
        intentNumPredict: Int,
        numCtx: Int,
        think: Boolean,
        requestTimeoutMinutes: Int,
        idleTimeoutMinutes: Int
    )

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
        val root = ConfigFactory.load().getConfig("nyetbot")
        val llm  = root.getConfig("llm")
        val oll  = root.getConfig("ollama")

        val botToken     = sys.env("NYETBOT_KEY")
        val databaseUrl  = sys.env("DATABASE_URL")
        val ollamaDomain = sys.env("OLLAMA_DOMAIN")

        // Env override kept for compatibility with the previous deployment.
        val messageEvery =
            sys.env.get("LLM_MESSAGE_EVERY").map(_.toInt).getOrElse(llm.getInt("message-every"))

        val llmConfig = LlmConfig(
          botName = llm.getString("bot-name"),
          botAlias = llm.getString("bot-alias"),
          userPrefix = llm.getString("user-prefix"),
          inputPrefix = llm.getString("input-prefix"),
          llmMessageEvery = messageEvery,
          chatBufferSize = llm.getInt("chat-buffer-size"),
          replyContextWindow = llm.getInt("reply-context-window"),
          recentUserMessages = llm.getInt("recent-user-messages"),
          profileMaxChars = llm.getInt("profile-max-chars"),
          summaryMaxChars = llm.getInt("summary-max-chars"),
          replyMinChars = llm.getInt("reply.min-chars"),
          replyMeanFactor = llm.getDouble("reply.mean-factor"),
          replySpread = llm.getDouble("reply.spread"),
          replyMaxChars = llm.getInt("reply.max-chars")
        )

        val ollamaConfig = OllamaConfig(
          uri = s"http://$ollamaDomain:${oll.getInt("port")}",
          replyModel = oll.getString("reply-model"),
          utilityModel = oll.getString("utility-model"),
          replyTemperature = oll.getDouble("reply-temperature"),
          utilityTemperature = oll.getDouble("utility-temperature"),
          replyNumPredict = oll.getInt("reply-num-predict"),
          summaryNumPredict = oll.getInt("summary-num-predict"),
          rewriteNumPredict = oll.getInt("rewrite-num-predict"),
          intentNumPredict = oll.getInt("intent-num-predict"),
          numCtx = oll.getInt("num-ctx"),
          think = oll.getBoolean("think"),
          requestTimeoutMinutes = oll.getInt("request-timeout-minutes"),
          idleTimeoutMinutes = oll.getInt("idle-timeout-minutes")
        )

        Config(botToken, buildDbConfig(databaseUrl), llmConfig, ollamaConfig)
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
