package nyetbot.lab

import fs2.io.file.Path
import munit.FunSuite
import nyetbot.lab.WindowSampler.WindowRange
import nyetbot.model.ProfileModels.UserId

import java.time.LocalDateTime

class ExtractAppSpec extends FunSuite:

    private val cfg = ExtractApp.ExtractConfig(
      dump = Path("dump.json"),
      out = Path("out"),
      chatId = ChatId(1581584348L),
      windowSize = 3,
      windows = 2,
      seed = 42L,
      botId = BotId("user467782420")
    )

    private val newest = LocalDateTime.parse("2026-06-01T12:00:00")
    private val dates  =
        Vector.tabulate(10)(i => newest.minusYears(2).plusDays(i.toLong)) ++
            Vector.tabulate(6)(i => newest.minusDays((5 - i).toLong))

    private def msg(
        id: Long,
        text: String,
        replyTo: Option[Long] = None,
        isBot: Boolean = false
    ): CorpusMessage =
        CorpusMessage(
          id = MessageId(id),
          date = s"2026-05-0${id}T10:00:00",
          userId = Some(UserId(id)),
          userName = s"user$id",
          isBot = isBot,
          text = text,
          replyToMessageId = replyTo.map(MessageId(_))
        )

    test("last-year cutoff excludes old messages from the sampling range") {
        assertEquals(
          ExtractApp.sampleRanges(cfg, dates),
          Right(List(WindowRange(10, 3), WindowRange(13, 3)))
        )
    }

    test("old messages do not count as eligible in the fit error") {
        assertEquals(
          ExtractApp.sampleRanges(cfg.copy(windowSize = 4), dates),
          Left(
            "cannot fit 2 windows of 4 messages: need 8 eligible messages in the last year, found 6"
          )
        )
    }

    test("trigger replying to a bot message inside the window") {
        val messages = Vector(
          msg(1L, "a"),
          msg(2L, "bot text", isBot = true),
          msg(3L, "c"),
          msg(4L, "reply", replyTo = Some(2L))
        )
        val window   = ExtractApp.buildWindow(cfg, 0, messages, Set(MessageId(2L)))
        assertEquals(window.trigger, WindowTrigger(replyToBot = true, replyToText = "bot text"))
    }

    test("trigger replying to a bot message outside the window") {
        val messages =
            Vector(msg(1L, "a"), msg(2L, "b"), msg(3L, "c"), msg(4L, "reply", replyTo = Some(999L)))
        val window   = ExtractApp.buildWindow(cfg, 0, messages, Set(MessageId(999L)))
        assertEquals(window.trigger, WindowTrigger(replyToBot = true, replyToText = ""))
    }

    test("trigger replying to a non-bot message inside the window") {
        val messages =
            Vector(msg(1L, "a"), msg(2L, "b"), msg(3L, "c"), msg(4L, "reply", replyTo = Some(3L)))
        val window   = ExtractApp.buildWindow(cfg, 0, messages, Set(MessageId(2L)))
        assertEquals(window.trigger, WindowTrigger(replyToBot = false, replyToText = "c"))
    }

    test("meta covers the window") {
        val messages = Vector(msg(1L, "a"), msg(2L, "b"), msg(3L, "c"), msg(4L, "d"))
        val window   = ExtractApp.buildWindow(cfg, 3, messages, Set.empty)
        assertEquals(
          window.meta,
          WindowMeta(
            sourceDump = "dump.json",
            chatId = ChatId(1581584348L),
            seed = 42L,
            windowIndex = 3,
            startDate = "2026-05-01T10:00:00",
            endDate = "2026-05-04T10:00:00",
            messageCount = 4
          )
        )
    }

    test("unknown flags are rejected") {
        ExtractApp.parseArgs(List("--dump", "d.json", "--out", "o", "--window", "5")) match
            case Left(err)     => assert(err.startsWith("unknown flags: --window"), err)
            case Right(parsed) => fail(s"expected an error, got $parsed")
    }

    test("last duplicate flag wins") {
        assertEquals(
          ExtractApp
              .parseArgs(List("--dump", "a.json", "--dump", "b.json", "--out", "o"))
              .map(_.dump),
          Right(Path("b.json"))
        )
    }
