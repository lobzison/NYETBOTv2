package nyetbot.service.llm

import cats.effect.IO
import nyetbot.config.LlmFunctionalityConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.feature.ClassifyIntentFeature.TagIntent
import nyetbot.service.llm.feature.ClassifyIntentFeature
import nyetbot.service.llm.feature.ClassifyRegisterFeature
import nyetbot.service.llm.feature.ClassifyRegisterFeature.Register
import nyetbot.service.llm.feature.ReplyFeature
import nyetbot.service.llm.feature.ReplyFeature.ReplyContext
import nyetbot.service.llm.feature.SummarizeThreadFeature
import nyetbot.service.llm.feature.SummarizeUserFeature

trait LlmFeatures:
    def generateReply(ctx: ReplyContext): IO[String]
    def summarizeUser(recent: List[LlmContextMessage], who: UserRef): IO[String]
    def summarizeThread(recentChat: List[LlmContextMessage]): IO[String]
    def rewriteProfile(oldProfile: String, recentSummary: String, who: UserRef): IO[String]
    def classifyTagIntent(
        question: String,
        replyToText: String,
        recentChat: List[LlmContextMessage]
    ): IO[TagIntent]
    def classifyRegister(
        triggerText: String,
        recentChat: List[LlmContextMessage]
    ): IO[Register]

object LlmFeatures:

    object OllamaPrompts:
        def reply(ctx: ReplyContext, cfg: LlmFunctionalityConfig): String =
            ReplyFeature.Prompt.render(ctx, cfg)

        def summary(
            recent: List[LlmContextMessage],
            who: UserRef,
            cfg: LlmFunctionalityConfig,
            summaryMaxChars: Int
        ): String =
            SummarizeUserFeature.Prompt.summary(recent, who, cfg, summaryMaxChars)

        def topic(recentChat: List[LlmContextMessage], cfg: LlmFunctionalityConfig): String =
            SummarizeThreadFeature.Prompt.render(recentChat, cfg)

        def register(
            triggerText: String,
            recentChat: List[LlmContextMessage],
            cfg: LlmFunctionalityConfig
        ): String =
            ClassifyRegisterFeature.Prompt.render(triggerText, recentChat, cfg)

        def rewrite(
            oldProfile: String,
            summary: String,
            who: UserRef,
            profileMaxChars: Int
        ): String =
            SummarizeUserFeature.Prompt.rewrite(oldProfile, summary, who, profileMaxChars)

        def intent(
            question: String,
            replyToText: String,
            recentChat: List[LlmContextMessage],
            cfg: LlmFunctionalityConfig
        ): String =
            ClassifyIntentFeature.Prompt.render(question, replyToText, recentChat, cfg)

    def apply(
        replyFeature: ReplyFeature,
        summarizeThreadFeature: SummarizeThreadFeature,
        classifyIntentFeature: ClassifyIntentFeature,
        summarizeUserFeature: SummarizeUserFeature,
        classifyRegisterFeature: ClassifyRegisterFeature
    ): LlmFeatures =
        new LlmFeatures:
            override def generateReply(ctx: ReplyContext): IO[String] =
                replyFeature.generateReply(ctx)

            override def summarizeUser(recent: List[LlmContextMessage], who: UserRef): IO[String] =
                summarizeUserFeature.summarizeUser(recent, who)

            override def summarizeThread(recentChat: List[LlmContextMessage]): IO[String] =
                summarizeThreadFeature.summarizeThread(recentChat)

            override def rewriteProfile(
                oldProfile: String,
                recentSummary: String,
                who: UserRef
            ): IO[String] =
                summarizeUserFeature.rewriteProfile(oldProfile, recentSummary, who)

            override def classifyTagIntent(
                question: String,
                replyToText: String,
                recentChat: List[LlmContextMessage]
            ): IO[TagIntent] =
                classifyIntentFeature.classifyIntent(question, replyToText, recentChat)

            override def classifyRegister(
                triggerText: String,
                recentChat: List[LlmContextMessage]
            ): IO[Register] =
                classifyRegisterFeature.classifyRegister(triggerText, recentChat)
