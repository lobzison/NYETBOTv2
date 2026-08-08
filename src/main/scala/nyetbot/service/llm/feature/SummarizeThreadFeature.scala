package nyetbot.service.llm.feature

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.LlmFunctionalityConfig
import nyetbot.config.llm.feature.SummarizeThreadFeatureConfig
import nyetbot.model.LlmContextMessage

trait SummarizeThreadFeature:
    def summarizeThread(recentChat: List[LlmContextMessage]): IO[String]

object SummarizeThreadFeature:
    def apply(
        client: OllamaClient,
        config: SummarizeThreadFeatureConfig,
        llmConfig: LlmFunctionalityConfig
    ): SummarizeThreadFeature =
        new SummarizeThreadFeatureImpl(client, config, llmConfig)

class SummarizeThreadFeatureImpl(
    client: OllamaClient,
    config: SummarizeThreadFeatureConfig,
    llmConfig: LlmFunctionalityConfig
) extends SummarizeThreadFeature:
    private val request = OllamaClient.Req.from(config.modelConfig)

    override def summarizeThread(recentChat: List[LlmContextMessage]): IO[String] =
        client.generate(
          request.copy(prompt = SummarizeThreadFeaturePrompt.render(recentChat, llmConfig))
        )

object SummarizeThreadFeaturePrompt:
    private def renderChat(chat: List[LlmContextMessage], cfg: LlmFunctionalityConfig): String =
        chat.map(m => s"${m.userName}${cfg.inputPrefix}${m.text}").mkString("\n")

    def render(recentChat: List[LlmContextMessage], cfg: LlmFunctionalityConfig): String =
        s"""Ниже фрагмент группового чата. Опиши в 2-3 предложениях, о чём сейчас идёт разговор:
тема, что утверждают участники, ключевые детали (цифры, названия, факты). Нейтрально, без оценок.

ЧАТ:
${renderChat(recentChat, cfg)}

СУТЬ:"""
