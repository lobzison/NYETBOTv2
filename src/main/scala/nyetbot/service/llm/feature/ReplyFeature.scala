package nyetbot.service.llm.feature

import nyetbot.client.OllamaClient
import nyetbot.config.llm.feature.ReplyFeatureConfig
import nyetbot.model.LlmContextMessage
import nyetbot.service.llm.LlmService
import nyetbot.service.llm.feature.ClassifyIntentFeature.TagIntent
import nyetbot.service.llm.feature.ClassifyRegisterFeature.Register
import cats.effect.IO
import nyetbot.model.ProfileModels.*
import org.slf4j.LoggerFactory
import nyetbot.model.NonEmptyString

trait ReplyFeature:
    def generateReply(ctx: ReplyFeature.ReplyContext): IO[String]

object ReplyFeature:

    private val logger = LoggerFactory.getLogger(getClass)

    // final case class ReplyContext2(
    //     target: UserRef,
    //     intent: Option[NonEmptyString],        // old intent
    //     regiseter: Option[NonEmptyString],     // old register
    //     userSummary: Option[NonEmptyString],   // old recentSummary
    //     threadSummary: Option[NonEmptyString], // old topic
    //     recentChat: List[LlmContextMessage],    //
    //     intent: Option[TagIntent]
    //
    // )

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
        trigger: LlmService.Trigger
    )

    def apply(
        client: OllamaClient,
        config: ReplyFeatureConfig
    ): ReplyFeature =
        new ReplyFeature:
            private val request = OllamaClient.Req.from(config.modelConfig)

            override def generateReply(ctx: ReplyContext): IO[String] =
                val prompt = Prompt.render(ctx)
                logger.debug("Prompt to send to LLM")
                logger.debug(prompt)
                client.generate(request.copy(prompt = prompt))

    object Prompt:
        private def section(title: String, body: String): String =
            s"[$title]\n$body"

        private def dossier(ctx: ReplyContext): Option[String] =
            val profile =
                if ctx.profile.isEmpty then "нет данных, новичок" else ctx.profile
            Some(
              section(
                "ДОСЬЕ НА СОБЕСЕДНИКА",
                s"""Кого разносишь: ${ctx.target.displayName}
Его давнее досье (как вёл себя раньше): $profile
Его свежие замашки (по последним сообщениям): ${ctx.recentSummary}"""
              )
            )

        private def topic(ctx: ReplyContext): Option[String] =
            Option.when(ctx.topic.nonEmpty)(section("СУТЬ ОБСУЖДЕНИЯ", ctx.topic))

        private def chat(ctx: ReplyContext): Option[String] =
            Some(section("КОНТЕКСТ ЧАТА", ChatLog.render(ctx.recentChat)))

        private def isReplyToBot(trigger: LlmService.Trigger): Boolean =
            trigger match
                case LlmService.Trigger.Reply(_, _) => true
                case _                              => false

        private def replyToText(trigger: LlmService.Trigger): String =
            trigger match
                case LlmService.Trigger.Random(t)    => t
                case LlmService.Trigger.Tagged(_, t) => t
                case LlmService.Trigger.Reply(_, t)  => t

        private def replyTarget(ctx: ReplyContext): Option[String] =
            Option.when(replyToText(ctx.trigger).nonEmpty) {
                val marker =
                    if isReplyToBot(ctx.trigger) then
                        "(это ТВОЁ прошлое сообщение — собеседник отвечает тебе)"
                    else ""
                val body   =
                    List(marker, replyToText(ctx.trigger)).filter(_.nonEmpty).mkString("\n")
                section("НА ЧТО ОН ОТВЕЧАЕТ", body)
            }

        private def trigger(ctx: ReplyContext): Option[String] =
            Some(
              section(
                "СООБЩЕНИЕ, НА КОТОРОЕ ОТВЕЧАЕШЬ",
                s"${ctx.target.displayName}: ${ctx.triggerText}"
              )
            )

        private def task(ctx: ReplyContext): Option[String] =
            val continuationDirective =
                if isReplyToBot(ctx.trigger) then
                    """
Собеседник ответил на твоё сообщение: отстаивай или докручивай свою позицию, отвечай именно на его возражение и не повторяй уже сказанное тобой."""
                else ""
            Some(
              section(
                "ЗАДАЧА",
                s"""Ты давний участник этого чата и отвечаешь на последнее сообщение в нити.
${ctx.intent}
${ctx.register}
Главное — выскажись по СУТИ обсуждаемой темы (см. [СУТЬ ОБСУЖДЕНИЯ] и [КОНТЕКСТ ЧАТА]):
дай свой конкретный тейк, наблюдение или мрачную теорию про сам предмет разговора. Цепляйся за
конкретные детали треда — цифры, названия, факты из сообщений. Подкол собеседника — приправа,
а не основа ответа.
Целься примерно в ${ctx.minChars} символов. Пиши строго по-русски.
Сейчас ${ctx.currentDate}.
Опирайся только на то, что реально написано в чате и досье — не выдумывай фактов и слов.
Никогда не упоминай слова «досье», «сводка», «контекст» и сам факт, что тебе дали информацию, —
ты просто помнишь этого человека и чат сам.
Держи один грамматический род собеседника в пределах ответа.
Не извиняйся, не ломай образ, не будь полезным ассистентом.$continuationDirective"""
              )
            )

        def render(ctx: ReplyContext): String =
            List(
              dossier(ctx),
              topic(ctx),
              chat(ctx),
              replyTarget(ctx),
              trigger(ctx),
              task(ctx)
            ).flatten.mkString("\n\n")
