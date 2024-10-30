package nyetbot.functionality

import canoe.api.*
import canoe.models.*
import canoe.models.messages.TelegramMessage
import canoe.models.messages.TextMessage
import canoe.models.messages.UserMessage
import canoe.syntax.*
import cats.*
import cats.implicits.*

trait Discard[F[_]: Applicative: TelegramClient]:
    private def scenarioDiscardTrigger: TelegramMessage => Boolean =
        case t: TextMessage if t.text == "/discard" => true
        case _                                      => false

    private def scenarioDiscardAction: TelegramMessage => F[Unit] =
        case m: UserMessage => m.chat.send("Discarded").void
        case _              => Applicative[F].unit

    extension [A](s: Scenario[F, A])
        def handleDiscard: Scenario[F, A] =
            s.stopWith(scenarioDiscardTrigger)(scenarioDiscardAction)
