package nyetbot.config

import pureconfig.ConfigReader

final case class LlmFunctionalityConfig(
    botName: String,
    botAlias: String,
    userPrefix: String,
    inputPrefix: String,
    messageEvery: Int,
    chatBufferSize: Int,
    replyContextWindow: Int,
    recentUserMessages: Int
) derives ConfigReader
