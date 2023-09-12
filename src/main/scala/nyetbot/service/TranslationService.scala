package nyetbot.service

import nyetbot.model.LlmContextMessage
import cats.effect.kernel.Sync
import org.http4s.client.Client
import org.http4s.Request
import org.http4s.implicits.*
import nyetbot.Config
import org.http4s.Header
import org.http4s.Headers
import org.http4s.Credentials
import org.http4s.headers.Authorization
import org.typelevel.ci.CIString
import io.circe.literal.*
import nyetbot.service.TranslationService.{TargetLang, Translation}
import org.http4s.circe.*
import io.circe.Json
import cats.effect.kernel.Async
import cats.implicits.*
import cats.effect.MonadCancelThrow
import cats.effect.kernel.syntax.MonadCancelSyntax
import io.circe.Decoder

trait TranslationService[F[_]]:
    def translate(s: String, targetLang: TargetLang): F[String]
    def translateMessageBatch(
        q: List[LlmContextMessage],
        targetLang: TargetLang
    ): F[List[LlmContextMessage]]

object TranslationService:
    enum TargetLang:
        case EN, RU

    case class Translation(text: String)

    object Translation:
        implicit val translationDecoder: Decoder[Translation] =
            Decoder.forProduct1("text")(Translation.apply)

class DeeplTranslationService[F[_]: Async: MonadCancelThrow](
    client: Client[F],
    config: Config.TranslateConfig
) extends TranslationService[F]:
    private def translateBatch(messages: List[String], targetLang: TargetLang): F[List[String]] =
        val auth = Authorization(Credentials.Token(CIString("DeepL-Auth-Key"), config.token))
        val body =
            json"""{
                "target_lang": ${targetLang.toString},
                "text": $messages,
                "formality": "prefer_less"

            }"""
        val req  = Request[F](uri = config.uri, headers = Headers(List(auth))).withEntity(body)
        client.run(req).use { r =>
            r.decodeJson[Json].flatMap { j =>
                print(j)
                MonadCancelThrow[F]
                    .fromEither(
                      j.hcursor.downField("translations").as[List[Translation]]
                    )
                    .map(_.map(_.text))
            }
        }
    override def translate(s: String, targetLang: TargetLang): F[String]                        =
        translateBatch(List(s), targetLang).map(_.head)

    override def translateMessageBatch(
        q: List[LlmContextMessage],
        targetLang: TargetLang
    ): F[List[LlmContextMessage]] =
        val textMessages = q.map(m => m.text)
        translateBatch(textMessages, targetLang).map(l =>
            l.zip(q).map { case (translated, LlmContextMessage(name, _)) =>
                LlmContextMessage(name, translated)
            }
        )
