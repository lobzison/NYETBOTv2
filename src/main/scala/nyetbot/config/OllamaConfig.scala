package nyetbot.config
import pureconfig.ConfigReader

final case class OllamaConfig(
    domain: String,
    port: Int,
    reply: ReplyFeatureConfig,
    summarizeThread: SummarizeThreadFeatureConfig,
    classifyIntent: ClassifyIntentFeatureConfig,
    summarizeUser: SummarizeUserFeatureConfig,
    utilityModel: String,
    utilityTemperature: Double,
    registerNumPredict: Int,
    requestTimeoutMinutes: Int,
    idleTimeoutMinutes: Int
) derives ConfigReader:
    val uri: String = s"http://$domain:$port"
