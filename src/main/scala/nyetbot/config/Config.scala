package nyetbot.config

import cats.effect.kernel.Resource
import pureconfig.ConfigReader
import pureconfig.ConfigSource
import cats.implicits.*
import cats.effect.IO
import pureconfig.error.ConfigReaderException

case class Config(
    botToken: String,
    dbConfig: DbConfig,
    llmConfig: LlmFunctionalityConfig,
    replyLengthConfig: ReplyLengthConfig,
    ollamaConfig: OllamaConfig
)

object Config:
    final case class RawConfig(
        botToken: String,
        databaseUrl: String,
        llm: LlmFunctionalityConfig,
        replyLength: ReplyLengthConfig,
        ollama: OllamaConfig
    ) derives ConfigReader

    def apply(): IO[Config] =
        IO.fromEither(
          ConfigSource.default
              .at("nyetbot")
              .load[RawConfig]
              .leftMap(e => throw new ConfigReaderException[RawConfig](e))
        ).map { settings =>
            Config(
              botToken = settings.botToken,
              dbConfig = DbConfig(settings.databaseUrl),
              llmConfig = settings.llm,
              replyLengthConfig = settings.replyLength,
              ollamaConfig = settings.ollama
            )
        }

    def configResource: Resource[IO, Config] =
        Resource.eval(Config())
