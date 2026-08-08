package nyetbot.config

import munit.FunSuite
import pureconfig.ConfigSource

class ConfigSpec extends FunSuite:

    test("application.conf exposes all nyetbot tunables with expected defaults") {
        val root = ConfigSource.default.at("nyetbot").loadOrThrow[Config.RawConfig]
        assertEquals(root.llm.botName, "NYETBOT")
        assertEquals(root.llm.botAlias, "@nyetterbot")
        assertEquals(root.llm.userPrefix, "")
        assertEquals(root.llm.inputPrefix, ": ")
        assertEquals(root.llm.messageEvery, 150)
        assertEquals(root.llm.chatBufferSize, 200)
        assertEquals(root.llm.replyContextWindow, 20)
        assertEquals(root.llm.topicContextWindow, 10)
        assertEquals(root.llm.recentUserMessages, 50)
        assertEquals(root.llm.profileMaxChars, 300)
        assertEquals(root.llm.summaryMaxChars, 500)
        assertEquals(root.llm.reply.minChars, 150)
        assertEquals(root.llm.reply.meanFactor, 1.5)
        assertEquals(root.llm.reply.spread, 0.3)
        assertEquals(root.llm.reply.maxChars, 600)
        assertEquals(root.ollama.port, 11434)
        assertEquals(root.ollama.reply.model, "gemma4:e4b")
        assertEquals(root.ollama.utilityModel, "gemma4:e4b")
        assertEquals(root.ollama.reply.temperature, 0.85)
        assertEquals(root.ollama.reply.topP, 0.95)
        assertEquals(root.ollama.reply.topK, 40)
        assertEquals(root.ollama.reply.repeatPenalty, 1.1)
        assertEquals(root.ollama.reply.stop.size, 10)
        assert(root.ollama.reply.system.contains("Ты — NYETBOT"))
        assert(root.ollama.reply.template.contains("{{ .Prompt }}"))
        assertEquals(root.ollama.utilityTemperature, 0.2)
        assertEquals(root.ollama.reply.numPredict, 512)
        assertEquals(root.ollama.summaryNumPredict, 256)
        assertEquals(root.ollama.rewriteNumPredict, 200)
        assertEquals(root.ollama.intentNumPredict, 4)
        assertEquals(root.ollama.topicNumPredict, 160)
        assertEquals(root.ollama.registerNumPredict, 6)
        assertEquals(root.ollama.reply.numCtx, 8192)
        assertEquals(root.ollama.reply.think, false)
        assertEquals(root.ollama.requestTimeoutMinutes, 25)
        assertEquals(root.ollama.idleTimeoutMinutes, 25)
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
