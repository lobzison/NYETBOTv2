package nyetbot.config.llm.feature

import pureconfig.ConfigReader

final case class ReplyFeatureConfig(modelConfig: OllamaModelConfig) extends FeatureConfig
    derives ConfigReader
