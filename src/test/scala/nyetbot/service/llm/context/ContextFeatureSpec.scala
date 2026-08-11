package nyetbot.service.llm.context

import cats.effect.IO
import cats.effect.Ref
import munit.CatsEffectSuite
import nyetbot.model.ProfileModels.*
import nyetbot.service.llm.LlmService.Trigger
import nyetbot.service.llm.ReplyInputs

class ContextFeatureSpec extends CatsEffectSuite:

    private val inputs =
        ReplyInputs(UserRef(UserId(1L), DisplayName("Гоша")), "текст", Trigger.Random(""), Nil, Nil)

    test("io skips the computation entirely when disabled") {
        for
            calls  <- Ref.of[IO, Int](0)
            feature = ContextFeature.io[Int]("test", enabled = false) { _ =>
                          calls.update(_ + 1).as(Some(1))
                      }
            out    <- feature.get(inputs)
            seen   <- calls.get
        yield
            assertEquals(out, None)
            assertEquals(seen, 0)
    }

    test("io passes a successful value through") {
        ContextFeature
            .io[Int]("test", enabled = true)(_ => IO.pure(Some(42)))
            .get(inputs)
            .map(assertEquals(_, Some(42)))
    }

    test("io recovers a failure into None") {
        ContextFeature
            .io[Int]("test", enabled = true)(_ => IO.raiseError(new RuntimeException("boom")))
            .get(inputs)
            .map(assertEquals(_, None))
    }

    test("pure skips when disabled") {
        ContextFeature
            .pure[Int](enabled = false)(_ => Some(1))
            .get(inputs)
            .map(assertEquals(_, None))
    }

    test("pure evaluates when enabled") {
        ContextFeature
            .pure[Int](enabled = true)(_ => Some(1))
            .get(inputs)
            .map(assertEquals(_, Some(1)))
    }
