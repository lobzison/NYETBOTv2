package nyetbot.config

import pureconfig.ConfigReader

final case class SummarizeUserFeatureConfig(
    model: String,
    temperature: Double,
    summaryNumPredict: Int,
    rewriteNumPredict: Int,
    numCtx: Int,
    think: Boolean
) derives ConfigReader
