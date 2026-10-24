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

Each task is a folder `tasks/<id>/`:

- `task.json`: the title, the tier, the checks to run, and the driver class.
  Optional fields:
  - `prompt` (a default prompt is in `lib/tasks.js`).
  - `sourceRoots` (default `["src"]`).
  - `allowed`: globs of the files the agent may change (default
    `["**/*.java"]`).
  - `ignored`: extra globs of files the agent may create.
  - `tests`, `checkSourcepath` and `compileSourcepath`, for JUnit tasks.
  - `answerIncludes`, for the `answer` check.
  - `untouchedFails`: the checks the starting workspace fails (default
    `["j2c-check"]`).
  - `timeoutSeconds`.
- `workspace/`: what the agent starts with.
- `reference/`: a correct solution, laid over `workspace/`. Its optional
  `ANSWER.txt` stands for the agent's final answer.
- `hidden/`: the drivers, compiled with the workspace's sources, and
  `expected.txt`, the output of the driver named in `task.json` on the
  untouched workspace. The agent never sees this folder.
- `tampers/<name>/`: wrong solutions laid over `workspace/`, each with a
  `FAILS.txt` naming the checks it must fail.

The checks run in this order:

1. `protected`: files outside `allowed` are unchanged, and no files were
   added outside `allowed` and `ignored`.
2. `javac`: the sources and the drivers compile with `--release 17`.
3. `driver`: the driver's output equals `expected.txt`.
4. `j2c-check`: `j2c --check` reports no problems.
5. `answer`: the final answer mentions every `answerIncludes` text.

## Self-test

```sh
cd vscode && npm run bench:selftest   # or: node test/agent-bench/selftest.js [--task <ids>] [--tier <tiers>]
```

The self-test runs no agent and makes no model calls. For every task it
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
