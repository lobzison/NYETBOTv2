package nyetbot.service.llm.feature

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.LlmFunctionalityConfig
import nyetbot.config.llm.feature.ClassifyRegisterFeatureConfig
import nyetbot.model.LlmContextMessage
import nyetbot.service.llm.LlmService.Register

trait ClassifyRegisterFeature:
    def classifyRegister(
        triggerText: String,
        recentChat: List[LlmContextMessage]
    ): IO[Register]

object ClassifyRegisterFeature:
    def apply(
        client: OllamaClient,
        config: ClassifyRegisterFeatureConfig,
        llmConfig: LlmFunctionalityConfig
    ): ClassifyRegisterFeature =
        new ClassifyRegisterFeature:
            private val request = OllamaClient.Req.from(config.modelConfig)

            override def classifyRegister(
                triggerText: String,
                recentChat: List[LlmContextMessage]
            ): IO[Register] =
                client
                    .generate(
                      request.copy(
                        prompt = Prompt.render(
                          triggerText,
                          recentChat,
                          llmConfig
                        )
                      )
                    )
                    .map(_.toUpperCase)
                    .map { response =>
                        if response.contains("SPOR") then Register.Spor
                        else if response.contains("SOBYTIE") then Register.Sobytie
                        else if response.contains("SHUTKA") then Register.Shutka
                        else if response.contains("VOPROS") then Register.Vopros
                        else Register.Byt
                    }

    object Prompt:
        private def renderChat(chat: List[LlmContextMessage], cfg: LlmFunctionalityConfig): String =
            chat.map(m => s"${m.userName}${cfg.inputPrefix}${m.text}").mkString("\n")

        def render(
            triggerText: String,
            recentChat: List[LlmContextMessage],
            cfg: LlmFunctionalityConfig
        ): String =
            s"""Определи тип последнего сообщения в чате.

Недавний контекст:
${renderChat(recentChat.takeRight(8), cfg)}

Последнее сообщение: $triggerText

Типы:
SPOR — мнение, тейк, спорное утверждение
SOBYTIE — личная новость, поздравление, событие в жизни
SHUTKA — шутка, мем, стёб
VOPROS — вопрос или прямое обращение
BYT — бытовая болтовня, статус, мелочь

Ответь одним словом: SPOR, SOBYTIE, SHUTKA, VOPROS или BYT.
Ответ:"""
