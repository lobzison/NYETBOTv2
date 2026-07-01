package nyetbot.util

object Text:
    def truncate(s: String, max: Int): String =
        val t = s.take(max)
        if t.nonEmpty && Character.isHighSurrogate(t.charAt(t.length - 1)) then
            t.substring(0, t.length - 1)
        else t
