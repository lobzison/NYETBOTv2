package nyetbot.lab

import munit.FunSuite
import nyetbot.lab.WindowSampler.ImpossibleFit
import nyetbot.lab.WindowSampler.WindowRange

class WindowSamplerSpec extends FunSuite:

    private def sampled(
        eligible: Int,
        windowSize: Int,
        windows: Int,
        seed: Long
    ): List[WindowRange] =
        WindowSampler.sample(eligible, windowSize, windows, seed) match
            case Left(err)     => fail(s"unexpected error: $err")
            case Right(ranges) => ranges

    test("same seed produces the same windows") {
        assertEquals(
          WindowSampler.sample(1000, 40, 5, 42L),
          WindowSampler.sample(1000, 40, 5, 42L)
        )
    }

    test("different seeds produce different windows") {
        assertNotEquals(
          WindowSampler.sample(1000, 40, 5, 1L),
          WindowSampler.sample(1000, 40, 5, 2L)
        )
    }

    test("windows do not overlap") {
        val ranges = sampled(500, 45, 7, 7L)
        ranges.sliding(2).foreach {
            case List(prev, next) => assert(prev.end <= next.start, s"$prev overlaps $next")
            case _                => ()
        }
    }

    test("windows stay within bounds and keep their size") {
        val ranges = sampled(500, 45, 7, 13L)
        assertEquals(ranges.size, 7)
        ranges.foreach { r =>
            assert(r.start >= 0, s"$r starts before 0")
            assert(r.end <= 500, s"$r ends after 500")
            assertEquals(r.size, 45)
        }
    }

    test("tight fit packs windows back to back") {
        val ranges = sampled(200, 40, 5, 99L)
        assertEquals(ranges.map(_.start), List(0, 40, 80, 120, 160))
    }

    test("impossible fit is an error") {
        assertEquals(
          WindowSampler.sample(399, 40, 10, 42L),
          Left(ImpossibleFit(399, 400))
        )
    }
