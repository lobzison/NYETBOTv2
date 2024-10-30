package nyetbot.functionality

import canoe.api.Scenario
import cats.effect.IO

trait MemeFunctionality:
    def triggerMemeScenario: Scenario[IO, Unit]
    def memeManagementScenarios: List[Scenario[IO, Unit]]
