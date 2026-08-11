package nyetbot.service.llm.context

import nyetbot.config.llm.BlockConfig
import nyetbot.model.NonEmptyString
import nyetbot.service.llm.LlmService.Trigger

object ReplyTargetFeature:
    final case class ReplyTarget(text: NonEmptyString, isBot: Boolean)

    def apply(config: BlockConfig): ContextFeature[ReplyTarget] =
        ContextFeature.pure(config.enabled) { in =>
            val (text, isBot) = in.trigger match
                case Trigger.Random(t)    => (t, false)
                case Trigger.Tagged(_, t) => (t, false)
                case Trigger.Reply(_, t)  => (t, true)
            NonEmptyString.either(text).toOption.map(ReplyTarget(_, isBot))
        }
