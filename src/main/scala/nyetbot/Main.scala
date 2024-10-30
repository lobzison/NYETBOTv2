package nyetbot

import canoe.api.*
import cats.effect.IO
import cats.effect.IO.asyncForIO
import cats.effect.IOApp
import cats.effect.kernel.*
import cats.effect.std.Random
import fly4s.*
import nyetbot.functionality.*
import nyetbot.repo.*
import nyetbot.service.*
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.typelevel.otel4s.trace.Tracer
import skunk.Session

import scala.util.control.NonFatal

import concurrent.duration.DurationInt

object Main extends IOApp.Simple:
    def run =
        dependencies.use { case (given TelegramClient[IO], config, fly4s, db, client) =>
            for
                scenarios <- buildScenarios(config, fly4s, db, client)
                _         <- app(scenarios)
            yield ()
        }

    val dependencies
        : Resource[IO, (TelegramClient[IO], Config, Fly4s[IO], Session[IO], Client[IO])] =
        implicit val noopTracer: Tracer[IO] = Tracer.noop
        for
            config <- Config.configResource[IO]
            tg     <- TelegramClient.global[IO](config.botToken)
            fly4s  <- fly4sRes[IO](config.dbConfig)
            db     <- buildSessionResource[IO](config.dbConfig)
            client <- BlazeClientBuilder[IO]
                          .withRequestTimeout(25.minute)
                          .withIdleTimeout(25.minute)
                          .resource
        yield (tg, config, fly4s, db, client)

    def buildScenarios(config: Config, fly4s: Fly4s[IO], db: Session[IO], client: Client[IO])(using
        TelegramClient[IO]
    ): IO[List[Scenario[IO, Unit]]] =
        for
            _                 <- IO.println("Starting NYETBOTv2")
            given Random[IO]  <- Random.scalaUtilRandom[IO]
            _                 <- fly4s.migrate
            memeRepo           = MemeRepoDB(db)
            swearRepo          = SwearRepoImpl(db)
            swearService      <- SwearServiceCached(swearRepo)
            swear              = SwearFunctionalityImpl(swearService)
            service           <- MemeServiceCached(memeRepo)
            meme               = MemeFunctionalityImpl(service)
            // translation
            translationService = DeeplTranslationService(client, config.translateConfig)
            // Ollama
            ollamaService      = OllamaService(client, config.ollamaConfig, config.llmConfig)
            llm               <- LlmFunctionalityImpl.mk(ollamaService, translationService, config.llmConfig)
            _                 <- IO.println("Ready")
        yield List(
          meme.triggerMemeScenario
        ) ++ meme.memeManagementScenarios ++ swear.scenarios :+ llm.reply

    def app(scenarios: List[Scenario[IO, Unit]])(using TelegramClient[IO]): IO[Unit] =
        val prog = Bot.polling[IO].follow(scenarios*).compile.drain
        prog.recoverWith { case NonFatal(e) =>
            IO.println(s"Died with $e, restarting") >> IO.delay(e.printStackTrace()) >>
                IO.sleep(1.minute) >> app(scenarios)
        }
