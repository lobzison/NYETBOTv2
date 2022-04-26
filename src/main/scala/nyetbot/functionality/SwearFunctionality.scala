package nyetbot.functionality

import canoe.api.Scenario
import canoe.models.messages.TelegramMessage

trait SwearFunctionality[F[_]]:
    def scenario: Scenario[F, Unit]
    def getOptionalSwear: F[Option[String]]
    def sendOptionalSwear(msg: TelegramMessage): F[Unit]
