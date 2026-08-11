package nyetbot.config.llm.feature

import pureconfig.ConfigReader

final case class ReplyFeatureConfig(
    modelConfig: OllamaModelConfig,
    enabled: Boolean = true,
    profile: Boolean = true,
    topic: Boolean = true,
    chat: Boolean = true,
    replyTarget: Boolean = true,
    userTrigger: Boolean = true,
    task: Boolean = true,
    intent: Boolean = true,
    register: Boolean = true,
    date: Boolean = true
) extends FeatureConfig derives ConfigReader
