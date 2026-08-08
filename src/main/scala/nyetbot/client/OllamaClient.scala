package nyetbot.client

import cats.effect.IO
import io.circe.{Encoder, Json}
import io.circe.derivation.{ConfiguredEncoder, Configuration}
import io.circe.syntax.*
import org.http4s.Method.POST
import org.http4s.Request
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.circe.*

trait OllamaClient:
    def generate(req: OllamaClient.Req): IO[String]

object OllamaClient:
    private given Configuration = Configuration.default.withSnakeCaseMemberNames

    case class Req(
        model: String,
        system: Option[String],
        template: Option[String],
        prompt: String,
        stream: Boolean,
        think: Boolean,
        options: Req.Options
    )
    object Req:
        case class Options(
            numPredict: Int,
            temperature: Double,
            topP: Option[Double],
            topK: Option[Int],
            repeatPenalty: Option[Double],
            numCtx: Int,
            stop: Option[List[String]]
        )

        given Encoder[Options] = ConfiguredEncoder.derived
        given Encoder[Req]     = ConfiguredEncoder.derived

    def apply(client: Client[IO], uri: Uri): OllamaClient =
        val generateUri = Uri.unsafeFromString(s"$uri/api/generate")
        new OllamaClient:
            def generate(req: Req): IO[String] =
                val request =
                    Request[IO](method = POST)
                        .withUri(generateUri)
                        .withEntity(req.asJson.deepDropNullValues)
                client
                    .run(request)
                    .use { res =>
                        res.decodeJson[Json].flatMap { j =>
                            IO.fromEither(j.hcursor.downField("response").as[String])
                        }
                    }
                    .map(_.trim)
