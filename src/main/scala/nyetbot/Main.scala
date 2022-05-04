package nyetbot

import cats.effect.{IOApp, IO}
import fs2.Stream
import canoe.api.*
import canoe.syntax.*
import cats.effect.kernel.Async
import cats.effect.std.Random
import cats.Monad
import cats.effect.kernel.Sync
import nyetbot.functionality.*
import cats.effect.kernel.Resource
import nyetbot.model.*
import cats.effect.kernel.Ref
import nyetbot.service.MemeServiceCached
import fly4s.core.*
import nyetbot.vault.*
import skunk.Session
import nyetbot.service.SwearServiceImpl

object Main extends IOApp.Simple:
    def run =
        dependencies.use { case (given TelegramClient[IO], config, fly4s, db) =>
            for
                scenarios <- buildScenarios(config, fly4s, db)
                _         <- app(scenarios)
            yield ()
        }

    val dependencies: Resource[IO, (TelegramClient[IO], Config, Fly4s[IO], Session[IO])] =
        for
            config <- Config.configResource[IO]
            tg     <- TelegramClient.global[IO](config.botToken)
            fly4s  <- fly4sRes[IO](config)
            db     <- buildSessionResource[IO](config)
        yield (tg, config, fly4s, db)

    def buildScenarios(config: Config, fly4s: Fly4s[IO], db: Session[IO])(using
        TelegramClient[IO]
    ): IO[List[Scenario[IO, Unit]]] =
        for
            _                <- IO.println("Starting NYETBOTv2")
            given Random[IO] <- Random.scalaUtilRandom[IO]
            _                <- fly4s.migrate
            dbVault           = MemeVaultDB[IO](db)
            swearVault        = SwearVaultImpl[IO](db)
            swearService     <- SwearServiceImpl[IO](swearVault)
            swear             = SwearFunctionalityImpl[IO](swearService)
            service          <- MemeServiceCached[IO](dbVault)
            meme              = MemeFunctionalityImpl[IO](service)
            _                <- IO.println("Ready")
        yield List(meme.triggerMemeScenario, swear.scenario) ++ meme.memeManagementScenarios

    def app(scenarios: List[Scenario[IO, Unit]])(using TelegramClient[IO]): IO[Unit] =
        Bot.polling[IO].follow(scenarios*).compile.drain
