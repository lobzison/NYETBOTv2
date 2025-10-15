package nyetbot.service

import cats.effect.IO
import cats.syntax.all.*
import io.circe.Json
import io.circe.literal.json
import nyetbot.Config
import nyetbot.model.LlmContextMessage
import org.http4s.Method.POST
import org.http4s.Request
import org.http4s.Uri
import org.http4s.circe.*
import org.http4s.client.Client

trait LlmService:
    def predict(context: List[LlmContextMessage], skipPromptInjection: Boolean): IO[String]

class OllamaService(
    client: Client[IO],
    config: Config.OllamaConfig,
    llmConfig: Config.LlmConfig
) extends LlmService:

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
    ): IO[String] =
        val messages: String = buildPrompt(context, skipPromptInjection)
        val body             =
            json""" { "model": "NYETBOTv1", "prompt": $messages, "stream": false,  "options": {"num_predict": 500} } """

        val uri     = Uri.unsafeFromString(s"${config.uri}/api/generate")
        val request = Request[IO](method = POST).withUri(uri).withEntity(body)

        IO.println("Start prediction") *>
            client.run(request).use { res =>
                res.decodeJson[Json].flatMap { j =>
                    IO.fromEither(
                      j.hcursor.downField("response").as[String]
                    )
                }
            }
