package nyetbot.service.llm

import cats.effect.IO
import nyetbot.client.OllamaClient
import nyetbot.config.llm.ReplyGeneratorConfig
import nyetbot.service.llm.context.ChatLogFeature.ChatLog
import nyetbot.service.llm.context.DateFeature.ReplyDate
import nyetbot.service.llm.context.DossierFeature.Dossier
import nyetbot.service.llm.context.IntentFeature.TagIntent
import nyetbot.service.llm.context.RegisterFeature.Register
import nyetbot.service.llm.context.ReplyTargetFeature.ReplyTarget
import nyetbot.service.llm.context.TopicFeature.Topic
import nyetbot.service.llm.context.UserTriggerFeature.UserTrigger
import org.slf4j.LoggerFactory

trait ReplyGenerator:
    def generate(ctx: ReplyGenerator.ReplyContext): IO[String]

object ReplyGenerator:

    private val logger = LoggerFactory.getLogger(getClass)

    final case class ReplyContext(
        dossier: Option[Dossier],
        topic: Option[Topic],
        chatLog: Option[ChatLog],
        replyTarget: Option[ReplyTarget],
        userTrigger: Option[UserTrigger],
        register: Option[Register],
        intent: Option[TagIntent],
        date: Option[ReplyDate],
        minChars: Int
    )

    def apply(client: OllamaClient, config: ReplyGeneratorConfig): ReplyGenerator =
        new ReplyGenerator:
            private val request = OllamaClient.Req.from(config.modelConfig)

            override def generate(ctx: ReplyContext): IO[String] =
                val prompt = Prompt.render(ctx)
                logger.debug("Prompt to send to LLM")
                logger.debug(prompt)
                client.generate(request.copy(prompt = prompt))

    object Prompt:
        def render(ctx: ReplyContext): String =
            List(
              ctx.dossier.map(dossier),
              ctx.topic.map(t => section("СУТЬ ОБСУЖДЕНИЯ", t.value)),
              ctx.chatLog.map(c => section("КОНТЕКСТ ЧАТА", c.value)),
              ctx.replyTarget.map(replyTarget),
              ctx.userTrigger.map(userTrigger),
              Some(task(ctx))
            ).flatten.mkString("\n\n")

        private def section(title: String, body: String): String =
            s"[$title]\n$body"

        private def dossier(d: Dossier): String =
            val profile = d.profile.fold("нет данных, новичок")(_.value)
            val fresh   = d.fresh.fold("нет данных")(_.value)
            section(
              "ДОСЬЕ НА СОБЕСЕДНИКА",
              s"""Кого разносишь: ${d.who.displayName}
Его давнее досье (как вёл себя раньше): $profile
Его свежие замашки (по последним сообщениям): $fresh"""
            )

        private def replyTarget(r: ReplyTarget): String =
            val marker =
                if r.isBot then "(это ТВОЁ прошлое сообщение — собеседник отвечает тебе)"
                else ""
            val body   =
                List(marker, r.text.value).filter(_.nonEmpty).mkString("\n")
            section("НА ЧТО ОН ОТВЕЧАЕТ", body)

        private def userTrigger(t: UserTrigger): String =
            section("СООБЩЕНИЕ, НА КОТОРОЕ ОТВЕЧАЕШЬ", s"${t.who.displayName}: ${t.text.value}")

        private def intentDirective(intent: TagIntent): String = intent match
            case TagIntent.Contextual  =>
                "Тебя дёрнули внутри уже идущего спора — отвечай в контексте нити."
            case TagIntent.NewQuestion =>
                "Тебя дёрнули с новым, отдельным вопросом — отвечай именно на него, старьё не тащи."

        private def registerDirective(register: Register): String = register match
            case Register.Spor    =>
                "Это спорный тейк: найди слабое место, переверни его же слова, вызови на ответ."
            case Register.Sobytie =>
                "Это личное событие или поздравление: НЕ доёбывайся до человека. Поучаствуй " +
                    "по-своему — циничное поздравление, мрачный тост или едкий прогноз на будущее."
            case Register.Shutka  =>
                "Это шутка или мем: подхвати и докрути шутку в своём стиле, не разваливай её наездом."
            case Register.Vopros  =>
                "Это вопрос или обращение: ответь по существу, со своим мнением и лёгким подколом."
            case Register.Byt     =>
                "Это бытовая болтовня: вбрось свой тейк по теме как участник, без наезда на человека."

        private def task(ctx: ReplyContext): String =
            val continuation =
                Option.when(ctx.replyTarget.exists(_.isBot))(
                  "Собеседник ответил на твоё сообщение: отстаивай или докручивай свою позицию, отвечай " +
                      "именно на его возражение и не повторяй уже сказанное тобой."
                )
            val body         = List(
              Some("Ты давний участник этого чата и отвечаешь на последнее сообщение в нити."),
              ctx.intent.map(intentDirective),
              ctx.register.map(registerDirective),
              Some(
                "Главное — выскажись по СУТИ обсуждаемой темы (см. [СУТЬ ОБСУЖДЕНИЯ] и [КОНТЕКСТ ЧАТА]):"
              ),
              Some(
                "дай свой конкретный тейк, наблюдение или мрачную теорию про сам предмет разговора. " +
                    "Цепляйся за конкретные детали треда — цифры, названия, факты из сообщений. Подкол " +
                    "собеседника — приправа, а не основа ответа."
              ),
              Some(s"Целься примерно в ${ctx.minChars} символов. Пиши строго по-русски."),
              ctx.date.map(d => s"Сейчас ${d.value}."),
              Some(
                "Опирайся только на то, что реально написано в чате и досье — не выдумывай фактов и слов."
              ),
              Some(
                "Никогда не упоминай слова «досье», «сводка», «контекст» и сам факт, что тебе дали " +
                    "информацию, — ты просто помнишь этого человека и чат сам."
              ),
              Some("Держи один грамматический род собеседника в пределах ответа."),
              Some("Не извиняйся, не ломай образ, не будь полезным ассистентом."),
              continuation
            ).flatten.mkString("\n")
            section("ЗАДАЧА", body)
