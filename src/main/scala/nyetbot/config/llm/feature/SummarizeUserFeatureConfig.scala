package nyetbot.config.llm.feature

import pureconfig.ConfigReader

final case class SummarizeUserFeatureConfig(
    modelConfig: OllamaModelConfig,
    rewriteModelConfig: OllamaModelConfig,
    profileMaxChars: Int,
    summaryMaxChars: Int
) extends FeatureConfig derives ConfigReader
