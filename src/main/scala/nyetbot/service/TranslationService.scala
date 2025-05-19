package nyetbot.service

import cats.effect.IO
import cats.implicits.*
import io.circe.Decoder
import io.circe.Json
import io.circe.literal.*
import nyetbot.Config
import nyetbot.model.LlmContextMessage
import nyetbot.service.TranslationService.TargetLang
import nyetbot.service.TranslationService.Translation
import org.http4s.Credentials
import org.http4s.Header
import org.http4s.Headers
import org.http4s.Request
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.typelevel.ci.CIString

trait TranslationService:
    def translate(s: String, targetLang: TargetLang): IO[String]
    def translateMessageBatch(
        q: List[LlmContextMessage],
        targetLang: TargetLang
    ): IO[List[LlmContextMessage]]

object TranslationService:
    enum TargetLang:
        case EN, RU

    case class Translation(text: String)

    object Translation:
        implicit val translationDecoder: Decoder[Translation] =
            Decoder.forProduct1("text")(Translation.apply)

class DeeplTranslationService(
    client: Client[IO],
    config: Config.TranslateConfig
) extends TranslationService:
    private def translateBatch(messages: List[String], targetLang: TargetLang): IO[List[String]] =
        val auth = Authorization(Credentials.Token(CIString("DeepL-Auth-Key"), config.token))
        val body =
            json"""{
                "target_lang": ${targetLang.toString},
                "text": $messages,
                "formality": "prefer_less"

            }"""
        val req  = Request[IO](uri = config.uri, headers = Headers(List(auth))).withEntity(body)
        if messages.nonEmpty then
            client.run(req).use { r =>
                r.decodeJson[Json].flatMap { j =>
                    IO.fromEither(
                      j.hcursor.downField("translations").as[List[Translation]]
                    ).map(_.map(_.text))
                }
            }
        else IO.pure(List.empty)

    override def translate(s: String, targetLang: TargetLang): IO[String]                        =
        translateBatch(List(s), targetLang).map(_.head)

    override def translateMessageBatch(
        q: List[LlmContextMessage],
        targetLang: TargetLang
    ): IO[List[LlmContextMessage]] =
        val textMessages = q.map(m => m.text)
        translateBatch(textMessages, targetLang).map(l =>
            l.zip(q).map { case (translated, LlmContextMessage(name, _)) =>
                LlmContextMessage(name, translated)
            }
        )
