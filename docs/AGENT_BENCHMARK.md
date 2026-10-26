# Agent benchmark

How often an AI agent finishes J2C work with the J2C MCP server: making code
translate without changing what it does, running a program and its tests as
C++, and assessing a codebase. The tasks, graders and runner are in
[`test/agent-bench/`](../test/agent-bench/README.md); the plan is Phase 12
item 9 of [`PUBLISHING_PLAN.md`](PUBLISHING_PLAN.md).

Each trial is a headless Claude Code session in a copy of the task's
workspace, with only the J2C tools and the file tools. Grading is
deterministic, and no model judges another:

- the files the agent may not change are unchanged;
- the code still compiles with `javac` and a hidden driver prints what it
  printed before;
- `j2c --check` reports no problems, and where the task asks for it, `j2c
  run` matches the JVM and `j2c test` passes and still catches wrong code;
- an answer names what it must (a problem left on purpose, or the facts of
  `j2c report`).

The negative controls (`already-clean`, `frozen-module`) pass only if the
agent leaves alone what needs no change.

## Scoreboard

Columns:

- **Passed**: graded trials that passed every check. A trial stopped by the
  service, such as by a usage limit, is an infrastructure error ("infra")
  and is not graded.
- **Turns** and **Output tokens**: means over the graded trials.
- **Tokens per success**: all tokens of the graded trials (input, output and
  cache) over the trials that passed.
- **API-equivalent $**: what a trial would cost on the API, to compare runs.
  Runs use a Claude subscription's login; nothing is billed.

<!-- agent-bench-scoreboard:begin -->
2026-10-26: `claude-opus-5-5`, Claude Code 2.1.310, 3 trial(s) per task; 39 of 39 graded trials passed.

| Task | Tier | Config | Passed | Turns | Output tokens | Tokens per success | API-equivalent $ |
| --- | --- | --- | --- | --- | --- | --- | --- |
| already-clean | B | mcp | 3/3 | 5.7 | 976 | 55829 | 0.05 |
| anonymous-field-lambda | B | mcp | 3/3 | 5.0 | 824 | 54507 | 0.05 |
| anonymous-super-lambda | B | mcp | 3/3 | 5.0 | 876 | 54534 | 0.05 |
| anonymous-this | A | mcp | 3/3 | 5.0 | 863 | 74844 | 0.08 |
| assess-shop | E | mcp | 3/3 | 3.0 | 859 | 44235 | 0.06 |
| calc-tests | D | mcp | 3/3 | 13.0 | 2729 | 140626 | 0.16 |
| captured-wildcard | B | mcp | 3/3 | 5.0 | 1183 | 55093 | 0.06 |
| frozen-module | B | mcp | 3/3 | 6.7 | 1280 | 79719 | 0.08 |
| interface-super | A | mcp | 3/3 | 4.7 | 822 | 56808 | 0.05 |
| intersection-type-variable | B | mcp | 3/3 | 14.7 | 2544 | 152592 | 0.13 |
| library-multi | B | mcp | 3/3 | 10.0 | 2073 | 70310 | 0.11 |
| pattern-fields | A | mcp | 3/3 | 6.3 | 1407 | 58882 | 0.07 |
| word-stats | C | mcp | 3/3 | 13.3 | 3741 | 140179 | 0.19 |
<!-- agent-bench-scoreboard:end -->

## Running

Runs are by hand, under the developer's own Claude Code login, never in CI:

```sh
cd vscode
npm run bench -- --trials 3 --jobs 2 --update   # every task, then this table
npm run bench -- --task calc-tests              # one task, without --update
```

`--update` rewrites the table only from a run of every task that the usage
limit did not stop. Each run's details, with every transcript, are in
`target/agent-bench/<date>/`.
