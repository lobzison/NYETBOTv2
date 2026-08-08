package nyetbot.config
import pureconfig.ConfigReader

final case class OllamaConfig(
    domain: String,
    port: Int,
    reply: ReplyFeatureConfig,
    utilityModel: String,
    utilityTemperature: Double,
    summaryNumPredict: Int,
    rewriteNumPredict: Int,
    intentNumPredict: Int,
    topicNumPredict: Int,
    registerNumPredict: Int,
    requestTimeoutMinutes: Int,
    idleTimeoutMinutes: Int
) derives ConfigReader:
    val uri: String = s"http://$domain:$port"
