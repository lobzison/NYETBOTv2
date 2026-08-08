package nyetbot.service.llm.feature

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.LlmConfig
import nyetbot.config.llm.feature.SummarizeThreadFeatureConfig
import nyetbot.model.LlmContextMessage

trait SummarizeThreadFeature:
    def summarizeThread(recentChat: List[LlmContextMessage]): IO[String]

object SummarizeThreadFeature:
    def apply(
        client: OllamaClient,
        config: SummarizeThreadFeatureConfig,
        llmConfig: LlmConfig
    ): SummarizeThreadFeature =
        new SummarizeThreadFeatureImpl(client, config, llmConfig)

class SummarizeThreadFeatureImpl(
    client: OllamaClient,
    config: SummarizeThreadFeatureConfig,
    llmConfig: LlmConfig
) extends SummarizeThreadFeature:
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

    override def summarizeThread(recentChat: List[LlmContextMessage]): IO[String] =
        client.generate(
          request.copy(prompt = SummarizeThreadFeaturePrompt.render(recentChat, llmConfig))
        )

object SummarizeThreadFeaturePrompt:
    private def renderChat(chat: List[LlmContextMessage], cfg: LlmConfig): String =
        chat.map(m => s"${m.userName}${cfg.inputPrefix}${m.text}").mkString("\n")

    def render(recentChat: List[LlmContextMessage], cfg: LlmConfig): String =
        s"""Ниже фрагмент группового чата. Опиши в 2-3 предложениях, о чём сейчас идёт разговор:
тема, что утверждают участники, ключевые детали (цифры, названия, факты). Нейтрально, без оценок.

ЧАТ:
${renderChat(recentChat, cfg)}

СУТЬ:"""
