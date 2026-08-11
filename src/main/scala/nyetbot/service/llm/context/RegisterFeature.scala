package nyetbot.service.llm.context

import nyetbot.client.OllamaClient
import nyetbot.config.llm.RegisterConfig
import nyetbot.model.LlmContextMessage

object RegisterFeature:
    enum Register:
        case Spor, Sobytie, Shutka, Vopros, Byt

    def apply(client: OllamaClient, config: RegisterConfig): ContextFeature[Register] =
        val request = OllamaClient.Req.from(config.modelConfig)
        ContextFeature.io("register", config.enabled) { in =>
            client
                .generate(
                  request.copy(prompt = Prompt.render(in.triggerText, in.recentChat))
                )
                .map(response => Some(parse(response)))
        }

    private def parse(response: String): Register =
        val r = response.toUpperCase
        if r.contains("SPOR") then Register.Spor
        else if r.contains("SOBYTIE") then Register.Sobytie
        else if r.contains("SHUTKA") then Register.Shutka
        else if r.contains("VOPROS") then Register.Vopros
        else Register.Byt

    object Prompt:
        def render(
            triggerText: String,
            recentChat: List[LlmContextMessage]
        ): String =
            s"""Определи тип последнего сообщения в чате.

Недавний контекст:
${LlmContextMessage.render(recentChat.takeRight(8))}

Последнее сообщение: $triggerText

Типы:
SPOR — мнение, тейк, спорное утверждение
SOBYTIE — личная новость, поздравление, событие в жизни
SHUTKA — шутка, мем, стёб
VOPROS — вопрос или прямое обращение
BYT — бытовая болтовня, статус, мелочь

Ответь одним словом: SPOR, SOBYTIE, SHUTKA, VOPROS или BYT.
Ответ:"""
