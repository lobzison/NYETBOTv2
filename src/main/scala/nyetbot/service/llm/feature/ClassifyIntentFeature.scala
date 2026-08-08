package nyetbot.service.llm.feature

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.LlmConfig
import nyetbot.config.llm.feature.ClassifyIntentFeatureConfig
import nyetbot.model.LlmContextMessage
import nyetbot.service.llm.TagIntent

trait ClassifyIntentFeature:
    def classifyIntent(
        question: String,
        replyToText: String,
        recentChat: List[LlmContextMessage]
    ): IO[TagIntent]

object ClassifyIntentFeature:
    def apply(
        client: OllamaClient,
        config: ClassifyIntentFeatureConfig,
        llmConfig: LlmConfig
    ): ClassifyIntentFeature =
        new ClassifyIntentFeatureImpl(client, config, llmConfig)

class ClassifyIntentFeatureImpl(
    client: OllamaClient,
    config: ClassifyIntentFeatureConfig,
    llmConfig: LlmConfig
) extends ClassifyIntentFeature:
    private val request = OllamaClient.Req(
      model = config.modelConfig.model,
      system = None,
      template = None,
      prompt = "",
      stream = false,
      think = config.modelConfig.think,
      options = OllamaClient.Req.Options(
        numPredict = config.modelConfig.numPredict,
        temperature = config.modelConfig.temperature,
        topP = config.modelConfig.topP,
        topK = config.modelConfig.topK,
        repeatPenalty = config.modelConfig.repeatPenalty,
        numCtx = config.modelConfig.numCtx,
        stop = config.modelConfig.stop
      )
    )

    override def classifyIntent(
        question: String,
        replyToText: String,
        recentChat: List[LlmContextMessage]
    ): IO[TagIntent] =
        client
            .generate(
              request.copy(
                prompt = ClassifyIntentFeaturePrompt.render(
                  question,
                  replyToText,
                  recentChat,
                  llmConfig
                )
              )
            )
            .map { response =>
                if response.toUpperCase.contains("NEW") then TagIntent.NewQuestion
                else TagIntent.Contextual
            }

object ClassifyIntentFeaturePrompt:
    private def renderChat(chat: List[LlmContextMessage], cfg: LlmConfig): String =
        chat.map(m => s"${m.userName}${cfg.inputPrefix}${m.text}").mkString("\n")

    def render(
        question: String,
        replyToText: String,
        recentChat: List[LlmContextMessage],
        cfg: LlmConfig
    ): String =
        val repliedTo = if replyToText.isEmpty then "нет" else replyToText
        s"""Определи, к чему относится обращение к боту.
Сообщение с упоминанием бота: $question
Сообщение, на которое это ответ (может быть пустым): $repliedTo
Недавний контекст чата:
${renderChat(recentChat, cfg)}

Если это продолжение уже идущего обсуждения — ответь одним словом: CONTEXT.
Если это новый отдельный вопрос — ответь одним словом: NEW.
Ответ:"""
