package nyetbot.config

import pureconfig.ConfigReader

final case class ReplyLengthConfig(
    minChars: Int,
    meanFactor: Double,
    spread: Double,
    maxChars: Int
) derives ConfigReader
