package nyetbot.functionality

import canoe.api.*
import canoe.models.messages.TextMessage
import canoe.models.outgoing.MessageContent
import canoe.syntax.*
import cats.effect.IO
import cats.implicits.*
import nyetbot.service.MediaRelayService

trait MediaRelayFunctionality:
    def scenario: Scenario[IO, Unit]

class MediaRelayFunctionalityImpl(service: MediaRelayService[IO])(using
    TelegramClient[IO]
) extends MediaRelayFunctionality:
    override def scenario: Scenario[IO, Unit] =
        for
            msg     <- Scenario.expect(textMessage)
            replies <- Scenario.eval(service.relay(msg.text))
            _       <- Scenario.eval(sendReplies(msg, replies))
        yield ()

    private def sendReplies(msg: TextMessage, replies: List[MessageContent[?]]): IO[Unit] =
        replies.traverse_(send(msg, _))

    private def send(msg: TextMessage, content: MessageContent[?]): IO[Unit] =
        msg.chat.send(content, replyToMessageId = Some(msg.messageId)).void
