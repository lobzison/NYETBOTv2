package nyetbot

import nyetbot.config.Config.{LlmConfig, ReplyConfig}

object Fixtures:
    val llmConfig: LlmConfig = LlmConfig(
      botName = "NYETBOT",
      botAlias = "@nyetterbot",
      userPrefix = "",
      inputPrefix = ": ",
      messageEvery = 150,
      chatBufferSize = 200,
      replyContextWindow = 20,
      topicContextWindow = 10,
      recentUserMessages = 50,
      profileMaxChars = 300,
      summaryMaxChars = 500,
      reply = ReplyConfig(
        minChars = 150,
        meanFactor = 1.5,
        spread = 0.3,
        maxChars = 600
      )
    )
