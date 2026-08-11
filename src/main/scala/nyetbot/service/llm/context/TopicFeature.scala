package nyetbot.service.llm.context

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.all.Empty
import io.github.iltotore.iron.constraint.any.Not
import nyetbot.client.OllamaClient
import nyetbot.config.llm.TopicConfig
import nyetbot.model.LlmContextMessage

object TopicFeature:
    type Topic = Topic.T
    object Topic extends RefinedType[String, Not[Empty]]

    def apply(client: OllamaClient, config: TopicConfig): ContextFeature[Topic] =
        val request = OllamaClient.Req.from(config.modelConfig)
        ContextFeature.io("topic", config.enabled) { in =>
            client
                .generate(
                  request.copy(
                    prompt = Prompt.render(in.recentChat.takeRight(config.contextWindow))
                  )
                )
                .map(Topic.either(_).toOption)
        }

    object Prompt:
        def render(recentChat: List[LlmContextMessage]): String =
            s"""Ниже фрагмент группового чата. Опиши в 2-3 предложениях, о чём сейчас идёт разговор:
тема, что утверждают участники, ключевые детали (цифры, названия, факты). Нейтрально, без оценок.

ЧАТ:
${LlmContextMessage.render(recentChat)}

СУТЬ:"""
