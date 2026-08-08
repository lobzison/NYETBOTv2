package nyetbot.config.llm.feature

import pureconfig.ConfigReader

final case class SummarizeUserFeatureConfig(
    modelConfig: OllamaModelConfig,
    rewriteModelConfig: OllamaModelConfig
) extends FeatureConfig derives ConfigReader
