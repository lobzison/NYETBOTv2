package nyetbot.config

import com.typesafe.config.ConfigFactory
import munit.FunSuite

class ConfigSpec extends FunSuite:

    test("application.conf exposes all nyetbot tunables with expected defaults") {
        val root = ConfigFactory.load().getConfig("nyetbot")
        assertEquals(root.getInt("llm.message-every"), 150)
        assertEquals(root.getInt("llm.profile-max-chars"), 300)
        assertEquals(root.getInt("llm.topic-context-window"), 10)
        assertEquals(root.getInt("llm.reply.min-chars"), 150)
        assertEquals(root.getInt("llm.reply.max-chars"), 600)
        assertEquals(root.getString("ollama.reply-model"), "NYETBOTv1")
        assertEquals(root.getString("ollama.utility-model"), "gemma4:e4b")
        assertEquals(root.getDouble("ollama.reply-temperature"), 0.85)
        assertEquals(root.getDouble("ollama.utility-temperature"), 0.2)
        assertEquals(root.getInt("ollama.topic-num-predict"), 160)
        assertEquals(root.getInt("ollama.register-num-predict"), 6)
    }

    test("buildDbConfig parses a postgres URL into its parts") {
        val db = Config.buildDbConfig("postgres://user:pass@host:5432/mydb")
        assertEquals(db.dbHost, "host")
        assertEquals(db.dbPort, 5432)
        assertEquals(db.dbName, "mydb")
        assertEquals(db.dbUser, "user")
        assertEquals(db.dbPassword, "pass")
        assert(db.jdbcUrl.contains("jdbc:postgresql://host:5432/mydb"))
    }
