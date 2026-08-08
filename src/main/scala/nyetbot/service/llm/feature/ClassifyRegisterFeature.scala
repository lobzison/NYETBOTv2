package nyetbot.service.llm.feature

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.LlmConfig
import nyetbot.config.llm.feature.ClassifyRegisterFeatureConfig
import nyetbot.model.LlmContextMessage
import nyetbot.service.llm.Register

trait ClassifyRegisterFeature:
    def classifyRegister(
        triggerText: String,
        recentChat: List[LlmContextMessage]
    ): IO[Register]

object ClassifyRegisterFeature:
    def apply(
        client: OllamaClient,
        config: ClassifyRegisterFeatureConfig,
        llmConfig: LlmConfig
    ): ClassifyRegisterFeature =
        new ClassifyRegisterFeatureImpl(client, config, llmConfig)

class ClassifyRegisterFeatureImpl(
    client: OllamaClient,
    config: ClassifyRegisterFeatureConfig,
    llmConfig: LlmConfig
) extends ClassifyRegisterFeature:
    private val request = OllamaClient.Req.from(config.modelConfig)

    override def classifyRegister(
        triggerText: String,
        recentChat: List[LlmContextMessage]
    ): IO[Register] =
        client
            .generate(
              request.copy(
                prompt = ClassifyRegisterFeaturePrompt.render(
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

object ClassifyRegisterFeaturePrompt:
    private def renderChat(chat: List[LlmContextMessage], cfg: LlmConfig): String =
        chat.map(m => s"${m.userName}${cfg.inputPrefix}${m.text}").mkString("\n")

    def render(
        triggerText: String,
        recentChat: List[LlmContextMessage],
        cfg: LlmConfig
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
