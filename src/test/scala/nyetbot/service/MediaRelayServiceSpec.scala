package nyetbot.service

import canoe.models.outgoing.{MessageContent, TextContent}
import cats.effect.IO
import munit.CatsEffectSuite

class MediaRelayServiceSpec extends CatsEffectSuite:
    private val service = MediaRelayServiceImpl()

    private def textLinks(io: IO[List[MessageContent[?]]]): IO[List[String]] =
        io.map(_.collect { case TextContent(text, _, _) => text })

    test("relays an x.com link to xcancel.com preserving scheme and path") {
        textLinks(service.relay("see https://x.com/user/status/123"))
            .assertEquals(List("https://xcancel.com/user/status/123"))
    }

    test("relays an http x.com link") {
        textLinks(service.relay("http://x.com/ABC")).assertEquals(List("http://xcancel.com/ABC"))
    }

    test("relays a www.x.com link, dropping the www prefix") {
        textLinks(service.relay("https://www.x.com/foo"))
            .assertEquals(List("https://xcancel.com/foo"))
    }

    test("preserves query strings and fragments") {
        textLinks(service.relay("https://x.com/u/status/1?s=20&t=ab#frag"))
            .assertEquals(List("https://xcancel.com/u/status/1?s=20&t=ab#frag"))
    }

    test("relays all types of relay in the same message") {
        textLinks(service.relay("a https://x.com/a b https://x.com/b https://x.com/a"))
            .assertEquals(List("https://xcancel.com/a\nhttps://xcancel.com/b"))
    }

    test("ignores plain text without media links") {
        service.relay("just chatting, no links here").assertEquals(List.empty)
    }

    test("does not relay a bare domain mention without a path") {
        service.relay("see x.com sometime").assertEquals(List.empty)
    }
