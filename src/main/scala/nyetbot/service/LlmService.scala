package nyetbot.service

import com.donderom.llm4s.*
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
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

object LlmService:
    def apply[F[_]: Sync: Console](config: Config.LlmConfig): F[LlmService[F]] =
        val loadLib: F[Unit] =
            Sync[F].delay(System.load(config.llibPath))
        loadLib.as(new LlmServiceImpl[F](config))

class LlmServiceImpl[F[_]: Sync: Console](config: Config.LlmConfig) extends LlmService[F]:
    def model: Resource[F, Llm] =
        val loadModel = Console[F].println("Start loading llm") >> Sync[F].delay(
          Llm(model = config.modelPath, params = config.contextParams)
        )
        Resource.make(loadModel)(m => Console[F].println("Closing LLM") >> Sync[F].delay(m.close()))

    private def buildPrompt(context: List[LlmContextMessage]): String =
        val userInputContext = context
            .map(m => s"${m.userName}${config.inputPrefix}${m.text}")
            .mkString("\n")
        List(
          config.promptPrefix,
          "\n\n",
          userInputContext + s". Hey motherfucker ${config.botName}, what do you think about it?",
          s"${config.userPrefix}${config.botName}${config.inputPrefix}"
        ).mkString("\n")

    override def predict(
        context: List[LlmContextMessage],
        skipPromptInjection: Boolean
    ): F[String] =
        val fullPrompt = buildPrompt(context)
        model.use(llm =>
            for
                _        <- Console[F].println(fullPrompt)
                tryLlm   <- Sync[F].delay(llm(fullPrompt, config.llmParams))
                lazyList <- Sync[F].fromTry(tryLlm)
                res      <- Sync[F].blocking(lazyList.foldLeft("")(_ + _))
            yield res
        )

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
