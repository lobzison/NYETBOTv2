package nyetbot.functionality

import canoe.api.Scenario

trait MemeFunctionality[F[_]]:
    def triggerMemeScenario: Scenario[F, Unit]
    def memeManagementScenarios: List[Scenario[F, Unit]]
