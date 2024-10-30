package nyetbot

import cats.effect.kernel.Sync
import cats.implicits.*
import cats.*

import java.net.URI
import cats.effect.kernel.Resource
import nyetbot.Config.OllamaConfig

import org.http4s.implicits.*
import org.http4s.Uri

case class Config(
    botToken: String,
    dbConfig: Config.DbConfig,
    llmConfig: Config.LlmConfig,
    translateConfig: Config.TranslateConfig,
    ollamaConfig: OllamaConfig
)

object Config:
    case class LlmConfig(
        botName: String,
        userPrefix: String,
        inputPrefix: String,
        promptPrefix: String,
        promptSuffix: String,
        llmMessageEvery: Int,
        botAlias: String
    )

    case class OllamaConfig(
        uri: String
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

    case class TranslateConfig(
        uri: Uri,
        token: String
    )

    def build[F[_]: Sync]: F[Config] =
        for
            botToken        <- Sync[F].delay(sys.env("NYETBOT_KEY"))
            fullUrl         <- Sync[F].delay(sys.env("DATABASE_URL"))
            translateKey    <- Sync[F].delay(sys.env("TRANSLATE_KEY"))
            llmMessageEvery <- Sync[F].delay(sys.env("LLM_MESSAGE_EVERY").toInt)
            ollamaDomain    <- Sync[F].delay(sys.env("OLLAMA_DOMAIN"))
        yield buildConfig(
          botToken,
          fullUrl,
          translateKey,
          llmMessageEvery,
          ollamaDomain
        )

    def buildConfig(
        botToken: String,
        fullDbUrl: String,
        translateKey: String,
        llmMessageEvery: Int,
        ollamaDomain: String
    ) =
        val dbConfig        = buildDbConfig(fullDbUrl)
        val llmConfig       = buildLlmConfig(llmMessageEvery)
        val translateConfig =
            TranslateConfig(uri"https://api-free.deepl.com/v2/translate", translateKey)
        val ollama          = OllamaConfig(s"http://$ollamaDomain:11434")
        Config(
          botToken,
          dbConfig,
          llmConfig,
          translateConfig,
          ollama
        )

    def buildDbConfig(fullDbUrl: String): DbConfig =
        val dbUri = new URI(fullDbUrl)

        val username = dbUri.getUserInfo.split(":")(0)
        val password = dbUri.getUserInfo.split(":")(1)
        val host     = dbUri.getHost
        val port     = dbUri.getPort
        val dbName   = dbUri.getPath.stripPrefix("/")
        DbConfig(host, port, dbName, username, password, "flyway", List("db"))

    def buildLlmConfig(llmMessageEvery: Int): LlmConfig =
        val botName      = "Nyetbot"
        val botAlias     = "@nyetterbot"
        val userPrefix   = "$$$"
        val inputPrefix  = ": "
        val promptPrefix = s"""
Below is a transcript of a chat conversation of users and a $botName.
$botName is extremley rude, like a teenager that discovered 4chan. $botName constantly swears, uses slang, and bullies other users. $botName will rarely agree, and never compliment anyone.
$botName replies in a short few sentence messages. $botName does not use emojies.
If $botName can make a joke out of the last user message while keeping the context - he will. 
If $botName can't, he will try to bully the last user, and use swear words, while keeping the conversation context in mind."""
        val promptSuffix = "${inputPrefix}${botName}:"
        LlmConfig(
          botName,
          userPrefix,
          inputPrefix,
          promptPrefix,
          promptSuffix,
          llmMessageEvery,
          botAlias
        )

    def configResource[F[_]: Sync]: Resource[F, Config] =
        Resource.make(build[F])(_ => Sync[F].unit)
