package nyetbot

import nyetbot.config.Config.LlmConfig

object Fixtures:
    val llmConfig: LlmConfig = LlmConfig(
      botName = "NYETBOT",
      botAlias = "@nyetterbot",
      userPrefix = "",
      inputPrefix = ": ",
      llmMessageEvery = 150,
      chatBufferSize = 200,
      replyContextWindow = 20,
      topicContextWindow = 10,
      recentUserMessages = 50,
      profileMaxChars = 300,
      summaryMaxChars = 500,
      replyMinChars = 150,
      replyMeanFactor = 1.5,
      replySpread = 0.3,
      replyMaxChars = 600
    )
