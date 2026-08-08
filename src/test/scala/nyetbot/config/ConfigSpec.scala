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
        assertEquals(root.llm.recentUserMessages, 50)
        assertEquals(root.profileService.topicContextWindow, 10)
        assertEquals(root.profileService.minChars, 150)
        assertEquals(root.profileService.meanFactor, 1.5)
        assertEquals(root.profileService.spread, 0.3)
        assertEquals(root.profileService.maxChars, 600)
        assertEquals(root.ollama.port, 11434)
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
        assertEquals(root.ollama.summarizeThread.modelConfig.model, "gemma4:e4b")
        assertEquals(root.ollama.summarizeThread.modelConfig.temperature, Some(0.2))
        assertEquals(root.ollama.summarizeThread.modelConfig.numPredict, Some(160))
        assertEquals(root.ollama.summarizeThread.modelConfig.numCtx, Some(8192))
        assertEquals(root.ollama.summarizeThread.modelConfig.think, Some(false))
        assertEquals(root.ollama.classifyIntent.modelConfig.model, "gemma4:e4b")
        assertEquals(root.ollama.classifyIntent.modelConfig.temperature, Some(0.2))
        assertEquals(root.ollama.classifyIntent.modelConfig.numPredict, Some(4))
        assertEquals(root.ollama.classifyIntent.modelConfig.numCtx, Some(8192))
        assertEquals(root.ollama.classifyIntent.modelConfig.think, Some(false))
        assertEquals(root.ollama.summarizeUser.modelConfig.model, "gemma4:e4b")
        assertEquals(root.ollama.summarizeUser.modelConfig.temperature, Some(0.2))
        assertEquals(root.ollama.summarizeUser.modelConfig.numPredict, Some(256))
        assertEquals(root.ollama.summarizeUser.profileMaxChars, 300)
        assertEquals(root.ollama.summarizeUser.summaryMaxChars, 500)
        assertEquals(root.ollama.summarizeUser.rewriteModelConfig.model, "gemma4:e4b")
        assertEquals(root.ollama.summarizeUser.rewriteModelConfig.numPredict, Some(200))
        assertEquals(root.ollama.summarizeUser.modelConfig.numCtx, Some(8192))
        assertEquals(root.ollama.summarizeUser.modelConfig.think, Some(false))
        assertEquals(root.ollama.classifyRegister.modelConfig.model, "gemma4:e4b")
        assertEquals(root.ollama.classifyRegister.modelConfig.temperature, Some(0.2))
        assertEquals(root.ollama.classifyRegister.modelConfig.numPredict, Some(6))
        assertEquals(root.ollama.classifyRegister.modelConfig.numCtx, Some(8192))
        assertEquals(root.ollama.classifyRegister.modelConfig.think, Some(false))
        assertEquals(root.ollama.reply.modelConfig.numCtx, Some(8192))
        assertEquals(root.ollama.reply.modelConfig.think, Some(false))
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
