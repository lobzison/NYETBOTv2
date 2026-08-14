# LLM Lab — agent guide

## Chat data is off-limits

The lab works on a real Telegram chat export. Its content is private. As an agent you must
NEVER look at it, in any form:

- the dump itself (`result.json` from Telegram Desktop, wherever the human keeps it)
- extracted corpus windows: `lab/corpus/**`
- replay outputs: `lab/out/*.json` — they embed real messages inside recorded prompts and
  replies

Do not read, cat, grep, tail, jq, copy, or quote these files, and do not run commands whose
output would print their content. If you need data to debug or test against, use the
checked-in fixtures in `lab/src/test/` — they exist for exactly this reason.

Safe to read: `lab/out/scores.jsonl` (filenames and scores only, no chat text),
`lab/scenarios/*.conf`, everything under `lab/src/`.

The human is responsible for providing the dump and for grading. You never grade: do not run
ScoreApp interactively and do not write records with scorer `"human"`.

## Running a full A/B test

The A/B guarantee is same inputs: one corpus, extracted once, replayed through every variant.
Generation itself is not deterministic, so never compare scores across different corpora.

### 1. Corpus (human provides the dump)

The export is made with **Telegram Desktop** — the Qt client used on Windows/Linux, also
installable on macOS under that exact name (the native macOS "Telegram" app cannot export).
Format "Machine-readable JSON", media off → produces `result.json`.
The human does this; if they ask how, point them here.

Ask the human for the dump path — treat it as an opaque string. Extract once:

```sh
sbt "lab/runMain nyetbot.lab.ExtractApp --dump <path-from-human> --out lab/corpus"
```

Extraction is seeded (`--seed 42` default): the same dump and parameters reproduce the same
corpus. Do not re-extract between variants — reuse the existing `lab/corpus`. A new dump
means a new corpus and a fresh scoring round; old scores are not comparable.

### 2. Variants

Config A/B: add a scenario per variant in `lab/scenarios/` — an include plus overrides:

```hocon
include classpath("lab-base.conf")
nyetbot.ollama.reply.model-config.temperature = 1.2
```

`baseline.conf` is variant A by convention. Scenario files contain only config — you may
create and read them freely. Guard against typos: overriding a nonexistent path is silently
ignored, so extend `ScenariosSpec` for any scenario worth keeping.

Code A/B (prompt-building or pipeline changes): scenarios can't express it — run the replay
once per code version into separate out dirs (e.g. `lab/out/a`, `lab/out/b`) on the same
corpus.

### 3. Replay

```sh
sbt "lab/runMain nyetbot.lab.ReplayApp --inputs lab/corpus --scenarios lab/scenarios --out lab/out"
```

Every window runs against every scenario, so all variants see identical inputs. Requires a
reachable Ollama; `OLLAMA_DOMAIN` must be a bare domain (port is `nyetbot.ollama.port`).
Failed runs write their output with `error` set and the batch continues — do not inspect the
outputs to check them; count files or ask the human.

### 4. Grading — the human's job

Hand over to the human:

```sh
sbt "lab/runMain nyetbot.lab.ScoreApp"
```

They read each context + reply and score 1–5 per keypress (`s` skip, `q` quit). Rerunning
resumes; `--rescore` regrades. For code A/B with separate out dirs, they run it once per dir.

### 5. Analysis

`lab/out/scores.jsonl` is the only artifact you analyze. One JSON object per line with
`output`, `window`, `scenario`, `scorer`, `scores` (dimension → number), `scale`, `scoredAt`.
Take the latest record per (output, scorer), aggregate per scenario, report the comparison.
Records with scorer other than `"human"` are reserved for future headless graders.
