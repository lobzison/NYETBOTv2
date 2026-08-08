package nyetbot.config

import pureconfig.ConfigReader

final case class ProfileServiceConfig(
    topicContextWindow: Int,
    minChars: Int,
    meanFactor: Double,
    spread: Double,
    maxChars: Int
) derives ConfigReader
