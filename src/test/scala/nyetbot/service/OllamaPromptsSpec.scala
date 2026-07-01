package nyetbot.service

import munit.FunSuite
import nyetbot.Fixtures
import nyetbot.model.LlmContextMessage
import nyetbot.model.UserRef

class OllamaPromptsSpec extends FunSuite:

    private val cfg  = Fixtures.llmConfig
    private val who  = UserRef(42L, "Гоша Петров")
    private val chat = List(
      LlmContextMessage(Some(1L), "Seb", "банки говно"),
      LlmContextMessage(Some(42L), "Гоша", "казино хуже")
    )

    private def ctx(
        profile: String = "",
        summary: String = "s",
        intent: TagIntent = TagIntent.Contextual,
        minChars: Int = 200,
        trigger: String = "клава за 200 баксов"
    ) = ReplyContext(who, profile, summary, chat, intent, minChars, trigger)

    test("reply prompt carries target, profile, summary, minChars, trigger and schizo directive") {
        val p = OllamaPrompts.reply(
          ctx(profile = "старый параноик", summary = "свежая сводка", minChars = 250),
          cfg
        )
        assert(p.contains("Гоша Петров"))
        assert(p.contains("старый параноик"))
        assert(p.contains("свежая сводка"))
        assert(p.contains("250"))
        assert(p.contains("забористой шизой"))
        assert(p.contains("Seb: банки говно"))
    }

    test("reply prompt pins the exact message being answered") {
        val p = OllamaPrompts.reply(ctx(trigger = "уникальный-триггер-текст"), cfg)
        assert(p.contains("уникальный-триггер-текст"))
        assert(p.contains("[СООБЩЕНИЕ, НА КОТОРОЕ ОТВЕЧАЕШЬ]"))
    }

    test("reply prompt uses the contextual intent line") {
        assert(
          OllamaPrompts.reply(ctx(intent = TagIntent.Contextual), cfg).contains("уже идущего спора")
        )
    }

    test("reply prompt uses the new-question intent line") {
        assert(
          OllamaPrompts
              .reply(ctx(intent = TagIntent.NewQuestion), cfg)
              .contains("новым, отдельным вопросом")
        )
    }

    test("empty profile renders the newcomer placeholder") {
        assert(OllamaPrompts.reply(ctx(profile = ""), cfg).contains("нет данных, новичок"))
    }

    test("summary prompt carries the char limit and ends with the cue label") {
        val p = OllamaPrompts.summary(chat, who, cfg)
        assert(p.contains("500"))
        assert(p.endsWith("СВОДКА:"))
    }

    test("rewrite prompt carries the profile char limit and ends with the cue label") {
        val p = OllamaPrompts.rewrite("старое", "новое", who, cfg)
        assert(p.contains("300"))
        assert(p.endsWith("ОБНОВЛЁННОЕ ДОСЬЕ:"))
    }

    test("intent prompt lists CONTEXT and NEW and includes the reply-to text") {
        val p = OllamaPrompts.intent("эй бот", "исходное сообщение", chat, cfg)
        assert(p.contains("CONTEXT"))
        assert(p.contains("NEW"))
        assert(p.contains("исходное сообщение"))
    }

    test("intent prompt marks an empty reply-to") {
        val p = OllamaPrompts.intent("эй бот", "", chat, cfg)
        assert(p.contains("может быть пустым): нет"))
    }
