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

    def scenarios: List[Scenario[F, Unit]] = List(
      scenario,
      showSwear,
      showSwearGroups,
      addSwearGroup,
      addSwear,
      deleteSwear
    )

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
            _        <- Scenario.eval(chat.send("Send swear group id"))
            _        <- Scenario.eval(showSwearGroupsAction(chat))
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

    def addSwearGroup: Scenario[F, Unit] =
        for
            chat         <- Scenario.expect(command("add_swear_group").chat)
            _            <-
                Scenario.eval(
                  chat.send(
                    "Send a number that determines how often a swear from this group could be triggered." +
                        " From 1 — 'every time' to any positive number, i.e. 100 — one in 100 messages'."
                  )
                )
            chanceString <- Scenario.expect(text).handleDiscard
            res          <- Scenario.eval(MonadThrow[F].fromTry(Try(chanceString.toInt))).attempt
            _            <- res.fold(
                              _ => Scenario.eval(chat.send("Invalid chance, please send integer")),
                              chance =>
                                  Scenario.eval(
                                    service.addSwearGroup(Chance(chance)) >> chat.send("Swear group added")
                                  )
                            )
        yield ()

    def addSwear: Scenario[F, Unit] =
        for
            chat             <- Scenario.expect(command("add_swear").chat)
            _                <- Scenario.eval(chat.send("Send swear group id"))
            _                <- Scenario.eval(showSwearGroupsAction(chat))
            swearGroupString <- Scenario.expect(text).handleDiscard
            res              <- Scenario.eval(parseAndValidateGroupId(swearGroupString)).attempt
            _                <- res.fold(
                                  _ =>
                                      Scenario.eval(
                                        chat.send("Invalid group id, please send id of group that exists")
                                      ),
                                  groupId => addSwear2(groupId, chat)
                                )
        yield ()

    private def addSwear2(groupId: SwearGroupId, chat: Chat): Scenario[F, Unit] =
        for
            _                 <- Scenario.eval(chat.send("Send swear"))
            swearString       <- Scenario.expect(text).handleDiscard
            _                 <- Scenario.eval(chat.send("Send swear weight"))
            swearWeightString <- Scenario.expect(text).handleDiscard
            weightMaybe       <-
                Scenario.eval(MonadThrow[F].fromTry(Try(swearWeightString.toInt))).attempt
            _                 <- weightMaybe.fold(
                                   _ =>
                                       Scenario.eval(
                                         chat.send("Invalid weight id, send integer")
                                       ),
                                   weight =>
                                       Scenario.eval(service.addSwear(groupId, Swear(swearString), weight)) >>
                                           Scenario.eval(chat.send("Swear added"))
                                 )
        yield ()

    private def parseAndValidateGroupId(swearGroupString: String): F[SwearGroupId] =
        for
            idRaw            <- MonadThrow[F].fromTry(Try(swearGroupString.toInt))
            id                = SwearGroupId(idRaw)
            swearGroupExists <- service.swearGroupExists(id)
            res              <- if swearGroupExists then MonadThrow[F].pure(id)
                                else MonadThrow[F].raiseError(new Exception("Swear group does not exist"))
        yield res

    def deleteSwear: Scenario[F, Unit] =
        for
            chat          <- Scenario.expect(command("delete_swear").chat)
            _             <- Scenario.eval(chat.send("Send swear id"))
            swearIdString <- Scenario.expect(text).handleDiscard
            swearIdMaybe  <-
                Scenario.eval(MonadThrow[F].fromTry(Try(swearIdString.toInt))).attempt
            _             <- swearIdMaybe.fold(
                               _ =>
                                   Scenario.eval(
                                     chat.send("Invalid swear id, please integer")
                                   ),
                               swearIdInt =>
                                   Scenario.eval(
                                     service.deleteSwear(SwearId(swearIdInt)) >> chat.send("Swear deleted")
                                   )
                             )
        yield ()
