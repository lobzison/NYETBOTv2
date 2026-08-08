package nyetbot.model

import canoe.models.messages.TextMessage
import nyetbot.config.LlmFunctionalityConfig

final case class LlmContextMessage(userId: Option[UserId], userName: String, text: String)

object LlmContextMessage:
    def fromTextMessage(t: TextMessage, config: LlmFunctionalityConfig): LlmContextMessage =
        val user = t.from
            .map(u => s"${config.userPrefix}${u.firstName}_${u.lastName.getOrElse("")}")
            .getOrElse("user")
        LlmContextMessage(t.from.map(u => UserId(u.id)), user, t.text)
