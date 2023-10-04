package nyetbot.utils
import fs2.*

object StreamUtils:
    def prefixes(string: String) =
        def go() =
            string.foldLeft((List.empty[String], "")) { case ((acc, curr), s) =>
                val newCurr = curr + s
                val newAcc  = if (newCurr != string) then acc :+ newCurr else acc
                (newAcc, newCurr)
            }
        go()._1

    def suffixes(string: String) =
        def go() =
            string.foldRight((List.empty[String], "")) { case (s, (acc, curr)) =>
                val newCurr = s"$s$curr"
                val newAcc  = if (newCurr != string) then acc :+ newCurr else acc
                (newAcc, newCurr)
            }
        go()._1

    case class PrefixSuffix(prefix: String, suffix: String)

    def toSuffixesPrefixes(string: String) =
        val p = prefixes(string)
        val s = suffixes(string).reverse
        p.zip(s).map(PrefixSuffix.apply)

    def stopAt[F[_]](name: String): Pipe[F, String, String] = inputStream =>
        val parts = toSuffixesPrefixes(name)
        def go(
            stream: Stream[F, String],
            partialMatchMissingPart: Option[PrefixSuffix]
        ): Pull[F, String, Unit] =
            stream.pull.uncons1.flatMap {
                case None                  => Pull.done
                case Some((v, streamTail)) =>
                    partialMatchMissingPart match
                        case None     =>
                            if v == name then Pull.done
                            else if v.contains(name) then
                                val beforeMatch = v.split(name)(0)
                                Pull.output1(beforeMatch) >> Pull.done
                            else
                                val matchIndexMaybe =
                                    parts
                                        .find(ps => v.endsWith(ps.prefix))
                                val newOutput       =
                                    matchIndexMaybe.fold(v)(ps => v.dropRight(ps.prefix.length))
                                Pull.output1(newOutput) >> go(streamTail, matchIndexMaybe)
                        case Some(ps) =>
                            if v.startsWith(ps.suffix) then Pull.done
                            else Pull.output1(ps.suffix) >> go(stream, None)
            }
        go(inputStream, None).stream
