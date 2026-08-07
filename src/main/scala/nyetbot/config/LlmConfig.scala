package nyetbot.config
import pureconfig.ConfigReader

final case class LlmConfig(
    botName: String,
    botAlias: String,
    userPrefix: String,
    inputPrefix: String,
    messageEvery: Int,
    chatBufferSize: Int,
    replyContextWindow: Int,
    topicContextWindow: Int,
    recentUserMessages: Int,
    profileMaxChars: Int,
    summaryMaxChars: Int,
    reply: ReplyConfig
) derives ConfigReader
