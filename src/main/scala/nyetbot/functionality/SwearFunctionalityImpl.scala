package nyetbot.functionality

import canoe.api.Scenario
import canoe.api.TelegramClient
import cats.Monad
import cats.effect.std.Random
import cats.implicits.*
import cats.*
import canoe.api.*
import canoe.models.*
import canoe.models.messages.*
import canoe.syntax.*
import nyetbot.service.SwearService

class SwearFunctionalityImpl[F[_]: TelegramClient: Monad: Random](service: SwearService[F])
    extends SwearFunctionality[F]:
    override def scenario: Scenario[F, Unit] =
        for
            msg <- Scenario.expect(any)
            _   <- Scenario.eval(sendOptionalSwear(msg))
        yield ()

    def getOptionalSwear: F[Option[String]] =
        service.getSwear.map(_.map(_.value))

    def sendOptionalSwear(msg: TelegramMessage): F[Unit] =
        for
            swear <- getOptionalSwear
            _     <- swear.fold(Monad[F].unit)(swear =>
                         msg.chat.send(swear, replyToMessageId = Some(msg.messageId)).void
                     )
        yield ()