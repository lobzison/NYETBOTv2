# NYETBOTv2

A Telegram chat bot that lives in a single chat and reacts to messages with independent
features:

- Memes — posts memes based on a trigger
- Swears — swears based on a pure chance
- LLM replies — the main feature: a "schizo persona" that generates a response via a local
  **Ollama** model.

## Stack

- Sclala 3, sbt
- fork of canoe — Telegram bot framework (forked, git submodule in this repo,
  rebuilt on each deploy from the submodule commit)
- skunk — Postgres access via raw SQL
- Iron — refined types for the domain model
- fly4s / Flyway — DB migrations in `src/main/resources/db`
- http4s + blaze client
- Pureconfig — ???
- munit + munit-cats-effect — unit tests (pure/stubbed). Integration tests
  (`FlywayDbIntegrationSpec`) run against a real embedded Postgres via **Zonky embedded-postgres**
- sbt-pack for packaging; sbt-tpolecat (`-Werror`), sbt-scalafix, sbt-scalafmt for tooling

## Layout

Code is organised in layers under `src/main/scala/nyetbot/`:

- `repo/` — database access (skunk, raw SQL) + in-memory fakes for tests
- `service/` — business logic
- `functionality/` — canoe `Scenario` wiring that connects Telegram events to services
- `model/` — domain models
- `util/` — small helpers (e.g. surrogate-safe `Text.truncate`)
- `config/` 

`Main`  wires the dependencies (Telegram client, config, Flyway, skunk
`Session`, http4s client), runs migrations, builds the scenarios, starts a health server plus
heartbeat, and auto-restarts on non-fatal errors.

## Domain modelling

Domain values are **typed, never raw primitives**. Two complementary techniques live in `model/`:

- Iron refined types — `RefinedType[Base, Constraint]` bakes an invariant into the type so an
  invalid value is unrepresentable:
  - `Chance` / `Weight` = `RefinedType[Int, Positive]`
  - `Swear` = `RefinedType[String, Not[Empty]]`
  - `ProfileDescription` = `RefinedType[String, MaxLength[300]]`

  Construct through the companion: `X.either(v)` for runtime input that can fail, `X(v)` for
  known-valid literals.

  // This is BS, I should mirate to refined, it allows newtypes
- **Opaque-type newtypes** (plain Scala 3) wrap identifiers so they can't be transposed:
  `MemeId`, `SwearId`, `UserId`, `SwearGroupId`, `DisplayName`, `MemeTrigger`.

When adding a domain value, use one of these over a bare `Int`/`String`/`Long`

## Configuration

Config lives in `src/main/resources/application.conf`.

## Working on the code

Every change must compile, pass tests, and be formatted before it is committed. Unit tests
are pure/stubbed (no Ollama or external DB). The integration tests spin up a real embedded
Postgres in-process — no Docker required, but the first run downloads a Postgres binary.
`-Werror` is on, so any warning fails the build.

This is an sbt 2 build; two things differ from sbt 1:
- Chain multiple commands with semicolons inside one quoted string, not as separate arguments.

```sh
sbt test
sbt "scalafmt; scalafmtSbt; Test/scalafmt"
sbt "scalafmtCheckAll; scalafmtSbtCheck"
```

The formatting config is `.scalafmt.conf` — do not change it as part of an unrelated change.

**Never** write any comments
**Never** commit changes unless explicitly asked to
