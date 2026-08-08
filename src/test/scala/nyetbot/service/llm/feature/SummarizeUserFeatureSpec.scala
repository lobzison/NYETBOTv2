package nyetbot.service.llm.feature

import cats.effect.IO
import cats.effect.Ref
import io.circe.syntax.*
import munit.CatsEffectSuite
import nyetbot.Fixtures
import nyetbot.client.OllamaClient
import nyetbot.config.llm.feature.OllamaModelConfig
import nyetbot.config.llm.feature.SummarizeUserFeatureConfig
import nyetbot.model.DisplayName
import nyetbot.model.LlmContextMessage
import nyetbot.model.UserId
import nyetbot.model.UserRef

class SummarizeUserFeatureSpec extends CatsEffectSuite:

    private val config = SummarizeUserFeatureConfig(
      modelConfig = OllamaModelConfig(
        model = "user-model",
        temperature = Some(0.2),
        numPredict = Some(256),
        numCtx = Some(8192),
        think = Some(false)
      ),
      rewriteModelConfig = OllamaModelConfig(
        model = "user-model",
        temperature = Some(0.2),
        numPredict = Some(200),
        numCtx = Some(8192),
        think = Some(false)
      ),
      profileMaxChars = 300,
      summaryMaxChars = 500
    )

    private val who  = UserRef(UserId(42L), DisplayName("Гоша Петров"))
    private val chat = List(
      LlmContextMessage(Some(UserId(42L)), "Гоша", "банки говно")
    )

    private class RecordingClient(
        requests: Ref[IO, List[OllamaClient.Req]],
        responses: Ref[IO, List[String]]
    ) extends OllamaClient:
        override def generate(req: OllamaClient.Req): IO[String] =
            requests.update(_ :+ req) *>
                responses.modify {
                    case response :: rest => (rest, response)
                    case Nil              => (Nil, "")
                }

    test("builds separate summary and rewrite requests with feature settings") {
        for
            requests  <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            responses <- Ref.of[IO, List[String]](List("с".repeat(600), "д".repeat(400)))
            feature    = SummarizeUserFeature(
                           RecordingClient(requests, responses),
                           config,
                           Fixtures.llmConfig
                         )
            summary   <- feature.summarizeUser(chat, who)
            profile   <- feature.rewriteProfile("старое досье", "свежая сводка", who)
            captured  <- requests.get
        yield
            assertEquals(summary.length, 500)
            assertEquals(profile.length, 300)
            assertEquals(captured.size, 2)
            val summaryRequest = captured.head
            val rewriteRequest = captured(1)
            assertEquals(summaryRequest.model, "user-model")
            assertEquals(summaryRequest.options.numPredict, Some(256))
            assertEquals(rewriteRequest.options.numPredict, Some(200))
            assertEquals(summaryRequest.options.temperature, Some(0.2))
            assertEquals(summaryRequest.options.numCtx, Some(8192))
            assertEquals(summaryRequest.think, Some(false))
            assertEquals(summaryRequest.options.topP, None)
            assertEquals(summaryRequest.options.topK, None)
            assertEquals(summaryRequest.options.repeatPenalty, None)
            assertEquals(summaryRequest.options.stop, None)
            assert(summaryRequest.prompt.contains("Гоша Петров"))
            assert(summaryRequest.prompt.contains("Гоша: банки говно"))
            assert(rewriteRequest.prompt.contains("старое досье"))
            assert(rewriteRequest.prompt.contains("свежая сводка"))
            assert(rewriteRequest.prompt.contains("Гоша Петров"))
            val json           = summaryRequest.asJson.deepDropNullValues
            assertEquals(json.hcursor.downField("options").get[Int]("num_predict"), Right(256))
            assertEquals(json.hcursor.downField("options").downField("top_p").focus, None)
    }
