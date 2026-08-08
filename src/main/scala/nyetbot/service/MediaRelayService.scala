package nyetbot.service

import canoe.models.outgoing.MessageContent
import cats.effect.IO
import cats.implicits.*

trait MediaRelayService[F[_]]:
    def relay(text: String): F[List[MessageContent[?]]]

object MediaRelayService:
    private val defaultRelays: List[MediaRelay[IO]] = List(
      MediaRelay.linkHostRewrite("x.com", "xcancel.com")
    )

    def apply(): MediaRelayService[IO] =
        new MediaRelayService[IO]:
            override def relay(text: String): IO[List[MessageContent[?]]] =
                defaultRelays.traverse(_.relay(text)).map(_.flatten)
