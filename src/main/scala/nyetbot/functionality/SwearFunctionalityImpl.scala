package nyetbot.functionality

import canoe.api.Scenario
import canoe.api.TelegramClient
import cats.MonadThrow
import cats.effect.std.Random
import cats.implicits.*
import cats.*
import canoe.api.*
import canoe.models.*
import canoe.models.messages.*
import canoe.syntax.*
import nyetbot.service.SwearService
import nyetbot.model.{given, *}
import cats.effect.kernel.syntax.resource
import scala.util.Try

class SwearFunctionalityImpl[F[_]: TelegramClient: MonadThrow: Random](service: SwearService[F])
    extends SwearFunctionality[F]
    with Discard[F]:
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

    def showSwearGroups: Scenario[F, Unit] =
        for
            chat <- Scenario.expect(command("show_swear_groups").chat)
            _    <- Scenario.eval(showSwearGroupsAction(chat))
        yield ()

    private def showSwearGroupsAction(chat: Chat): F[Unit] =
        for
            memesTable <- service.showSwearGroups
            _          <- chat.send(textContent(memesTable).copy(parseMode = Some(ParseMode.HTML)))
        yield ()

    def showSwear: Scenario[F, Unit] =
        for
            chat     <- Scenario.expect(command("show_swears").chat)
            _        <- Scenario.eval(showSwearGroupsAction(chat))
            _        <- Scenario.eval(chat.send("Send swear group id"))
            idString <- Scenario.expect(text).handleDiscard
            res      <- Scenario.eval(showSwearAction(idString)).attempt
            _        <- res.fold(
                          _ => Scenario.eval(chat.send("Invalid id, please send integer")),
                          swears =>
                              Scenario.eval(
                                chat.send(textContent(swears).copy(parseMode = Some(ParseMode.HTML)))
                              )
                        )
        yield ()

    def showSwearAction(idString: String): F[String] =
        for
            id     <- MonadThrow[F].fromTry(Try(idString.toInt))
            swears <- service.showSwears(SwearGroupId(id))
        yield swears
