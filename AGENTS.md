# Working on J2C

Guidance for AI coding agents (and people) changing this repository. For
*using* J2C from an agent, see [docs/AGENTS.md](docs/AGENTS.md).

## Layout

- `plugin/src/se/arnetheduck/j2c/transform/`: the translator, which turns a
  JDT AST into C++ text.
  - `Transformer` drives translation and validation.
  - `ImplWriter`, `HeaderWriter`, `StubWriter` and `TypeBindingHeaderWriter`
    write C++.
  - `LambdaDesugarer` rewrites lambdas into anonymous classes before parsing.
  - `ProblemCatalog` lists every problem ID.
- `plugin/src/se/arnetheduck/j2c/cli/`: the CLI.
  - `Main` parses arguments.
  - `J2cService` is shared by the CLI and MCP.
  - `McpServer` is the `j2c mcp` server, `McpContent` its prompts and
    resources; `Json` is its JSON reader and writer.
  - `Report` is `j2c report` and `j2c_report`; `RuntimeApi` reads the
    runtime's API, generated into `runtime/api.txt` (regenerate it when
    `runtime/java` changes: `test-cli.js` fails when it is stale).
- `plugin/src/se/arnetheduck/j2c/resources/`: C++ helpers and the Makefile
  template, copied into generated projects.
- `vscode/`: the extension (`extension.js`) and its scripts.
- `test/src/`: Java fixtures (`Java17*.java`). The whole tree must keep
  translating.
- `test/cpp/`: native smoke tests run against generated code.
- `test/probes/`: feature probes with expected outcomes.
- `test/agent-bench/`: agent benchmark tasks and their graders (see its
  README); `npm run bench:selftest` checks the graders without an agent.
  After changing a tool description, problem hint or skill, run the
  affected tasks (`npm run bench -- --task <id>`); `--update` rewrites the
  scoreboard in `docs/AGENT_BENCHMARK.md` from a full run.
- `test/projects/`: small Maven and Gradle projects for project auto-setup
  (`ProjectSetup`), checked by `vscode/scripts/test-cli-projects.js`.
- `runtime/`: the J2C runtime (`--runtime`), Java sources translated by J2C
  plus hand-written C++ natives and hooks; `test/runtime/` holds programs CI
  runs against it and compares with `java`. The runtime matches JDK 17's
  messages and formats, as CI runs; a newer `java` (such as 21 or 25) makes
  `npm run runtime` report differences that are not regressions.
- `licenses/`: notices for bundled dependencies, checked against the build.
- `agents/`: agent guidance and distribution: the Claude Code skill and
  plugin, Copilot instructions and cloud agent configuration, and the npm
  package (`agents/npm`) that the MCP registry's `server.json` names.
  `vscode/scripts/check-distribution.js` checks them against the version.
- `docs/PUBLISHING_PLAN.md`: release plan, translation gaps (Phase 9), and
  roadmap.

The legacy Eclipse plugin (`plugin/plugin.xml`, `handlers/`) is not built by
Maven. Leave it alone unless asked.

## Build and test

```sh
mvn -q package -DskipTests        # builds target/j2c-cli.jar (Java 11+, Maven)
cd vscode && npm ci && npm run vscode:prepublish
```

`vscode:prepublish` builds the CLI, then runs, in order:

1. The license check.
2. `test-cli.js`, the CLI integration suite, including whole-corpus
   translation.
3. `test-extension.js`, the extension logic with a mocked `vscode` module.
4. `test-mcp.js`, an MCP protocol session.
5. `check-distribution.js`, the MCP registry, npm and plugin files.

If VS Code's Java extension has this repository open, it compiles into
Maven's `target/classes` (with errors left in as "Unresolved compilation
problem"), and Maven then reuses those classes. Run `rm -rf target/classes`
before `mvn -q package` when the editor is open; `mvn clean` would also
delete the corpus cache in `target/corpus`.

Each can also be run alone (`npm run test:cli`, `test:extension`,
`test:mcp`, `test:licenses`, `test:distribution`) once the JAR exists.
`npm run probe` translates `test/probes` and fails when an outcome changes.

The JUnit fixtures (`test/junit`, and `test/junit-runtime` for assertions on
JDK objects) record each test's outcome and JUnit's failure message in
`expected-results.json`. `npm run junit:jvm` checks them with real JUnit
(downloaded with Maven into `target/`); `junit:gtest` and
`junit:gtest:runtime` translate them with `--tests` (against stubs, or with
`--runtime runtime`) and check the GoogleTest results, which needs GoogleTest
(`libgtest-dev`, in WSL on Windows).

`npm run corpus` checks the real projects in `test/corpus/projects.json`,
each pinned to a commit, and fails when a problem count rises or a project
drops a ladder step. `--project` and `--tier` select projects, `--build` also
compiles them (in WSL on Windows), `--jobs N` runs N projects at once, and
`--update` records improvements (never regressions) and
rewrites the scoreboard in `docs/VALIDATION_PROJECTS.md`. Clones and
dependency JARs are cached under `target/corpus`. CI runs it nightly
(`.github/workflows/corpus.yml`).

CI (`.github/workflows/ci.yml`) also translates every `Java17*` fixture,
builds it with `make`, and runs the `test/cpp/*Smoke.cpp` harnesses with
`CFLAGS="-fsanitize=undefined -fno-sanitize-recover=all"`. To reproduce
locally you need GCC 13+ and GNU make. On Windows, translate with the Windows
JDK and build in WSL (`wsl -d Ubuntu`), as CI's step does.

## Conventions

- Match the surrounding code. The translator is Java 11 (`--release 11`), in
  the original J2C style: tabs, explicit generic types, and anonymous classes
  rather than lambdas.
- Every behavior change gets a fixture or test:
  - A Java construct goes in a `test/src` fixture, with assertions in
    `test-cli.js`.
  - Generated C++ is compiled in CI.
  - Runtime behavior is checked in a `test/cpp` smoke harness, which may only
    call code that needs no JDK runtime: the harnesses stub `class_`,
    `unimplemented_` and the `_j` string literal.
- Unsupported constructs must fail loudly. Report them as a `Problem` with an
  ID from `ProblemCatalog` (add new IDs there, with a hint), never as
  plausible-looking C++. IDs are public API: do not rename them.
- Generated output paths and names are compared in tests. If you change the
  generated C++ shape, compare the whole-corpus output before and after.
- Keep stdout clean in JSON and MCP modes: the translator's progress is
  redirected to stderr there.
- Do not change public CLI options or exit codes without updating both
  READMEs, `CHANGELOG.md`, and `docs/AGENTS.md`.

## Finding and fixing translation gaps

1. Write a probe in `test/probes/probes/` (first line `// expect: compiles`)
   or a scratch file, then translate and build it.
2. Reduce the failure to the smallest Java snippet. Fix it at the narrowest
   layer (a writer, `LambdaDesugarer`, or a `Transformer` check).
3. Add the snippet to a fixture and, if it has runtime behavior, to a smoke
   harness. Confirm the new test fails without the fix.
4. Record the gap's status in Phase 9 of `docs/PUBLISHING_PLAN.md`.
