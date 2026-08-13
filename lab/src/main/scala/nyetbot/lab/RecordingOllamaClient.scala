package nyetbot.lab

import cats.effect.IO
import cats.effect.Ref
import io.circe.Encoder
import nyetbot.client.OllamaClient

final case class LlmCall(tag: String, request: OllamaClient.Req, response: String, millis: Long)
    derives Encoder.AsObject

final class RecordingOllamaClient(underlying: OllamaClient, calls: Ref[IO, Vector[LlmCall]]):
    def tagged(tag: String): OllamaClient = new OllamaClient:
        def generate(req: OllamaClient.Req): IO[String] =
            underlying.generate(req).timed.flatMap { (elapsed, response) =>
                calls.update(_ :+ LlmCall(tag, req, response, elapsed.toMillis)).as(response)
            }

    val dossier: OllamaClient        = tagged("dossier")
    val topic: OllamaClient          = tagged("topic")
    val register: OllamaClient       = tagged("register")
    val intent: OllamaClient         = tagged("intent")
    val reply: OllamaClient          = tagged("reply")
    val profileRewrite: OllamaClient = tagged("profile-rewrite")

    def recorded: IO[List[LlmCall]] = calls.get.map(_.toList)

object RecordingOllamaClient:
    def create(underlying: OllamaClient): IO[RecordingOllamaClient] =
        Ref.of[IO, Vector[LlmCall]](Vector.empty).map(RecordingOllamaClient(underlying, _))
