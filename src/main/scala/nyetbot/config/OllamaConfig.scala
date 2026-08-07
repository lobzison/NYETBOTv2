package nyetbot.config
import pureconfig.ConfigReader

final case class OllamaConfig(
    port: Int,
    replyModel: String,
    utilityModel: String,
    replyTemperature: Double,
    utilityTemperature: Double,
    replyNumPredict: Int,
    summaryNumPredict: Int,
    rewriteNumPredict: Int,
    intentNumPredict: Int,
    topicNumPredict: Int,
    registerNumPredict: Int,
    numCtx: Int,
    think: Boolean,
    requestTimeoutMinutes: Int,
    idleTimeoutMinutes: Int
) derives ConfigReader:
    def uri(domain: String): String = s"http://$domain:$port"
