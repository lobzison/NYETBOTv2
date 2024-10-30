package nyetbot.model

import cats.Show

given Show[Chance] with
    def show(c: Chance): String =
        c.value match
            case 1 => "✔"
            case _ => s"1/$c"
