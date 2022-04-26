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
            _             <- Scenario.eval(memeSendingAction(maybeResponses, message.chat))
        yield ()

    private def memeSendingAction(messagesF: F[List[SupportedMemeType]], chat: Chat): F[Unit] =
        for
            messages <- messagesF
            res      <- messages.traverse(message => sendMeme(message, chat))
        yield ()

    // TODO: think how to avoid this
    private def sendMeme(meme: SupportedMemeType, chat: Chat): F[Unit] =
        meme match
            case SupportedMemeType.Sticker(s)   => chat.send(s).void
            case SupportedMemeType.PhotoSize(p) => chat.send(p).void
            case SupportedMemeType.Animation(a) => chat.send(a).void

    def memeManagementScenarios: List[Scenario[F, Unit]] =
        List(
          addMemeScenario.tolerateAll(_ => MonadThrow[F].unit)
          // , showMemesScenario, deleteMemeScenario
        )

    def addMemeScenario: Scenario[F, Unit] =
        for
            chat            <- Scenario.expect(command("add_meme").chat)
            _               <- Scenario.eval(chat.send("Send the name of the meme"))
            trigger         <- Scenario.expect(text)
            _               <- Scenario.eval(
                                 chat.send(s"Name set to $trigger\nSend picture, sticker or animation")
                               )
            meme            <- Scenario.expect(any)
            creationResult   = createMeme(trigger, meme)
            resultingMessage =
                creationResult.fold(chat.send("Unsupported media, meme not added"))(action =>
                    action *> chat.send("Meme added")
                )
            _               <- Scenario.eval(resultingMessage)
        yield ()

        // TODO: burh
    private def createMeme(trigger: String, meme: TelegramMessage): Option[F[Unit]] = meme match
        case stickerMessage: StickerMessage     =>
            Some(
              service.addMeme(
                MemeCreationRequest(trigger, SupportedMemeType.Sticker(stickerMessage.sticker))
              )
            )
        case imageMessage: PhotoMessage         =>
            Some(
              service.addMeme(
                MemeCreationRequest(trigger, SupportedMemeType.PhotoSize(imageMessage.photo.head))
              )
            )
        case animationMessage: AnimationMessage =>
            Some(
              service.addMeme(
                MemeCreationRequest(
                  trigger,
                  SupportedMemeType.Animation(animationMessage.animation)
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

    def deleteMemeScenario: Scenario[F, Unit] =
        for
            chat     <- Scenario.expect(command("del_meme").chat)
            _        <- Scenario.eval(chat.send("Send meme id"))
            idString <- Scenario.expect(text)
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
