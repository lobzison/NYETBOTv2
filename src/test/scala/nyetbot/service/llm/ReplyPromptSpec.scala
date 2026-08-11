package nyetbot.service.llm

import munit.FunSuite
import nyetbot.model.NonEmptyString
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.ReplyGenerator.ReplyContext
import nyetbot.service.llm.context.ChatLogFeature.ChatLog
import nyetbot.service.llm.context.DateFeature.ReplyDate
import nyetbot.service.llm.context.DossierFeature.*
import nyetbot.service.llm.context.IntentFeature.TagIntent
import nyetbot.service.llm.context.RegisterFeature.Register
import nyetbot.service.llm.context.ReplyTargetFeature.ReplyTarget
import nyetbot.service.llm.context.TopicFeature.Topic
import nyetbot.service.llm.context.UserTriggerFeature.UserTrigger

class ReplyPromptSpec extends FunSuite:

    private val who = UserRef(UserId(42L), DisplayName("Гоша Петров"))

    private def render(
        dossier: Option[Dossier] = Some(
          Dossier(
            who,
            Some(ProfileDescription("старый параноик")),
            Some(UserSummary("свежая сводка"))
          )
        ),
        topic: Option[Topic] = Some(Topic("суть обсуждения")),
        chatLog: Option[ChatLog] = Some(ChatLog("Seb: банки говно\nГоша: казино хуже")),
        replyTarget: Option[ReplyTarget] = None,
        userTrigger: Option[UserTrigger] = Some(
          UserTrigger(who, NonEmptyString("клава за 200 баксов"))
        ),
        register: Option[Register] = Some(Register.Byt),
        intent: Option[TagIntent] = None,
        date: Option[ReplyDate] = Some(ReplyDate("август 2026")),
        minChars: Int = 200
    ): String =
        ReplyGenerator.Prompt.render(
          ReplyContext(
            dossier,
            topic,
            chatLog,
            replyTarget,
            userTrigger,
            register,
            intent,
            date,
            minChars
          )
        )

    test("reply prompt carries the participant framing and topic-first directive") {
        val p = render(minChars = 250)
        assert(p.contains("Гоша Петров"))
        assert(p.contains("старый параноик"))
        assert(p.contains("свежая сводка"))
        assert(p.contains("Ты давний участник этого чата"))
        assert(p.contains("Главное — выскажись по СУТИ обсуждаемой темы"))
        assert(p.contains("Целься примерно в 250 символов"))
        assert(p.contains("Seb: банки говно"))
    }

    test("reply prompt orders dossier, topic, chat, reply-to and trigger blocks") {
        val p            =
            render(replyTarget = Some(ReplyTarget(NonEmptyString("моя прошлая позиция"), true)))
        val dossierIndex = p.indexOf("[ДОСЬЕ НА СОБЕСЕДНИКА]")
        val topicIndex   = p.indexOf("[СУТЬ ОБСУЖДЕНИЯ]")
        val contextIndex = p.indexOf("[КОНТЕКСТ ЧАТА]")
        val replyToIndex = p.indexOf("[НА ЧТО ОН ОТВЕЧАЕТ]")
        val triggerIndex = p.indexOf("[СООБЩЕНИЕ, НА КОТОРОЕ ОТВЕЧАЕШЬ]")
        assert(dossierIndex >= 0)
        assert(dossierIndex < topicIndex)
        assert(topicIndex < contextIndex)
        assert(contextIndex < replyToIndex)
        assert(replyToIndex < triggerIndex)
    }

    test("reply prompt pins the exact message being answered") {
        val p = render(userTrigger = Some(UserTrigger(who, NonEmptyString("уникальный-триггер"))))
        assert(p.contains("уникальный-триггер"))
        assert(p.contains("[СООБЩЕНИЕ, НА КОТОРОЕ ОТВЕЧАЕШЬ]"))
    }

    test(
      "reply prompt marks the bot message being replied to and ends with the continuation directive"
    ) {
        val p         =
            render(replyTarget = Some(ReplyTarget(NonEmptyString("моя прошлая позиция"), true)))
        val directive =
            "Собеседник ответил на твоё сообщение: отстаивай или докручивай свою позицию, отвечай " +
                "именно на его возражение и не повторяй уже сказанное тобой."
        assert(p.contains("(это ТВОЁ прошлое сообщение — собеседник отвечает тебе)"))
        assert(p.contains("моя прошлая позиция"))
        assert(p.endsWith(directive))
    }

    test("ordinary reply prompt omits the bot ownership marker") {
        val p = render(replyTarget = Some(ReplyTarget(NonEmptyString("чужое сообщение"), false)))
        assert(p.contains("[НА ЧТО ОН ОТВЕЧАЕТ]"))
        assert(!p.contains("(это ТВОЁ прошлое сообщение — собеседник отвечает тебе)"))
    }

    test("missing blocks disappear from the prompt") {
        val p = render(
          dossier = None,
          topic = None,
          chatLog = None,
          replyTarget = None,
          userTrigger = None,
          register = None,
          intent = None,
          date = None
        )
        assert(!p.contains("[ДОСЬЕ НА СОБЕСЕДНИКА]"))
        assert(!p.contains("[СУТЬ ОБСУЖДЕНИЯ]\n"))
        assert(!p.contains("[КОНТЕКСТ ЧАТА]\n"))
        assert(!p.contains("[НА ЧТО ОН ОТВЕЧАЕТ]"))
        assert(!p.contains("[СООБЩЕНИЕ, НА КОТОРОЕ ОТВЕЧАЕШЬ]"))
        assert(!p.contains("Сейчас "))
        assert(!p.contains("Это бытовая болтовня"))
        assert(!p.contains("Тебя дёрнули"))
        assert(p.startsWith("[ЗАДАЧА]"))
    }

    test("reply prompt includes the current date") {
        assert(render().contains("Сейчас август 2026."))
    }

    test("reply prompt uses the contextual intent line") {
        assert(render(intent = Some(TagIntent.Contextual)).contains("уже идущего спора"))
    }

    test("reply prompt uses the new-question intent line") {
        assert(render(intent = Some(TagIntent.NewQuestion)).contains("новым, отдельным вопросом"))
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
            assert(render(register = Some(register)).contains(expected))
        }
    }

    test("dossier without a stored profile renders the newcomer placeholder") {
        val p = render(dossier = Some(Dossier(who, None, Some(UserSummary("свежая сводка")))))
        assert(p.contains("нет данных, новичок"))
    }
