package nyetbot.service.llm.feature

import cats.effect.IO
import cats.effect.Ref
import io.circe.syntax.*
import munit.CatsEffectSuite
import nyetbot.Fixtures
import nyetbot.client.OllamaClient
import nyetbot.config.ClassifyIntentFeatureConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.UserId
import nyetbot.service.llm.TagIntent

class ClassifyIntentFeatureSpec extends CatsEffectSuite:

    private val config = ClassifyIntentFeatureConfig(
      model = "intent-model",
      temperature = 0.2,
      numPredict = 4,
      numCtx = 8192,
      think = false
    )

    private val chat = List(
      LlmContextMessage(Some(UserId(1L)), "Seb", "банки говно"),
      LlmContextMessage(Some(UserId(2L)), "Гоша", "казино хуже")
    )

    private class RecordingClient(
        ref: Ref[IO, List[OllamaClient.Req]],
        response: String
    ) extends OllamaClient:
        override def generate(req: OllamaClient.Req): IO[String] =
            ref.update(_ :+ req).as(response)

    test("builds a configured request and classifies a new question") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   =
                ClassifyIntentFeature(
                  RecordingClient(requests, "NEW"),
                  config,
                  Fixtures.llmConfig
                )
            result   <- feature.classifyIntent("эй бот, что с банками?", "", chat)
            captured <- requests.get
        yield
            assertEquals(result, TagIntent.NewQuestion)
            assertEquals(captured.size, 1)
            val req  = captured.head
            assertEquals(req.model, "intent-model")
            assertEquals(req.system, None)
            assertEquals(req.template, None)
            assertEquals(req.stream, false)
            assertEquals(req.think, false)
            assertEquals(req.options.numPredict, 4)
            assertEquals(req.options.temperature, 0.2)
            assertEquals(req.options.topP, None)
            assertEquals(req.options.topK, None)
            assertEquals(req.options.repeatPenalty, None)
            assertEquals(req.options.numCtx, 8192)
            assertEquals(req.options.stop, None)
            assert(req.prompt.contains("эй бот, что с банками?"))
            assert(req.prompt.contains("может быть пустым): нет"))
            assert(req.prompt.contains("Seb: банки говно"))
            assert(req.prompt.endsWith("Ответ:"))
            val json = req.asJson.deepDropNullValues
            assertEquals(json.hcursor.downField("options").get[Int]("num_predict"), Right(4))
            assertEquals(json.hcursor.downField("options").downField("top_p").focus, None)
            assertEquals(json.hcursor.downField("options").downField("stop").focus, None)
    }

    test("treats a response without NEW as contextual") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   =
                ClassifyIntentFeature(
                  RecordingClient(requests, "CONTEXT"),
                  config,
                  Fixtures.llmConfig
                )
            result   <- feature.classifyIntent("продолжение", "предыдущее сообщение", chat)
        yield assertEquals(result, TagIntent.Contextual)
    }
