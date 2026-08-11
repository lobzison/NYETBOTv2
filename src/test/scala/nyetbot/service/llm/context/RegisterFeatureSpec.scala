package nyetbot.service.llm.context

import cats.effect.IO
import cats.effect.Ref
import munit.CatsEffectSuite
import nyetbot.client.OllamaClient
import nyetbot.config.llm.OllamaModelConfig
import nyetbot.config.llm.RegisterConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.LlmService.Trigger
import nyetbot.service.llm.ReplyInputs
import nyetbot.service.llm.context.RegisterFeature.Register

class RegisterFeatureSpec extends CatsEffectSuite:

    private val config = RegisterConfig(
      modelConfig = OllamaModelConfig(
        model = "register-model",
        temperature = Some(0.2),
        numPredict = Some(6),
        numCtx = Some(8192),
        think = Some(false)
      )
    )

    private val who  = UserRef(UserId(42L), DisplayName("Гоша"))
    private val chat = (1 to 10).toList.map(i =>
        LlmContextMessage(Some(UserId(i.toLong)), s"User$i", s"message-$i")
    )

    private val inputs = ReplyInputs(who, "уникальный-триггер", Trigger.Random(""), chat, Nil)

    private class RecordingClient(
        ref: Ref[IO, List[OllamaClient.Req]],
        response: String
    ) extends OllamaClient:
        override def generate(req: OllamaClient.Req): IO[String] =
            ref.update(_ :+ req).as(response)

    test("builds a configured request and classifies the register") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   = RegisterFeature(RecordingClient(requests, "SPOR"), config)
            result   <- feature.get(inputs)
            captured <- requests.get
        yield
            assertEquals(result, Some(Register.Spor))
            assertEquals(captured.size, 1)
            val req = captured.head
            assertEquals(req.model, "register-model")
            assertEquals(req.options.numPredict, Some(6))
            assert(req.prompt.contains("уникальный-триггер"))
            assert(!req.prompt.contains("User1: message-1"))
            assert(!req.prompt.contains("User2: message-2"))
            assert(req.prompt.contains("User3: message-3"))
            assert(req.prompt.contains("User10: message-10"))
            List("SPOR", "SOBYTIE", "SHUTKA", "VOPROS", "BYT").foreach(option =>
                assert(req.prompt.contains(option))
            )
    }

    test("falls back to the everyday register for an unrecognized response") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   = RegisterFeature(RecordingClient(requests, "неизвестно"), config)
            result   <- feature.get(inputs)
        yield assertEquals(result, Some(Register.Byt))
    }

    test("disabled register feature never calls the model") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   = RegisterFeature(
                          RecordingClient(requests, "SPOR"),
                          config.copy(enabled = false)
                        )
            result   <- feature.get(inputs)
            captured <- requests.get
        yield
            assertEquals(result, None)
            assertEquals(captured, Nil)
    }
