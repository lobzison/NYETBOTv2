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

- **Scala 3.8.3**, cats-effect 3 (`IO`, `Resource`, `Ref`, `Mutex`)
- **canoe** — Telegram bot framework (forked, pinned; do not bump)
- **skunk** — Postgres access via **raw SQL** (no query DSL)
- **fly4s / Flyway** — DB migrations in `src/main/resources/db`
- **http4s + blaze client** — talks to Ollama's `/api/generate`
- **Typesafe Config** — tunables in `application.conf`; secrets from the environment
- **munit + munit-cats-effect** — tests (pure/stubbed, no live Postgres or Ollama)
- **sbt-pack** for packaging; **sbt-tpolecat**, **sbt-scalafix**, **sbt-scalafmt** for tooling

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

## Configuration

Secrets come from the environment:

- `NYETBOT_KEY` — Telegram bot token
- `DATABASE_URL` — Postgres connection string
- `OLLAMA_DOMAIN` — Ollama host
- `LLM_MESSAGE_EVERY` — optional override for the random-reply frequency

All non-secret tunables live in `src/main/resources/application.conf` and can be overridden at
deploy time with `-Dconfig.file=/path/to/application.conf`.

## Working on the code

Every change **must compile, pass tests, and be formatted** before it is committed. Tests are
pure/stubbed, so no database or Ollama instance is required to run them.

```sh
sbt test                                  # compiles main + test sources and runs the suite
sbt scalafmt scalafmtSbt Test/scalafmt    # format main sources, *.sbt / project files, and test sources
```

To only check formatting without rewriting files (e.g. in CI), use
`sbt scalafmtCheckAll scalafmtSbtCheck`.

The formatting config is `.scalafmt.conf` (4-space indent, `align.preset = most`,
`maxColumn = 100`, Scala 3 dialect) — do not change it as part of an unrelated change.
