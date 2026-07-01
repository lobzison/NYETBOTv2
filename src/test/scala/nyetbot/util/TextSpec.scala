package nyetbot.util

import munit.FunSuite

class TextSpec extends FunSuite:

    test("truncate keeps short strings intact") {
        assertEquals(Text.truncate("абв", 10), "абв")
    }

    test("truncate caps at max chars") {
        assertEquals(Text.truncate("я".repeat(500), 300).length, 300)
    }

    test("truncate never leaves a lone high surrogate at the boundary") {
        val s = "a" + "🤡".repeat(10)
        val t = Text.truncate(s, 2)
        assert(!t.nonEmpty || !Character.isHighSurrogate(t.charAt(t.length - 1)))
        assertEquals(t, "a")
    }

    test("truncate keeps a whole surrogate pair when it fits") {
        val t = Text.truncate("🤡x", 2)
        assertEquals(t, "🤡")
    }
