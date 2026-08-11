package nyetbot.service.llm.context

import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.all.Empty
import io.github.iltotore.iron.constraint.any.Not
import nyetbot.config.llm.BlockConfig
import nyetbot.model.LlmContextMessage

object ChatLogFeature:
    type ChatLog = ChatLog.T
    object ChatLog extends RefinedType[String, Not[Empty]]

    def apply(config: BlockConfig): ContextFeature[ChatLog] =
        ContextFeature.pure(config.enabled) { in =>
            ChatLog.either(LlmContextMessage.render(in.recentChat)).toOption
        }
