package nyetbot.service.llm.feature

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.llm.feature.SummarizeThreadFeatureConfig
import nyetbot.model.LlmContextMessage

trait SummarizeThreadFeature:
    def summarizeThread(recentChat: List[LlmContextMessage]): IO[String]

object SummarizeThreadFeature:
    def apply(
        client: OllamaClient,
        config: SummarizeThreadFeatureConfig
    ): SummarizeThreadFeature =
        new SummarizeThreadFeature:
            private val request = OllamaClient.Req.from(config.modelConfig)

            override def summarizeThread(recentChat: List[LlmContextMessage]): IO[String] =
                client.generate(
                  request.copy(prompt = Prompt.render(recentChat))
                )

    object Prompt:
        def render(recentChat: List[LlmContextMessage]): String =
            s"""Ниже фрагмент группового чата. Опиши в 2-3 предложениях, о чём сейчас идёт разговор:
тема, что утверждают участники, ключевые детали (цифры, названия, факты). Нейтрально, без оценок.

ЧАТ:
${ChatLog.render(recentChat)}

СУТЬ:"""
