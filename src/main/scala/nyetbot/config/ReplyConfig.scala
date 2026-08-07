package nyetbot.config

import pureconfig.ConfigReader

final case class ReplyConfig(
    minChars: Int,
    meanFactor: Double,
    spread: Double,
    maxChars: Int
) derives ConfigReader
