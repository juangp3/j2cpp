# Validation roadmap: from `--check` to passing tests on real projects

Status (2026-10-11): M0 waiting on the maintainer's manual checks; M1 done;
M2 started.

## Goal

Translate the open-source projects in
[VALIDATION_PROJECTS.md](VALIDATION_PROJECTS.md) to C++, build them, and run
their own unit tests against the C++ with the same outcomes as on the JVM.
Each project climbs the same ladder:

1. **Checks:** `j2c --check` reports no problems.
2. **Builds:** the generated C++ compiles with `make`.
3. **Links:** the library and its tests link.
4. **Tests build:** `--tests` output compiles against GoogleTest.
5. **Tests match:** every test has the same outcome in C++ as on the JVM.

Progress is measured as how far each project gets, not as features
finished.

## Why this order

The 2026-10-11 baseline over 24 projects showed:

- **The front end is in good shape.** 12 projects pass `--check` cleanly,
  including commons-lang (98k lines). J2C itself caused only one bug
  (captured wildcards in lambda targets, now fixed), 4 accurate gap reports
  in commons-collections, and about 48 lambda reports in javaparser that are
  probably caused by missing dependencies.
- **No real project's C++ has been compiled yet.** Only our fixtures have.
  This is the largest unknown and the cheapest to resolve.
- **Nothing runs without the runtime** (Phase 10): calls into the Java class
  library reach `unimplemented_()` stubs.
- **Project setup blocks most remaining projects:** dependency JARs,
  generated sources, and module layouts.
- **Test support does not match the corpus.** JUnit 3 (joda-time, javaewah,
  jBCrypt, Guava) and parameterized tests (commons-codec, TheAlgorithms) are
  common. gMock (Phase 13 T5) is not needed by any of them.

## Milestones

| Milestone | Deliverable | Effort | Done when |
| --- | --- | --- | --- |
| M0 | Ship 0.6.0 preview | about 1 day | Release published; CI run ID recorded |
| M1 | Corpus harness and scoreboard | 2–3 days | Nightly run green; problem counts can only fall |
| M2 | Clean projects build | 1–2 weeks (timeboxed) | The 9 A-rated projects reach "builds" |
| M3 | Translator gaps from the corpus | 2–4 days (alongside M2) | commons-collections clean, or each remaining report justified |
| M4 | Project auto-setup | 2–4 days | jsoup, commons-text and joda-time check with no dependency problems |
| M5 | Runtime, then T3b | 0.6.x, then 0.7.0 | hashids-java's tests match the JVM |
| M6 | JUnit 3, parameterized tests and TestNG | about 1 week | joda-time and commons-codec tests build |
| M7 | Agent-efficient, readable output | A1–A3 during M2; the rest after | Token benchmark shows the J2C workflow far cheaper than LLM-only translation |

### M0 — Ship 0.6.0

The release has waited while the scope grew (MCP, `--tests`). Ship what
exists before adding more.

1. Push `modern-release` to `modern` (juangp3/j2cpp).
2. Wait for `ci.yml` to pass on that exact commit; record the run ID in
   `PUBLISHING_PLAN.md`.
3. Refresh "Current known state" in `PUBLISHING_PLAN.md`.
4. Rename the default branch to `main`.
5. Manual checks (by the maintainer):
   - Translate from a real VS Code window in a clean profile.
   - Copilot agent mode lists the J2C tools, and Claude Code completes the
     check, fix and translate workflow.
6. Tag `v0.6.0`; download the draft release, verify `SHA256SUMS`, then
   publish it as a pre-release.
7. Marketplace: only after the `juangp3` publisher is verified.

Prerequisite: the `GH_TOKEN` environment variable on the maintainer's
machine holds an invalid token that overrides the valid keyring login.
Clear it, or run `gh` with it unset.

### M1 — Corpus harness

**Status (2026-10-11):** Done. `npm run corpus`, 26 projects; nightly CI in
`.github/workflows/corpus.yml`. On Windows, a project whose tree has a file
name Windows rejects (TheAlgorithms) is read from Git objects instead of
checked out. Building it surfaced the first M2 work: see the scoreboard.

- `test/corpus/projects.json`: per project, the repository, a **pinned
  commit**, source roots, dependency Maven coordinates, and the expected
  `--check` problem count and ladder step.
- `npm run corpus` (in `vscode/scripts/`):
  - Sparse-clones each project into `target/corpus` (`.java` and build
    files only), and downloads dependency JARs from Maven Central.
  - Runs `--check`, and fails if a project's problem count rises or it drops
    a ladder step. Lower counts print a reminder to update the expected
    values.
  - With `--build`, translates and runs `make` (in WSL on Windows), and
    records the first C++ errors per project.
  - `--project <name>` limits the run.
- A CI workflow on a nightly schedule and `workflow_dispatch`, kept out of
  the per-push gate because it is slow.
- A scoreboard table in `VALIDATION_PROJECTS.md` showing each project's
  ladder step.

Third-party sources are never committed: the harness clones them at the
pinned commit.

### M2 — Make the clean projects build

1. Run `--build` on hashids-java, jBCrypt, semver4j, jsemver, minimal-json,
   JSON-java, javaewah, commons-codec and commons-lang, smallest first.
2. Triage each C++ compile error into a new Phase 9 gap (G10 onwards).
3. Fix with the loop in `AGENTS.md`: reduce to a minimal Java snippet, fix
   the narrowest layer, add a fixture, and confirm it fails without the fix.
4. Raise each project's expected ladder step in `projects.json`.

Timebox: two weeks. Whatever is left becomes Phase 9 entries with a
priority.

### M3 — Translator gaps from the corpus

- commons-collections:
  - two method references targeting the intersection
    `Number & Comparable<?> & Constable`;
  - one `ToIntFunction<capture of ?>` target;
  - one lambda that refers to an anonymous class.

  Ground the targets to a type J2C can write where that is sound; otherwise
  keep the report and document it.
- javaparser: recheck its 48 lambda reports once its dependencies are on the
  class path (M4), and fix whatever remains.

### M4 — Project auto-setup

This is Phase 12 item 2, moved up because the corpus needs it.

- Detect `pom.xml` and Gradle builds, and use their main and test source
  roots.
- Get the dependency class path from the build tool
  (`mvn dependency:build-classpath`, or a Gradle init script).
- Report sources generated at build time (for example jackson-core's
  `PackageVersion` and javaparser's JavaCC parser) with a hint to run
  `generate-sources`, instead of a wall of unresolved types.
- Available in the CLI, MCP (`paths: ["."]`) and the extension.

### M5 — Runtime and runtime-backed assertions

Phase 10 as planned:

1. Step 1 (0.6.x): `--runtime`, `docs/RUNTIME.md`, the conformance check, and
   fake-runtime tests.
2. Step 2, milestone 1 (0.7.0): the core runtime, with differential tests
   against the JVM.
3. Phase 13 T3b: runtime-backed `equals` and messages in assertions, and
   running the fixtures on the JVM to compare.
4. First end-to-end target: hashids-java's own tests pass in C++ with the
   same outcomes as on the JVM. Then jBCrypt.
5. Milestone 2 (collections) unlocks JSON-java, semver4j and jsemver.

### M6 — Test features the corpus needs

- **T3c, JUnit 3:** `TestCase` subclasses, `setUp`/`tearDown`, and
  `test*` methods. It unlocks jBCrypt, javaewah, joda-time and Guava.
- **T3d, parameterized tests:** `@ValueSource`, `@CsvSource` and
  `@MethodSource` become GoogleTest parameterized tests.
- **T3e, TestNG:** `@Test`, `@BeforeMethod`/`@AfterMethod`,
  `@BeforeClass`/`@AfterClass`, `expectedExceptions` and `Assert.*`.
  Forge's tests use it (95 files).
- **Later:** Hamcrest and Truth assertions.
- **Deferred:** T5 (gMock).

### M7 — Agent-efficient, readable output

The project's goal is to make translating a Java project to C++ cheap for an
AI agent. Measured on commons-codec (2026-10-11):

| Content | Approx. tokens |
| --- | ---: |
| Java source, all of it | 275k |
| Java source without comments | 126k |
| J2C output for the project's own classes (338 files) | 360k |
| J2C stubs for Java library classes (825 files) | 574k (never needs reading) |

Boilerplate is only about 20–25% of the project output: includes 7.4%,
literal casts 5.4%, `class_`/`getClass0` 5.0%, `::java::lang::` 3.5%,
per-file helper templates about 3%, `default_init_tag` constructors 2.6%,
array typedefs 2.5%, `clinit()` and `npc()` 1.6%. The rest is inherent to
keeping Java semantics. Comments are dropped.

So the main saving is that the agent never reads the bulk C++. It fixes
problems in Java, and gets compact, Java-mapped results from J2C. Readable
output matters for the final step, when chosen spots are refined into
idiomatic C++.

**A: agent-facing tools (largest savings)**

| ID | Item |
| --- | --- |
| A1 | `j2c build` and a `j2c_build` MCP tool: run `make`, return errors deduplicated and mapped to Java file and line with 3-line snippets, capped |
| A2 | Source map: optional `#line` directives and `j2c-map.json` (Java line ranges to C++ ranges) |
| A3 | Compact results: `detail: summary\|grouped\|full` and `maxProblems`; problems grouped by ID with counts and first locations |
| A4 | `j2c-index.json` and a `j2c_lookup` tool: Java class or method to C++ file, symbol and renamed name (such as `toArray_`) |
| A5 | Structured quick fixes (Phase 12 item 1): problems carry exact text edits |
| A6 | Incremental retranslation (Phase 12 item 7): retranslate changed files only and report which outputs changed |
| A7 | Token benchmark, the success measure: hashids-java and minimal-json migrated by an agent, LLM-only against J2C plus the agent; record tokens, cost and test results |

**R: output readability.** Each change is checked by comparing the whole
corpus's output before and after, plus the corpus builds.

| ID | Item | Saves | Risk |
| --- | --- | ---: | --- |
| R1 | Helper templates (`npc`, `jdiv`, ...) in one shared header, not one copy per `.cpp` | ~3% | Low |
| R2 | Plain literals where the type is unambiguous (`0`, not `int32_t(0)`); keep the cast where it picks an overload | ~5% | Low |
| R3 | Array typedefs in the forward-declaration headers only | ~2.5% | Low |
| R4 | Macros for class boilerplate (`J2C_CLASS(...)` for `class_` and `getClass0`) | ~5% | Low |
| R5 | Keep comments: Javadoc becomes Doxygen `///` in headers; `--comments api\|all\|none`, default `api` | Adds tokens | Low |
| R6 | No `clinit()` for classes with no static initialization anywhere in their supertypes | <1% | Medium |
| R7 | No `npc()` where the value cannot be null (`this`, `new`, literals, concatenation results) | <1% | Medium |
| R8 | Refinement markers, such as `// j2c: lambda for IntBinaryOperator (Hello.java:11)` | Small | Low |
| R9 | Pragmatic profile, `--semantics strict\|pragmatic`: drop Java machinery the whole program provably does not use (`class_` without reflection, `npc` without `NullPointerException` handlers, `clinit` without static side effects, `std::function` for lambdas that never escape), and list each decision with its reason. `strict` stays the default. | ~7%, and much more readable | Medium |

Order:

1. A1–A3 during M2: the M2 triage needs mapped, deduplicated errors anyway.
2. The A7 baseline early, so later items are judged by tokens saved.
3. R1–R5 after M2; then A4–A6; then R6–R9 (R9 builds on R6 and R7).

This takes Phase 12 items 1, 4 and 7 into M7, because they are central to the
project's goal.

## Deferred

- Phase 12 items 3, 6 and 8. Item 5 (the readiness report) can build on
  the M1 harness. Items 1, 4 and 7 moved into M7.
- Java 21 source level (TheAlgorithms).
- Garbage collection (Phase 10 milestone 3), until real programs run.

## Risks

- **M2 may surface many generated-C++ bugs.** The timebox and smallest-first
  order keep it bounded. Every fix lands with a fixture, so progress is not
  lost.
- **Upstream projects change.** Pinned commits keep runs reproducible.
  Update pins deliberately.
- **Dependency downloads in CI** can be slow or flaky. Cache the Maven
  repository, and keep the nightly run out of the release gate.
