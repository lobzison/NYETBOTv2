package nyetbot.service.llm.feature

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.LlmFunctionalityConfig
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
        llmConfig: LlmFunctionalityConfig
    ): SummarizeUserFeature =
        new SummarizeUserFeatureImpl(client, config, llmConfig)

class SummarizeUserFeatureImpl(
    client: OllamaClient,
    config: SummarizeUserFeatureConfig,
    llmConfig: LlmFunctionalityConfig
) extends SummarizeUserFeature:
    private val summaryRequest = OllamaClient.Req.from(config.modelConfig)
    private val rewriteRequest = OllamaClient.Req.from(config.rewriteModelConfig)

    override def summarizeUser(recent: List[LlmContextMessage], who: UserRef): IO[String] =
        client
            .generate(
              summaryRequest.copy(
                prompt = SummarizeUserFeaturePrompt.summary(
                  recent,
                  who,
                  llmConfig,
                  config.summaryMaxChars
                )
              )
            )
            .map(Text.truncate(_, config.summaryMaxChars))

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
                  config.profileMaxChars
                )
              )
            )
            .map(Text.truncate(_, config.profileMaxChars))

object SummarizeUserFeaturePrompt:
    private def renderChat(chat: List[LlmContextMessage], cfg: LlmFunctionalityConfig): String =
        chat.map(m => s"${m.userName}${cfg.inputPrefix}${m.text}").mkString("\n")

    def summary(
        recent: List[LlmContextMessage],
        who: UserRef,
        cfg: LlmFunctionalityConfig,
        summaryMaxChars: Int
    ): String =
        s"""Ниже последние сообщения пользователя ${who.displayName} из чата.
Составь сжатую нейтральную сводку: о чём он пишет, какая позиция, манера, повторяющиеся темы.
Только описание поведения, без ролей, без оценок, без обращений. Не больше $summaryMaxChars символов.

СООБЩЕНИЯ:
${renderChat(recent, cfg)}

СВОДКА:"""

    def rewrite(
        oldProfile: String,
        summary: String,
        who: UserRef,
        profileMaxChars: Int
    ): String =
        val old = if oldProfile.isEmpty then "пусто" else oldProfile
        s"""Есть старое досье на пользователя ${who.displayName} и свежая сводка его поведения.
Слей их в одно обновлённое досье: сохрани важное из старого, добавь новое, выкинь устаревшее.
Пиши в третьем лице, нейтрально, одним абзацем, строго не больше $profileMaxChars символов.

СТАРОЕ ДОСЬЕ:
$old

СВЕЖАЯ СВОДКА:
$summary

ОБНОВЛЁННОЕ ДОСЬЕ:"""
