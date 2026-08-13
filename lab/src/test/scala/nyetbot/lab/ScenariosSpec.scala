package nyetbot.lab

import cats.effect.IO
import fs2.io.file.Path
import munit.CatsEffectSuite

import java.nio.file.Files as JFiles
import java.nio.file.Paths

class ScenariosSpec extends CatsEffectSuite:

    private val scenariosDir: Path =
        val start = Paths.get("").toAbsolutePath
        Iterator
            .iterate(start)(_.getParent)
            .takeWhile(_ != null)
            .map(_.resolve("lab").resolve("scenarios"))
            .find(JFiles.isDirectory(_))
            .map(Path.fromNioPath)
            .getOrElse(fail(s"lab/scenarios not found walking up from $start"))

    private def load(name: String): IO[LabConfig] =
        LabConfig.load(scenariosDir / name).map(_.fold(err => fail(err), identity))

    test("baseline.conf loads the base config values") {
        load("baseline.conf").map { cfg =>
            assertEquals(cfg.llm.botName, "NYETBOT")
            assertEquals(cfg.llm.botAlias, "@nyetterbot")
            assertEquals(cfg.llm.chatBufferSize, 200)
            assertEquals(cfg.llm.replyContextWindow, 20)
            assertEquals(cfg.llm.recentUserMessages, 50)
            assertEquals(cfg.ollama.port, 11434)
            assertEquals(cfg.ollama.reply.modelConfig.temperature, Some(0.85))
            assertEquals(cfg.ollama.context.dossier.enabled, true)
        }
    }

    test("no-dossier.conf flips the dossier flag and nothing else") {
        for
            base <- load("baseline.conf")
            cfg  <- load("no-dossier.conf")
        yield
            assertEquals(cfg.ollama.context.dossier.enabled, false)
            val restored = cfg.copy(ollama =
                cfg.ollama.copy(context =
                    cfg.ollama.context
                        .copy(dossier = cfg.ollama.context.dossier.copy(enabled = true))
                )
            )
            assertEquals(restored, base)
    }

    test("hot-temp.conf raises the reply temperature and nothing else") {
        for
            base <- load("baseline.conf")
            cfg  <- load("hot-temp.conf")
        yield
            assertEquals(cfg.ollama.reply.modelConfig.temperature, Some(1.2))
            val restored = cfg.copy(ollama =
                cfg.ollama.copy(reply =
                    cfg.ollama.reply.copy(modelConfig =
                        cfg.ollama.reply.modelConfig
                            .copy(temperature = base.ollama.reply.modelConfig.temperature)
                    )
                )
            )
            assertEquals(restored, base)
    }
