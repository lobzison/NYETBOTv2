package nyetbot.service.llm.context

import cats.effect.IO
import cats.effect.Ref
import munit.CatsEffectSuite
import nyetbot.client.OllamaClient
import nyetbot.config.llm.IntentConfig
import nyetbot.config.llm.OllamaModelConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.LlmService.Trigger
import nyetbot.service.llm.ReplyInputs
import nyetbot.service.llm.context.IntentFeature.TagIntent

class IntentFeatureSpec extends CatsEffectSuite:

    private val config = IntentConfig(
      modelConfig = OllamaModelConfig(
        model = "intent-model",
        temperature = Some(0.2),
        numPredict = Some(4),
        numCtx = Some(8192),
        think = Some(false)
      )
    )

    private val who  = UserRef(UserId(42L), DisplayName("Гоша"))
    private val chat = List(
      LlmContextMessage(Some(UserId(1L)), "Seb", "банки говно"),
      LlmContextMessage(Some(UserId(42L)), "Гоша", "казино хуже")
    )

    private def inputs(trigger: Trigger) =
        ReplyInputs(who, "триггер", trigger, chat, Nil)

    private class RecordingClient(
        ref: Ref[IO, List[OllamaClient.Req]],
        response: String
    ) extends OllamaClient:
        override def generate(req: OllamaClient.Req): IO[String] =
            ref.update(_ :+ req).as(response)

    test("classifies a tagged question as new") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   = IntentFeature(RecordingClient(requests, "NEW"), config)
            result   <- feature.get(inputs(Trigger.Tagged("эй бот, что с банками?", "")))
            captured <- requests.get
        yield
            assertEquals(result, Some(TagIntent.NewQuestion))
            assertEquals(captured.size, 1)
            val req = captured.head
            assertEquals(req.model, "intent-model")
            assertEquals(req.options.numPredict, Some(4))
            assert(req.prompt.contains("эй бот, что с банками?"))
            assert(req.prompt.contains("может быть пустым): нет"))
            assert(req.prompt.contains("Seb: банки говно"))
            assert(req.prompt.endsWith("Ответ:"))
    }

    test("classifies a reply continuation as contextual") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   = IntentFeature(RecordingClient(requests, "CONTEXT"), config)
            result   <- feature.get(inputs(Trigger.Reply("продолжение", "предыдущее сообщение")))
            captured <- requests.get
        yield
            assertEquals(result, Some(TagIntent.Contextual))
            assert(captured.head.prompt.contains("предыдущее сообщение"))
    }

    test("a random trigger yields no intent and never calls the model") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   = IntentFeature(RecordingClient(requests, "NEW"), config)
            result   <- feature.get(inputs(Trigger.Random("что-то")))
            captured <- requests.get
        yield
            assertEquals(result, None)
            assertEquals(captured, Nil)
    }

    test("disabled intent feature never calls the model even when tagged") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   = IntentFeature(
                          RecordingClient(requests, "NEW"),
                          config.copy(enabled = false)
                        )
            result   <- feature.get(inputs(Trigger.Tagged("эй бот", "")))
            captured <- requests.get
        yield
            assertEquals(result, None)
            assertEquals(captured, Nil)
    }
