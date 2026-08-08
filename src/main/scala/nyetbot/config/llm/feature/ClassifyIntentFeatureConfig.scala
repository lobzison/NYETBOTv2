package nyetbot.config.llm.feature

import pureconfig.ConfigReader

final case class ClassifyIntentFeatureConfig(modelConfig: OllamaModelConfig) extends FeatureConfig
    derives ConfigReader
