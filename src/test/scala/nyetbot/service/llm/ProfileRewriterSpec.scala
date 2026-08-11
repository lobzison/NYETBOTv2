package nyetbot.service.llm

import cats.effect.IO
import cats.effect.Ref
import munit.CatsEffectSuite
import nyetbot.client.OllamaClient
import nyetbot.config.llm.OllamaModelConfig
import nyetbot.config.llm.ProfileRewriterConfig
import nyetbot.model.ProfileModels.*
import nyetbot.repo.ProfileRepoInMemory
import nyetbot.service.llm.context.DossierFeature.*

class ProfileRewriterSpec extends CatsEffectSuite:

    private val config = ProfileRewriterConfig(
      modelConfig = OllamaModelConfig(model = "rewrite-model", numPredict = Some(200)),
      profileMaxChars = 300
    )

    private val who = UserRef(UserId(42L), DisplayName("Гоша Петров"))

    private class RecordingClient(
        ref: Ref[IO, List[OllamaClient.Req]],
        response: String
    ) extends OllamaClient:
        override def generate(req: OllamaClient.Req): IO[String] =
            ref.update(_ :+ req).as(response)

    test("merges the dossier and persists a description truncated to the limit") {
        val longOut = "я".repeat(500)
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            repo     <- ProfileRepoInMemory.create
            rewriter  = ProfileRewriter(RecordingClient(requests, longOut), repo, config)
            _        <- rewriter.rewrite(
                          Dossier(
                            who,
                            Some(ProfileDescription("старое досье")),
                            Some(UserSummary("свежая сводка"))
                          )
                        )
            saved    <- repo.getProfile(UserId(42L))
            captured <- requests.get
        yield
            assertEquals(saved.map(_.description.value.length), Some(300))
            assertEquals(captured.size, 1)
            val req = captured.head
            assertEquals(req.model, "rewrite-model")
            assertEquals(req.options.numPredict, Some(200))
            assert(req.prompt.contains("старое досье"))
            assert(req.prompt.contains("свежая сводка"))
            assert(req.prompt.contains("Гоша Петров"))
            assert(req.prompt.contains("300"))
            assert(req.prompt.endsWith("ОБНОВЛЁННОЕ ДОСЬЕ:"))
    }

    test("renders a missing stored profile as the empty placeholder") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            repo     <- ProfileRepoInMemory.create
            rewriter  = ProfileRewriter(RecordingClient(requests, "новое досье"), repo, config)
            _        <- rewriter.rewrite(Dossier(who, None, Some(UserSummary("свежая сводка"))))
            captured <- requests.get
        yield assert(captured.head.prompt.contains("СТАРОЕ ДОСЬЕ:\nпусто"))
    }

    test("does nothing when the dossier has no fresh summary") {
        for
            requests <- Ref.of[IO, List[OllamaClient.Req]](Nil)
            repo     <- ProfileRepoInMemory.create
            rewriter  = ProfileRewriter(RecordingClient(requests, "новое досье"), repo, config)
            _        <- rewriter.rewrite(Dossier(who, Some(ProfileDescription("старое")), None))
            saved    <- repo.getProfile(UserId(42L))
            captured <- requests.get
        yield
            assertEquals(captured, Nil)
            assertEquals(saved, None)
    }
