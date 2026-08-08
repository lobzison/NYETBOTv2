package nyetbot.service.llm.feature

import cats.effect.IO
import cats.effect.Ref
import io.circe.syntax.*
import munit.CatsEffectSuite
import nyetbot.Fixtures
import nyetbot.client.OllamaClient
import nyetbot.config.llm.feature.OllamaModelConfig
import nyetbot.config.llm.feature.SummarizeThreadFeatureConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.UserId

class SummarizeThreadFeatureSpec extends CatsEffectSuite:

    private val config = SummarizeThreadFeatureConfig(
      modelConfig = OllamaModelConfig(
        model = "summary-model",
        temperature = Some(0.2),
        numPredict = Some(160),
        numCtx = Some(8192),
        think = Some(false)
      )
    )

    private val chat = List(
      LlmContextMessage(Some(UserId(1L)), "Seb", "банки говно"),
      LlmContextMessage(Some(UserId(2L)), "Гоша", "казино хуже")
    )

    private class RecordingClient(ref: Ref[IO, List[OllamaClient.Req]]) extends OllamaClient:
        override def generate(req: OllamaClient.Req): IO[String] =
            ref.update(_ :+ req).as("суть обсуждения")

    test("builds a configured request and renders the thread prompt") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   =
                SummarizeThreadFeature(RecordingClient(requests), config, Fixtures.llmConfig)
            result   <- feature.summarizeThread(chat)
            captured <- requests.get
        yield
            assertEquals(result, "суть обсуждения")
            assertEquals(captured.size, 1)
            val req  = captured.head
            assertEquals(req.model, "summary-model")
            assertEquals(req.system, None)
            assertEquals(req.template, None)
            assertEquals(req.stream, false)
            assertEquals(req.think, Some(false))
            assertEquals(req.options.numPredict, Some(160))
            assertEquals(req.options.temperature, Some(0.2))
            assertEquals(req.options.topP, None)
            assertEquals(req.options.topK, None)
            assertEquals(req.options.repeatPenalty, None)
            assertEquals(req.options.numCtx, Some(8192))
            assertEquals(req.options.stop, None)
            assert(req.prompt.contains("Seb: банки говно"))
            assert(req.prompt.contains("Гоша: казино хуже"))
            assert(req.prompt.endsWith("СУТЬ:"))
            val json = req.asJson.deepDropNullValues
            assertEquals(json.hcursor.downField("options").get[Int]("num_predict"), Right(160))
            assertEquals(json.hcursor.downField("options").downField("top_p").focus, None)
            assertEquals(json.hcursor.downField("options").downField("stop").focus, None)
    }
