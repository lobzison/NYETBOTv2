package nyetbot.service.llm

import cats.effect.IO
import nyetbot.config.LlmFunctionalityConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.UserRef
import nyetbot.service.llm.feature.{
    ReplyFeature,
    ReplyFeaturePrompt,
    ClassifyIntentFeature,
    ClassifyIntentFeaturePrompt,
    SummarizeUserFeature,
    SummarizeUserFeaturePrompt,
    SummarizeThreadFeature,
    SummarizeThreadFeaturePrompt,
    ClassifyRegisterFeature,
    ClassifyRegisterFeaturePrompt
}

enum TagIntent:
    case Contextual
    case NewQuestion

enum Register:
    case Spor
    case Sobytie
    case Shutka
    case Vopros
    case Byt

final case class ReplyContext(
    target: UserRef,
    profile: String,
    recentSummary: String,
    topic: String,
    recentChat: List[LlmContextMessage],
    intent: TagIntent,
    register: Register,
    minChars: Int,
    triggerText: String,
    currentDate: String,
    replyToText: String,
    replyToBot: Boolean
)

trait LlmService:
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

object OllamaPrompts:
    def reply(ctx: ReplyContext, cfg: LlmFunctionalityConfig): String =
        ReplyFeaturePrompt.render(ctx, cfg)

    def summary(
        recent: List[LlmContextMessage],
        who: UserRef,
        cfg: LlmFunctionalityConfig,
        summaryMaxChars: Int
    ): String =
        SummarizeUserFeaturePrompt.summary(recent, who, cfg, summaryMaxChars)

    def topic(recentChat: List[LlmContextMessage], cfg: LlmFunctionalityConfig): String =
        SummarizeThreadFeaturePrompt.render(recentChat, cfg)

    def register(
        triggerText: String,
        recentChat: List[LlmContextMessage],
        cfg: LlmFunctionalityConfig
    ): String =
        ClassifyRegisterFeaturePrompt.render(triggerText, recentChat, cfg)

    def rewrite(
        oldProfile: String,
        summary: String,
        who: UserRef,
        profileMaxChars: Int
    ): String =
        SummarizeUserFeaturePrompt.rewrite(oldProfile, summary, who, profileMaxChars)

    def intent(
        question: String,
        replyToText: String,
        recentChat: List[LlmContextMessage],
        cfg: LlmFunctionalityConfig
    ): String =
        ClassifyIntentFeaturePrompt.render(question, replyToText, recentChat, cfg)

class OllamaService(
    replyFeature: ReplyFeature,
    summarizeThreadFeature: SummarizeThreadFeature,
    classifyIntentFeature: ClassifyIntentFeature,
    summarizeUserFeature: SummarizeUserFeature,
    classifyRegisterFeature: ClassifyRegisterFeature
) extends LlmService:

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
