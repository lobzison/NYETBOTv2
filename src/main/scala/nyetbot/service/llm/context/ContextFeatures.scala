package nyetbot.service.llm.context

import nyetbot.service.llm.context.ChatLogFeature.ChatLog
import nyetbot.service.llm.context.DateFeature.ReplyDate
import nyetbot.service.llm.context.DossierFeature.Dossier
import nyetbot.service.llm.context.IntentFeature.TagIntent
import nyetbot.service.llm.context.RegisterFeature.Register
import nyetbot.service.llm.context.ReplyTargetFeature.ReplyTarget
import nyetbot.service.llm.context.TopicFeature.Topic
import nyetbot.service.llm.context.UserTriggerFeature.UserTrigger

final case class ContextFeatures(
    dossier: ContextFeature[Dossier],
    topic: ContextFeature[Topic],
    register: ContextFeature[Register],
    intent: ContextFeature[TagIntent],
    chatLog: ContextFeature[ChatLog],
    replyTarget: ContextFeature[ReplyTarget],
    userTrigger: ContextFeature[UserTrigger],
    date: ContextFeature[ReplyDate]
)
