package nyetbot.lab

import cats.effect.IO
import cats.effect.std.Random
import nyetbot.client.OllamaClient
import nyetbot.repo.ProfileRepoInMemory
import nyetbot.service.llm.LlmService
import nyetbot.service.llm.ProfileRewriter
import nyetbot.service.llm.ReplyGenerator
import nyetbot.service.llm.context.*

final case class ReplayWiring(llmService: LlmService, recorder: RecordingOllamaClient)

object ReplayWiring:
    def apply(client: OllamaClient, config: LabConfig)(using Random[IO]): IO[ReplayWiring] =
        for
            recorder    <- RecordingOllamaClient.create(client)
            profileRepo <- ProfileRepoInMemory.create
        yield
            val contextConfig   = config.ollama.context
            val contextFeatures = ContextFeatures(
              dossier = DossierFeature(recorder.dossier, profileRepo, contextConfig.dossier),
              topic = TopicFeature(recorder.topic, contextConfig.topic),
              register = RegisterFeature(recorder.register, contextConfig.register),
              intent = IntentFeature(recorder.intent, contextConfig.intent),
              chatLog = ChatLogFeature(contextConfig.chatLog),
              replyTarget = ReplyTargetFeature(contextConfig.replyTarget),
              userTrigger = UserTriggerFeature(contextConfig.userTrigger),
              date = DateFeature(contextConfig.date)
            )
            val replyGenerator  = ReplyGenerator(recorder.reply, config.ollama.reply)
            val profileRewriter =
                ProfileRewriter(recorder.profileRewrite, profileRepo, config.ollama.profileRewrite)
            ReplayWiring(
              LlmService(contextFeatures, replyGenerator, profileRewriter, config.replyLength),
              recorder
            )
