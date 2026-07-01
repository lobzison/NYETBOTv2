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
        // "🤡" is a surrogate pair (2 UTF-16 units). Cut so the boundary splits it.
        val s = "a" + "🤡".repeat(10)
        val t = Text.truncate(s, 2) // would keep 'a' + a lone high surrogate without the guard
        assert(!t.nonEmpty || !Character.isHighSurrogate(t.charAt(t.length - 1)))
        // the split pair is dropped entirely, leaving just "a"
        assertEquals(t, "a")
    }

    test("truncate keeps a whole surrogate pair when it fits") {
        val t = Text.truncate("🤡x", 2)
        assertEquals(t, "🤡")
    }
