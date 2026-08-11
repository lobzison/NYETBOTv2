package nyetbot.service.llm.context

import munit.CatsEffectSuite
import nyetbot.config.llm.BlockConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.NonEmptyString
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.LlmService.Trigger
import nyetbot.service.llm.ReplyInputs
import nyetbot.service.llm.context.ChatLogFeature.ChatLog
import nyetbot.service.llm.context.ReplyTargetFeature.ReplyTarget
import nyetbot.service.llm.context.UserTriggerFeature.UserTrigger

class BlockFeaturesSpec extends CatsEffectSuite:

    private val who  = UserRef(UserId(42L), DisplayName("Гоша"))
    private val chat = List(
      LlmContextMessage(Some(UserId(1L)), "Seb", "банки говно"),
      LlmContextMessage(Some(UserId(42L)), "Гоша", "казино хуже")
    )

    private def inputs(
        trigger: Trigger = Trigger.Random(""),
        triggerText: String = "триггер",
        recentChat: List[LlmContextMessage] = chat
    ) = ReplyInputs(who, triggerText, trigger, recentChat, Nil)

    test("chat log renders the recent chat") {
        ChatLogFeature(BlockConfig())
            .get(inputs())
            .map(assertEquals(_, Some(ChatLog("Seb: банки говно\nГоша: казино хуже"))))
    }

    test("chat log yields None for an empty chat") {
        ChatLogFeature(BlockConfig()).get(inputs(recentChat = Nil)).map(assertEquals(_, None))
    }

    test("reply target marks a reply to the bot") {
        ReplyTargetFeature(BlockConfig())
            .get(inputs(trigger = Trigger.Reply("возражение", "позиция бота")))
            .map(assertEquals(_, Some(ReplyTarget(NonEmptyString("позиция бота"), true))))
    }

    test("reply target keeps an ordinary reply-to unmarked") {
        ReplyTargetFeature(BlockConfig())
            .get(inputs(trigger = Trigger.Tagged("вопрос", "чужое сообщение")))
            .map(assertEquals(_, Some(ReplyTarget(NonEmptyString("чужое сообщение"), false))))
    }

    test("reply target yields None when nothing was replied to") {
        ReplyTargetFeature(BlockConfig())
            .get(inputs(trigger = Trigger.Random("")))
            .map(assertEquals(_, None))
    }

    test("user trigger carries the target and the trigger text") {
        UserTriggerFeature(BlockConfig())
            .get(inputs())
            .map(assertEquals(_, Some(UserTrigger(who, NonEmptyString("триггер")))))
    }

    test("user trigger yields None for empty text") {
        UserTriggerFeature(BlockConfig()).get(inputs(triggerText = "")).map(assertEquals(_, None))
    }

    test("date renders a non-empty month and year") {
        DateFeature(BlockConfig()).get(inputs()).map(out => assert(out.exists(_.value.nonEmpty)))
    }

    test("disabled blocks yield None") {
        val disabled = BlockConfig(enabled = false)
        for
            chatLog     <- ChatLogFeature(disabled).get(inputs())
            replyTarget <- ReplyTargetFeature(disabled)
                               .get(inputs(trigger = Trigger.Reply("возражение", "позиция бота")))
            userTrigger <- UserTriggerFeature(disabled).get(inputs())
            date        <- DateFeature(disabled).get(inputs())
        yield
            assertEquals(chatLog, None)
            assertEquals(replyTarget, None)
            assertEquals(userTrigger, None)
            assertEquals(date, None)
    }
