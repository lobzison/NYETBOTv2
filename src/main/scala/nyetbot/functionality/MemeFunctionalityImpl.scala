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
import nyetbot.model.Memes
import scala.util.Try
import nyetbot.model.given

class MemeFunctionalityImpl[F[_]: MonadThrow: TelegramClient](service: MemeService[F])
    extends MemeFunctionality[F]:
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

    // TODO: think how to avoid this
    private def sendMeme(meme: SupportedMemeType, chat: Chat, replyTo: Int): F[Unit] =
        meme match
            case SupportedMemeType.Sticker(s)   =>
                chat.send(s, replyToMessageId = Some(replyTo)).void
            case SupportedMemeType.PhotoSize(p) =>
                chat.send(p, replyToMessageId = Some(replyTo)).void
            case SupportedMemeType.Animation(a) =>
                chat.send(a, replyToMessageId = Some(replyTo)).void

    def memeManagementScenarios: List[Scenario[F, Unit]] =
        List(
          addMemeScenario,
          showMemesScenario,
          deleteMemeScenario
        )

    def addMemeScenario: Scenario[F, Unit] =
        for
            chat            <- Scenario.expect(command("add_meme").chat)
            _               <- Scenario.eval(chat.send("Send the name of the meme"))
            triggerRaw      <- Scenario.expect(text).handleDiscard
            trigger          = triggerRaw.toLowerCase
            _               <- Scenario.eval(
                                 chat.send(s"Name set to $trigger\nSend picture, sticker or animation")
                               )
            meme            <- Scenario.expect(any).handleDiscard
            _               <-
                Scenario.eval(
                  chat.send(
                    "Send a number that determines how often the meme could be triggered." +
                        " From 1 — 'every time' to any positive number, i.e. 100 — one in 100 messages'."
                  )
                )
            chanceString    <- Scenario.expect(text).handleDiscard
            chance          <- Scenario.eval(parseChance(chanceString)).handleErrorWith {
                                   case _: NumberFormatException =>
                                       Scenario.eval(chat.send("Invalid chance, setting it to 1, IDGAS")) >>
                                           Scenario.pure(1)
                               }
            creationResult   = createMeme(trigger, meme, chance)
            resultingMessage =
                creationResult.fold(chat.send("Unsupported media, meme not added"))(action =>
                    action >> chat.send("Meme added")
                )
            _               <- Scenario.eval(resultingMessage)
        yield ()

        // TODO: burh
    private def createMeme(trigger: String, meme: TelegramMessage, chance: Int): Option[F[Unit]] =
        meme match
            case stickerMessage: StickerMessage     =>
                Some(
                  service.addMeme(
                    MemeCreationRequest(
                      trigger,
                      SupportedMemeType.Sticker(stickerMessage.sticker),
                      chance
                    )
                  )
                )
            case imageMessage: PhotoMessage         =>
                Some(
                  service.addMeme(
                    MemeCreationRequest(
                      trigger,
                      SupportedMemeType.PhotoSize(imageMessage.photo.head),
                      chance
                    )
                  )
                )
            case animationMessage: AnimationMessage =>
                Some(
                  service.addMeme(
                    MemeCreationRequest(
                      trigger,
                      SupportedMemeType.Animation(animationMessage.animation),
                      chance
                    )
                  )
                )
            case _                                  => None

    def showMemesScenario: Scenario[F, Unit] =
        for
            chat <- Scenario.expect(command("show_memes").chat)
            _    <- Scenario.eval(showMemesAction(chat))
        yield ()

    private def showMemesAction(chat: Chat)(using Show[Memes]): F[Unit] =
        for
            memes <- service.getAllMemes
            _     <- chat.send(textContent(memes.show).copy(parseMode = Some(ParseMode.HTML)))
        yield ()

    private def parseChance(chanceString: String): F[Int] =
        for chanceParsed <- MonadThrow[F].fromTry(Try(chanceString.toInt))
        yield chanceParsed.max(1)

    def deleteMemeScenario: Scenario[F, Unit] =
        for
            chat     <- Scenario.expect(command("del_meme").chat)
            _        <- Scenario.eval(chat.send("Send meme id"))
            idString <- Scenario.expect(text).handleDiscard
            _        <- Scenario
                            .eval(deleteMemeAction(idString))
                            .handleErrorWith { case _: NumberFormatException =>
                                Scenario.eval(chat.send("Invalid id, please send integer"))
                            }
            _        <- Scenario.eval(chat.send("Meme deleted"))
        yield ()

    private def deleteMemeAction(idString: String): F[Unit] =
        for
            id <- MonadThrow[F].fromTry(Try(idString.toInt))
            _  <- service.deleteMeme(MemeId(id))
        yield ()

    private def scenarioDiscardTrigger: TelegramMessage => Boolean =
        case t: TextMessage if t.text == "/discard" => true
        case _                                      => false

    private def scenarioDiscardAction: TelegramMessage => F[Unit] =
        case m: UserMessage => m.chat.send("Discarded").void
        case _              => MonadThrow[F].unit

    extension [A](s: Scenario[F, A])
        def handleDiscard: Scenario[F, A] =
            s.stopWith(scenarioDiscardTrigger)(scenarioDiscardAction)
