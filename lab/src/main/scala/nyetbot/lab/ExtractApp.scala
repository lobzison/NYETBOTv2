package nyetbot.lab

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.std.Console
import cats.syntax.all.*
import fs2.Stream
import fs2.data.json.tokens
import fs2.data.text.utf8.given
import fs2.io.file.Files
import fs2.io.file.Path
import io.circe.Json
import io.circe.syntax.*
import nyetbot.lab.WindowSampler.WindowRange

import java.time.LocalDateTime

object ExtractApp extends IOApp:

    final case class ExtractConfig(
        dump: Path,
        out: Path,
        chatId: ChatId,
        windowSize: Int,
        windows: Int,
        seed: Long,
        botId: BotId
    )

    final case class Pass1(eligibleDates: Vector[LocalDateTime], botMessageIds: Set[MessageId])

    private val usage =
        "usage: ExtractApp --dump <path> --out <dir> [--chat-id 1581584348] [--window-size 400] [--windows 10] [--seed 42] [--bot-id user467782420]"

    override def run(args: List[String]): IO[ExitCode] =
        parseArgs(args) match
            case Left(err)  => Console[IO].errorln(err).as(ExitCode.Error)
            case Right(cfg) =>
                extract(cfg).flatMap {
                    case Left(err) => Console[IO].errorln(err).as(ExitCode.Error)
                    case Right(n)  =>
                        Console[IO].println(s"wrote $n windows to ${cfg.out}").as(ExitCode.Success)
                }

    private val knownFlags =
        Set("dump", "out", "chat-id", "window-size", "windows", "seed", "bot-id")

    def parseArgs(args: List[String]): Either[String, ExtractConfig] =
        def toPairs(
            rest: List[String],
            acc: Map[String, String]
        ): Either[String, Map[String, String]] =
            rest match
                case Nil                                          => Right(acc)
                case key :: value :: tail if key.startsWith("--") =>
                    toPairs(tail, acc.updated(key.drop(2), value))
                case other :: _                                   => Left(s"unexpected argument '$other'\n$usage")
        for
            pairs      <- toPairs(args, Map.empty)
            _          <- pairs.keySet.diff(knownFlags).toList.sorted match
                              case Nil => Right(())
                              case bad => Left(s"unknown flags: ${bad.map("--" + _).mkString(" ")}\n$usage")
            dump       <- pairs.get("dump").toRight(s"missing --dump\n$usage")
            out        <- pairs.get("out").toRight(s"missing --out\n$usage")
            chatId     <- parseLong(pairs, "chat-id", 1581584348L)
            windowSize <- parsePositiveInt(pairs, "window-size", 400)
            windows    <- parsePositiveInt(pairs, "windows", 10)
            seed       <- parseLong(pairs, "seed", 42L)
        yield ExtractConfig(
          dump = Path(dump),
          out = Path(out),
          chatId = ChatId(chatId),
          windowSize = windowSize,
          windows = windows,
          seed = seed,
          botId = BotId(pairs.getOrElse("bot-id", "user467782420"))
        )

    private def parseLong(
        pairs: Map[String, String],
        key: String,
        default: Long
    ): Either[String, Long] =
        pairs
            .get(key)
            .fold[Either[String, Long]](Right(default))(
              _.toLongOption.toRight(s"--$key must be a number\n$usage")
            )

    private def parsePositiveInt(
        pairs: Map[String, String],
        key: String,
        default: Int
    ): Either[String, Int] =
        pairs
            .get(key)
            .fold[Either[String, Int]](Right(default))(
              _.toIntOption.filter(_ > 0).toRight(s"--$key must be a positive number\n$usage")
            )

    private def extract(cfg: ExtractConfig): IO[Either[String, Int]] =
        pass1(messageStream(cfg), cfg.botId).flatMap { p1 =>
            sampleRanges(cfg, p1.eligibleDates) match
                case Left(err)     => IO.pure(Left(err))
                case Right(ranges) =>
                    for
                        _       <- Files[IO].createDirectories(cfg.out)
                        windows <- pass2(messageStream(cfg), cfg.botId, ranges)
                        _       <- windows.zipWithIndex.traverse_ { (messages, idx) =>
                                       writeWindow(
                                         cfg.out,
                                         buildWindow(cfg, idx, messages, p1.botMessageIds)
                                       )
                                   }
                    yield Right(windows.size)
        }

    private def messageStream(cfg: ExtractConfig): Stream[IO, Json] =
        Files[IO]
            .readAll(cfg.dump)
            .through(tokens[IO, Byte])
            .through(TelegramExportPipe.messages[IO](cfg.chatId))

    def pass1(messages: Stream[IO, Json], botId: BotId): IO[Pass1] =
        messages
            .fold(Pass1(Vector.empty, Set.empty)) { (acc, m) =>
                val botIds =
                    if Preprocess.fromId(m).contains(botId.value) then
                        m.hcursor
                            .get[Long]("id")
                            .toOption
                            .fold(acc.botMessageIds)(id => acc.botMessageIds + MessageId(id))
                    else acc.botMessageIds
                val dates  =
                    Preprocess
                        .message(m, botId)
                        .fold(acc.eligibleDates)(cm =>
                            acc.eligibleDates :+ LocalDateTime.parse(cm.date)
                        )
                Pass1(dates, botIds)
            }
            .compile
            .lastOrError

    def sampleRanges(
        cfg: ExtractConfig,
        dates: Vector[LocalDateTime]
    ): Either[String, List[WindowRange]] =
        val firstIdx =
            dates.lastOption.fold(0)(newest => dates.indexWhere(!_.isBefore(newest.minusYears(1))))
        WindowSampler
            .sample(dates.length - firstIdx, cfg.windowSize, cfg.windows, cfg.seed)
            .bimap(
              fit =>
                  s"cannot fit ${cfg.windows} windows of ${cfg.windowSize} messages: " +
                      s"need ${fit.required} eligible messages in the last year, found ${fit.eligible}",
              _.map(r => r.copy(start = r.start + firstIdx))
            )

    def pass2(
        messages: Stream[IO, Json],
        botId: BotId,
        ranges: List[WindowRange]
    ): IO[Vector[Vector[CorpusMessage]]] =
        messages
            .fold((0, ranges.map(_ => Vector.empty[CorpusMessage]).toVector)) {
                case ((idx, acc), m) =>
                    Preprocess.message(m, botId) match
                        case None     => (idx, acc)
                        case Some(cm) =>
                            val next = ranges.indexWhere(_.contains(idx)) match
                                case -1 => acc
                                case i  => acc.updated(i, acc(i) :+ cm)
                            (idx + 1, next)
            }
            .compile
            .lastOrError
            .map(_._2)

    def buildWindow(
        cfg: ExtractConfig,
        idx: Int,
        messages: Vector[CorpusMessage],
        botMessageIds: Set[MessageId]
    ): CorpusWindow =
        val last = messages.last
        CorpusWindow(
          meta = WindowMeta(
            sourceDump = cfg.dump.toString,
            chatId = cfg.chatId,
            seed = cfg.seed,
            windowIndex = idx,
            startDate = messages.head.date,
            endDate = last.date,
            messageCount = messages.size
          ),
          trigger = WindowTrigger(
            replyToBot = last.replyToMessageId.exists(botMessageIds.contains),
            replyToText = last.replyToMessageId
                .flatMap(rid => messages.find(_.id == rid))
                .map(_.text)
                .getOrElse("")
          ),
          messages = messages.toList
        )

    private def writeWindow(out: Path, window: CorpusWindow): IO[Unit] =
        val name = f"window-${window.meta.windowIndex}%02d-end${window.messages.last.id}.json"
        Stream
            .emit(window.asJson.deepDropNullValues.spaces2)
            .through(fs2.text.utf8.encode)
            .through(Files[IO].writeAll(out / name))
            .compile
            .drain
