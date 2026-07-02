package nyetbot.service

import cats.effect.IO
import cats.effect.Ref
import cats.effect.std.Random
import io.github.iltotore.iron.*
import munit.CatsEffectSuite
import nyetbot.Fixtures
import nyetbot.model.DisplayName
import nyetbot.model.LlmContextMessage
import nyetbot.model.UserId
import nyetbot.model.UserRef
import nyetbot.repo.ProfileRepoInMemory

class ProfileServiceSpec extends CatsEffectSuite:

    private class RecordingLlm(
        calls: Ref[IO, List[String]],
        rewriteOut: String = "обновлённое досье"
    ) extends LlmService:
        def generateReply(ctx: ReplyContext): IO[String]                                        =
            calls.update(_ :+ "generateReply").as("шиза-ответ")
        def summarizeUser(recent: List[LlmContextMessage], who: UserRef): IO[String]            =
            calls.update(_ :+ "summarizeUser").as("свежая сводка")
        def rewriteProfile(oldProfile: String, recentSummary: String, who: UserRef): IO[String] =
            calls.update(_ :+ "rewriteProfile").as(rewriteOut)
        def classifyTagIntent(
            question: String,
            replyToText: String,
            recentChat: List[LlmContextMessage]
        ): IO[TagIntent] =
            calls.update(_ :+ "classifyTagIntent").as(TagIntent.NewQuestion)

    private val target = UserRef(UserId(42L), DisplayName("Гоша"))
    private val chat   = List(LlmContextMessage(Some(UserId(42L)), "Гоша", "казино хуже"))

    private def mkService(repo: ProfileRepoInMemory, llm: LlmService): IO[ProfileServiceImpl] =
        Random
            .scalaUtilRandom[IO]
            .map(r => ProfileServiceImpl(repo, llm, Fixtures.llmConfig)(using r))

    test("random trigger does no intent classification and keeps the call order") {
        for
            calls <- Ref.of[IO, List[String]](Nil)
            repo  <- ProfileRepoInMemory.create
            svc   <- mkService(repo, RecordingLlm(calls))
            gen   <- svc.generateReply(target, "триггер", chat, chat, Trigger.Random)
            seen  <- calls.get
        yield
            assertEquals(gen.text, "шиза-ответ")
            assertEquals(seen, List("summarizeUser", "generateReply"))
    }

    test("tagged trigger classifies intent between summary and reply") {
        for
            calls <- Ref.of[IO, List[String]](Nil)
            repo  <- ProfileRepoInMemory.create
            svc   <- mkService(repo, RecordingLlm(calls))
            _     <- svc.generateReply(
                       target,
                       "триггер",
                       chat,
                       chat,
                       Trigger.Tagged("эй бот", "исходное")
                     )
            seen  <- calls.get
        yield assertEquals(seen, List("summarizeUser", "classifyTagIntent", "generateReply"))
    }

    test("rewriteProfile persists a description truncated to <= 300 chars") {
        val longOut = "я".repeat(500)
        for
            calls <- Ref.of[IO, List[String]](Nil)
            repo  <- ProfileRepoInMemory.create
            svc   <- mkService(repo, RecordingLlm(calls, rewriteOut = longOut))
            _     <- svc.rewriteProfile(target, GeneratedReply("t", "сводка", "старое"))
            saved <- repo.getProfile(UserId(42L))
        yield
            assert(saved.isDefined)
            assertEquals(saved.get.description.value.length, 300)
    }

    test("an empty stored profile is forwarded as an empty oldProfile") {
        for
            calls <- Ref.of[IO, List[String]](Nil)
            repo  <- ProfileRepoInMemory.create
            svc   <- mkService(repo, RecordingLlm(calls))
            gen   <- svc.generateReply(target, "триггер", chat, chat, Trigger.Random)
        yield assertEquals(gen.oldProfile, "")
    }
