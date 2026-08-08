package nyetbot.config

import nyetbot.config.llm.feature.*
import pureconfig.ConfigReader

final case class OllamaConfig(
    domain: String,
    port: Int,
    reply: ReplyFeatureConfig,
    summarizeThread: SummarizeThreadFeatureConfig,
    classifyIntent: ClassifyIntentFeatureConfig,
    summarizeUser: SummarizeUserFeatureConfig,
    classifyRegister: ClassifyRegisterFeatureConfig,
    requestTimeoutMinutes: Int,
    idleTimeoutMinutes: Int
) derives ConfigReader:
    val uri: String = s"http://$domain:$port"
