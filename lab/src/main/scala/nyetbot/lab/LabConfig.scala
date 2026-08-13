package nyetbot.lab

import cats.effect.IO
import cats.syntax.all.*
import fs2.io.file.Path
import nyetbot.config.LlmFunctionalityConfig
import nyetbot.config.OllamaConfig
import nyetbot.config.ReplyLengthConfig
import pureconfig.ConfigReader
import pureconfig.ConfigSource

final case class LabConfig(
    llm: LlmFunctionalityConfig,
    replyLength: ReplyLengthConfig,
    ollama: OllamaConfig
) derives ConfigReader

object LabConfig:
    def load(path: Path): IO[Either[String, LabConfig]] =
        IO.blocking(
          ConfigSource
              .file(path.toNioPath)
              .at("nyetbot")
              .load[LabConfig]
              .leftMap(failures => s"cannot load scenario $path: ${failures.prettyPrint()}")
        )
