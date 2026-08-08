package nyetbot.config.llm.feature

import pureconfig.ConfigReader

final case class ClassifyRegisterFeatureConfig(modelConfig: OllamaModelConfig) extends FeatureConfig
    derives ConfigReader
