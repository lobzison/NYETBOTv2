package nyetbot

import canoe.api.*
import canoe.api.models.ChatApi
import canoe.models.*
import canoe.models.messages.*
import canoe.syntax.*
import cats.*
import cats.syntax.functor.toFunctorOps

def greetings[F[_]: TelegramClient]: Scenario[F, Unit] =
    for
        chat <- Scenario.expect(command("hi").chat)
        _    <- Scenario.eval(chat.send("Hello. What's your name?"))
        name <- Scenario.expect(text)
        _    <- Scenario.eval(chat.send(s"Nice to meet you, $name"))
    yield ()

def echos[F[_]: TelegramClient: Functor]: Scenario[F, Unit] =
    for
        msg <- Scenario.expect(any)
        _   <- Scenario.eval(echoBack[F](msg))
    yield ()

def echoBack[F[_]: TelegramClient: Functor](msg: TelegramMessage): F[Unit] = msg match
    case stickerMessage: StickerMessage     => msg.chat.send(stickerMessage.sticker).void
    case imageMessage: PhotoMessage         => msg.chat.send(imageMessage.photo.head).void
    case animationMessage: AnimationMessage => msg.chat.send(animationMessage.animation).void
    case x @ _                              => msg.chat.send(s"Sorry! I can't echo that back. $x").void

def womboCombo[F[_]: TelegramClient: Functor] =
    for
        _ <- greetings[F]
        _ <- echos[F]
    yield ()
