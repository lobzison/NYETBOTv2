package nyetbot.config

import nyetbot.config.llm.*
import pureconfig.ConfigReader

final case class OllamaConfig(
    domain: String,
    port: Int,
    reply: ReplyGeneratorConfig,
    profileRewrite: ProfileRewriterConfig,
    context: ContextConfig,
    requestTimeoutMinutes: Int,
    idleTimeoutMinutes: Int
) derives ConfigReader:
    val uri: String = s"http://$domain:$port"
