package nyetbot

import canoe.api.*
import canoe.syntax.*
import cats.effect.{IO, IOApp}
import cats.effect.kernel.*
import cats.effect.std.Random
import fly4s.core.*
import fs2.Stream
import nyetbot.functionality.*
import nyetbot.model.*
import nyetbot.service.*
import nyetbot.vault.*
import skunk.Session

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
            swearService     <- SwearServiceCached[IO](swearVault)
            swear             = SwearFunctionalityImpl[IO](swearService)
            service          <- MemeServiceCached[IO](dbVault)
            meme              = MemeFunctionalityImpl[IO](service)
            _                <- IO.println("Ready")
        yield List(
          meme.triggerMemeScenario,
          swear.scenario,
          swear.showSwearGroups,
          swear.showSwear
        ) ++ meme.memeManagementScenarios

    def app(scenarios: List[Scenario[IO, Unit]])(using TelegramClient[IO]): IO[Unit] =
        Bot.polling[IO].follow(scenarios*).compile.drain
