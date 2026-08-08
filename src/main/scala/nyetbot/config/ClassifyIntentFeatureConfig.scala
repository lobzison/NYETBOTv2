package nyetbot.config

import pureconfig.ConfigReader

final case class ClassifyIntentFeatureConfig(
    model: String,
    temperature: Double,
    numPredict: Int,
    numCtx: Int,
    think: Boolean
) derives ConfigReader
