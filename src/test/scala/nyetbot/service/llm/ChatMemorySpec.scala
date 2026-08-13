package nyetbot.service.llm

import cats.effect.IO
import cats.syntax.all.*
import munit.CatsEffectSuite
import nyetbot.config.LlmFunctionalityConfig
import nyetbot.model.LlmContextMessage
import nyetbot.model.ProfileModels.*

class ChatMemorySpec extends CatsEffectSuite:

    private val config = LlmFunctionalityConfig(
      botName = "NYETBOT",
      botAlias = "@nyetterbot",
      messageEvery = 150,
      chatBufferSize = 5,
      replyContextWindow = 3,
      recentUserMessages = 2
    )

    private def userMsg(id: Long, text: String): LlmContextMessage =
        LlmContextMessage(Some(UserId(id)), s"user$id", text)

    test("chat buffer is capped at chatBufferSize") {
        val wideWindow = config.copy(replyContextWindow = 100)
        for
            memory <- ChatMemory(wideWindow)
            _      <- (1 to 7).toList.traverse_(i => memory.ingest(userMsg(1L, s"m$i")))
            ctx    <- memory.replyContext
        yield assertEquals(ctx.map(_.text), List("m3", "m4", "m5", "m6", "m7"))
    }

    test("replyContext returns the last replyContextWindow messages") {
        for
            memory <- ChatMemory(config)
            _      <- (1 to 5).toList.traverse_(i => memory.ingest(userMsg(1L, s"m$i")))
            ctx    <- memory.replyContext
        yield assertEquals(ctx.map(_.text), List("m3", "m4", "m5"))
    }

    test("per-user buffers are capped and separated by user") {
        for
            memory <- ChatMemory(config)
            _      <- memory.ingest(userMsg(1L, "a1"))
            _      <- memory.ingest(userMsg(2L, "b1"))
            _      <- memory.ingest(userMsg(1L, "a2"))
            _      <- memory.ingest(userMsg(1L, "a3"))
            userA  <- memory.recentUser(UserId(1L))
            userB  <- memory.recentUser(UserId(2L))
            userC  <- memory.recentUser(UserId(3L))
        yield
            assertEquals(userA.map(_.text), List("a2", "a3"))
            assertEquals(userB.map(_.text), List("b1"))
            assertEquals(userC, Nil)
    }

    test("bot reply without userId lands in chat context but no per-user buffer") {
        for
            memory <- ChatMemory(config)
            _      <- memory.ingest(userMsg(1L, "question"))
            _      <- memory.ingest(LlmContextMessage(None, "NYETBOT", "answer"))
            ctx    <- memory.replyContext
            userA  <- memory.recentUser(UserId(1L))
        yield
            assertEquals(ctx.map(_.text), List("question", "answer"))
            assertEquals(userA.map(_.text), List("question"))
    }
