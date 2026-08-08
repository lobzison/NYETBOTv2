package nyetbot.service.llm

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
        rewriteOut: String = "обновлённое досье",
        replyContexts: Option[Ref[IO, List[ReplyContext]]] = None,
        threadInputs: Option[Ref[IO, List[List[LlmContextMessage]]]] = None,
        topicResult: Either[Throwable, String] = Right("суть обсуждения"),
        registerResult: Either[Throwable, Register] = Right(Register.Spor)
    ) extends LlmService:
        def generateReply(ctx: ReplyContext): IO[String]                                        =
            calls.update(_ :+ "generateReply").flatMap { _ =>
                replyContexts match
                    case Some(contexts) => contexts.update(_ :+ ctx).as("шиза-ответ")
                    case None           => IO.pure("шиза-ответ")
            }
        def summarizeUser(recent: List[LlmContextMessage], who: UserRef): IO[String]            =
            calls.update(_ :+ "summarizeUser").as("свежая сводка")
        def summarizeThread(recentChat: List[LlmContextMessage]): IO[String]                    =
            calls.update(_ :+ "summarizeThread") *>
                threadInputs.fold(IO.unit)(_.update(_ :+ recentChat)) *>
                IO.fromEither(topicResult)
        def rewriteProfile(oldProfile: String, recentSummary: String, who: UserRef): IO[String] =
            calls.update(_ :+ "rewriteProfile").as(rewriteOut)
        def classifyTagIntent(
            question: String,
            replyToText: String,
            recentChat: List[LlmContextMessage]
        ): IO[TagIntent] =
            calls.update(_ :+ "classifyTagIntent").as(TagIntent.NewQuestion)
        def classifyRegister(
            triggerText: String,
            recentChat: List[LlmContextMessage]
        ): IO[Register] =
            calls.update(_ :+ "classifyRegister") *> IO.fromEither(registerResult)

    private val target = UserRef(UserId(42L), DisplayName("Гоша"))
    private val chat   = List(LlmContextMessage(Some(UserId(42L)), "Гоша", "казино хуже"))

    private def mkService(repo: ProfileRepoInMemory, llm: LlmService): IO[ProfileServiceImpl] =
        Random
            .scalaUtilRandom[IO]
            .map(r => ProfileServiceImpl(repo, llm, Fixtures.profileServiceConfig)(using r))

    test(
      "random trigger skips intent classification but enriches the reply with topic and register"
    ) {
        for
            calls <- Ref.of[IO, List[String]](Nil)
            repo  <- ProfileRepoInMemory.create
            svc   <- mkService(repo, RecordingLlm(calls))
            gen   <- svc.generateReply(target, "триггер", chat, chat, Trigger.Random(""))
            seen  <- calls.get
        yield
            assertEquals(gen.text, "шиза-ответ")
            assertEquals(
              seen,
              List("summarizeUser", "summarizeThread", "classifyRegister", "generateReply")
            )
    }

    test("tagged trigger classifies intent after topic and register") {
        for
            calls <- Ref.of[IO, List[String]](Nil)
            repo  <- ProfileRepoInMemory.create
            svc   <- mkService(repo, RecordingLlm(calls))
            _     <- svc.generateReply(
                       target,
                       "триггер",
                       chat,
                       chat,
                       Trigger.Tagged("эй бот", "исходное", replyToBot = false)
                     )
            seen  <- calls.get
        yield assertEquals(
          seen,
          List(
            "summarizeUser",
            "summarizeThread",
            "classifyRegister",
            "classifyTagIntent",
            "generateReply"
          )
        )
    }

    test("topic summarization receives only the configured tail of recent chat") {
        val longChat = (1 to 12).toList.map(i =>
            LlmContextMessage(Some(UserId(i.toLong)), s"User$i", s"message-$i")
        )
        for
            calls  <- Ref.of[IO, List[String]](Nil)
            inputs <- Ref.of[IO, List[List[LlmContextMessage]]](Nil)
            repo   <- ProfileRepoInMemory.create
            svc    <- mkService(repo, RecordingLlm(calls, threadInputs = Some(inputs)))
            _      <- svc.generateReply(target, "триггер", chat, longChat, Trigger.Random(""))
            seen   <- inputs.get
        yield
            assertEquals(seen.size, 1)
            assertEquals(seen.head.map(_.text), (3 to 12).toList.map(i => s"message-$i"))
    }

    test("topic and register failures fall back and still produce a reply") {
        for
            calls    <- Ref.of[IO, List[String]](Nil)
            contexts <- Ref.of[IO, List[ReplyContext]](Nil)
            repo     <- ProfileRepoInMemory.create
            llm       = RecordingLlm(
                          calls,
                          replyContexts = Some(contexts),
                          topicResult = Left(new RuntimeException("topic unavailable")),
                          registerResult = Left(new RuntimeException("register unavailable"))
                        )
            svc      <- mkService(repo, llm)
            gen      <- svc.generateReply(target, "триггер", chat, chat, Trigger.Random(""))
            captured <- contexts.get
        yield
            assertEquals(gen.text, "шиза-ответ")
            assertEquals(captured.size, 1)
            assertEquals(captured.head.topic, "")
            assertEquals(captured.head.register, Register.Byt)
    }

    test("random trigger forwards ordinary reply-to text without marking it as the bot") {
        for
            calls    <- Ref.of[IO, List[String]](Nil)
            contexts <- Ref.of[IO, List[ReplyContext]](Nil)
            repo     <- ProfileRepoInMemory.create
            svc      <- mkService(repo, RecordingLlm(calls, replyContexts = Some(contexts)))
            _        <- svc.generateReply(
                          target,
                          "случайное сообщение",
                          chat,
                          chat,
                          Trigger.Random("сообщение другого человека")
                        )
            captured <- contexts.get
        yield
            assertEquals(captured.size, 1)
            assertEquals(captured.head.replyToText, "сообщение другого человека")
            assert(!captured.head.replyToBot)
    }

    test("reply-to-bot details reach the reply context") {
        for
            calls    <- Ref.of[IO, List[String]](Nil)
            contexts <- Ref.of[IO, List[ReplyContext]](Nil)
            repo     <- ProfileRepoInMemory.create
            svc      <- mkService(repo, RecordingLlm(calls, replyContexts = Some(contexts)))
            _        <- svc.generateReply(
                          target,
                          "возражение",
                          chat,
                          chat,
                          Trigger.Tagged("возражение", "позиция бота", replyToBot = true)
                        )
            captured <- contexts.get
        yield
            assertEquals(captured.size, 1)
            assertEquals(captured.head.replyToText, "позиция бота")
            assert(captured.head.replyToBot)
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
            gen   <- svc.generateReply(target, "триггер", chat, chat, Trigger.Random(""))
        yield assertEquals(gen.oldProfile, "")
    }
