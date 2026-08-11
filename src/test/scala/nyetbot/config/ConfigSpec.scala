package nyetbot.config

import munit.FunSuite
import pureconfig.ConfigSource

class ConfigSpec extends FunSuite:

    test("application.conf exposes all nyetbot tunables with expected defaults") {
        val root    = ConfigSource.default.at("nyetbot").loadOrThrow[Config.RawConfig]
        assertEquals(root.llm.enabled, true)
        assertEquals(root.llm.botName, "NYETBOT")
        assertEquals(root.llm.botAlias, "@nyetterbot")
        assertEquals(root.llm.messageEvery, sys.env.get("LLM_MESSAGE_EVERY").fold(150)(_.toInt))
        assertEquals(root.llm.chatBufferSize, 200)
        assertEquals(root.llm.replyContextWindow, 20)
        assertEquals(root.llm.recentUserMessages, 50)
        assertEquals(root.replyLength.minChars, 150)
        assertEquals(root.replyLength.meanFactor, 1.5)
        assertEquals(root.replyLength.spread, 0.3)
        assertEquals(root.replyLength.maxChars, 600)
        assertEquals(root.ollama.port, 11434)
        assertEquals(root.ollama.requestTimeoutMinutes, 25)
        assertEquals(root.ollama.idleTimeoutMinutes, 25)
        assertEquals(root.ollama.reply.modelConfig.model, "gemma4:e4b")
        assertEquals(
          root.ollama.reply.modelConfig.system.map(_.contains("Ты — NYETBOT")),
          Some(true)
        )
        assertEquals(
          root.ollama.reply.modelConfig.template.map(_.contains("{{ .Prompt }}")),
          Some(true)
        )
        assertEquals(root.ollama.reply.modelConfig.temperature, Some(0.85))
        assertEquals(root.ollama.reply.modelConfig.topP, Some(0.95))
        assertEquals(root.ollama.reply.modelConfig.topK, Some(40))
        assertEquals(root.ollama.reply.modelConfig.repeatPenalty, Some(1.1))
        assertEquals(root.ollama.reply.modelConfig.stop.map(_.size), Some(10))
        assertEquals(root.ollama.reply.modelConfig.numPredict, Some(512))
        assertEquals(root.ollama.reply.modelConfig.numCtx, Some(8192))
        assertEquals(root.ollama.reply.modelConfig.think, Some(false))
        assertEquals(root.ollama.profileRewrite.profileMaxChars, 300)
        assertEquals(root.ollama.profileRewrite.modelConfig.model, "gemma4:e4b")
        assertEquals(root.ollama.profileRewrite.modelConfig.numPredict, Some(200))
        val context = root.ollama.context
        assertEquals(context.dossier.enabled, true)
        assertEquals(context.dossier.summaryMaxChars, 500)
        assertEquals(context.dossier.modelConfig.model, "gemma4:e4b")
        assertEquals(context.dossier.modelConfig.temperature, Some(0.2))
        assertEquals(context.dossier.modelConfig.numPredict, Some(256))
        assertEquals(context.dossier.modelConfig.numCtx, Some(8192))
        assertEquals(context.dossier.modelConfig.think, Some(false))
        assertEquals(context.topic.enabled, true)
        assertEquals(context.topic.contextWindow, 10)
        assertEquals(context.topic.modelConfig.numPredict, Some(160))
        assertEquals(context.intent.enabled, true)
        assertEquals(context.intent.modelConfig.numPredict, Some(4))
        assertEquals(context.register.enabled, true)
        assertEquals(context.register.modelConfig.numPredict, Some(6))
        assertEquals(context.chatLog.enabled, true)
        assertEquals(context.replyTarget.enabled, true)
        assertEquals(context.userTrigger.enabled, true)
        assertEquals(context.date.enabled, true)
    }

    test("buildDbConfig parses a postgres URL into its parts") {
        val db = DbConfig("postgres://user:pass@host:5432/mydb")
        assertEquals(db.dbHost, "host")
        assertEquals(db.dbPort, 5432)
        assertEquals(db.dbName, "mydb")
        assertEquals(db.dbUser, "user")
        assertEquals(db.dbPassword, "pass")
        assert(db.jdbcUrl.contains("jdbc:postgresql://host:5432/mydb"))
    }
