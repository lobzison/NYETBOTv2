package nyetbot

import cats.effect.kernel.Sync
import cats.implicits.*
import cats.*
import java.net.URI
import cats.effect.kernel.Resource
import com.donderom.llm4s.*
import java.nio.file.{Paths, Path}
import org.http4s.implicits.*
import org.http4s.Uri

case class Config(
    botToken: String,
    dbConfig: Config.DbConfig,
    llmConfig: Config.LlmConfig,
    translateConfig: Config.TranslateConfig
)

object Config:
    case class LlmConfig(
        llibPath: String,
        modelPath: Path,
        contextParams: ContextParams,
        botName: String,
        userPrefix: String,
        inputPrefix: String,
        promptPrefix: String,
        promptSuffix: String,
        llmParams: LlmParams
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
            botToken     <- Sync[F].delay(sys.env("NYETBOT_KEY"))
            fullUrl      <- Sync[F].delay(sys.env("DATABASE_URL"))
            translateKey <- Sync[F].delay(sys.env("TRANSLATE_KEY"))
            libllamaPath <- Sync[F].delay(sys.env("LIBLLAMA"))
            weightsPath  <- Sync[F].delay(sys.env("WEIGHTS"))
        yield buildConfig(botToken, fullUrl, translateKey, libllamaPath, weightsPath)

    def buildConfig(
        botToken: String,
        fullDbUrl: String,
        translateKey: String,
        libllamaPath: String,
        weightsPath: String
    ) =
        val dbConfig        = buildDbConfig(fullDbUrl)
        val llmConfig       = buildLlmConfig(libllamaPath, weightsPath)
        val translateConfig =
            TranslateConfig(uri"https://api-free.deepl.com/v2/translate", translateKey)
        Config(
          botToken,
          dbConfig,
          llmConfig,
          translateConfig
        )

    def buildDbConfig(fullDbUrl: String): DbConfig =
        val dbUri = new URI(fullDbUrl)

        val username = dbUri.getUserInfo.split(":")(0)
        val password = dbUri.getUserInfo.split(":")(1)
        val host     = dbUri.getHost
        val port     = dbUri.getPort
        val dbName   = dbUri.getPath.stripPrefix("/")
        DbConfig(host, port, dbName, username, password, "flyway", List("db"))

    def buildLlmConfig(llibPath: String, weightsPathStr: String): LlmConfig =
        val weightsPath   = Paths.get(weightsPathStr)
        val contextParams = ContextParams(threads = 6)
        val llmParams     = LlmParams(context = contextParams, predictTokens = 720, echo = false)
        val botName       = "Nyetbot"
        val userPrefix    = "$$"
        val inputPrefix   = "*****>"
        val promptPrefix  = s"""
Below is a transcript of a chat conversation of users and a $botName.
$botName is extremley rude, like a teenager that discovered 4chan. $botName constantly swears, uses slang, and bullies other users. $botName in short, edgy, one sentence punchlines. $botName will rarely agree, and never compliment anyone.
If $botName can make a joke out of the last user message while keeping the context - he will.
If $botName can't, he will try to bully the last user, and use swear words, while keeping the conversation context in mind.
Example replies from $botName include "pathetic scum", "lol, you are weak", "your mom, lmao" and so on. """
        val promptSuffix  = "${inputPrefix}${botName}:"
        LlmConfig(
          llibPath,
          weightsPath,
          contextParams,
          botName,
          userPrefix,
          inputPrefix,
          promptPrefix,
          promptSuffix,
          llmParams
        )

    def configResource[F[_]: Sync]: Resource[F, Config] =
        Resource.make(build[F])(_ => Sync[F].unit)
