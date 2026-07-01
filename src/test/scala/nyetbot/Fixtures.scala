package nyetbot

import nyetbot.Config.LlmConfig

object Fixtures:
    val llmConfig: LlmConfig = LlmConfig(
      botName = "NYETBOT",
      botAlias = "@nyetterbot",
      userPrefix = "",
      inputPrefix = ": ",
      llmMessageEvery = 150,
      chatBufferSize = 200,
      replyContextWindow = 20,
      recentUserMessages = 50,
      profileMaxChars = 300,
      summaryMaxChars = 500,
      replyMinChars = 200,
      replyMeanFactor = 1.5,
      replySpread = 0.3,
      replyMaxChars = 900
    )
