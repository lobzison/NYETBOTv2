package nyetbot.model

import cats.Show
import cats.implicits.toShow
import nyetbot.service.TableDrawer
import cats.MonadThrow
import cats.implicits.toFunctorOps

given Show[Chance] with
    def show(c: Chance): String =
        c.value match
            case 1 => "✔"
            case _ => s"1/$c"
