package nyetbot.service.llm.feature

import cats.effect.IO
import cats.effect.Ref
import io.circe.syntax.*
import munit.CatsEffectSuite
import nyetbot.client.OllamaClient
import nyetbot.config.llm.feature.OllamaModelConfig
import nyetbot.config.llm.feature.ReplyFeatureConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.LlmService.Trigger
import nyetbot.service.llm.feature.ClassifyIntentFeature.TagIntent
import nyetbot.service.llm.feature.ClassifyRegisterFeature.Register
import nyetbot.service.llm.feature.ReplyFeature.ReplyContext

class ReplyFeatureSpec extends CatsEffectSuite:

    private val config = ReplyFeatureConfig(
      modelConfig = OllamaModelConfig(
        model = "reply-model",
        system = Some("system prompt"),
        template = Some("template"),
        temperature = Some(0.85),
        numPredict = Some(512),
        numCtx = Some(8192),
        think = Some(false),
        topP = Some(0.95),
        topK = Some(40),
        repeatPenalty = Some(1.1),
        stop = Some(List("stop", "another stop"))
      )
    )

    private val context = ReplyContext(
      target = UserRef(UserId(42L), DisplayName("Гоша")),
      profile = "старый профиль",
      recentSummary = "свежая сводка",
      topic = "тема",
      recentChat = List(LlmContextMessage(Some(UserId(42L)), "Гоша", "сообщение")),
      intent = TagIntent.Contextual,
      register = Register.Byt,
      minChars = 200,
      triggerText = "триггер",
      currentDate = "август 2026",
      trigger = Trigger.Random("")
    )

    private class RecordingClient(ref: Ref[IO, List[OllamaClient.Req]]) extends OllamaClient:
        override def generate(req: OllamaClient.Req): IO[String] =
            ref.update(_ :+ req).as("ответ")

    test("builds a configured request and changes only the prompt") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   = ReplyFeature(RecordingClient(requests), config)
            result   <- feature.generateReply(context)
            captured <- requests.get
        yield
            assertEquals(result, "ответ")
            assertEquals(captured.size, 1)
            val req  = captured.head
            assertEquals(req.model, "reply-model")
            assertEquals(req.system, Some("system prompt"))
            assertEquals(req.template, Some("template"))
            assertEquals(req.stream, false)
            assertEquals(req.think, Some(false))
            assertEquals(req.options.numPredict, Some(512))
            assertEquals(req.options.temperature, Some(0.85))
            assertEquals(req.options.topP, Some(0.95))
            assertEquals(req.options.topK, Some(40))
            assertEquals(req.options.repeatPenalty, Some(1.1))
            assertEquals(req.options.numCtx, Some(8192))
            assertEquals(req.options.stop, Some(List("stop", "another stop")))
            assert(req.prompt.contains("триггер"))
            val json = req.asJson
            assertEquals(json.hcursor.downField("options").get[Int]("num_predict"), Right(512))
            assertEquals(json.hcursor.downField("options").get[Double]("top_p"), Right(0.95))
            assertEquals(
              json.hcursor.downField("options").get[Double]("repeat_penalty"),
              Right(1.1)
            )
    }
