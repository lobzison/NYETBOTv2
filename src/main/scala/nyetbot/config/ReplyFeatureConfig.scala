package nyetbot.config

import pureconfig.ConfigReader

final case class ReplyFeatureConfig(
    model: String,
    system: String,
    template: String,
    stop: List[String],
    temperature: Double,
    topP: Double,
    topK: Int,
    repeatPenalty: Double,
    numPredict: Int,
    numCtx: Int,
    think: Boolean
) derives ConfigReader
