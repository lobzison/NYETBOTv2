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

extension (memes: List[Meme])(using Show[Chance])
    def show[F[_]: MonadThrow]: F[String] =
        val header = List("id", "trigger", "chance of trigger")
        val body   = memes.map { m =>
            List(
              m.id.value.toString,
              m.trigger.toMemeTriggerUserSyntax.value,
              m.chance.show
            )
        }
        val drawer = TableDrawer.create[F](header.length, header :: body)
        drawer.map(_.buildHtmlCodeTable)
