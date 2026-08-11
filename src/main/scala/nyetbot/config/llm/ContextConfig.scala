package nyetbot.config.llm

import pureconfig.ConfigReader

final case class BlockConfig(enabled: Boolean = true) derives ConfigReader

final case class DossierConfig(
    modelConfig: OllamaModelConfig,
    summaryMaxChars: Int,
    enabled: Boolean = true
) derives ConfigReader

final case class TopicConfig(
    modelConfig: OllamaModelConfig,
    contextWindow: Int,
    enabled: Boolean = true
) derives ConfigReader

final case class RegisterConfig(
    modelConfig: OllamaModelConfig,
    enabled: Boolean = true
) derives ConfigReader

final case class IntentConfig(
    modelConfig: OllamaModelConfig,
    enabled: Boolean = true
) derives ConfigReader

final case class ContextConfig(
    dossier: DossierConfig,
    topic: TopicConfig,
    register: RegisterConfig,
    intent: IntentConfig,
    chatLog: BlockConfig,
    replyTarget: BlockConfig,
    userTrigger: BlockConfig,
    date: BlockConfig
) derives ConfigReader
