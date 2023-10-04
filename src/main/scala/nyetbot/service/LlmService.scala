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
    def model: Resource[F, Llm]
    def predict(context: List[LlmContextMessage]): F[String]

object LlmService:
    def apply[F[_]: Sync: Console](config: Config.LlmConfig): F[LlmService[F]] =
        val loadLib: F[Unit] =
            Sync[F].delay(System.load(config.llibPath))
        loadLib.as(new LlmServiceImpl[F](config))

class LlmServiceImpl[F[_]: Sync: Console](config: Config.LlmConfig) extends LlmService[F]:
    override def model: Resource[F, Llm] =
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
          "\n",
          userInputContext,
          s"${config.userPrefix}${config.botName}${config.inputPrefix}"
        ).mkString("\n")

    override def predict(context: List[LlmContextMessage]): F[String] =
        val fullPrompt = buildPrompt(context)
        model.use(llm =>
            for
                _        <- Console[F].println(fullPrompt)
                tryLlm   <- Sync[F].delay(llm(fullPrompt, config.llmParams))
                lazyList <- Sync[F].fromTry(tryLlm)
                _        <- Console[F].println("Start prediction")
                stream    = Stream.fromBlockingIterator(lazyList.iterator, 1)
                res      <- stream
                                .evalMap(m => Console[F].println(s""""$m"""").as(m))
                                .through(StreamUtils.stopAt(config.userPrefix))
                                .compile
                                .foldMonoid
            yield res
        )
