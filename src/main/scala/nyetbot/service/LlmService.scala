package nyetbot.service

import cats.syntax.all.*
import nyetbot.Config
import nyetbot.model.LlmContextMessage
import cats.effect.std.Console
import cats.effect.{Async, MonadCancelThrow}
import io.circe.Json
import io.circe.literal.json
import org.http4s.Method.POST
import org.http4s.{Request, Uri}
import org.http4s.client.Client
import org.http4s.circe.*

trait LlmService[F[_]]:
    def predict(context: List[LlmContextMessage], skipPromptInjection: Boolean): F[String]

class OllamaService[F[_]: Async: Console](
    client: Client[F],
    config: Config.OllamaConfig,
    llmConfig: Config.LlmConfig
) extends LlmService[F]:

    private def buildPrompt(
        context: List[LlmContextMessage],
        skipPromptInjection: Boolean
    ): String =
        val userInputContext = context
            .map(m => s"${m.userName}${llmConfig.inputPrefix}${m.text}")
            .mkString("\n")
        if skipPromptInjection then userInputContext.replace(llmConfig.botAlias, llmConfig.botName)
        else userInputContext + s". Hey ${llmConfig.botName}, what do you think about it?"

    override def predict(
        context: List[LlmContextMessage],
        skipPromptInjection: Boolean
    ): F[String] =
        val messages: String = buildPrompt(context, skipPromptInjection)
        val body             =
            json""" { "model": "NYETBOTv1", "prompt": $messages, "stream": false } """

        val uri     = Uri.unsafeFromString(s"${config.uri}/api/generate")
        val request = Request[F](method = POST).withUri(uri).withEntity(body)

        Console[F].println("Start prediction") *>
            client.run(request).use { res =>
                res.decodeJson[Json].flatMap { j =>
                    MonadCancelThrow[F]
                        .fromEither(
                          j.hcursor.downField("response").as[String]
                        )
                }
            }
