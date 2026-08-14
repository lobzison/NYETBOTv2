package nyetbot.lab

import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all.*
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import io.circe.parser.decode
import io.circe.syntax.*
import munit.CatsEffectSuite
import nyetbot.model.ProfileModels.UserId

import java.time.Instant

class ScoreAppSpec extends CatsEffectSuite:

    private def msg(id: Long, name: String, text: String, isBot: Boolean = false): CorpusMessage =
        CorpusMessage(
          id = MessageId(id),
          date = "2026-05-01T10:00:00",
          userId = Some(UserId(id)),
          userName = name,
          isBot = isBot,
          text = text,
          replyToMessageId = None
        )

    private def window(messages: List[CorpusMessage]): CorpusWindow =
        CorpusWindow(
          meta = WindowMeta(
            sourceDump = "dump.json",
            chatId = ChatId(1581584348L),
            seed = 42L,
            windowIndex = 0,
            startDate = "2026-05-01T10:00:00",
            endDate = "2026-05-01T10:00:00",
            messageCount = messages.size
          ),
          trigger = WindowTrigger(replyToBot = false, replyToText = ""),
          messages = messages
        )

    private val chat = window(
      List(
        msg(1, "Оля", "раз"),
        msg(2, "Боря", "два"),
        msg(3, "NYETBOT", "бот-реплика", isBot = true),
        msg(4, "Вера", "триггер")
      )
    )

    private val promptLine = "[1-5] score   [s]kip   [q]uit"

    test("render keeps only the last context messages with the trigger last") {
        val screen = ScoreApp.render("o.json", 1, 2, chat, "baseline", "ответ", 2)
        assert(!screen.contains("Оля: раз"), screen)
        assert(!screen.contains("Боря: два"), screen)
        val lines  = screen.linesIterator.toList
        assertEquals(
          lines.indexOf("Вера: триггер"),
          lines.indexOf("NYETBOT: бот-реплика") + 1
        )
        assertEquals(lines(lines.indexOf("Вера: триггер") + 1), "")
    }

    test("render shows the header and one userName: text line per message") {
        val screen =
            ScoreApp.render("window-00__baseline.json", 2, 5, chat, "baseline", "ответ", 50)
        assert(screen.startsWith("[2/5] window-00__baseline.json"), screen)
        assert(screen.contains("Оля: раз"), screen)
        assert(screen.contains("Боря: два"), screen)
        assert(screen.contains("NYETBOT: бот-реплика"), screen)
        assert(screen.contains("Вера: триггер"), screen)
    }

    test("render flattens multi-line messages to one line") {
        val w      = window(List(msg(1, "Оля", "с\nпереносом")))
        val screen = ScoreApp.render("o.json", 1, 1, w, "baseline", "ответ", 50)
        assert(screen.contains("Оля: с переносом"), screen)
    }

    test("render separates the reply block with the scenario name and the reply") {
        val screen = ScoreApp.render("o.json", 1, 1, chat, "hot-temp", "сгенерированный ответ", 50)
        val lines  = screen.linesIterator.toList
        assertEquals(lines.count(_ == "-" * 60), 2)
        assert(screen.contains("-" * 60 + "\nhot-temp\nсгенерированный ответ\n" + "-" * 60), screen)
        assertEquals(lines.last, promptLine)
    }

    private def item(n: Int): ScoreApp.ScoreItem =
        ScoreApp.ScoreItem(s"out$n.json", s"window-0$n", "baseline", s"screen$n")

    private def runLoop(
        items: List[ScoreApp.ScoreItem],
        keys: String
    ): IO[(List[ScoreApp.ScoreRecord], List[String])] =
        for
            keysRef <- Ref.of[IO, List[Char]](keys.toList)
            records <- Ref.of[IO, List[ScoreApp.ScoreRecord]](Nil)
            shown   <- Ref.of[IO, List[String]](Nil)
            readKey  = keysRef.modify {
                           case c :: rest => (rest, c)
                           case Nil       => (Nil, 'q')
                       }
            _       <- ScoreApp.loop(
                         items,
                         readKey,
                         r => records.update(_ :+ r),
                         s => shown.update(_ :+ s)
                       )
            r       <- records.get
            s       <- shown.get
        yield (r, s)

    test("a score keypress writes a correct record and advances") {
        runLoop(List(item(1), item(2)), "42").map { (records, shown) =>
            assertEquals(shown, List("screen1", "screen2"))
            assertEquals(records.map(_.output), List("out1.json", "out2.json"))
            assertEquals(records.map(_.scores), List(Map("overall" -> 4), Map("overall" -> 2)))
            records.foreach { r =>
                assertEquals(r.scorer, "human")
                assertEquals(r.scale, "1-5")
            }
            assertEquals(records.head.window, "window-01")
            assertEquals(records.head.scenario, "baseline")
        }
    }

    test("skip advances without a record") {
        runLoop(List(item(1), item(2)), "s3").map { (records, shown) =>
            assertEquals(shown, List("screen1", "screen2"))
            assertEquals(records.map(_.output), List("out2.json"))
        }
    }

    test("quit stops the loop") {
        runLoop(List(item(1), item(2)), "q").map { (records, shown) =>
            assertEquals(shown, List("screen1"))
            assertEquals(records, Nil)
        }
    }

    test("invalid keys are ignored") {
        runLoop(List(item(1)), "x905").map { (records, shown) =>
            assertEquals(shown, List("screen1"))
            assertEquals(records.map(_.scores), List(Map("overall" -> 5)))
        }
    }

    private def writeFile(path: Path, content: String): IO[Unit] =
        Stream
            .emit(content)
            .through(fs2.text.utf8.encode)
            .through(Files[IO].writeAll(path))
            .compile
            .drain

    private def outputJson(
        windowFile: Path,
        scenario: String,
        reply: Option[String],
        error: Option[String]
    ): String =
        ReplayApp
            .ReplayOutput(
              scenario = ReplayApp.ScenarioInfo(scenario, s"$scenario.conf", "src"),
              input = ReplayApp.InputInfo(windowFile.toString, chat.meta, chat.trigger),
              calls = Nil,
              reply = reply,
              error = error,
              totalMillis = 1L
            )
            .asJson
            .spaces2

    private def setupOut(dir: Path): IO[Path] =
        val corpus     = dir / "corpus"
        val out        = dir / "out"
        val windowFile = corpus / "window-00.json"
        for
            _ <- Files[IO].createDirectories(corpus)
            _ <- Files[IO].createDirectories(out)
            _ <- writeFile(windowFile, chat.asJson.spaces2)
            _ <- writeFile(
                   out / "window-00__good.json",
                   outputJson(windowFile, "good", Some("ответ"), None)
                 )
            _ <- writeFile(
                   out / "window-00__errored.json",
                   outputJson(windowFile, "errored", None, Some("boom"))
                 )
            _ <- writeFile(
                   out / "window-00__gone.json",
                   outputJson(corpus / "missing.json", "gone", Some("ответ"), None)
                 )
        yield out

    test("prepare offers successes and skips errored outputs and missing windows") {
        Files[IO].tempDirectory.use { dir =>
            for
                out   <- setupOut(dir)
                items <- ScoreApp.prepare(ScoreApp.ScoreArgs(out, 50, rescore = false))
            yield
                val offered = items.map(_.map(i => (i.output, i.window, i.scenario)))
                assertEquals(offered, Right(List(("window-00__good.json", "window-00", "good"))))
                val screen  = items.toOption.get.head.screen
                assert(screen.startsWith("[1/1] window-00__good.json"), screen)
                assert(screen.contains("Вера: триггер"), screen)
                assert(screen.contains("good\nответ"), screen)
        }
    }

    test("resume skips scored outputs and --rescore re-offers them") {
        Files[IO].tempDirectory.use { dir =>
            val args = ScoreApp.ScoreArgs(dir / "out", 50, rescore = false)
            for
                out    <- setupOut(dir)
                first  <- ScoreApp.prepare(args)
                _      <- ScoreApp.loop(
                            first.toOption.get,
                            IO.pure('3'),
                            ScoreApp.appendRecord(out / "scores.jsonl"),
                            _ => IO.unit
                          )
                second <- ScoreApp.prepare(args)
                third  <- ScoreApp.prepare(args.copy(rescore = true))
            yield
                assertEquals(first.map(_.map(_.output)), Right(List("window-00__good.json")))
                assertEquals(second.map(_.map(_.output)), Right(Nil))
                assertEquals(third.map(_.map(_.output)), Right(List("window-00__good.json")))
        }
    }

    private val record = ScoreApp.ScoreRecord(
      output = "window-00-end312792__baseline.json",
      window = "window-00-end312792",
      scenario = "baseline",
      scorer = "human",
      scores = Map("overall" -> 4),
      scale = "1-5",
      scoredAt = Instant.parse("2026-08-14T10:00:00Z")
    )

    test("a score record encodes and decodes") {
        assertEquals(decode[ScoreApp.ScoreRecord](record.asJson.noSpaces), Right(record))
        assertEquals(
          record.asJson.hcursor.get[String]("scoredAt"),
          Right("2026-08-14T10:00:00Z")
        )
    }

    test("appending preserves prior lines") {
        Files[IO].tempDirectory.use { dir =>
            val path   = dir / "scores.jsonl"
            val second = record.copy(output = "other.json", scores = Map("overall" -> 1))
            for
                _     <- ScoreApp.appendRecord(path)(record)
                _     <- ScoreApp.appendRecord(path)(second)
                lines <- Files[IO].readUtf8(path).through(fs2.text.lines).compile.toList
            yield assertEquals(
              lines.filter(_.nonEmpty).traverse(decode[ScoreApp.ScoreRecord]),
              Right(List(record, second))
            )
        }
    }

    test("unknown flags are rejected") {
        ScoreApp.parseArgs(List("--out", "o", "--contxt", "5")) match
            case Left(err)     => assert(err.startsWith("unknown flags: --contxt"), err)
            case Right(parsed) => fail(s"expected an error, got $parsed")
    }

    test("defaults apply without flags") {
        assertEquals(
          ScoreApp.parseArgs(Nil),
          Right(ScoreApp.ScoreArgs(Path("lab/out"), 50, rescore = false))
        )
    }

    test("flags parse with --rescore taking no value") {
        assertEquals(
          ScoreApp.parseArgs(List("--rescore", "--out", "x", "--context", "10")),
          Right(ScoreApp.ScoreArgs(Path("x"), 10, rescore = true))
        )
    }

    test("last duplicate flag wins") {
        assertEquals(
          ScoreApp.parseArgs(List("--context", "10", "--context", "20")).map(_.context),
          Right(20)
        )
    }

    test("a non-positive context is rejected") {
        ScoreApp.parseArgs(List("--context", "0")) match
            case Left(err)     => assert(err.startsWith("--context must be a positive number"), err)
            case Right(parsed) => fail(s"expected an error, got $parsed")
    }
