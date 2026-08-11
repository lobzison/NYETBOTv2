package nyetbot.service.llm.context

import cats.effect.Clock
import cats.effect.IO
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.all.Empty
import io.github.iltotore.iron.constraint.any.Not
import nyetbot.config.llm.BlockConfig

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object DateFeature:
    type ReplyDate = ReplyDate.T
    object ReplyDate extends RefinedType[String, Not[Empty]]

    private val formatter =
        DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("ru"))

    def apply(config: BlockConfig): ContextFeature[ReplyDate] =
        ContextFeature.io("date", config.enabled) { _ =>
            Clock[IO].realTimeInstant.map { instant =>
                ReplyDate.either(instant.atZone(ZoneId.systemDefault()).format(formatter)).toOption
            }
        }
