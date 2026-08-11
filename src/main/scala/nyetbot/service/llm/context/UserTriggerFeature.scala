package nyetbot.service.llm.context

import nyetbot.config.llm.BlockConfig
import nyetbot.model.NonEmptyString
import nyetbot.model.ProfileModels.*

object UserTriggerFeature:
    final case class UserTrigger(who: UserRef, text: NonEmptyString)

    def apply(config: BlockConfig): ContextFeature[UserTrigger] =
        ContextFeature.pure(config.enabled) { in =>
            NonEmptyString.either(in.triggerText).toOption.map(UserTrigger(in.target, _))
        }
