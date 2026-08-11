package nyetbot.service.llm.context

import cats.effect.IO
import cats.effect.Ref
import munit.CatsEffectSuite
import nyetbot.client.OllamaClient
import nyetbot.config.llm.OllamaModelConfig
import nyetbot.config.llm.TopicConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.LlmService.Trigger
import nyetbot.service.llm.ReplyInputs
import nyetbot.service.llm.context.TopicFeature.Topic

class TopicFeatureSpec extends CatsEffectSuite:

    private val config = TopicConfig(
      modelConfig = OllamaModelConfig(
        model = "summary-model",
        temperature = Some(0.2),
        numPredict = Some(160),
        numCtx = Some(8192),
        think = Some(false)
      ),
      contextWindow = 10
    )

    private val who  = UserRef(UserId(42L), DisplayName("Гоша"))
    private val chat = List(
      LlmContextMessage(Some(UserId(1L)), "Seb", "банки говно"),
      LlmContextMessage(Some(UserId(42L)), "Гоша", "казино хуже")
    )

    private def inputs(recentChat: List[LlmContextMessage]) =
        ReplyInputs(who, "триггер", Trigger.Random(""), recentChat, Nil)

    private class RecordingClient(
        ref: Ref[IO, List[OllamaClient.Req]],
        response: String
    ) extends OllamaClient:
        override def generate(req: OllamaClient.Req): IO[String] =
            ref.update(_ :+ req).as(response)

    test("builds a configured request and renders the thread prompt") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   = TopicFeature(RecordingClient(requests, "суть обсуждения"), config)
            result   <- feature.get(inputs(chat))
            captured <- requests.get
        yield
            assertEquals(result, Some(Topic("суть обсуждения")))
            assertEquals(captured.size, 1)
            val req = captured.head
            assertEquals(req.model, "summary-model")
            assertEquals(req.stream, false)
            assertEquals(req.think, Some(false))
            assertEquals(req.options.numPredict, Some(160))
            assertEquals(req.options.temperature, Some(0.2))
            assertEquals(req.options.numCtx, Some(8192))
            assert(req.prompt.contains("Seb: банки говно"))
            assert(req.prompt.contains("Гоша: казино хуже"))
            assert(req.prompt.endsWith("СУТЬ:"))
    }

    test("feeds only the configured tail of recent chat") {
        val longChat = (1 to 12).toList.map(i =>
            LlmContextMessage(Some(UserId(i.toLong)), s"User$i", s"message-$i")
        )
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   = TopicFeature(RecordingClient(requests, "суть"), config)
            _        <- feature.get(inputs(longChat))
            captured <- requests.get
        yield
            val prompt = captured.head.prompt
            assert(!prompt.contains("User1: message-1"))
            assert(!prompt.contains("User2: message-2"))
            assert(prompt.contains("User3: message-3"))
            assert(prompt.contains("User12: message-12"))
    }

    test("an empty response yields no topic") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   = TopicFeature(RecordingClient(requests, ""), config)
            result   <- feature.get(inputs(chat))
        yield assertEquals(result, None)
    }

    test("a failing call yields no topic") {
        val failing = new OllamaClient:
            override def generate(req: OllamaClient.Req): IO[String] =
                IO.raiseError(new RuntimeException("ollama down"))
        TopicFeature(failing, config).get(inputs(chat)).map(assertEquals(_, None))
    }

    test("disabled topic feature never calls the model") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   = TopicFeature(
                          RecordingClient(requests, "суть"),
                          config.copy(enabled = false)
                        )
            result   <- feature.get(inputs(chat))
            captured <- requests.get
        yield
            assertEquals(result, None)
            assertEquals(captured, Nil)
    }
