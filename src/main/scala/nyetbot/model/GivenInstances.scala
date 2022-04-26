package nyetbot.model

import cats.Show

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
