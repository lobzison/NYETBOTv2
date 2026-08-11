package nyetbot.service.llm

import cats.effect.IO
import cats.effect.Ref
import cats.effect.std.Random
import munit.CatsEffectSuite
import nyetbot.Fixtures
import nyetbot.model.LlmContextMessage
import nyetbot.model.NonEmptyString
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.LlmService.*
import nyetbot.service.llm.ReplyGenerator.ReplyContext
import nyetbot.service.llm.context.*
import nyetbot.service.llm.context.ChatLogFeature.ChatLog
import nyetbot.service.llm.context.DateFeature.ReplyDate
import nyetbot.service.llm.context.DossierFeature.*
import nyetbot.service.llm.context.IntentFeature.TagIntent
import nyetbot.service.llm.context.RegisterFeature.Register
import nyetbot.service.llm.context.ReplyTargetFeature.ReplyTarget
import nyetbot.service.llm.context.TopicFeature.Topic
import nyetbot.service.llm.context.UserTriggerFeature.UserTrigger

class LlmServiceSpec extends CatsEffectSuite:

    private val target  = UserRef(UserId(42L), DisplayName("Гоша"))
    private val chat    = List(LlmContextMessage(Some(UserId(42L)), "Гоша", "казино хуже"))
    private val inputs  = ReplyInputs(target, "триггер", Trigger.Random(""), chat, chat)
    private val dossier = Dossier(target, None, Some(UserSummary("свежая сводка")))

    private def feature[A](
        calls: Ref[IO, List[String]],
        name: String,
        out: Option[A]
    ): ContextFeature[A] =
        _ => calls.update(_ :+ name).as(out)

    private def allFeatures(calls: Ref[IO, List[String]]): ContextFeatures =
        ContextFeatures(
          dossier = feature(calls, "dossier", Some(dossier)),
          topic = feature(calls, "topic", Some(Topic("суть обсуждения"))),
          register = feature(calls, "register", Some(Register.Spor)),
          intent = feature(calls, "intent", Some(TagIntent.NewQuestion)),
          chatLog = feature(calls, "chatLog", Some(ChatLog("Гоша: казино хуже"))),
          replyTarget = feature(
            calls,
            "replyTarget",
            Some(ReplyTarget(NonEmptyString("позиция бота"), true))
          ),
          userTrigger =
              feature(calls, "userTrigger", Some(UserTrigger(target, NonEmptyString("триггер")))),
          date = feature(calls, "date", Some(ReplyDate("август 2026")))
        )

    private def noneFeatures(calls: Ref[IO, List[String]]): ContextFeatures =
        ContextFeatures(
          dossier = feature(calls, "dossier", None),
          topic = feature(calls, "topic", None),
          register = feature(calls, "register", None),
          intent = feature(calls, "intent", None),
          chatLog = feature(calls, "chatLog", None),
          replyTarget = feature(calls, "replyTarget", None),
          userTrigger = feature(calls, "userTrigger", None),
          date = feature(calls, "date", None)
        )

    private class RecordingGenerator(contexts: Ref[IO, List[ReplyContext]]) extends ReplyGenerator:
        def generate(ctx: ReplyContext): IO[String] = contexts.update(_ :+ ctx).as("шиза-ответ")

    private class RecordingRewriter(rewrites: Ref[IO, List[Dossier]]) extends ProfileRewriter:
        def rewrite(dossier: Dossier): IO[Unit] = rewrites.update(_ :+ dossier)

    private def mkService(
        features: ContextFeatures,
        generator: ReplyGenerator,
        rewriter: ProfileRewriter
    ): IO[LlmService] =
        Random
            .scalaUtilRandom[IO]
            .map(r =>
                LlmService(features, generator, rewriter, Fixtures.replyLengthConfig)(using r)
            )

    test("generateReply consults every context feature and returns the generated text") {
        for
            calls    <- Ref.of[IO, List[String]](Nil)
            contexts <- Ref.of[IO, List[ReplyContext]](Nil)
            rewrites <- Ref.of[IO, List[Dossier]](Nil)
            svc      <- mkService(
                          allFeatures(calls),
                          RecordingGenerator(contexts),
                          RecordingRewriter(rewrites)
                        )
            gen      <- svc.generateReply(inputs)
            seen     <- calls.get
        yield
            assertEquals(gen.text, "шиза-ответ")
            assertEquals(gen.dossier, Some(dossier))
            assertEquals(
              seen,
              List(
                "dossier",
                "topic",
                "register",
                "intent",
                "chatLog",
                "replyTarget",
                "userTrigger",
                "date"
              )
            )
    }

    test("feature outputs flow into the reply context unchanged") {
        for
            calls    <- Ref.of[IO, List[String]](Nil)
            contexts <- Ref.of[IO, List[ReplyContext]](Nil)
            rewrites <- Ref.of[IO, List[Dossier]](Nil)
            svc      <- mkService(
                          allFeatures(calls),
                          RecordingGenerator(contexts),
                          RecordingRewriter(rewrites)
                        )
            _        <- svc.generateReply(inputs)
            captured <- contexts.get
        yield
            assertEquals(captured.size, 1)
            val ctx = captured.head
            assertEquals(ctx.dossier, Some(dossier))
            assertEquals(ctx.topic, Some(Topic("суть обсуждения")))
            assertEquals(ctx.register, Some(Register.Spor))
            assertEquals(ctx.intent, Some(TagIntent.NewQuestion))
            assertEquals(ctx.chatLog, Some(ChatLog("Гоша: казино хуже")))
            assertEquals(ctx.replyTarget, Some(ReplyTarget(NonEmptyString("позиция бота"), true)))
            assertEquals(ctx.userTrigger, Some(UserTrigger(target, NonEmptyString("триггер"))))
            assertEquals(ctx.date, Some(ReplyDate("август 2026")))
            assert(ctx.minChars >= Fixtures.replyLengthConfig.minChars)
            assert(ctx.minChars <= Fixtures.replyLengthConfig.maxChars)
    }

    test("features returning None still produce a reply with an empty context") {
        for
            calls    <- Ref.of[IO, List[String]](Nil)
            contexts <- Ref.of[IO, List[ReplyContext]](Nil)
            rewrites <- Ref.of[IO, List[Dossier]](Nil)
            svc      <- mkService(
                          noneFeatures(calls),
                          RecordingGenerator(contexts),
                          RecordingRewriter(rewrites)
                        )
            gen      <- svc.generateReply(inputs)
            captured <- contexts.get
        yield
            assertEquals(gen.text, "шиза-ответ")
            assertEquals(gen.dossier, None)
            val ctx = captured.head
            assertEquals(ctx.dossier, None)
            assertEquals(ctx.topic, None)
            assertEquals(ctx.register, None)
            assertEquals(ctx.intent, None)
            assertEquals(ctx.chatLog, None)
            assertEquals(ctx.replyTarget, None)
            assertEquals(ctx.userTrigger, None)
            assertEquals(ctx.date, None)
    }

    test("rewriteProfile delegates to the rewriter when a dossier exists") {
        for
            calls    <- Ref.of[IO, List[String]](Nil)
            contexts <- Ref.of[IO, List[ReplyContext]](Nil)
            rewrites <- Ref.of[IO, List[Dossier]](Nil)
            svc      <- mkService(
                          allFeatures(calls),
                          RecordingGenerator(contexts),
                          RecordingRewriter(rewrites)
                        )
            _        <- svc.rewriteProfile(GeneratedReply("текст", Some(dossier)))
            seen     <- rewrites.get
        yield assertEquals(seen, List(dossier))
    }

    test("rewriteProfile does nothing without a dossier") {
        for
            calls    <- Ref.of[IO, List[String]](Nil)
            contexts <- Ref.of[IO, List[ReplyContext]](Nil)
            rewrites <- Ref.of[IO, List[Dossier]](Nil)
            svc      <- mkService(
                          allFeatures(calls),
                          RecordingGenerator(contexts),
                          RecordingRewriter(rewrites)
                        )
            _        <- svc.rewriteProfile(GeneratedReply("текст", None))
            seen     <- rewrites.get
        yield assertEquals(seen, Nil)
    }
