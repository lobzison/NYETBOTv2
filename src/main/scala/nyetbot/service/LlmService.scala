package nyetbot.service
import com.donderom.llm4s.*
import cats.effect.kernel.Resource
import cats.effect.kernel.Sync
import cats.syntax.applicative.*
import cats.syntax.all.*
import nyetbot.Config
import nyetbot.model.LlmContextMessage
import fs2.Stream
import cats.effect.std.Console
import canoe.models.messages.TextMessage
import nyetbot.utils.StreamUtils

trait LlmService[F[_]]:
    def predict(context: List[LlmContextMessage]): F[String]

object LlmService:
    def apply[F[_]: Sync: Console](config: Config.LlmConfig): Resource[F, LlmService[F]] =
        val model: Resource[F, Llm] =
            val loadModel = Console[F].println("Start loading llm") >> Sync[F].delay(
              Llm(model = config.modelPath, params = config.contextParams)
            )
            Resource.make(loadModel)(m =>
                Console[F].println("Closing LLM") >> Sync[F].delay(m.close())
            )
        val loadLib: F[Unit]        =
            Sync[F].delay(System.load(config.llibPath))
        model.evalMap(model => loadLib.as(new LlmServiceImpl[F](config, model)))

class LlmServiceImpl[F[_]: Sync: Console](config: Config.LlmConfig, model: Llm)
    extends LlmService[F]:

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

    override def predict(context: List[LlmContextMessage]): F[String] =
        val fullPrompt = buildPrompt(context)
        for
            _        <- Console[F].println(fullPrompt)
            tryLlm   <- Sync[F].delay(model(fullPrompt, config.llmParams))
            lazyList <- Sync[F].fromTry(tryLlm)
            _        <- Console[F].println("Start prediction")
            stream    = Stream.fromBlockingIterator(lazyList.iterator, 1)
            res      <- stream
                            .evalMap(m => Console[F].println(s""""$m"""").as(m))
                            .through(StreamUtils.stopAt(config.userPrefix))
                            .compile
                            .foldMonoid
        yield res
