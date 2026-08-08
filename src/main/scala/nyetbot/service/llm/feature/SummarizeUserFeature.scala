package nyetbot.service.llm.feature

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.LlmConfig
import nyetbot.config.llm.feature.OllamaModelConfig
import nyetbot.config.llm.feature.SummarizeUserFeatureConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.UserRef
import nyetbot.util.Text

trait SummarizeUserFeature:
    def summarizeUser(recent: List[LlmContextMessage], who: UserRef): IO[String]
    def rewriteProfile(oldProfile: String, recentSummary: String, who: UserRef): IO[String]

object SummarizeUserFeature:
    def apply(
        client: OllamaClient,
        config: SummarizeUserFeatureConfig,
        llmConfig: LlmConfig
    ): SummarizeUserFeature =
        new SummarizeUserFeatureImpl(client, config, llmConfig)

class SummarizeUserFeatureImpl(
    client: OllamaClient,
    config: SummarizeUserFeatureConfig,
    llmConfig: LlmConfig
) extends SummarizeUserFeature:
    private def request(modelConfig: OllamaModelConfig): OllamaClient.Req =
        OllamaClient.Req(
          model = modelConfig.model,
          system = None,
          template = None,
          prompt = "",
          stream = false,
          think = modelConfig.think,
          options = OllamaClient.Req.Options(
            numPredict = modelConfig.numPredict,
            temperature = modelConfig.temperature,
            topP = modelConfig.topP,
            topK = modelConfig.topK,
            repeatPenalty = modelConfig.repeatPenalty,
            numCtx = modelConfig.numCtx,
            stop = modelConfig.stop
          )
        )

    private val summaryRequest = request(config.modelConfig)
    private val rewriteRequest = request(config.rewriteModelConfig)

    override def summarizeUser(recent: List[LlmContextMessage], who: UserRef): IO[String] =
        client
            .generate(
              summaryRequest.copy(
                prompt = SummarizeUserFeaturePrompt.summary(recent, who, llmConfig)
              )
            )
            .map(Text.truncate(_, llmConfig.summaryMaxChars))

    override def rewriteProfile(
        oldProfile: String,
        recentSummary: String,
        who: UserRef
    ): IO[String] =
        client
            .generate(
              rewriteRequest.copy(
                prompt = SummarizeUserFeaturePrompt.rewrite(
                  oldProfile,
                  recentSummary,
                  who,
                  llmConfig
                )
              )
            )
            .map(Text.truncate(_, llmConfig.profileMaxChars))

object SummarizeUserFeaturePrompt:
    private def renderChat(chat: List[LlmContextMessage], cfg: LlmConfig): String =
        chat.map(m => s"${m.userName}${cfg.inputPrefix}${m.text}").mkString("\n")

    def summary(recent: List[LlmContextMessage], who: UserRef, cfg: LlmConfig): String =
        s"""Ниже последние сообщения пользователя ${who.displayName} из чата.
Составь сжатую нейтральную сводку: о чём он пишет, какая позиция, манера, повторяющиеся темы.
Только описание поведения, без ролей, без оценок, без обращений. Не больше ${cfg.summaryMaxChars} символов.

СООБЩЕНИЯ:
${renderChat(recent, cfg)}

СВОДКА:"""

    def rewrite(oldProfile: String, summary: String, who: UserRef, cfg: LlmConfig): String =
        val old = if oldProfile.isEmpty then "пусто" else oldProfile
        s"""Есть старое досье на пользователя ${who.displayName} и свежая сводка его поведения.
Слей их в одно обновлённое досье: сохрани важное из старого, добавь новое, выкинь устаревшее.
Пиши в третьем лице, нейтрально, одним абзацем, строго не больше ${cfg.profileMaxChars} символов.

СТАРОЕ ДОСЬЕ:
$old

СВЕЖАЯ СВОДКА:
$summary

ОБНОВЛЁННОЕ ДОСЬЕ:"""
