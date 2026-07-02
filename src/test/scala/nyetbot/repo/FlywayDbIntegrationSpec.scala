package nyetbot.repo

import cats.effect.IO
import cats.effect.Resource
import fly4s.Fly4s
import fly4s.data.Fly4sConfig
import fly4s.data.Locations
import io.circe.Json
import io.github.iltotore.iron.*
import io.github.iltotore.iron.constraint.all.*
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import munit.CatsEffectSuite
import nyetbot.model.*
import org.typelevel.otel4s.metrics.Meter
import org.typelevel.otel4s.trace.Tracer
import skunk.*
import skunk.circe.codec.json.json
import skunk.codec.all.*
import skunk.implicits.*

class FlywayDbIntegrationSpec extends CatsEffectSuite:

    given Tracer[IO] = Tracer.noop
    given Meter[IO]  = Meter.noop

    private def runMigrations(port: Int): IO[Unit] =
        Fly4s
            .make[IO](
              url = s"jdbc:postgresql://localhost:$port/postgres",
              user = Some("postgres"),
              password = Some("postgres".toArray),
              config = Fly4sConfig(table = "flyway", locations = Locations(List("db")))
            )
            .use(_.migrate.void)

    private val embeddedDb: Resource[IO, Session[IO]] =
        for
            pg      <- Resource.fromAutoCloseable(IO.blocking(EmbeddedPostgres.builder().start()))
            _       <- Resource.eval(runMigrations(pg.getPort))
            session <- Session
                           .Builder[IO]
                           .withHost("localhost")
                           .withPort(pg.getPort)
                           .withUserAndPassword("postgres", "postgres")
                           .withDatabase("postgres")
                           .withSSL(SSL.None)
                           .single
        yield session

    private val db             = ResourceSuiteLocalFixture("embedded-pg", embeddedDb)
    override def munitFixtures = List(db)

    test("all migrations apply and V1_3 seeds the ten swears") {
        SwearRepoImpl(db()).getSwears.map { swears =>
            assertEquals(swears.size, 10)
            assert(swears.map(_.swear.value).contains("nyet"))
            assertEquals(swears.head.groupChance.value, 300)
        }
    }

    test("user_profile (V1_5) round-trips through ProfileRepoDB") {
        val repo = ProfileRepoDB(db())
        for
            _   <- repo.upsertProfile(
                     UserId(42L),
                     DisplayName("Гоша"),
                     ProfileDescription("любит казино")
                   )
            got <- repo.getProfile(UserId(42L))
        yield
            assertEquals(got.map(_.displayName.value), Some("Гоша"))
            assertEquals(got.map(p => p.description.value: String), Some("любит казино"))
    }

    test("memes (V1_0/V1_1) round-trip a json body through the skunk-circe codec") {
        val session = db()
        val body    =
            Json.obj("type" -> Json.fromString("sticker"), "fileId" -> Json.fromString("abc"))
        val insert  =
            sql"insert into memes (trigger, body, chance) values ($text, $json, $int4)".command
        for
            _    <- session.prepareR(insert).use(_.execute(("%cat%", body, 3)))
            rows <- MemeRepoDB(session).getAllMemes
        yield
            assertEquals(rows.size, 1)
            assertEquals(rows.head.body, body)
            assertEquals(rows.head.chance.value, 3)
    }
