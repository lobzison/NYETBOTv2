package nyetbot.model

import cats.Show
import io.github.iltotore.iron.*

given Show[Chance] with
    def show(c: Chance): String =
        c.value match
            case 1 => "✔"
            case _ => s"1/${c.value}"
