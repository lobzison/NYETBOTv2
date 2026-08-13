package nyetbot.lab

import cats.effect.IO
import cats.effect.Resource
import cats.syntax.all.*
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax.*
import munit.CatsEffectSuite
import nyetbot.client.OllamaClient
import nyetbot.config.LlmFunctionalityConfig
import nyetbot.model.ProfileModels.UserId
import nyetbot.service.llm.LlmService.Trigger

class ReplayAppSpec extends CatsEffectSuite:

    private val llmConfig = LlmFunctionalityConfig(
      botName = "NYETBOT",
      botAlias = "@nyetterbot",
      messageEvery = 150,
      chatBufferSize = 200,
      replyContextWindow = 20,
      recentUserMessages = 50
    )

    private val scenarioSource =
        """include classpath("lab-base.conf")
          |nyetbot.ollama.domain = "localhost"
          |nyetbot.ollama.reply.model-config.temperature = 1.2
          |""".stripMargin

    private val stub: OllamaClient        = _ => IO.pure("stub-response")
    private val failingStub: OllamaClient = _ => IO.raiseError(new RuntimeException("boom"))

    private def msg(id: Long, text: String, isBot: Boolean = false): CorpusMessage =
        CorpusMessage(
          id = MessageId(id),
          date = "2026-05-01T10:00:00",
          userId = Some(UserId(id)),
          userName = s"user_$id",
          isBot = isBot,
          text = text,
          replyToMessageId = None
        )

    private def window(
        idx: Int,
        trigger: WindowTrigger,
        messages: List[CorpusMessage]
    ): CorpusWindow =
        CorpusWindow(
          meta = WindowMeta(
            sourceDump = "dump.json",
            chatId = ChatId(1581584348L),
            seed = 42L,
            windowIndex = idx,
            startDate = "2026-05-01T10:00:00",
            endDate = "2026-05-01T10:00:00",
            messageCount = messages.size
          ),
          trigger = trigger,
          messages = messages
        )

    private def write(path: Path, content: String): IO[Unit] =
        Stream
            .emit(content)
            .through(fs2.text.utf8.encode)
            .through(Files[IO].writeAll(path))
            .compile
            .drain

    private def setup(dir: Path, windows: List[CorpusWindow]): IO[ReplayApp.ReplayArgs] =
        val corpus = dir / "corpus"
        val scen   = dir / "scenarios"
        for
            _ <- Files[IO].createDirectories(corpus)
            _ <- Files[IO].createDirectories(scen)
            _ <- windows.zipWithIndex.traverse_ { (w, i) =>
                     write(corpus / f"window-$i%02d.json", w.asJson.deepDropNullValues.spaces2)
                 }
            _ <- write(scen / "hot.conf", scenarioSource)
        yield ReplayApp.ReplayArgs(List(corpus), List(scen), dir / "out")

    private def readOutput(dir: Path, name: String): IO[Json] =
        Files[IO]
            .readUtf8(dir / "out" / name)
            .compile
            .string
            .map(raw => parse(raw).fold(throw _, identity))

    private def calls(json: Json): List[Json] =
        json.hcursor.downField("calls").focus.flatMap(_.asArray).map(_.toList).getOrElse(Nil)

    private def tags(json: Json): List[String] =
        calls(json).flatMap(_.hcursor.get[String]("tag").toOption)

    private def replyRequest(json: Json): Json =
        calls(json)
            .find(_.hcursor.get[String]("tag").contains("reply"))
            .getOrElse(fail(s"no reply call in $json"))
            .hcursor
            .downField("request")
            .focus
            .getOrElse(fail(s"reply call has no request in $json"))

    test("replay records tagged calls, the reply and the scenario override") {
        Files[IO].tempDirectory.use { dir =>
            val w = window(
              0,
              WindowTrigger(replyToBot = false, replyToText = ""),
              List(
                msg(1, "первое сообщение"),
                msg(2, "бот-реплика", isBot = true),
                msg(3, "казино хуже")
              )
            )
            for
                args <- setup(dir, List(w))
                res  <- ReplayApp.replay(args, _ => Resource.pure(stub))
                json <- readOutput(dir, "window-00__hot.json")
            yield
                assertEquals(res, Right(()))
                assertEquals(tags(json), List("dossier", "topic", "register", "reply"))
                assertEquals(json.hcursor.get[String]("reply"), Right("stub-response"))
                assert(json.hcursor.downField("error").focus.contains(Json.Null))
                assert(json.hcursor.get[Long]("totalMillis").exists(_ >= 0L))
                assertEquals(json.hcursor.downField("scenario").get[String]("name"), Right("hot"))
                assertEquals(
                  json.hcursor.downField("scenario").get[String]("source"),
                  Right(scenarioSource)
                )
                assertEquals(
                  json.hcursor.downField("input").downField("meta").get[Int]("windowIndex"),
                  Right(0)
                )
                assertEquals(
                  json.hcursor.downField("input").downField("trigger").get[Boolean]("replyToBot"),
                  Right(false)
                )
                val request = replyRequest(json)
                assertEquals(
                  request.hcursor.downField("options").get[Double]("temperature"),
                  Right(1.2)
                )
                val prompt  = request.hcursor.get[String]("prompt").fold(throw _, identity)
                assert(prompt.contains("NYETBOT: бот-реплика"), prompt)
                assert(prompt.contains("user_3: казино хуже"), prompt)
                calls(json).foreach { call =>
                    assertEquals(call.hcursor.get[String]("response"), Right("stub-response"))
                }
        }
    }

    test("tagged trigger calls intent and replaces the alias in the reply prompt") {
        Files[IO].tempDirectory.use { dir =>
            val w = window(
              0,
              WindowTrigger(replyToBot = false, replyToText = ""),
              List(msg(1, "первое"), msg(2, "@nyetterbot что думаешь"))
            )
            for
                args <- setup(dir, List(w))
                _    <- ReplayApp.replay(args, _ => Resource.pure(stub))
                json <- readOutput(dir, "window-00__hot.json")
            yield
                assertEquals(tags(json), List("dossier", "topic", "register", "intent", "reply"))
                val prompt =
                    replyRequest(json).hcursor.get[String]("prompt").fold(throw _, identity)
                assert(prompt.contains("user_2: NYETBOT что думаешь"), prompt)
        }
    }

    test("reply-to-bot trigger calls intent and marks the reply target as the bot") {
        Files[IO].tempDirectory.use { dir =>
            val w = window(
              0,
              WindowTrigger(replyToBot = true, replyToText = "позиция бота"),
              List(msg(1, "позиция бота", isBot = true), msg(2, "не согласен"))
            )
            for
                args <- setup(dir, List(w))
                _    <- ReplayApp.replay(args, _ => Resource.pure(stub))
                json <- readOutput(dir, "window-00__hot.json")
            yield
                assertEquals(tags(json), List("dossier", "topic", "register", "intent", "reply"))
                val prompt =
                    replyRequest(json).hcursor.get[String]("prompt").fold(throw _, identity)
                assert(prompt.contains("это ТВОЁ прошлое сообщение"), prompt)
                assert(prompt.contains("позиция бота"), prompt)
        }
    }

    test("deriveTrigger: reply-to-bot wins over a tag in the text") {
        assertEquals(
          ReplayApp.deriveTrigger(
            msg(5, "@nyetterbot не согласен"),
            WindowTrigger(replyToBot = true, replyToText = "позиция бота"),
            llmConfig
          ),
          ("NYETBOT не согласен", Trigger.Reply("@nyetterbot не согласен", "позиция бота"))
        )
    }

    test("deriveTrigger: alias in the text makes a tagged trigger") {
        assertEquals(
          ReplayApp.deriveTrigger(
            msg(5, "@nyetterbot как дела"),
            WindowTrigger(replyToBot = false, replyToText = "чей-то текст"),
            llmConfig
          ),
          ("NYETBOT как дела", Trigger.Tagged("@nyetterbot как дела", "чей-то текст"))
        )
    }

    test("deriveTrigger: plain text makes a random trigger") {
        assertEquals(
          ReplayApp.deriveTrigger(
            msg(5, "просто текст"),
            WindowTrigger(replyToBot = false, replyToText = ""),
            llmConfig
          ),
          ("просто текст", Trigger.Random("просто текст"))
        )
    }

    test("a failing run writes output with the error set and the batch continues") {
        Files[IO].tempDirectory.use { dir =>
            val windows = List(
              window(0, WindowTrigger(false, ""), List(msg(1, "раз"))),
              window(1, WindowTrigger(false, ""), List(msg(2, "два")))
            )
            for
                args  <- setup(dir, windows)
                res   <- ReplayApp.replay(args, _ => Resource.pure(failingStub))
                json0 <- readOutput(dir, "window-00__hot.json")
                json1 <- readOutput(dir, "window-01__hot.json")
            yield
                assertEquals(res, Right(()))
                List(json0, json1).foreach { json =>
                    assert(json.hcursor.downField("reply").focus.contains(Json.Null))
                    assert(
                      json.hcursor.get[String]("error").exists(_.contains("boom")),
                      json.hcursor.downField("error").focus
                    )
                    assertEquals(calls(json), Nil)
                }
        }
    }

    test("a scenario whose domain makes an invalid ollama uri fails before any run") {
        Files[IO].tempDirectory.use { dir =>
            val w = window(0, WindowTrigger(false, ""), List(msg(1, "раз")))
            for
                args      <- setup(dir, List(w))
                _         <- write(
                               dir / "scenarios" / "bad.conf",
                               """include classpath("lab-base.conf")
                                   |nyetbot.ollama.domain = "localhost:8080"
                                   |""".stripMargin
                             )
                res       <- ReplayApp.replay(
                               args,
                               _ =>
                                   Resource.eval(
                                     IO.raiseError(new RuntimeException("client must not be built"))
                                   )
                             )
                outExists <- Files[IO].exists(dir / "out")
            yield
                res match
                    case Left(err) =>
                        assert(err.contains("http://localhost:8080:11434"), err)
                        assert(err.contains("localhost:8080"), err)
                        assert(err.contains("OLLAMA_DOMAIN"), err)
                    case Right(v)  => fail(s"expected an error, got $v")
                assertEquals(outExists, false)
        }
    }

    test("a window whose trigger has no userId is skipped") {
        Files[IO].tempDirectory.use { dir =>
            val windows = List(
              window(0, WindowTrigger(false, ""), List(msg(1, "пост канала").copy(userId = None))),
              window(1, WindowTrigger(false, ""), List(msg(2, "обычное")))
            )
            for
                args    <- setup(dir, windows)
                res     <- ReplayApp.replay(args, _ => Resource.pure(stub))
                skipped <- Files[IO].exists(dir / "out" / "window-00__hot.json")
                json    <- readOutput(dir, "window-01__hot.json")
            yield
                assertEquals(res, Right(()))
                assertEquals(skipped, false)
                assertEquals(json.hcursor.get[String]("reply"), Right("stub-response"))
        }
    }
