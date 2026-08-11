package nyetbot.config.llm

import pureconfig.ConfigReader

final case class ReplyGeneratorConfig(modelConfig: OllamaModelConfig) derives ConfigReader
