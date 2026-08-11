package nyetbot.service.llm

import cats.effect.IO
import nyetbot.config.llm.feature.ReplyFeatureConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.feature.ClassifyIntentFeature.TagIntent
import nyetbot.service.llm.feature.ClassifyIntentFeature
import nyetbot.service.llm.feature.ClassifyRegisterFeature
import nyetbot.service.llm.feature.ClassifyRegisterFeature.Register
import nyetbot.service.llm.feature.ChatLog
import nyetbot.service.llm.feature.ReplyBlocks
import nyetbot.service.llm.feature.ReplyFeature
import nyetbot.service.llm.feature.ReplyFeature.ReplyContext
import nyetbot.service.llm.feature.SummarizeThreadFeature
import nyetbot.service.llm.feature.SummarizeUserFeature

trait LlmFeatures:
    def generateReply(ctx: ReplyContext): IO[String]
    def assembleReply(
        target: UserRef,
        triggerText: String,
        minChars: Int,
        trigger: LlmService.Trigger,
        oldProfile: String,
        summary: String,
        topic: String,
        register: Register,
        intent: TagIntent,
        date: String,
        recentChat: List[LlmContextMessage]
    ): ReplyContext
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
        def reply(ctx: ReplyContext): String =
            ReplyBlocks.render(ctx.blocks)

        def summary(
            recent: List[LlmContextMessage],
            who: UserRef,
            summaryMaxChars: Int
        ): String =
            SummarizeUserFeature.Prompt.summary(recent, who, summaryMaxChars)

        def topic(recentChat: List[LlmContextMessage]): String =
            SummarizeThreadFeature.Prompt.render(recentChat)

        def register(
            triggerText: String,
            recentChat: List[LlmContextMessage]
        ): String =
            ClassifyRegisterFeature.Prompt.render(triggerText, recentChat)

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
            recentChat: List[LlmContextMessage]
        ): String =
            ClassifyIntentFeature.Prompt.render(question, replyToText, recentChat)

    def assemble(
        config: ReplyFeatureConfig,
        target: UserRef,
        triggerText: String,
        minChars: Int,
        trigger: LlmService.Trigger,
        oldProfile: String,
        summary: String,
        topic: String,
        register: Register,
        intent: TagIntent,
        date: String,
        recentChat: List[LlmContextMessage]
    ): ReplyContext =
        if !config.enabled then ReplyContext(Nil)
        else
            def block(enabled: Boolean)(compute: => Option[String]): Option[String] =
                if config.enabled && enabled then compute else None

            val isReplyToBot = trigger match
                case LlmService.Trigger.Reply(_, _) => true
                case _                              => false
            val replyToText  = trigger match
                case LlmService.Trigger.Random(t)    => t
                case LlmService.Trigger.Tagged(_, t) => t
                case LlmService.Trigger.Reply(_, t)  => t
            val chatText     = ChatLog.render(recentChat)
            val blocks       = List(
              block(config.profile)(Some(ReplyBlocks.dossier(target, oldProfile, summary))),
              block(config.topic)(Option.when(topic.nonEmpty)(ReplyBlocks.topic(topic))),
              block(config.chat)(Option.when(chatText.nonEmpty)(ReplyBlocks.chat(chatText))),
              block(config.replyTarget)(
                Option.when(replyToText.nonEmpty)(
                  ReplyBlocks.replyTarget(replyToText, isReplyToBot)
                )
              ),
              block(config.userTrigger)(Some(ReplyBlocks.userTrigger(target, triggerText))),
              block(config.task)(
                Some(
                  ReplyBlocks.task(
                    Option.when(config.intent)(intent.toString),
                    Option.when(config.register)(register.toString),
                    minChars,
                    Option.when(config.date)(date),
                    isReplyToBot
                  )
                )
              )
            ).flatten
            ReplyContext(blocks)

    def apply(
        replyFeature: ReplyFeature,
        summarizeThreadFeature: SummarizeThreadFeature,
        classifyIntentFeature: ClassifyIntentFeature,
        summarizeUserFeature: SummarizeUserFeature,
        classifyRegisterFeature: ClassifyRegisterFeature,
        config: ReplyFeatureConfig
    ): LlmFeatures =
        new LlmFeatures:
            override def generateReply(ctx: ReplyContext): IO[String] =
                replyFeature.generateReply(ctx)

            override def assembleReply(
                target: UserRef,
                triggerText: String,
                minChars: Int,
                trigger: LlmService.Trigger,
                oldProfile: String,
                summary: String,
                topic: String,
                register: Register,
                intent: TagIntent,
                date: String,
                recentChat: List[LlmContextMessage]
            ): ReplyContext =
                assemble(
                  config,
                  target,
                  triggerText,
                  minChars,
                  trigger,
                  oldProfile,
                  summary,
                  topic,
                  register,
                  intent,
                  date,
                  recentChat
                )

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
