package nyetbot

import nyetbot.config.LlmFunctionalityConfig
import nyetbot.config.ReplyLengthConfig

object Fixtures:
    val llmConfig: LlmFunctionalityConfig = LlmFunctionalityConfig(
      botName = "NYETBOT",
      botAlias = "@nyetterbot",
      messageEvery = 150,
      chatBufferSize = 200,
      replyContextWindow = 20,
      recentUserMessages = 50
    )

    val replyLengthConfig: ReplyLengthConfig = ReplyLengthConfig(
      minChars = 150,
      meanFactor = 1.5,
      spread = 0.3,
      maxChars = 600
    )
