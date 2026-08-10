package nyetbot.service.llm

import munit.FunSuite
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.LlmFeatures.*
import nyetbot.service.llm.feature.ClassifyIntentFeature.TagIntent
import nyetbot.service.llm.feature.ClassifyRegisterFeature.Register
import nyetbot.service.llm.feature.ReplyFeature.ReplyContext

class OllamaPromptsSpec extends FunSuite:

    private val who  = UserRef(UserId(42L), DisplayName("Гоша Петров"))
    private val chat = List(
      LlmContextMessage(Some(UserId(1L)), "Seb", "банки говно"),
      LlmContextMessage(Some(UserId(42L)), "Гоша", "казино хуже")
    )

    private def ctx(
        profile: String = "",
        summary: String = "s",
        topic: String = "банки и казино устроены против людей",
        intent: TagIntent = TagIntent.Contextual,
        register: Register = Register.Byt,
        minChars: Int = 200,
        trigger: String = "клава за 200 баксов",
        currentDate: String = "август 2026",
        replyToText: String = "",
        replyToBot: Boolean = false
    ) = ReplyContext(
      target = who,
      profile = profile,
      recentSummary = summary,
      topic = topic,
      recentChat = chat,
      intent = intent,
      register = register,
      minChars = minChars,
      triggerText = trigger,
      currentDate = currentDate,
      replyToText = replyToText,
      replyToBot = replyToBot
    )

    test("reply prompt carries the participant framing and topic-first directive") {
        val p = OllamaPrompts.reply(
          ctx(profile = "старый параноик", summary = "свежая сводка", minChars = 250)
        )
        assert(p.contains("Гоша Петров"))
        assert(p.contains("старый параноик"))
        assert(p.contains("свежая сводка"))
        assert(p.contains("Ты давний участник этого чата"))
        assert(p.contains("Главное — выскажись по СУТИ обсуждаемой темы"))
        assert(p.contains("Целься примерно в 250 символов"))
        assert(p.contains("Seb: банки говно"))
    }

    test("reply prompt orders topic, chat, reply-to and trigger blocks") {
        val p            = OllamaPrompts.reply(
          ctx(replyToText = "моя прошлая позиция", replyToBot = true)
        )
        val topicIndex   = p.indexOf("[СУТЬ ОБСУЖДЕНИЯ]")
        val contextIndex = p.indexOf("[КОНТЕКСТ ЧАТА]")
        val replyToIndex = p.indexOf("[НА ЧТО ОН ОТВЕЧАЕТ]")
        val triggerIndex = p.indexOf("[СООБЩЕНИЕ, НА КОТОРОЕ ОТВЕЧАЕШЬ]")
        assert(topicIndex < contextIndex)
        assert(contextIndex < replyToIndex)
        assert(replyToIndex < triggerIndex)
    }

    test("reply prompt pins the exact message being answered") {
        val p = OllamaPrompts.reply(ctx(trigger = "уникальный-триггер-текст"))
        assert(p.contains("уникальный-триггер-текст"))
        assert(p.contains("[СООБЩЕНИЕ, НА КОТОРОЕ ОТВЕЧАЕШЬ]"))
    }

    test(
      "reply prompt marks the bot message being replied to and ends with the continuation directive"
    ) {
        val p         = OllamaPrompts.reply(
          ctx(replyToText = "моя прошлая позиция", replyToBot = true)
        )
        val directive =
            "Собеседник ответил на твоё сообщение: отстаивай или докручивай свою позицию, " +
                "отвечай именно на его возражение и не повторяй уже сказанное тобой."
        assert(p.contains("(это ТВОЁ прошлое сообщение — собеседник отвечает тебе)"))
        assert(p.contains("моя прошлая позиция"))
        assert(p.endsWith(directive))
    }

    test("ordinary reply prompt omits the bot ownership marker") {
        val p = OllamaPrompts.reply(
          ctx(replyToText = "сообщение другого человека", replyToBot = false)
        )
        assert(p.contains("[НА ЧТО ОН ОТВЕЧАЕТ]"))
        assert(!p.contains("(это ТВОЁ прошлое сообщение — собеседник отвечает тебе)"))
    }

    test("reply prompt omits the reply-to block when reply-to text is empty") {
        val p = OllamaPrompts.reply(ctx(replyToText = "", replyToBot = false))
        assert(!p.contains("[НА ЧТО ОН ОТВЕЧАЕТ]"))
    }

    test("reply prompt omits the topic block when the topic is empty") {
        val p = OllamaPrompts.reply(ctx(topic = ""))
        assert(!p.contains("\n[СУТЬ ОБСУЖДЕНИЯ]\n"))
    }

    test("reply prompt includes the current date") {
        val p = OllamaPrompts.reply(ctx(currentDate = "август 2026"))
        assert(p.contains("Сейчас август 2026."))
    }

    test("reply prompt uses the contextual intent line") {
        assert(
          OllamaPrompts.reply(ctx(intent = TagIntent.Contextual)).contains("уже идущего спора")
        )
    }

    test("reply prompt uses the new-question intent line") {
        assert(
          OllamaPrompts
              .reply(ctx(intent = TagIntent.NewQuestion))
              .contains("новым, отдельным вопросом")
        )
    }

    test("reply prompt selects the instruction for every register") {
        val cases = List(
          Register.Spor    -> "Это спорный тейк: найди слабое место",
          Register.Sobytie -> "Это личное событие или поздравление: НЕ доёбывайся до человека",
          Register.Shutka  -> "Это шутка или мем: подхвати и докрути шутку",
          Register.Vopros  -> "Это вопрос или обращение: ответь по существу",
          Register.Byt     -> "Это бытовая болтовня: вбрось свой тейк"
        )
        cases.foreach { case (register, expected) =>
            assert(OllamaPrompts.reply(ctx(register = register)).contains(expected))
        }
    }

    test("empty profile renders the newcomer placeholder") {
        assert(OllamaPrompts.reply(ctx(profile = "")).contains("нет данных, новичок"))
    }

    test("summary prompt carries the char limit and ends with the cue label") {
        val p = OllamaPrompts.summary(chat, who, 500)
        assert(p.contains("500"))
        assert(p.endsWith("СВОДКА:"))
    }

    test("topic prompt asks for the current discussion and renders chat") {
        val p = OllamaPrompts.topic(chat)
        assert(p.contains("Опиши в 2-3 предложениях, о чём сейчас идёт разговор"))
        assert(p.contains("Seb: банки говно"))
        assert(p.endsWith("СУТЬ:"))
    }

    test("register prompt lists all five options and includes the trigger") {
        val p = OllamaPrompts.register("уникальный-триггер", chat)
        List("SPOR", "SOBYTIE", "SHUTKA", "VOPROS", "BYT").foreach(option =>
            assert(p.contains(option))
        )
        assert(p.contains("Последнее сообщение: уникальный-триггер"))
    }

    test("register prompt limits recent context to eight messages") {
        val longChat = (1 to 10).toList.map(i =>
            LlmContextMessage(Some(UserId(i.toLong)), s"User$i", s"message-$i")
        )
        val p        = OllamaPrompts.register("триггер", longChat)
        assert(!p.contains("User1: message-1\n"))
        assert(!p.contains("User2: message-2\n"))
        assert(p.contains("message-3"))
        assert(p.contains("message-10"))
    }

    test("rewrite prompt carries the profile char limit and ends with the cue label") {
        val p = OllamaPrompts.rewrite("старое", "новое", who, 300)
        assert(p.contains("300"))
        assert(p.endsWith("ОБНОВЛЁННОЕ ДОСЬЕ:"))
    }

    test("intent prompt lists CONTEXT and NEW and includes the reply-to text") {
        val p = OllamaPrompts.intent("эй бот", "исходное сообщение", chat)
        assert(p.contains("CONTEXT"))
        assert(p.contains("NEW"))
        assert(p.contains("исходное сообщение"))
    }

    test("intent prompt marks an empty reply-to") {
        val p = OllamaPrompts.intent("эй бот", "", chat)
        assert(p.contains("может быть пустым): нет"))
    }
