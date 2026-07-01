package nyetbot.util

object Text:
    // Truncate to at most `max` chars without splitting a surrogate pair. A lone surrogate is
    // invalid UTF-8 and would make skunk's parameter encoder throw, so drop a dangling high half.
    def truncate(s: String, max: Int): String =
        val t = s.take(max)
        if t.nonEmpty && Character.isHighSurrogate(t.charAt(t.length - 1)) then t.substring(0, t.length - 1)
        else t
