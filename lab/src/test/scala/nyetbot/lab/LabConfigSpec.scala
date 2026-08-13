package nyetbot.lab

import cats.effect.IO
import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Path
import munit.CatsEffectSuite

class LabConfigSpec extends CatsEffectSuite:

    private def write(path: Path, content: String): IO[Unit] =
        Stream
            .emit(content)
            .through(fs2.text.utf8.encode)
            .through(Files[IO].writeAll(path))
            .compile
            .drain

    test("scenario file including lab-base.conf loads with overrides applied") {
        Files[IO].tempDirectory.use { dir =>
            val file = dir / "hot-temp.conf"
            for
                _      <- write(
                            file,
                            """include classpath("lab-base.conf")
                                |nyetbot.ollama.reply.model-config.temperature = 1.2
                                |""".stripMargin
                          )
                loaded <- LabConfig.load(file)
            yield
                val cfg = loaded.fold(err => fail(err), identity)
                assertEquals(cfg.llm.botName, "NYETBOT")
                assertEquals(cfg.llm.botAlias, "@nyetterbot")
                assertEquals(cfg.llm.chatBufferSize, 200)
                assertEquals(cfg.llm.replyContextWindow, 20)
                assertEquals(cfg.llm.recentUserMessages, 50)
                assertEquals(cfg.replyLength.minChars, 150)
                assertEquals(cfg.replyLength.maxChars, 600)
                assertEquals(cfg.ollama.domain, sys.env.getOrElse("OLLAMA_DOMAIN", "localhost"))
                assertEquals(cfg.ollama.port, 11434)
                assertEquals(cfg.ollama.reply.modelConfig.temperature, Some(1.2))
                assertEquals(cfg.ollama.reply.modelConfig.model, "gemma4:e4b")
                assertEquals(cfg.ollama.reply.modelConfig.numPredict, Some(512))
                assertEquals(cfg.ollama.context.dossier.summaryMaxChars, 500)
                assertEquals(cfg.ollama.context.topic.contextWindow, 10)
                assertEquals(cfg.ollama.profileRewrite.profileMaxChars, 300)
                assertEquals(cfg.ollama.context.chatLog.enabled, true)
        }
    }

    test("a scenario file missing required keys loads as an error value") {
        Files[IO].tempDirectory.use { dir =>
            val file = dir / "broken.conf"
            for
                _      <- write(file, "nyetbot.llm.bot-name = \"X\"\n")
                loaded <- LabConfig.load(file)
            yield loaded match
                case Left(err) => assert(err.startsWith(s"cannot load scenario $file"), err)
                case Right(c)  => fail(s"expected an error, got $c")
        }
    }
