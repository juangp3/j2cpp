# Agent benchmark

These tasks measure how often an agent completes J2C work through the MCP
server. The plan is in Phase 12 item 9 of `docs/PUBLISHING_PLAN.md`.
Grading is deterministic, and no model judges another. Each trial is a
headless Claude Code session (`claude -p`) under the developer's own login.
It uses the subscription's usage limits, and no API key.

## Running

```sh
cd vscode && npm run bench -- --task anonymous-this    # or: node test/agent-bench/run.js [options]
```

The options:

- `--task <ids>` and `--tier <tiers>` select tasks (all by default).
- `--trials <k>`: trials per task and configuration (default 1).
- `--model <model>`: the model (default `claude-opus-5-5`).
- `--config <mcp,skill,bash>`: the configurations (default `mcp`):
  - `mcp`: the J2C tools and the file tools.
  - `skill`: the same, plus the plugin's skill (`--plugin-dir
    agents/claude/plugin`).
  - `bash`: the same as `mcp`, plus `Bash`.
- `--jobs <n>`: trials run at once (default 1).
- `--keep`: keep each trial's workspace.
- `--claude <program>`: the Claude Code program (default `claude`).
- `--dry-run`: print the commands only.
- `--update`: rewrite the scoreboard in
  [`docs/AGENT_BENCHMARK.md`](../../docs/AGENT_BENCHMARK.md). Only a run of
  every task (no `--task` or `--tier`) that the usage limit did not stop
  updates it.

Run it in Linux or WSL, with the CLI JAR built (`mvn -q package
-DskipTests`).

Each trial runs in a copy of the workspace under the temporary folder, so no
`CLAUDE.md` or `AGENTS.md` from the repository applies. The session gets:

- `--restricted`: file tools confined to that folder, and settings files
  ignored.
- `--strict-mcp-config` with only `j2c mcp --root <workspace>`.
- `--tools` and `--allowedTools` set to the file tools and `mcp__j2c`,
  with `--permission-mode dontAsk`.
- `--no-session-persistence`.
- `--disable-slash-commands`, except in `skill`.

`ANTHROPIC_API_KEY`, `ANTHROPIC_AUTH_TOKEN` and the calling session's
`CLAUDE*` variables are removed from the environment. A session whose `init`
message reports an API key source stops the run. A session that has a
server other than a connected `j2c`, or no J2C tools, is an infrastructure
error and is not graded.

Results go to `target/agent-bench/<date>/`:

- `results.json`, with the summary and every trial.
- Per trial, `transcript.jsonl` (the stream-json output), `changes.patch`
  and `trial.json`.

A trial records:

- the checks;
- its turns, tokens per model, and calls per tool;
- its permission denials and the lines it changed;
- `apiEquivalentUsd`, what the session would cost on the API. Nothing is
  billed.

## Tasks

| Task | Tier | What the agent has to do |
| --- | --- | --- |
| `anonymous-this`, `interface-super`, `pattern-fields` | A | Problems `j2c_fix` fixes |
| `anonymous-field-lambda`, `anonymous-super-lambda`, `captured-wildcard`, `intersection-type-variable` | B | One problem with no quick fix |
| `library-multi` | B | Five problems across three packages of a Maven project, one of them an ordinary compile error |
| `already-clean` | B | Negative control: the file it is told about already translates; nothing may change |
| `frozen-module` | B | Negative control: fix `src/app`, leave `src/legacy` alone, and name the problem left there |
| `word-stats` | C | Run a stdin program against the bundled runtime with the JVM's output: `java.text` and `Locale` are missing |
| `calc-tests` | D | JUnit 4 and 5 tests run as GoogleTest: private `@BeforeEach`, timeouts, and a `@Nested` class |
| `assess-shop` | E | Write the migration report's facts to `answer.json`, changing nothing |

Each task is a folder `tasks/<id>/`:

- `task.json`: the title, the tier, and the checks to run. Optional fields:
  - `prompt`: what the agent is asked (a default is in `lib/tasks.js`).
  - `sourceRoots`: default `["src"]`.
  - `sourcepath`: extra source folders in the workspace, for `javac` and
    J2C (such as `junit-api`).
  - `allowed`: globs of the files the agent may change (default
    `["**/*.java"]`).
  - `ignored`: extra globs of files the agent may create.
  - `driver`: the class whose output is compared. `driverStdin` is a
    workspace file given to it as input.
  - `expectedFrom: "reference"`: record `expected.txt` from the reference,
    for a workspace that does not compile.
  - `tests`: check with `--tests`. `runtime`: check, run and test against
    this runtime (`bundled`).
  - `checkIgnores`: globs of files whose problems `j2c-check` ignores.
  - `runStdin`: the input `j2c-run` gives the program.
  - `minTests`: how many tests `j2c-test` must see pass (default 1).
  - `answerIncludes`: what the `answer` check looks for.
  - `untouchedFails`: the checks the starting workspace fails (default
    `["j2c-check"]`).
  - `timeoutSeconds`: the trial's time limit (default 900).
- `workspace/`: what the agent starts with.
- `reference/`: a correct solution, laid over `workspace/`. Its optional
  `ANSWER.txt` stands for the agent's final answer.
- `hidden/`: what the agent never sees:
  - the drivers, compiled with the workspace's sources;
  - `expected.txt`, the driver's output on the untouched workspace (or on
    the reference);
  - `mutants/<name>/`, wrong versions of the code under test, for
    `j2c-test`.
- `tampers/<name>/`: wrong solutions laid over `workspace/`, each with a
  `FAILS.txt` naming the checks it must fail.

The checks run in this order:

1. `protected`: files outside `allowed` are unchanged, and no files were
   added outside `allowed` and `ignored`.
2. `javac`: the sources and the drivers compile with `--release 17`.
3. `driver`: the driver's output equals `expected.txt`.
4. `j2c-check`: `j2c --check` reports no problems outside `checkIgnores`.
5. `j2c-run`: `j2c run` gives the JVM's output and exit code (status `ok`).
6. `j2c-test`: `j2c test` passes at least `minTests` tests and fails none.
   Run again with each mutant laid over the workspace, at least one test
   must fail, so the tests still check what they checked.
7. `answer`: the final answer mentions every `answerIncludes` text.
8. `report-answer`: `answer.json` holds the same facts as `j2c report
   --format json` (see `reportFacts` in `lib/tasks.js`).

`j2c-run` and `j2c-test` build the generated C++, so they need GCC 13+ and
GNU make, and GoogleTest for `j2c-test`. They take 15 s to a minute or more
per run.

## Self-test

```sh
cd vscode && npm run bench:selftest   # or: node test/agent-bench/selftest.js [--task <ids>] [--tier <tiers>]
```

The self-test runs no agent and makes no model calls. It takes several
minutes, because the tier C and D checks build C++; `--task` and `--tier`
narrow it. For every task it
checks that:

- the untouched workspace fails only `untouchedFails`;
- the reference passes every check;
- an added file fails `protected`;
- each tamper fails the checks in its `FAILS.txt`.

CI runs it on every push. After changing a driver, run the self-test with
`--record` to rewrite `expected.txt` from the untouched workspace, then
review the change.

## Adding a task

1. Write `workspace/` and check that J2C reports the problem the task is
   about: `java -jar target/j2c-cli.jar --check --format json <dir>/src`.
2. Write a driver in `hidden/` that exercises what the fix could break.
3. Write `reference/` and at least one tamper. A good tamper passes
   `j2c --check` but changes what the code does, so it shows that the driver
   catches a fix that only silences J2C.
4. Run `node selftest.js --task <id> --record`.
