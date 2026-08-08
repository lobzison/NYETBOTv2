package nyetbot.service.llm.feature

import cats.effect.IO
import cats.effect.Ref
import io.circe.syntax.*
import munit.CatsEffectSuite
import nyetbot.Fixtures
import nyetbot.client.OllamaClient
import nyetbot.config.ReplyFeatureConfig
import nyetbot.model.DisplayName
import nyetbot.model.LlmContextMessage
import nyetbot.model.UserId
import nyetbot.model.UserRef
import nyetbot.service.llm.Register
import nyetbot.service.llm.ReplyContext
import nyetbot.service.llm.TagIntent

class ReplyFeatureSpec extends CatsEffectSuite:

    private val config = ReplyFeatureConfig(
      model = "reply-model",
      system = "system prompt",
      template = "template",
      stop = List("stop", "another stop"),
      temperature = 0.85,
      topP = 0.95,
      topK = 40,
      repeatPenalty = 1.1,
      numPredict = 512,
      numCtx = 8192,
      think = false
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
      replyToText = "",
      replyToBot = false
    )

    private class RecordingClient(ref: Ref[IO, List[OllamaClient.Req]]) extends OllamaClient:
        override def generate(req: OllamaClient.Req): IO[String] =
            ref.update(_ :+ req).as("ответ")

    test("builds a configured request and changes only the prompt") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   = ReplyFeature(RecordingClient(requests), config, Fixtures.llmConfig)
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
            assertEquals(req.think, false)
            assertEquals(req.options.numPredict, 512)
            assertEquals(req.options.temperature, 0.85)
            assertEquals(req.options.topP, 0.95)
            assertEquals(req.options.topK, 40)
            assertEquals(req.options.repeatPenalty, 1.1)
            assertEquals(req.options.numCtx, 8192)
            assertEquals(req.options.stop, List("stop", "another stop"))
            assert(req.prompt.contains("триггер"))
            val json = req.asJson
            assertEquals(json.hcursor.downField("options").get[Int]("num_predict"), Right(512))
            assertEquals(json.hcursor.downField("options").get[Double]("top_p"), Right(0.95))
            assertEquals(
              json.hcursor.downField("options").get[Double]("repeat_penalty"),
              Right(1.1)
            )
    }
