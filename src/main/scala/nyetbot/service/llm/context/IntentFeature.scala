package nyetbot.service.llm.context

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.llm.IntentConfig
import nyetbot.model.LlmContextMessage
import nyetbot.service.llm.LlmService.Trigger

object IntentFeature:
    enum TagIntent:
        case Contextual, NewQuestion

    def apply(client: OllamaClient, config: IntentConfig): ContextFeature[TagIntent] =
        val request = OllamaClient.Req.from(config.modelConfig)

        def classify(
            question: String,
            replyToText: String,
            recentChat: List[LlmContextMessage]
        ): IO[Option[TagIntent]] =
            client
                .generate(
                  request.copy(prompt = Prompt.render(question, replyToText, recentChat))
                )
                .map { response =>
                    if response.toUpperCase.contains("NEW") then Some(TagIntent.NewQuestion)
                    else Some(TagIntent.Contextual)
                }

        ContextFeature.io("intent", config.enabled) { in =>
            in.trigger match
                case Trigger.Random(_)    => IO.pure(None)
                case Trigger.Tagged(q, r) => classify(q, r, in.recentChat)
                case Trigger.Reply(q, r)  => classify(q, r, in.recentChat)
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
${LlmContextMessage.render(recentChat)}

Если это продолжение уже идущего обсуждения — ответь одним словом: CONTEXT.
Если это новый отдельный вопрос — ответь одним словом: NEW.
Ответ:"""
