package nyetbot.lab

import cats.data.EitherT
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.std.Console
import cats.syntax.all.*
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Flags
import fs2.io.file.Path
import io.circe.Codec
import io.circe.Decoder
import io.circe.parser.decode
import io.circe.syntax.*
import nyetbot.lab.ReplayApp.InputInfo
import nyetbot.lab.ReplayApp.ScenarioInfo

import java.time.Instant

object ScoreApp extends IOApp:

    final case class ScoreArgs(out: Path, context: Int, rescore: Boolean)

    final case class ReplaySummary(
        scenario: ScenarioInfo,
        input: InputInfo,
        reply: Option[String],
        error: Option[String]
    ) derives Decoder

    final case class ScoreRecord(
        output: String,
        window: String,
        scenario: String,
        scorer: String,
        scores: Map[String, Int],
        scale: String,
        scoredAt: Instant
    ) derives Codec.AsObject

    final case class ScoreItem(output: String, window: String, scenario: String, screen: String)

    private final case class ResumeKey(output: String, scorer: String) derives Decoder

    private final case class Draft(
        output: String,
        window: String,
        scenario: String,
        reply: String,
        corpus: CorpusWindow
    )

    private enum Decision:
        case Score(value: Int)
        case Skip
        case Quit

    private val usage        = "usage: ScoreApp [--out lab/out] [--context 50] [--rescore]"
    private val prompt       = "[1-5] score   [s]kip   [q]uit"
    private val humanScorer  = "human"
    private val overallScale = "1-5"
    private val ansiClear    = "\u001b[2J\u001b[H"

    override def run(args: List[String]): IO[ExitCode] =
        parseArgs(args) match
            case Left(err)     => Console[IO].errorln(err).as(ExitCode.Error)
            case Right(parsed) =>
                prepare(parsed).flatMap {
                    case Left(err)    => Console[IO].errorln(err).as(ExitCode.Error)
                    case Right(Nil)   => Console[IO].println("nothing to score").as(ExitCode.Success)
                    case Right(items) => score(items, parsed.out)
                }

    private val knownFlags = Set("out", "context", "rescore")

    def parseArgs(args: List[String]): Either[String, ScoreArgs] =
        def toPairs(
            rest: List[String],
            acc: Map[String, String]
        ): Either[String, Map[String, String]] =
            rest match
                case Nil                                          => Right(acc)
                case "--rescore" :: tail                          => toPairs(tail, acc.updated("rescore", "true"))
                case key :: value :: tail if key.startsWith("--") =>
                    toPairs(tail, acc.updated(key.drop(2), value))
                case other :: _                                   => Left(s"unexpected argument '$other'\n$usage")
        for
            pairs   <- toPairs(args, Map.empty)
            _       <- pairs.keySet.diff(knownFlags).toList.sorted match
                           case Nil => Right(())
                           case bad => Left(s"unknown flags: ${bad.map("--" + _).mkString(" ")}\n$usage")
            context <- parsePositiveInt(pairs, "context", 50)
        yield ScoreArgs(
          out = Path(pairs.getOrElse("out", "lab/out")),
          context = context,
          rescore = pairs.contains("rescore")
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

    def prepare(args: ScoreArgs): IO[Either[String, List[ScoreItem]]] =
        (for
            files  <- EitherT(listOutputs(args.out))
            scored <- if args.rescore then EitherT.rightT[IO, String](Set.empty[String])
                      else EitherT(scoredSet(args.out / "scores.jsonl"))
            drafts <- EitherT.right(files.traverseFilter(loadDraft(_, scored)))
        yield toItems(drafts, args.context)).value

    private def listOutputs(out: Path): IO[Either[String, List[Path]]] =
        Files[IO].isDirectory(out).flatMap {
            case false => IO.pure(Left(s"--out $out is not a directory"))
            case true  =>
                Files[IO]
                    .list(out)
                    .filter(_.fileName.toString.endsWith(".json"))
                    .compile
                    .toList
                    .map(_.sortBy(_.fileName.toString))
                    .map { files =>
                        if files.isEmpty then Left(s"no .json outputs found in $out")
                        else Right(files)
                    }
        }

    private def scoredSet(path: Path): IO[Either[String, Set[String]]] =
        Files[IO].exists(path).flatMap {
            case false => IO.pure(Right(Set.empty))
            case true  =>
                Files[IO]
                    .readUtf8(path)
                    .through(fs2.text.lines)
                    .compile
                    .toList
                    .map {
                        _.filter(_.nonEmpty)
                            .traverse(decode[ResumeKey])
                            .bimap(
                              e => s"cannot parse $path: ${e.getMessage}",
                              _.filter(_.scorer == humanScorer).map(_.output).toSet
                            )
                    }
        }

    private def loadDraft(file: Path, scored: Set[String]): IO[Option[Draft]] =
        val name = file.fileName.toString
        if scored.contains(name) then IO.pure(None)
        else
            readJson[ReplaySummary](file).flatMap {
                case Left(err)                                => skip(s"skipping $name: $err")
                case Right(summary) if summary.error.nonEmpty =>
                    skip(s"skipping $name: replay errored, nothing to judge")
                case Right(summary)                           =>
                    val windowFile = Path(summary.input.file)
                    Files[IO].exists(windowFile).flatMap {
                        case false => skip(s"skipping $name: window file $windowFile not found")
                        case true  =>
                            readJson[CorpusWindow](windowFile).flatMap {
                                case Left(err)     => skip(s"skipping $name: $err")
                                case Right(corpus) =>
                                    IO.pure(
                                      Some(
                                        Draft(
                                          output = name,
                                          window =
                                              windowFile.fileName.toString.stripSuffix(".json"),
                                          scenario = summary.scenario.name,
                                          reply = summary.reply.getOrElse(""),
                                          corpus = corpus
                                        )
                                      )
                                    )
                            }
                    }
            }

    private def skip(message: String): IO[Option[Draft]] =
        Console[IO].errorln(message).as(None)

    private def readJson[A: Decoder](file: Path): IO[Either[String, A]] =
        Files[IO].readUtf8(file).compile.string.attempt.map {
            _.leftMap(e => s"cannot read $file: ${e.getMessage}")
                .flatMap(raw =>
                    decode[A](raw).leftMap(e => s"cannot decode $file: ${e.getMessage}")
                )
        }

    private def toItems(drafts: List[Draft], context: Int): List[ScoreItem] =
        drafts.zipWithIndex.map { (draft, idx) =>
            ScoreItem(
              output = draft.output,
              window = draft.window,
              scenario = draft.scenario,
              screen = render(
                output = draft.output,
                position = idx + 1,
                total = drafts.size,
                window = draft.corpus,
                scenario = draft.scenario,
                reply = draft.reply,
                context = context
              )
            )
        }

    def render(
        output: String,
        position: Int,
        total: Int,
        window: CorpusWindow,
        scenario: String,
        reply: String,
        context: Int
    ): String =
        val chat      = window.messages
            .takeRight(context)
            .map(m => s"${m.userName}: ${m.text.replace('\n', ' ')}")
        val separator = "-" * 60
        (List(s"[$position/$total] $output", "") ++ chat ++
            List("", separator, scenario, reply, separator, "", prompt)).mkString("\n")

    def loop(
        items: List[ScoreItem],
        readKey: IO[Char],
        sink: ScoreRecord => IO[Unit],
        show: String => IO[Unit]
    ): IO[Unit] =
        items match
            case Nil          => IO.unit
            case item :: rest =>
                show(item.screen) *> decide(readKey).flatMap {
                    case Decision.Score(value) =>
                        recordFor(item, value).flatMap(sink) *> loop(rest, readKey, sink, show)
                    case Decision.Skip         => loop(rest, readKey, sink, show)
                    case Decision.Quit         => IO.unit
                }

    private def decide(readKey: IO[Char]): IO[Decision] =
        readKey.flatMap {
            case c if c >= '1' && c <= '5' => IO.pure(Decision.Score(c - '0'))
            case 's'                       => IO.pure(Decision.Skip)
            case 'q'                       => IO.pure(Decision.Quit)
            case _                         => decide(readKey)
        }

    private def recordFor(item: ScoreItem, value: Int): IO[ScoreRecord] =
        IO.realTimeInstant.map(now =>
            ScoreRecord(
              output = item.output,
              window = item.window,
              scenario = item.scenario,
              scorer = humanScorer,
              scores = Map("overall" -> value),
              scale = overallScale,
              scoredAt = now
            )
        )

    def appendRecord(path: Path)(record: ScoreRecord): IO[Unit] =
        Stream
            .emit(record.asJson.noSpaces + "\n")
            .through(fs2.text.utf8.encode)
            .through(Files[IO].writeAll(path, Flags.Append))
            .compile
            .drain

    private def score(items: List[ScoreItem], out: Path): IO[ExitCode] =
        Tty.session.attempt.use {
            case Left(e)        =>
                Console[IO]
                    .errorln(
                      s"cannot set up raw input on /dev/tty (${e.getMessage}); " +
                          "run ScoreApp from an interactive terminal"
                    )
                    .as(ExitCode.Error)
            case Right(readKey) =>
                loop(items, readKey, appendRecord(out / "scores.jsonl"), showScreen)
                    .as(ExitCode.Success)
        }

    private def showScreen(screen: String): IO[Unit] =
        Console[IO].print(ansiClear) *> Console[IO].println(screen)
