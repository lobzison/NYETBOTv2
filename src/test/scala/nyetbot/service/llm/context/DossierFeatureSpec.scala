package nyetbot.service.llm.context

import cats.effect.IO
import cats.effect.Ref
import munit.CatsEffectSuite
import nyetbot.client.OllamaClient
import nyetbot.config.llm.DossierConfig
import nyetbot.config.llm.OllamaModelConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*
import nyetbot.repo.ProfileRepoInMemory
import nyetbot.service.llm.LlmService.Trigger
import nyetbot.service.llm.ReplyInputs

class DossierFeatureSpec extends CatsEffectSuite:

    private val config = DossierConfig(
      modelConfig = OllamaModelConfig(
        model = "user-model",
        temperature = Some(0.2),
        numPredict = Some(256),
        numCtx = Some(8192),
        think = Some(false)
      ),
      summaryMaxChars = 500
    )

    private val who  = UserRef(UserId(42L), DisplayName("Гоша Петров"))
    private val msgs = List(LlmContextMessage(Some(UserId(42L)), "Гоша", "банки говно"))

    private val inputs = ReplyInputs(who, "триггер", Trigger.Random(""), Nil, msgs)

    private class RecordingClient(
        ref: Ref[IO, List[OllamaClient.Req]],
        response: String
    ) extends OllamaClient:
        override def generate(req: OllamaClient.Req): IO[String] =
            ref.update(_ :+ req).as(response)

    test("summarises recent messages and picks up the stored profile") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            repo     <- ProfileRepoInMemory.create
            _        <- repo.upsertProfile(who.id, who.displayName, ProfileDescription("старое досье"))
            feature   = DossierFeature(RecordingClient(requests, "с".repeat(600)), repo, config)
            result   <- feature.get(inputs)
            captured <- requests.get
        yield
            assert(result.isDefined)
            val dossier = result.get
            assertEquals(dossier.who, who)
            assertEquals(dossier.profile, Some(ProfileDescription("старое досье")))
            assertEquals(dossier.fresh.map(_.value.length), Some(500))
            assertEquals(captured.size, 1)
            val req     = captured.head
            assertEquals(req.model, "user-model")
            assertEquals(req.options.numPredict, Some(256))
            assert(req.prompt.contains("Гоша Петров"))
            assert(req.prompt.contains("Гоша: банки говно"))
            assert(req.prompt.contains("500"))
            assert(req.prompt.endsWith("СВОДКА:"))
    }

    test("missing profile and empty summary yield no dossier") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            repo     <- ProfileRepoInMemory.create
            feature   = DossierFeature(RecordingClient(requests, ""), repo, config)
            result   <- feature.get(inputs)
        yield assertEquals(result, None)
    }

    test("keeps the stored profile when the summary comes back empty") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            repo     <- ProfileRepoInMemory.create
            _        <- repo.upsertProfile(who.id, who.displayName, ProfileDescription("старое досье"))
            feature   = DossierFeature(RecordingClient(requests, ""), repo, config)
            result   <- feature.get(inputs)
        yield
            assert(result.isDefined)
            assertEquals(result.get.profile, Some(ProfileDescription("старое досье")))
            assertEquals(result.get.fresh, None)
    }

    test("keeps the fresh summary when no profile is stored") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            repo     <- ProfileRepoInMemory.create
            feature   = DossierFeature(RecordingClient(requests, "свежая сводка"), repo, config)
            result   <- feature.get(inputs)
        yield
            assert(result.isDefined)
            assertEquals(result.get.profile, None)
            assertEquals(result.get.fresh.map(_.value: String), Some("свежая сводка"))
    }

    test("disabled dossier feature never calls the model") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            repo     <- ProfileRepoInMemory.create
            feature   = DossierFeature(
                          RecordingClient(requests, "сводка"),
                          repo,
                          config.copy(enabled = false)
                        )
            result   <- feature.get(inputs)
            captured <- requests.get
        yield
            assertEquals(result, None)
            assertEquals(captured, Nil)
    }
