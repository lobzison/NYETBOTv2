package nyetbot.service.llm.feature

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.llm.feature.ClassifyIntentFeatureConfig
import nyetbot.model.LlmContextMessage

trait ClassifyIntentFeature:
    def classifyIntent(
        question: String,
        replyToText: String,
        recentChat: List[LlmContextMessage]
    ): IO[ClassifyIntentFeature.TagIntent]

object ClassifyIntentFeature:

    enum TagIntent:
        case Contextual
        case NewQuestion

        override def toString: String = this match
            case Contextual  =>
                "Тебя дёрнули внутри уже идущего спора — отвечай в контексте нити."
            case NewQuestion =>
                "Тебя дёрнули с новым, отдельным вопросом — отвечай именно на него, старьё не тащи."

    def apply(
        client: OllamaClient,
        config: ClassifyIntentFeatureConfig
    ): ClassifyIntentFeature =
        new ClassifyIntentFeature:
            private val request = OllamaClient.Req.from(config.modelConfig)

            override def classifyIntent(
                question: String,
                replyToText: String,
                recentChat: List[LlmContextMessage]
            ): IO[TagIntent] =
                client
                    .generate(
                      request.copy(
                        prompt = Prompt.render(
                          question,
                          replyToText,
                          recentChat
                        )
                      )
                    )
                    .map { response =>
                        if response.toUpperCase.contains("NEW") then TagIntent.NewQuestion
                        else TagIntent.Contextual
                    }

    object Prompt:
        def render(
            question: String,
            replyToText: String,
            recentChat: List[LlmContextMessage]
        ): String =
            val repliedTo = if replyToText.isEmpty then "нет" else replyToText
            s"""Определи, к чему относится обращение к боту.
Сообщение с упоминанием бота: $question
Сообщение, на которое это ответ (может быть пустым): $repliedTo
Недавний контекст чата:
${ChatLog.render(recentChat)}

Если это продолжение уже идущего обсуждения — ответь одним словом: CONTEXT.
Если это новый отдельный вопрос — ответь одним словом: NEW.
Ответ:"""
