package nyetbot.lab

import scala.util.Random

object WindowSampler:

    final case class WindowRange(start: Int, size: Int):
        def end: Int                  = start + size
        def contains(i: Int): Boolean = i >= start && i < end

    final case class ImpossibleFit(eligible: Int, required: Int)

    def sample(
        eligible: Int,
        windowSize: Int,
        windows: Int,
        seed: Long
    ): Either[ImpossibleFit, List[WindowRange]] =
        val required = windowSize * windows
        if eligible < required then Left(ImpossibleFit(eligible, required))
        else
            val random  = new Random(seed)
            val slack   = eligible - required
            val offsets = List.fill(windows)(random.nextInt(slack + 1)).sorted
            Right(
              offsets.zipWithIndex.map((offset, i) =>
                  WindowRange(offset + i * windowSize, windowSize)
              )
            )
