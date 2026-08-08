package nyetbot.model

import cats.Show
import io.github.iltotore.iron.RefinedType
import io.github.iltotore.iron.constraint.all.Positive

type Chance = Chance.T

object Chance extends RefinedType[Int, Positive]:
    given Show[Chance] with
        def show(c: Chance): String =
            c.value match
                case 1 => "✔"
                case _ => s"1/${c.value}"
