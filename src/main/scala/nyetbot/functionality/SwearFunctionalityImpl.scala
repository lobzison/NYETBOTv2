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

class SwearFunctionalityImpl[F[_]: TelegramClient: Monad: Random] extends SwearFunctionality[F]:
    override def scenario: Scenario[F, Unit] =
        for
            msg <- Scenario.expect(any)
            _   <- Scenario.eval(sendOptionalSwear(msg))
        yield ()

    def getOptionalSwear: F[Option[String]] =
        for
            r           <- Random[F].betweenInt(0, swearEveryNMessage)
            randomSwear <- Random[F].betweenInt(0, swears.size).map(swears(_))
        yield Option.when(r == 0) { randomSwear }

    def sendOptionalSwear(msg: TelegramMessage): F[Unit] =
        for
            swear <- getOptionalSwear
            _     <- swear.fold(Monad[F].unit)(swear =>
                         msg.chat.send(swear, replyToMessageId = Some(msg.messageId)).void
                     )
        yield ()

    val swearEveryNMessage = 300

    val swears: Vector[String] =
        Vector(
          "slava Ukraine!",
          "nu eto Zalupa uje",
          "da",
          "tak tochno",
          "eto ne tak",
          "infa 100",
          "nyet",
          "podderjivau vot etogo",
          "puk puk",
          "welcome to the club, buddy"
        )
