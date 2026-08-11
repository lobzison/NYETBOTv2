package nyetbot.service.llm.context

import cats.effect.IO
import nyetbot.service.llm.ReplyInputs
import org.slf4j.LoggerFactory

trait ContextFeature[A]:
    def get(in: ReplyInputs): IO[Option[A]]

object ContextFeature:
    private val logger = LoggerFactory.getLogger(getClass)

    def io[A](name: String, enabled: Boolean)(f: ReplyInputs => IO[Option[A]]): ContextFeature[A] =
        if !enabled then _ => IO.pure(None)
        else
            in =>
                f(in).handleErrorWith { e =>
                    IO.delay(logger.warn(s"$name feature failed, skipping", e)).as(None)
                }

    def pure[A](enabled: Boolean)(f: ReplyInputs => Option[A]): ContextFeature[A] =
        if enabled then in => IO.pure(f(in)) else _ => IO.pure(None)
