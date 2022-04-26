package nyetbot.model

import canoe.models.{Sticker, PhotoSize, Animation}
import io.circe.Json
import io.circe.syntax.*
import io.circe.generic.auto.*
import skunk.codec.all.*
import skunk.circe.codec.json.json
import skunk.*
import cats.Show
import skunk.Decoder

opaque type MemeId = Int
object MemeId:
    def apply(id: Int): MemeId           = id
    extension (x: MemeId) def value: Int = x

enum SupportedMemeType:
    case Sticker(sticker: canoe.models.Sticker)
    case PhotoSize(photo: canoe.models.PhotoSize)
    case Animation(animation: canoe.models.Animation)

case class Meme(id: MemeId, trigger: String, body: SupportedMemeType)

case class MemeCreationRequest(trigger: String, body: SupportedMemeType):
    def toPersisted(id: MemeId): MemePersisted           =
        MemePersisted(id, trigger, body.asJson)
    def toPersistedRequest: MemeCreationRequestPersisted =
        MemeCreationRequestPersisted(trigger, body.asJson)

case class MemeCreationRequestPersisted(trigger: String, body: Json)

case class Memes(triggers: Set[String], memes: Map[String, Meme])
object Memes:
    given Show[Memes] with
        def show(m: Memes): String =
            val idHeader                                      = "id"
            val triggerHeader                                 = "trigger"
            val memeLengths                                   =
                m.memes.values.map { m =>
                    (m.id.value.toString.length, m.trigger.length)
                }.toList :+ (idHeader.length, triggerHeader.length)
            val maxIdLength                                   = memeLengths.map(_._1).max
            val maxTriggerLength                              = memeLengths.map(_._2).max
            def buildRow(id: String, trigger: String): String =
                val idPadding      = " " * (maxIdLength - id.length)
                val triggerPadding = " " * (maxTriggerLength - trigger.length)
                s"$id$idPadding | $trigger$triggerPadding\n"
            def buildHorizontalSeparator(): String            =
                val line = "-" * (maxIdLength + maxTriggerLength)
                s"$line\n"
            """<code>""" + buildRow(idHeader, triggerHeader) + buildHorizontalSeparator() +
                m.memes.values
                    .map(x => buildRow(x.id.value.toString, x.trigger))
                    .mkString + """</code>"""

case class MemePersisted(id: MemeId, trigger: String, body: Json):
    def toMeme: Meme = Meme(
      id,
      trigger,
      // TODO: Cover that shit
      body.as[SupportedMemeType].getOrElse(throw new Exception("Invalid meme body"))
    )
object MemePersisted:
    val memePersisted: Decoder[MemePersisted] =
        (int4 ~ text ~ json).map { case id ~ trigger ~ body =>
            MemePersisted(MemeId(id), trigger, body)
        }

case class MemesPersisted(memes: List[MemePersisted]):
    def toMemes: Memes =
        Memes(
          triggers = memes.map(_.trigger).toSet,
          memes = memes.map(x => x.trigger -> x.toMeme).toMap
        )
