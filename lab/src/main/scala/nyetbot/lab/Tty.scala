package nyetbot.lab

import cats.effect.IO
import cats.effect.Resource

import java.io.FileInputStream
import java.nio.charset.StandardCharsets

object Tty:

    val session: Resource[IO, IO[Char]] =
        for
            in <- Resource.fromAutoCloseable(IO.blocking(new FileInputStream("/dev/tty")))
            _  <- Resource.make(saveAndRaw)(saved => sh(s"stty '$saved' < /dev/tty").void)
        yield readChar(in)

    private val saveAndRaw: IO[String] =
        sh("stty -g < /dev/tty").flatTap(_ => sh("stty -icanon -echo < /dev/tty"))

    private def readChar(in: FileInputStream): IO[Char] =
        IO.blocking(in.read()).flatMap {
            case -1 => IO.raiseError(new RuntimeException("/dev/tty reached end of input"))
            case c  => IO.pure(c.toChar)
        }

    private def sh(command: String): IO[String] =
        IO.blocking {
            val process = new ProcessBuilder("sh", "-c", command).start()
            val output  =
                new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8).trim
            val error   =
                new String(process.getErrorStream.readAllBytes(), StandardCharsets.UTF_8).trim
            if process.waitFor() != 0 then throw new RuntimeException(s"'$command' failed: $error")
            output
        }
