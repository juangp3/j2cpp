# Validation roadmap: from `--check` to passing tests on real projects

Status: proposed 2026-10-11. M0 in progress.

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
| M6 | JUnit 3 and parameterized tests | about 1 week | joda-time and commons-codec tests build |

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
- **Later:** Hamcrest and Truth assertions.
- **Deferred:** T5 (gMock).

## Deferred

- Phase 12 items 1, 3 and 6–8. Item 5 (the readiness report) can build on
  the M1 harness.
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
