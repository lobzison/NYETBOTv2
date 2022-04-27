package nyetbot.model

import cats.Show
import cats.implicits.toShow

given (using Show[Chance]): Show[Memes] with
    def show(m: Memes): String =
        val idHeader                                                      = "id"
        val triggerHeader                                                 = "trigger"
        val chanceHeader                                                  = "chance of trigger"
        val memeLengths                                                   =
            m.memes.map { m =>
                (
                  m.id.value.toString.length,
                  m.trigger.toMemeTriggerUserSyntax.value.length,
                  m.chance.show.length
                )
            }.toList :+ (idHeader.length, triggerHeader.length, chanceHeader.length)
        val maxIdLength                                                   = memeLengths.map(_._1).max
        val maxTriggerLength                                              = memeLengths.map(_._2).max
        val maxChanceLength                                               = memeLengths.map(_._3).max
        def buildRow(id: String, trigger: String, chance: String): String =
            val idPadding      = " " * (maxIdLength - id.length)
            val triggerPadding = " " * (maxTriggerLength - trigger.length)
            val chancePadding  = " " * (maxChanceLength - chance.length)
            s"$id$idPadding | $trigger$triggerPadding | $chance$chancePadding\n"
        def buildHorizontalSeparator(): String                            =
            val line = "-" * (maxIdLength + maxTriggerLength + maxChanceLength + 6)
            s"$line\n"
        """<code>""" + buildRow(
          idHeader,
          triggerHeader,
          chanceHeader
        ) + buildHorizontalSeparator() +
            m.memes.map { x =>
                buildRow(
                  x.id.value.toString,
                  x.trigger.toMemeTriggerUserSyntax.value,
                  x.chance.show
                )
            }.mkString + """</code>"""

given Show[Chance] with
    def show(c: Chance): String =
        c.value match
            case 1 => "everytime"
            case _ => s"1/$c"
