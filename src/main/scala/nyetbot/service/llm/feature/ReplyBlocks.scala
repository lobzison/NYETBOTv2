package nyetbot.service.llm.feature

import nyetbot.model.ProfileModels.*

object ReplyBlocks:

    def render(blocks: List[String]): String =
        blocks.mkString("\n\n")

    private def section(title: String, body: String): String =
        s"[$title]\n$body"

    def dossier(target: UserRef, profile: String, summary: String): String =
        val p = if profile.nonEmpty then profile else "нет данных, новичок"
        section(
          "ДОСЬЕ НА СОБЕСЕДНИКА",
          s"""Кого разносишь: ${target.displayName}
Его давнее досье (как вёл себя раньше): $p
Его свежие замашки (по последним сообщениям): $summary"""
        )

    def topic(topic: String): String =
        section("СУТЬ ОБСУЖДЕНИЯ", topic)

    def chat(chat: String): String =
        section("КОНТЕКСТ ЧАТА", chat)

    def replyTarget(replyToText: String, replyToBot: Boolean): String =
        val marker =
            if replyToBot then "(это ТВОЁ прошлое сообщение — собеседник отвечает тебе)"
            else ""
        val body   =
            List(marker, replyToText).filter(_.nonEmpty).mkString("\n")
        section("НА ЧТО ОН ОТВЕЧАЕТ", body)

    def userTrigger(target: UserRef, triggerText: String): String =
        section("СООБЩЕНИЕ, НА КОТОРОЕ ОТВЕЧАЕШЬ", s"${target.displayName}: $triggerText")

    def task(
        intent: Option[String],
        register: Option[String],
        minChars: Int,
        date: Option[String],
        replyToBot: Boolean
    ): String =
        val continuation =
            if replyToBot then
                Some(
                  "Собеседник ответил на твоё сообщение: отстаивай или докручивай свою позицию, отвечай " +
                      "именно на его возражение и не повторяй уже сказанное тобой."
                )
            else None
        val body         = List(
          Some("Ты давний участник этого чата и отвечаешь на последнее сообщение в нити."),
          intent,
          register,
          Some(
            "Главное — выскажись по СУТИ обсуждаемой темы (см. [СУТЬ ОБСУЖДЕНИЯ] и [КОНТЕКСТ ЧАТА]):"
          ),
          Some(
            "дай свой конкретный тейк, наблюдение или мрачную теорию про сам предмет разговора. " +
                "Цепляйся за конкретные детали треда — цифры, названия, факты из сообщений. Подкол " +
                "собеседника — приправа, а не основа ответа."
          ),
          Some(s"Целься примерно в $minChars символов. Пиши строго по-русски."),
          date.map(d => s"Сейчас $d."),
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
