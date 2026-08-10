package nyetbot.service.llm.feature

import nyetbot.model.LlmContextMessage

object ChatLog:
    def render(chat: List[LlmContextMessage]): String =
        chat.map(m => s"${m.userName}: ${m.text}").mkString("\n")
