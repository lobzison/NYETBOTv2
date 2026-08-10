package nyetbot.service.llm.feature

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.llm.feature.SummarizeUserFeatureConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*
import nyetbot.util.Text

trait SummarizeUserFeature:
    def summarizeUser(recent: List[LlmContextMessage], who: UserRef): IO[String]
    def rewriteProfile(oldProfile: String, recentSummary: String, who: UserRef): IO[String]

object SummarizeUserFeature:
    def apply(
        client: OllamaClient,
        config: SummarizeUserFeatureConfig
    ): SummarizeUserFeature =
        new SummarizeUserFeature:
            private val summaryRequest = OllamaClient.Req.from(config.modelConfig)
            private val rewriteRequest = OllamaClient.Req.from(config.rewriteModelConfig)

            override def summarizeUser(recent: List[LlmContextMessage], who: UserRef): IO[String] =
                client
                    .generate(
                      summaryRequest.copy(
                        prompt = Prompt.summary(
                          recent,
                          who,
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
                        prompt = Prompt.rewrite(
                          oldProfile,
                          recentSummary,
                          who,
                          config.profileMaxChars
                        )
                      )
                    )
                    .map(Text.truncate(_, config.profileMaxChars))

    object Prompt:
        def summary(
            recent: List[LlmContextMessage],
            who: UserRef,
            summaryMaxChars: Int
        ): String =
            s"""Ниже последние сообщения пользователя ${who.displayName} из чата.
Составь сжатую нейтральную сводку: о чём он пишет, какая позиция, манера, повторяющиеся темы.
Только описание поведения, без ролей, без оценок, без обращений. Не больше $summaryMaxChars символов.

СООБЩЕНИЯ:
${ChatLog.render(recent)}

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
