package nyetbot.config.llm

import pureconfig.ConfigReader

final case class ProfileRewriterConfig(
    modelConfig: OllamaModelConfig,
    profileMaxChars: Int
) derives ConfigReader
