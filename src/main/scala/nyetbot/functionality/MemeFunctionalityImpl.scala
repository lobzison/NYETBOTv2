package nyetbot.functionality

import canoe.api.Scenario
import nyetbot.service.MemeService
import cats.Monad
import cats.implicits.*
import cats.*
import canoe.syntax.*
import canoe.api.*
import canoe.models.*
import canoe.models.messages.*
import nyetbot.model.{SupportedMemeType, MemeCreationRequest, MemeId}
import nyetbot.model.Meme
import scala.util.Try
import nyetbot.model.{*, given}

class MemeFunctionalityImpl[F[_]: MonadThrow: TelegramClient](service: MemeService[F])
    extends MemeFunctionality[F]
    with Discard[F]:
    def triggerMemeScenario: Scenario[F, Unit] =
        for
            message       <- Scenario.expect(textMessage)
            maybeResponses = service.getMemeResponse(message.text)
            _             <- Scenario.eval(memeSendingAction(maybeResponses, message.chat, message.messageId))
        yield ()

    private def memeSendingAction(
        messagesF: F[List[SupportedMemeType]],
        chat: Chat,
        replyTo: Int
    ): F[Unit] =
        for
            messages <- messagesF
            res      <- messages.traverse(message => sendMeme(message, chat, replyTo))
        yield ()

    private def sendMeme(meme: SupportedMemeType, chat: Chat, replyTo: Int): F[Unit] =
        chat.send(meme.toMessageContent, replyToMessageId = Some(replyTo)).void

    def memeManagementScenarios: List[Scenario[F, Unit]] =
        List(
          addMemeScenario,
          showMemesScenario,
          deleteMemeScenario
        )

    def addMemeScenario: Scenario[F, Unit] =
        for
            chat           <- Scenario.expect(command("add_meme").chat)
            _              <-
                Scenario.eval(
                  chat.send(
                    "Send trigger of the meme. The syntax is the same as SQL's like - '_' matches one symbol," +
                        " '%' matches any number of symbols. Normally you want to wrap your trigger into %trigger%, " +
                        "so it will be triggered independently of other words"
                  )
                )
            triggerRaw     <- Scenario.expect(text).handleDiscard
            trigger         = triggerRaw.toLowerCase
            _              <- Scenario.eval(
                                chat.send(s"Name set to $trigger\nSend picture, sticker or animation")
                              )
            meme           <- Scenario.expect(any).handleDiscard
            _              <-
                Scenario.eval(
                  chat.send(
                    "Send a number that determines how often the meme could be triggered." +
                        " From 1 — 'every time' to any positive number, i.e. 100 — one in 100 messages'."
                  )
                )
            chanceString   <- Scenario.expect(text).handleDiscard
            chance         <- Scenario.eval(parseChance(chanceString)).handleErrorWith {
                                  case _: NumberFormatException =>
                                      Scenario.eval(chat.send("Invalid chance, setting it to 1, IDGAS")) >>
                                          Scenario.pure(1)
                              }
            creationResult <- Scenario.eval(createMeme(trigger, meme, chance))
            _              <-
                Scenario.eval(
                  creationResult.fold(chat.send("Unsupported media, meme not added"))(_ =>
                      chat.send("Meme added")
                  )
                )
        yield ()

    private def createMeme(trigger: String, meme: TelegramMessage, chance: Int): F[Option[Unit]] =
        val memeTypeOpt = SupportedMemeType.fromTelegramMessage(meme)
        memeTypeOpt.traverse(memeType =>
            service.addMeme(MemeCreationRequest(trigger, memeType, chance))
        )

    def showMemesScenario: Scenario[F, Unit] =
        for
            chat <- Scenario.expect(command("show_memes").chat)
            _    <- Scenario.eval(showMemesAction(chat))
        yield ()

    private def showMemesAction(chat: Chat): F[Unit] =
        for
            memesTable <- service.showAllMemes
            _          <- chat.send(textContent(memesTable).copy(parseMode = Some(ParseMode.HTML)))
        yield ()

    private def parseChance(chanceString: String): F[Int] =
        for chanceParsed <- MonadThrow[F].fromTry(Try(chanceString.toInt))
        yield chanceParsed.max(1)

    def deleteMemeScenario: Scenario[F, Unit] =
        for
            chat     <- Scenario.expect(command("del_meme").chat)
            _        <- Scenario.eval(showMemesAction(chat))
            _        <- Scenario.eval(chat.send("Send meme id"))
            idString <- Scenario.expect(text).handleDiscard
            res      <- Scenario.eval(deleteMemeAction(idString)).attempt
            _        <- res.fold(
                          _ => Scenario.eval(chat.send("Invalid id, please send integer")),
                          _ => Scenario.eval(chat.send("Meme deleted"))
                        )
        yield ()

    private def deleteMemeAction(idString: String): F[Unit] =
        for
            id  <- MonadThrow[F].fromTry(Try(idString.toInt))
            res <- service.deleteMeme(MemeId(id))
        yield ()
