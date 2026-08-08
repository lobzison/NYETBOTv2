package nyetbot.config.llm.feature

import pureconfig.ConfigReader

final case class SummarizeThreadFeatureConfig(modelConfig: OllamaModelConfig) extends FeatureConfig
    derives ConfigReader
