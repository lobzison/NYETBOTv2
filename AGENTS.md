# NYETBOTv2

A Telegram chat bot written in Scala 3 on cats-effect. It lives in a single chat
and reacts to messages with three independent features:

- **Memes** — trigger and manage stored memes (`MemeFunctionality`, `MemeService`, `MemeRepo`).
- **Swears** — weighted swear responses (`SwearFunctionality`, `SwearService`, `SwearRepo`).
- **LLM replies** — the main feature: a "schizo persona" that answers **natively in Russian**
  via a local **Ollama** model. It keeps a per-user behavioural profile in Postgres, summarises
  the target's recent messages, classifies whether an @mention continues the current thread or
  asks a new question, replies in-character, then rewrites and persists the profile.

## Stack

- **Scala 3.8.4** on **sbt 2**, cats-effect 3 (`IO`, `Resource`, `Ref`, `Mutex`)
- **canoe** — Telegram bot framework (forked, pinned; do not bump)
- **skunk** — Postgres access via **raw SQL** (no query DSL)
- **Iron** — refined types (`io.github.iltotore.iron`) for the domain model in `model/`; domain
  values are validated at construction (see **Domain modelling** below)
- **fly4s / Flyway** — DB migrations in `src/main/resources/db`
- **http4s + blaze client** — talks to Ollama's `/api/generate`
- **Typesafe Config** — tunables in `application.conf`; secrets from the environment
- **munit + munit-cats-effect** — unit tests (pure/stubbed). Integration tests
  (`FlywayDbIntegrationSpec`) run against a real embedded Postgres via **Zonky embedded-postgres**
- **sbt-pack** for packaging; **sbt-tpolecat** (`-Werror`), **sbt-scalafix**, **sbt-scalafmt** for tooling

## Layout

Code is organised in layers under `src/main/scala/nyetbot/`:

- `repo/` — database access (skunk, raw SQL) + in-memory fakes for tests
- `service/` — business logic: `LlmService`/`OllamaService`, `OllamaPrompts`, `ProfileService`,
  `MemeService`, `SwearService`, `HealthServer`, `HeartbeatService`
- `functionality/` — canoe `Scenario` wiring that connects Telegram events to services
- `model/` — domain models
- `util/` — small helpers (e.g. surrogate-safe `Text.truncate`)

`Main` (an `IOApp.Simple`) wires the dependencies (Telegram client, config, Flyway, skunk
`Session`, http4s client), runs migrations, builds the scenarios, starts a health server plus
heartbeat, and auto-restarts on non-fatal errors.

## Domain modelling

Domain values are **typed, never raw primitives**. Two complementary techniques live in `model/`:

- **Iron refined types** — `RefinedType[Base, Constraint]` bakes an invariant into the type so an
  invalid value is unrepresentable:
  - `Chance` / `Weight` = `RefinedType[Int, Positive]`
  - `Swear` = `RefinedType[String, Not[Empty]]`
  - `ProfileDescription` = `RefinedType[String, MaxLength[300]]`

  Construct through the companion: `X.either(v)` for runtime input that can fail, `X(v)` for
  known-valid literals. Once built, the type carries the guarantee — don't re-validate downstream.
- **Opaque-type newtypes** (plain Scala 3) wrap identifiers so they can't be transposed:
  `MemeId`, `SwearId`, `UserId`, `SwearGroupId`, `DisplayName`, `MemeTrigger`.

When adding a domain value, prefer one of these over a bare `Int`/`String`/`Long` — reach for an
Iron refined type whenever the value has an invariant (non-empty, positive, bounded length, …),
and a newtype whenever a primitive is really an identifier.

## Configuration

Secrets come from the environment:

- `NYETBOT_KEY` — Telegram bot token
- `DATABASE_URL` — Postgres connection string
- `OLLAMA_DOMAIN` — Ollama host
- `LLM_MESSAGE_EVERY` — optional override for the random-reply frequency

All non-secret tunables live in `src/main/resources/application.conf` and can be overridden at
deploy time with `-Dconfig.file=/path/to/application.conf`.

## Working on the code

Every change **must compile, pass tests, and be formatted** before it is committed. Unit tests
are pure/stubbed (no Ollama or external DB). The integration tests (`FlywayDbIntegrationSpec`)
spin up a real embedded Postgres in-process — no Docker required, but the first run downloads a
Postgres binary. `-Werror` is on (via sbt-tpolecat), so any warning fails the build.

This is an **sbt 2** build; two things differ from sbt 1:
- Chain multiple commands with semicolons inside one quoted string, not as separate arguments.
- `sbt test` is **incremental** — it skips suites whose inputs are unchanged. Use `sbt "testOnly *"`
  (or clear `~/.cache/sbt`) to force the whole suite.

```sh
sbt "testOnly *"                              # run every test (unit + integration)
sbt "scalafmt; scalafmtSbt; Test/scalafmt"    # format main, *.sbt/project, and test sources
sbt "scalafmtCheckAll; scalafmtSbtCheck"      # check formatting only (e.g. CI)
```

The formatting config is `.scalafmt.conf` (4-space indent, `align.preset = most`,
`maxColumn = 100`, Scala 3 dialect) — do not change it as part of an unrelated change.
