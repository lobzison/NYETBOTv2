package nyetbot.service.llm

import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*

final case class ReplyInputs(
    target: UserRef,
    triggerText: String,
    trigger: LlmService.Trigger,
    recentChat: List[LlmContextMessage],
    recentUserMsgs: List[LlmContextMessage]
)
