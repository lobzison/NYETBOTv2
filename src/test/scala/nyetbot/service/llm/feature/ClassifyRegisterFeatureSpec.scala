package nyetbot.service.llm.feature

import cats.effect.IO
import cats.effect.Ref
import io.circe.syntax.*
import munit.CatsEffectSuite
import nyetbot.Fixtures
import nyetbot.client.OllamaClient
import nyetbot.config.llm.feature.ClassifyRegisterFeatureConfig
import nyetbot.config.llm.feature.OllamaModelConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.feature.ClassifyRegisterFeature.Register

class ClassifyRegisterFeatureSpec extends CatsEffectSuite:

    private val config = ClassifyRegisterFeatureConfig(
      modelConfig = OllamaModelConfig(
        model = "register-model",
        temperature = Some(0.2),
        numPredict = Some(6),
        numCtx = Some(8192),
        think = Some(false)
      )
    )

    private val chat = (1 to 10).toList.map(i =>
        LlmContextMessage(Some(UserId(i.toLong)), s"User$i", s"message-$i")
    )

    private class RecordingClient(
        ref: Ref[IO, List[OllamaClient.Req]],
        response: String
    ) extends OllamaClient:
        override def generate(req: OllamaClient.Req): IO[String] =
            ref.update(_ :+ req).as(response)

    test("builds a configured request and classifies the register") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   =
                ClassifyRegisterFeature(
                  RecordingClient(requests, "SPOR"),
                  config,
                  Fixtures.llmConfig
                )
            result   <- feature.classifyRegister("уникальный-триггер", chat)
            captured <- requests.get
        yield
            assertEquals(result, Register.Spor)
            assertEquals(captured.size, 1)
            val req  = captured.head
            assertEquals(req.model, "register-model")
            assertEquals(req.system, None)
            assertEquals(req.template, None)
            assertEquals(req.stream, false)
            assertEquals(req.think, Some(false))
            assertEquals(req.options.numPredict, Some(6))
            assertEquals(req.options.temperature, Some(0.2))
            assertEquals(req.options.topP, None)
            assertEquals(req.options.topK, None)
            assertEquals(req.options.repeatPenalty, None)
            assertEquals(req.options.numCtx, Some(8192))
            assertEquals(req.options.stop, None)
            assert(req.prompt.contains("уникальный-триггер"))
            assert(!req.prompt.contains("User1: message-1"))
            assert(!req.prompt.contains("User2: message-2"))
            assert(req.prompt.contains("User3: message-3"))
            assert(req.prompt.contains("User10: message-10"))
            val json = req.asJson.deepDropNullValues
            assertEquals(json.hcursor.downField("options").get[Int]("num_predict"), Right(6))
            assertEquals(json.hcursor.downField("options").downField("top_p").focus, None)
    }

    test("falls back to the everyday register for an unrecognized response") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            feature   =
                ClassifyRegisterFeature(
                  RecordingClient(requests, "неизвестно"),
                  config,
                  Fixtures.llmConfig
                )
            result   <- feature.classifyRegister("сообщение", chat)
        yield assertEquals(result, Register.Byt)
    }
