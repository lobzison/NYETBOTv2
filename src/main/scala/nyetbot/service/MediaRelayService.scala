package nyetbot.service

import canoe.models.outgoing.{MessageContent, TextContent}
import cats.effect.IO
import cats.implicits.*

import scala.util.matching.Regex

trait MediaRelay[F[_]]:
    def relay(text: String): F[Option[MessageContent[?]]]

object MediaRelay:

    def linkHostRewrite(mediaHost: String, relayHost: String): MediaRelay[IO] =
        val quotedMedia = Regex.quote(mediaHost)
        val pattern     = raw"""https?://(?:www\.)?$quotedMedia/\S*""".r
        new MediaRelay[IO]:
            def relay(text: String): IO[Option[MessageContent[?]]] =
                val links = pattern
                    .findAllMatchIn(text)
                    .map(m =>
                        m.matched.replaceFirst(
                          raw"(?i)(https?://)(?:www\.)?$quotedMedia",
                          "$1" + Regex.quoteReplacement(relayHost)
                        )
                    )
                    .distinct
                    .toList

                Option.when(links.nonEmpty)(TextContent(links.mkString("\n"))).pure

trait MediaRelayService[F[_]]:
    def relay(text: String): F[List[MessageContent[?]]]

class MediaRelayServiceImpl(relays: List[MediaRelay[IO]]) extends MediaRelayService[IO]:
    override def relay(text: String): IO[List[MessageContent[?]]] =
        relays.traverse(_.relay(text)).map(_.flatten)

object MediaRelayServiceImpl:

    val defaultRelays: List[MediaRelay[IO]] = List(
      MediaRelay.linkHostRewrite("x.com", "xcancel.com")
    )

    def apply(): MediaRelayService[IO] = new MediaRelayServiceImpl(defaultRelays)
