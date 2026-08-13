package nyetbot.lab

import cats.data.EitherT
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Resource
import cats.effect.std.Console
import cats.effect.std.Random
import cats.syntax.all.*
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import io.circe.Encoder
import io.circe.parser.decode
import io.circe.syntax.*
import nyetbot.client.OllamaClient
import nyetbot.config.LlmFunctionalityConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.ChatMemory
import nyetbot.service.llm.LlmService.Trigger
import nyetbot.service.llm.ReplyInputs
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder

import concurrent.duration.DurationInt

object ReplayApp extends IOApp:

    final case class ReplayArgs(inputs: List[Path], scenarios: List[Path], out: Path)

    final case class LoadedScenario(
        name: String,
        file: Path,
        source: String,
        config: LabConfig,
        uri: Uri
    )
    final case class LoadedWindow(name: String, file: Path, window: CorpusWindow)

    final case class ScenarioInfo(name: String, file: String, source: String)
        derives Encoder.AsObject
    final case class InputInfo(file: String, meta: WindowMeta, trigger: WindowTrigger)
        derives Encoder.AsObject
    final case class ReplayOutput(
        scenario: ScenarioInfo,
        input: InputInfo,
        calls: List[LlmCall],
        reply: Option[String],
        error: Option[String],
        totalMillis: Long
    ) derives Encoder.AsObject

    private val usage = "usage: ReplayApp --inputs <dir|files> --scenarios <dir|files> --out <dir>"

    override def run(args: List[String]): IO[ExitCode] =
        parseArgs(args) match
            case Left(err)     => Console[IO].errorln(err).as(ExitCode.Error)
            case Right(parsed) =>
                replay(parsed, ollamaClient).flatMap {
                    case Left(err) => Console[IO].errorln(err).as(ExitCode.Error)
                    case Right(()) => IO.pure(ExitCode.Success)
                }

    private val knownFlags = Set("inputs", "scenarios", "out")

    def parseArgs(args: List[String]): Either[String, ReplayArgs] =
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
            pairs     <- toPairs(args, Map.empty)
            _         <- pairs.keySet.diff(knownFlags).toList.sorted match
                             case Nil => Right(())
                             case bad => Left(s"unknown flags: ${bad.map("--" + _).mkString(" ")}\n$usage")
            inputs    <- pairs.get("inputs").toRight(s"missing --inputs\n$usage")
            scenarios <- pairs.get("scenarios").toRight(s"missing --scenarios\n$usage")
            out       <- pairs.get("out").toRight(s"missing --out\n$usage")
        yield ReplayArgs(
          inputs = inputs.split(',').toList.map(Path(_)),
          scenarios = scenarios.split(',').toList.map(Path(_)),
          out = Path(out)
        )

    def ollamaClient(scenario: LoadedScenario): Resource[IO, OllamaClient] =
        BlazeClientBuilder[IO]
            .withRequestTimeout(scenario.config.ollama.requestTimeoutMinutes.minutes)
            .withIdleTimeout(scenario.config.ollama.idleTimeoutMinutes.minutes)
            .resource
            .map(OllamaClient(_, scenario.uri))

    def replay(
        args: ReplayArgs,
        mkClient: LoadedScenario => Resource[IO, OllamaClient]
    ): IO[Either[String, Unit]] =
        val prepared = for
            inputFiles    <- EitherT(expand(args.inputs, ".json", "inputs"))
            scenarioFiles <- EitherT(expand(args.scenarios, ".conf", "scenarios"))
            windows       <- inputFiles.traverse(f => EitherT(loadWindow(f)))
            scenarios     <- scenarioFiles.traverse(f => EitherT(loadScenario(f)))
        yield (windows, scenarios)
        prepared
            .semiflatMap((windows, scenarios) => runAll(windows, scenarios, args.out, mkClient))
            .value

    private def expand(
        paths: List[Path],
        ext: String,
        flag: String
    ): IO[Either[String, List[Path]]] =
        paths
            .flatTraverse { p =>
                Files[IO].isDirectory(p).flatMap {
                    case true  =>
                        Files[IO]
                            .list(p)
                            .filter(_.fileName.toString.endsWith(ext))
                            .compile
                            .toList
                            .map(_.sortBy(_.fileName.toString))
                    case false => IO.pure(List(p))
                }
            }
            .map { files =>
                if files.isEmpty then Left(s"no $ext files found for --$flag") else Right(files)
            }

    private def baseName(file: Path, ext: String): String =
        file.fileName.toString.stripSuffix(ext)

    private def loadWindow(file: Path): IO[Either[String, LoadedWindow]] =
        Files[IO].readUtf8(file).compile.string.attempt.map {
            _.leftMap(e => s"cannot read $file: ${e.getMessage}")
                .flatMap(raw =>
                    decode[CorpusWindow](raw).leftMap(e => s"cannot decode $file: ${e.getMessage}")
                )
                .map(w => LoadedWindow(baseName(file, ".json"), file, w))
        }

    private def loadScenario(file: Path): IO[Either[String, LoadedScenario]] =
        Files[IO].readUtf8(file).compile.string.attempt.flatMap {
            case Left(e)       => IO.pure(Left(s"cannot read $file: ${e.getMessage}"))
            case Right(source) =>
                LabConfig
                    .load(file)
                    .map(_.flatMap { config =>
                        Uri.fromString(config.ollama.uri)
                            .bimap(
                              _ =>
                                  s"scenario $file: invalid ollama uri '${config.ollama.uri}' — " +
                                      s"domain '${config.ollama.domain}' must be a bare domain " +
                                      "(check OLLAMA_DOMAIN)",
                              uri =>
                                  LoadedScenario(baseName(file, ".conf"), file, source, config, uri)
                            )
                    })
        }

    private def runAll(
        windows: List[LoadedWindow],
        scenarios: List[LoadedScenario],
        out: Path,
        mkClient: LoadedScenario => Resource[IO, OllamaClient]
    ): IO[Unit] =
        for
            given Random[IO] <- Random.scalaUtilRandom[IO]
            _                <- Files[IO].createDirectories(out)
            _                <- windows.zipWithIndex.traverse_ { (w, wi) =>
                                    triggerOf(w.window) match
                                        case None                 =>
                                            Console[IO].errorln(
                                              s"skipping ${w.name}: trigger has no userId"
                                            )
                                        case Some((last, userId)) =>
                                            scenarios.zipWithIndex.traverse_ { (s, si) =>
                                                for
                                                    output <- mkClient(s).use(
                                                                runOne(w, last, userId, s, _)
                                                              )
                                                    _      <- writeOutput(out, w, s, output)
                                                    _      <-
                                                        IO.println(
                                                          s"window ${wi + 1}/${windows.size} × " +
                                                              s"scenario ${si + 1}/${scenarios.size} " +
                                                              s"… done in ${output.totalMillis / 1000}s"
                                                        )
                                                yield ()
                                            }
                                }
        yield ()

    private def triggerOf(window: CorpusWindow): Option[(CorpusMessage, UserId)] =
        window.messages.lastOption.flatMap(m => m.userId.map(id => (m, id)))

    def toContext(m: CorpusMessage, botName: String): LlmContextMessage =
        if m.isBot then LlmContextMessage(None, botName, m.text)
        else LlmContextMessage(m.userId, m.userName, m.text)

    def deriveTrigger(
        last: CorpusMessage,
        windowTrigger: WindowTrigger,
        config: LlmFunctionalityConfig
    ): (String, Trigger) =
        val trigger =
            if windowTrigger.replyToBot then Trigger.Reply(last.text, windowTrigger.replyToText)
            else if last.text.contains(config.botAlias) then
                Trigger.Tagged(last.text, windowTrigger.replyToText)
            else Trigger.Random(last.text)
        (last.text.replace(config.botAlias, config.botName), trigger)

    def runOne(
        w: LoadedWindow,
        last: CorpusMessage,
        userId: UserId,
        scenario: LoadedScenario,
        client: OllamaClient
    )(using Random[IO]): IO[ReplayOutput] =
        val llmConfig              = scenario.config.llm
        val (triggerText, trigger) = deriveTrigger(last, w.window.trigger, llmConfig)
        for
            wiring            <- ReplayWiring(client, scenario.config)
            memory            <- ChatMemory(llmConfig)
            _                 <- w.window.messages.traverse_(m => memory.ingest(toContext(m, llmConfig.botName)))
            timed             <- (for
                                     recentChat <- memory.replyContext
                                     recentUser <- memory.recentUser(userId)
                                     gen        <- wiring.llmService.generateReply(
                                                     ReplyInputs(
                                                       target = UserRef(userId, DisplayName(last.userName)),
                                                       triggerText = triggerText,
                                                       trigger = trigger,
                                                       recentChat = recentChat,
                                                       recentUserMsgs = recentUser
                                                     )
                                                   )
                                 yield gen.text).attempt.timed
            (elapsed, outcome) = timed
            calls             <- wiring.recorder.recorded
        yield ReplayOutput(
          scenario = ScenarioInfo(scenario.name, scenario.file.toString, scenario.source),
          input = InputInfo(w.file.toString, w.window.meta, w.window.trigger),
          calls = calls,
          reply = outcome.toOption,
          error = outcome.left.toOption.map(_.toString),
          totalMillis = elapsed.toMillis
        )

    private def writeOutput(
        out: Path,
        w: LoadedWindow,
        s: LoadedScenario,
        output: ReplayOutput
    ): IO[Unit] =
        Stream
            .emit(output.asJson.spaces2)
            .through(fs2.text.utf8.encode)
            .through(Files[IO].writeAll(out / s"${w.name}__${s.name}.json"))
            .compile
            .drain
