package nyetbot.functionality

import canoe.api.Scenario
import canoe.models.messages.TelegramMessage
import cats.effect.IO

trait SwearFunctionality:
    def scenario: Scenario[IO, Unit]
    def getOptionalSwear: IO[Option[String]]
    def sendOptionalSwear(msg: TelegramMessage): IO[Unit]
