# J2C Modern Edition: Repository and Publication Plan

## Goal

Publish the modern Java-to-C++ CLI and VS Code extension as a distinct project
with its own repository, release process, documentation, and issue tracking.
Preserve the original repository as the upstream/legacy Eclipse project, and
retain Git history in the new repository.

The first public release is a **preview**. It must not imply complete
Java-to-C++ semantic equivalence.

## Current known state

Last rechecked on 2026-10-11 against local `modern-release`.

- **Repositories.** `juangp3/j2cpp` is public and has a single branch,
  `modern-release`, which is therefore its default branch. Locally, `origin`
  still points to `arnetheduck/j2c` (left intact) and `modern` points to
  `juangp3/j2cpp`. No release tags exist.
- **Pushed state.** Being pushed for M0 of
  [VALIDATION_ROADMAP.md](VALIDATION_ROADMAP.md); previously
  `modern/modern-release` was at `409a0be`.
- **Worktree.** Clean on `modern-release`. The legacy Eclipse IDE metadata
  and the `HandlerHelper.java` handler edit are parked on the local, unpushed
  branch `legacy/eclipse-ide-edits` (`e4261f0`). Generated `plugin/bin`,
  `test/bin`, `target`, the staged CLI JAR, copied license files, and VSIX
  packages are ignored.
- **Scope of 0.6.0.** Beyond the CLI and extension: `--check`, `--version`,
  `--format json`, the output guard, problem IDs with `j2c explain`,
  `j2c capabilities`, the Problems panel, lambdas and method references,
  Phase 9 gaps G1–G6, the `j2c mcp` server with VS Code registration
  (Phase 11), and `--tests` with JUnit assertions (Phase 13 T1–T3a).
  `CHANGELOG.md`'s `0.6.0 — Preview` section lists it all and is the
  release notes.
- **Versions.** Maven and the VS Code extension are both `0.6.0`. The
  extension is marked `"preview": true`. The CLI is compiled with
  `--release 11`, matching the documented Java 11 minimum.
- **Licensing.** The project's EPL-1.0 text is in `LICENSE`.
  `THIRD-PARTY-NOTICES.txt` inventories the 16 runtime dependencies (14 under
  EPL-2.0, 2 under Apache-2.0) and says where to get their sources.
  `licenses/` holds the full EPL-2.0 and Apache-2.0 texts plus every
  dependency's unmodified notice files, which ship under
  `META-INF/j2c-licenses/` in the JAR and at the VSIX root.
- **Checks.** `vscode:prepublish` runs the Maven build, then the license
  check, the CLI integration suite, the extension-logic suite (mocked
  `vscode` module), and an MCP protocol session. `npm run probe` tracks
  translation gaps.
- **CI.** `ci.yml` builds and tests on Java 11 and 17. On Java 17 it also
  builds the generated C++20 fixtures and runs the smoke harnesses under
  UBSan, runs the translated JUnit fixtures with GoogleTest and compares all
  34 outcomes, and runs the probes. Last verified locally on 2026-10-11
  (`fc01cae`); the CI run on the pushed commit is recorded in Phase 8.
- **Release automation.** `release.yml` runs on `v*` tags: the full CI
  matrix, a tag/version/changelog agreement check, a rebuild from the tag
  with a byte-for-byte JAR comparison, then `SHA256SUMS` and a **draft
  pre-release**. The `gh release` step has not run yet.
- **Validation corpus.** [VALIDATION_PROJECTS.md](VALIDATION_PROJECTS.md)
  lists 24 open-source projects with a `--check` baseline; 12 are clean.
  [VALIDATION_ROADMAP.md](VALIDATION_ROADMAP.md) orders the work from here
  (M0–M6).
- **Marketplace.** Publisher is `juangp3`. The item URL for `juangp3.j2c`
  returns 404; publisher ownership and a publishing token remain unverified.
- **Not yet done.** Interactive translation from a real VS Code window, and
  agent tools checked with a real Copilot or Claude Code client. On the
  maintainer's machine an invalid `GH_TOKEN` overrides the valid `gh`
  keyring login; run `gh` with it unset.

## Phase 1 — Decide repository identity and create the destination

1. **Done:** The destination is `juangp3/j2cpp`, public, created without an
   auto-generated README or license.
2. **Done:** A distinct repository was chosen instead of a GitHub fork.
3. **Done:** `arnetheduck/j2c` is preserved; local `origin` is unchanged and
   the new repository is the `modern` remote.
4. **Done:** Pushed the reviewed branch with existing project history.
5. **Pending:** Rename the published branch to `main` and make it the default
   before tagging, so that the release, the README links, and anyone's first
   clone all use the stable name:

   ```sh
   git branch -m modern-release main
   git push -u modern main
   gh repo edit juangp3/j2cpp --default-branch main
   git push modern --delete modern-release
   ```

**Gate:** Destination identity and visibility are intentional; the original
repository is intact; the default branch is `main`.

## Phase 2 — Isolate and review the modernization changes

1. **Done:** Recorded branch, remotes, tracked diff, untracked files, and
   recent commits before changing repository configuration.
2. **Done:** Classified changes as CLI/extension behavior, tests/fixtures,
   documentation, or release infrastructure.
3. **Done:** Added ignore rules for generated Eclipse, Maven, extension, and
   packaging output. Generated `bin` contents remain on disk.
4. **Done:** Moved the unrelated legacy IDE and handler edits off the release
   branch onto local branch `legacy/eclipse-ide-edits`. Nothing was discarded.
5. **Done:** Committed the modernization snapshot (`94ceede`) and follow-ups.
6. **Done:** Moved this plan from the repository root to `docs/`.

**Gate (passed):** Every committed file is explained by the release; unrelated
work is preserved off-branch; a detached clean worktree reproduced the build,
tests, package, and C++ checks; CI passed on `789d478`.

## Phase 3 — Define product scope and project documentation

Support contract for the first release:

- The CLI parses Java 17 source and generates C++20-targeted output.
- Java 17 feature support is incremental. Verified slices:
  pattern-matching `instanceof`, switch expressions, records, and sealed-type
  advisory metadata. Complete Java 17 support is not claimed.
- Sealed types default to ordinary C++ inheritance with comments listing
  permitted direct subtypes; C++ does not enforce `permits`.
  `--sealed-types reject` rejects sealed and non-sealed declarations.
- Generated output may need manual correction and a compatible Java runtime
  implementation. Known record semantic gaps are documented.
- Java 11+ runs the CLI; generated Makefiles need a C++20 toolchain.

1. **Done:** Root README rewritten as the entry point for the CLI and extension.
2. **Done:** Legacy Eclipse install/release steps removed from the root README.
3. **Done:** VS Code README covers invocation, settings, paths, prerequisites,
   packaging, and limitations.
4. **Done:** `CHANGELOG.md` has a `0.6.0 — Preview` section with scope and
   limitations.
5. **Done:** Relationship to `arnetheduck/j2c` documented.

**Gate (passed; the repository is already public):** A new user can identify
the entry points, run the documented example, and see what the translator does
not guarantee. Re-read both READMEs once more on the frozen release commit.

## Phase 4 — Verify identity, version, and licensing

1. **Partially done:** GitHub repository `juangp3/j2cpp`; extension publisher
   `juangp3`. **Pending (user):** create or confirm the `juangp3` publisher
   at <https://marketplace.visualstudio.com/manage> and an Azure DevOps personal
   access token with Marketplace *Manage* scope.
2. **Done:** Maven and extension versions are `0.6.0`; `release.yml`
   enforces agreement with the tag and changelog.
3. **Confirmed by user:** Public redistribution under EPL-1.0 with upstream
   attribution is authorized.
4. **Done:** Dependency notices are complete and shipped:
   - The inventory.
   - The full license texts.
   - Each dependency's original notice files.
   - Where to obtain each dependency's source.

   `check-licenses.js` keeps the notices in sync with the resolved
   dependencies. A human legal review is still recommended but no longer
   blocks a preview.
5. **Done:** VS Code engine `^1.85.0`, Java 11+ requirement, `j2c.translate`
   command, and the five `j2c.*` settings are consistent between
   `package.json` and both READMEs.

**Gate:** The source, CLI artifact, VSIX, Marketplace metadata, and release
page have consistent names, versions, requirements, and license information.
Only publisher verification remains.

## Phase 5 — Automated behavior regression checks

All checks run via `npm run vscode:prepublish` (and so in CI and the release
workflow):

- **License consistency** (`check-licenses.js`): inventory, notice directories,
  and JAR contents match the resolved runtime dependencies.
- **CLI behavior** (`test-cli.js`):
  - `--help`, `--version` (matches the extension version), missing input,
    missing option values, invalid `--sealed-types` and `--encoding` values,
    and `--check` combined with `--output`.
  - `--check` lists every problem with a per-kind summary, exits 1, and
    writes nothing, not even into the working directory.
  - Translation and output paths containing spaces.
  - Lambdas and method references fail with `file:line` diagnostics and no
    stack trace.
  - A non-ASCII string literal survives as UTF-8 in the generated C++.
  - Java 17 fixtures, gated on the Java version running the tests:
    - The whole `test/src` corpus translates, with no `MISSING`
      placeholders in the generated sources, and `--check` reports it clean.
    - `Java17Features`: text blocks, enum and String switch expressions,
      String switch statements, and both try-with-resources forms. CI also
      compiles the generated C++ for this fixture.
    - Pattern `instanceof`, switch expressions, and arrow-rule switch
      statements over ints and enums.
    - Records.
    - Sealed advisory, reject, and invalid policies.
- **Extension logic** (`test-extension.js`, mocked `vscode`):
  - Configured source and class paths.
  - Custom CLI and Java executables.
  - `j2c.sealedTypes` pass-through.
  - Invalid or missing inputs and a missing CLI JAR.
  - Nonzero exits and process-start failures.
  - Cancellation kills the CLI, and a second concurrent run is refused.
  - The runner fails if a test never settles.

Not yet covered: record component combinations beyond the fixture, and real
VS Code host integration (see Phase 7 step 5). Consider `@vscode/test-electron`
after the preview.

**Gate:** All automated checks pass locally on Java 17, and the generated C++
builds and the smoke harness passes with GCC 13.3. No local JDK 11 is
available, so the Java 11 CI job must pass on the pushed commit.

### Senior review findings (2026-10-10)

Fixed. These were release blockers; the first five gave wrong or
uncompilable output while still exiting 0:

1. Moving the parser from JLS4 to JLS17 broke legacy DOM calls.
   `ArrayType.getComponentType()` crashed on array class literals, and
   `SwitchCase.getExpression()` printed `MISSING` for every enum `case`
   label.
2. Lambdas, method references, and text blocks produced invalid C++. They now
   fail before writing, as do `try (existingVar)` (previously a crash) and
   switch expressions on non-primitive selectors.
3. Arrow-rule switch statements were emitted as `return` or fell through.
4. Per-file parsing omitted dependency headers that generated sources
   include (for example `java/util/ArrayList.hpp`). Batched parsing fixes
   this and is about 1.5× faster on the corpus (3.6 s → 2.4 s).
5. Sources were read, and C++ written, in the platform charset (cp1252 on
   Windows with Java 11/17). Both are now UTF-8.
6. `ObjectArray.hpp` included `Cloneable`/`Serializable` headers that were
   never generated for small projects.
7. Extension: no cancellation, chunk-wise output decoding, concurrent runs.

Features added after the review (2026-10-10):

- Lambdas and method references, rewritten into anonymous classes before
  parsing (`LambdaDesugarer`). Two older translator gaps surfaced and were
  fixed:
  - Only explicitly `final` locals were captured.
  - Default and static interface methods had no stub definitions, so
    implementing `Comparator`, `Function` and similar interfaces failed to
    link.

  `Java17Lambdas` covers every lambda and method-reference form, and its
  generated C++ is compiled, linked and run by `Java17LambdasSmoke.cpp` in
  CI. This is the first runtime check of generated objects.

- `--check`, which reports all problems with a per-kind summary and writes
  nothing; `--version`, stamped from the Maven version into the JAR
  manifest.
- VS Code: **J2C: Check Translatability**, and Problems-panel diagnostics
  for both commands.
- Translation of text blocks, try-with-resources (previously emitted
  verbatim as invalid C++), `case X ->` switch expressions on enums and
  strings, and `String` switch statements.

Deferred (not release blockers):

- A Java error in a dependency unit, not just a selected one, fails the whole
  run. Upstream fell back to header-only output for such units.
- Validation runs per unit as it is processed, so a failure can leave earlier
  output written. Validating every selected unit first would avoid that.
- `if`-chain switches (enum and `String`) whose cases all return trigger a
  harmless GCC `-Wreturn-type` warning.
- The shaded CLI JAR is 10 MB. `minimizeJar` is risky with Eclipse's
  reflective loading, so it was not attempted.
- There is no `j2c.encoding` extension setting; the CLI default of UTF-8
  applies.

## Phase 6 — Validate generated C++ and practical end-to-end use

1. **Done:** Translated `Java17ControlFlow.java` and `Java17Records.java` with
   the CLI.
2. **Done:** Both compile with `make` and GCC 13 using `-std=c++20`, locally
   (WSL) and in CI.
3. **Done:** A C++ harness checks generated `switchValue` results in CI.
4. A pre-existing missing-return warning in one generated Java runtime stub
   remains; it does not fail compilation. This proof covers these fixtures
   only.

**Gate (passed):** Compile proof passes in CI; preview limitations stay
explicit.

## Phase 7 — Clean-checkout CI and package verification

1. **Done:** GitHub Actions workflow for build, tests, and VSIX packaging on
   Java 11 and 17.
2. **Done:** `@vscode/vsce` pinned to 4.0.0; `npm audit` reports zero
   vulnerabilities.
3. **Done:** The VSIX contains:
   - The extension code and the CLI JAR.
   - The README and CHANGELOG.
   - The project license.
   - `THIRD-PARTY-NOTICES.txt` and the `licenses/` tree.
4. **Done:** Rebuilt from a detached clean worktree using `npm ci`.
5. **Pending (manual, about 5 minutes):** Interactive check in a clean profile:

   ```sh
   code --user-data-dir "$TMP/j2c-ud" --extensions-dir "$TMP/j2c-ext" \
     --install-extension vscode/j2c-0.6.0.vsix
   code --user-data-dir "$TMP/j2c-ud" --extensions-dir "$TMP/j2c-ext" test
   ```

   Then:
   1. Open `src/se/arnetheduck/j2c/test/Java17Records.java` and run
      **J2C: Translate to C++** from the editor context menu into a new
      empty folder.
   2. Check that the progress notification appears, the J2C output channel
      shows the CLI log, and C++ files are written.
   3. Run it once from the Explorer context menu on the `src` folder.
   4. Set `j2c.sealedTypes` to `reject`, translate `Java17Unsupported.java`,
      and confirm an error is shown and the Problems panel marks both
      declarations.
   5. Run **J2C: Check Translatability** on a Java file that contains a
      lambda. Confirm the Problems panel marks it, and that clicking the
      entry opens the right line. Remove the lambda, check again, and
      confirm the entry clears.

   Record the outcome here.

**Gate:** Steps 1–4 passed; step 5 must pass before tagging.

## Phase 8 — Release and post-release verification

1. **Freeze.** Commit the release changes on `main` (Phase 1 step 5), push,
   and confirm `git status` is clean.
2. **CI on the exact commit.** Wait for `ci.yml` to pass on the pushed commit
   and record the run ID here.
3. **Tag.** Only after every go/no-go item below is checked:

   ```sh
   git tag -a v0.6.0 -m "J2C Modern Edition 0.6.0 (preview)"
   git push modern v0.6.0
   ```

4. **Draft release.** `release.yml` reruns CI on the tag, rebuilds both
   artifacts from the tag, checks versions, and creates a draft pre-release
   containing `j2c-cli-0.6.0.jar`, `j2c-0.6.0.vsix`, `SHA256SUMS`, and notes.
5. **Verify.** Download the draft's assets into an empty directory, run
   `sha256sum -c SHA256SUMS`, and check that `java -jar j2c-cli-0.6.0.jar
   --help` works and the VSIX installs. Review the notes, then publish the
   draft (it stays marked pre-release).
6. **Marketplace.** After publisher verification, publish the *same* VSIX that
   was attached to the release (do not rebuild):

   ```sh
   npx @vscode/vsce publish --packagePath j2c-0.6.0.vsix
   ```

7. **Post-publish smoke test.** Install `juangp3.j2c` from the Marketplace into
   a clean profile and repeat the Phase 7 step 5 translation.
8. **Rollback and superseding releases:**
   - A bad GitHub release can be returned to draft, edited, or deleted. Never
     move or reuse a published tag; fix forward with `v0.6.1`.
   - The Marketplace cannot republish a version number, and unpublishing
     removes the whole extension. Fix forward by bumping both versions to
     `0.6.1`, adding a changelog entry, and repeating steps 1–7.
   - Watch GitHub issues for the first week; label translator gaps separately
     from packaging bugs.

**Gate:** GitHub artifacts and the Marketplace package come from the same tag
and commit, verify against `SHA256SUMS`, install cleanly, and match the
documented requirements.

## Phase 9 — Close translation gaps

On 2026-10-10, probe files exercising Java 9–17 features and common JDK
idioms were translated and compiled with GCC 13.3. Most of it works:

- Interface default, private and static methods, and `Interface.super`.
- Every switch form tested.
- Generics, streams, `Optional`, lambdas and method references.
- Local records, enums and interfaces, and text blocks.
- Labeled loops, multi-catch, anonymous diamond, varargs and `synchronized`.

The gaps below remain. They fall into three groups:

- G1–G4 and G6 are language gaps.
- G5 and G9 are semantic differences.
- G7 and G8 are runtime gaps that decide whether translated programs can run.

### Gaps

#### G1 — `instanceof` patterns outside plain `if` conditions

**Status (2026-10-10):** Done: hoisting with unique names, braces for unbraced bodies and `else if`; `Java17Patterns` and its runtime smoke test cover nine forms. Patterns in field initializers are reported.

- **Symptom:** the translator crashes (`NullPointerException` in
  `ImplWriter.visit(InfixExpression)`) when a pattern appears in `&&`,
  `while`, `?:`, or a negated `if` such as
  `if (!(o instanceof String s)) return; use(s);`.
- **Cause:**
  - JDT gives no type for a `PatternInstanceofExpression` operand, so `lt`
    is null.
  - Pattern variables are only declared for the plain `if` form.
- **Implement:**
  1. Before writing any statement that contains patterns (skipping nested
     lambda, anonymous and local class bodies), declare each pattern
     variable in the enclosing block as `T* name = nullptr;`.
  2. Emit the test as `((name = dynamic_cast<T*>(expr)) != nullptr)`, which
     evaluates the operand once.
  3. Declaring in the enclosing block gives Java's flow scoping for free:
     the negated-`if` form and `while` conditions just work.
  4. Make names unique with the existing `localInScope` helper, and print
     each variable through a binding-to-name map.
  5. Treat a pattern operand's type as `boolean` in infix expressions.
  6. Move the existing `if` path onto the same mechanism.
- **Test:**
  - A `Java17Patterns` fixture with every probe form.
  - CLI assertions that translation succeeds.
  - CI `make`.
  - A runtime smoke harness. Use user-defined classes, not `String`, so no
    library code is needed. Check the `&&`, `while`, ternary and negated
    forms, plus a null operand (which must not match).
- **Effort:** medium. **Release:** blocks documented `instanceof` support;
  fix before 0.6.0 or narrow the README claim.

#### G2 — Record with an object component (e.g. `List<String>`)

**Status (2026-10-10):** Done: record components are hard dependencies; covered by `Java17Records.Tagged`.

- **Symptom:** C++ compile error "invalid use of incomplete type" in the
  generated `equals`.
- **Cause:** `RecordSupport` calls component methods (`equals`, `hashCode`)
  without a hard dependency on the component type, so its header is not
  included.
- **Implement:** call `hardDep` for every reference-typed component in
  `RecordSupport`.
- **Test:** add a `List<String>` component and an array component to
  `Java17Records`; CI `make`.
- **Effort:** small. **Release:** blocks documented record support.

#### G3 — Record whose component type is a nested type

**Status (2026-10-10):** Done: accessors print qualified return types; `Java17Records` evaluates a sealed record hierarchy at runtime.

- **Symptom:** `'Sealed_Expr' does not name a type` in an out-of-class
  accessor definition. This happens, for example, for records implementing a
  nested sealed interface.
- **Cause:** the accessor's return type is printed relative to the class
  scope, but out-of-class definitions need the qualified name.
- **Implement:** print accessor return types with the qualified name, as
  the other out-of-class definitions do (`TransformUtil.qualifiedRef`).
- **Test:** a sealed-interface-plus-records fixture (`Expr`, `Num`, `Add`,
  `eval`); CI `make`.
- **Effort:** small. **Release:** blocks documented record and sealed
  support.

#### G4 — Remaining switch forms

**Status (2026-10-10):** Done: boxed selectors were already unboxed for statements and now work for expressions; colon-form enum and String switch expressions reuse the if-chain writer. Covered by `Java17Features`.

- **Symptom:**
  - Switch expressions on boxed selectors (`Integer`, `Character`) and
    colon-form (`case X:`) switch expressions on enums or strings are
    rejected by `--check`.
  - Switch *statements* on boxed selectors emit a native C++ `switch` on a
    pointer, which does not compile. This predates the modernization.
- **Implement:**
  - Unbox the selector (`npc(k)->intValue()` and so on) in both native
    switch paths.
  - For colon-form enum or String switch expressions, refactor
    `enumSwitch` to take a selector and a statement list instead of a
    `SwitchStatement`, and reuse it inside the expression lambda.
    `yield` already becomes `return` there.
- **Test:** fixture methods for each form; CLI assertions; CI `make`. Check
  int-valued boxed switches at runtime in the control-flow smoke harness.
- **Effort:** small to medium.

#### G5 — Java arithmetic semantics

**Status (2026-10-10):** Done: `java_math.hpp` helpers and `-fwrapv`; `Java17ArithmeticSmoke.cpp` checks 28 cases under UBSan. UBSan also exposed, and this fixed, the null-reference constructor tag in every generated constructor, and an unrelated `x %= y` bug (`fmod(x, x)`) for floating-point values. Not covered: implicit narrowing in compound assignments with a floating-point right side (`i += 1.5`), which stays a C++ conversion.

- **Symptom:** the generated C++ uses raw operators, so these cases are
  undefined behavior instead of Java's defined results:
  - Integer `/` and `%` by zero (Java throws `ArithmeticException`).
  - `Integer.MIN_VALUE / -1` (traps on x86).
  - Signed overflow (Java wraps).
  - Shift counts of 32 or more for `int`, or 64 or more for `long` (Java
    masks the count).
  - `(int) NaN` and out-of-range floating-point casts (Java saturates).
- **Implement:**
  - Add `-fwrapv` to `Makefile.tmpl`.
  - Add a resource header with inline helpers: division and remainder
    (check for zero, special-case `-1`), shift with masking, and saturating
    `d2i`, `d2l`, `f2i` and `f2l`.
  - Emit them from `ImplWriter` for `/`, `%`, `<<`, `>>`, `>>>`, their
    compound assignments, and narrowing casts from `float` or `double`.
  - Fold the helpers away for constant operands to keep the output
    readable.
- **Test:**
  - An arithmetic smoke harness: wrap-around, `MIN_VALUE / -1`, shift by 33
    and 65, casts of NaN and of ±1e20, and division by zero caught as an
    exception.
  - Run every smoke harness with `-fsanitize=undefined` so any remaining UB
    fails CI.
- **Effort:** small to medium. **Release:** silent wrong results; fix
  early, or at least document before 0.6.0.

#### G6 — Lambda forms that cannot be rewritten

**Status (2026-10-10):** Done for generic functional methods and intersection targets (a generated member interface keeps `instanceof` true for every component; casts to intersection types elsewhere are now checked per component). `this` inside an anonymous class stays unsupported by design.

- **Symptom:** `--check` rejects lambdas with an intersection-type target,
  method references to generic functional methods, and `this` inside an
  anonymous class.
- **Implement:**
  - **Intersection targets:** when every extra interface has no abstract
    methods (`Serializable`, other marker interfaces), implement only the
    functional interface and record the dropped markers in a generated
    comment.
  - **Generic functional methods:** render the method's type parameters on
    the generated method (`public <T> R m(...)`).
  - **`this` in an anonymous class:** keep it unsupported. The anonymous
    class cannot be named from the generated class.
- **Test:** extend `Java17Lambdas`; keep the `--check` negative test for
  `this` in an anonymous class.
- **Effort:** small (generic) to medium (intersection).

#### G7 — Java class library at runtime

**Status (2026-10-10):** Planned in Phase 10. Option (b), runtime integration, comes first (0.6.x), then option (a), the bundled runtime (0.7.0 onwards).

- **Symptom:** JDK classes are generated as stubs that call
  `unimplemented_()`. Translated programs compile and link, but any call into
  the library (`String`, `StringBuilder`, boxing, collections, streams,
  `System.out`) does nothing or aborts. The lambda runtime test passes only
  because it calls no library code.
- **Implement:** this needs a decision first.
  - **(a) Bundled minimal runtime:** a hand-written `j2c-runtime` C++
    library for `Object`, `String` (UTF-16), `StringBuilder`, boxed types,
    `Math`, the `Throwable` hierarchy, `System.out` and arrays, then
    `ArrayList`, `HashMap`, `HashSet` and iterators. The generator would
    skip stubs for types the runtime provides.
  - **(b) Documented integration** with an existing runtime the user
    supplies.

  Option (a) makes "translate and run" work for small programs; option (b)
  is far smaller.
- **Test:** differential testing. Run each test program with `java` and as a
  translated native binary, then compare standard output and exit codes.
- **Effort:** large (weeks for a useful subset). **Release:** already a
  documented limitation; make the README say plainly that output does not
  run without a runtime.

#### G8 — Memory management

**Status (2026-10-14):** Done, as Phase 10 milestone R5.

- **Symptom:** objects are allocated with `new` and never freed.
- **Fix:** `make J2C_GC=boehm` links the Boehm conservative collector
  (`-lgc`, `-DJ2C_GC_BOEHM`), and the generated main file replaces every
  global `operator new` with `GC_MALLOC` (scanned, so standard containers'
  buffers keep what they point to alive) and `operator delete` with
  `GC_FREE`. C++ exception objects are `malloc`'d, so a Throwable in flight
  could be freed while a `finally` block allocates; the Makefile links with
  `-Wl,--wrap=__cxa_throw`, and the wrapper keeps the last 256 thrown
  pointers in static data. Finalizers stay unsupported.
- **Test:** `test/runtime/allocation` allocates about 1 GB in a loop with a
  bounded live set, keeps objects in static fields, collections, locals and
  exceptions in flight, and checks them at the end. `runtime-programs.js
  --gc boehm` (CI, `npm run runtime:gc`) builds every runtime program at
  `-O2` with UBSan, collecting every 1000 allocations (`J2C_GC_STRESS`),
  compares the output with `java`, and requires the allocation program's
  peak RSS to stay under 100 MB (16 MB measured; 910 MB without the
  collector, which the non-GC run requires to exceed the bound). Without
  the `__cxa_throw` wrapper, the program prints corrupt exception messages
  at `-O2`.
- **Limits:** single-threaded programs only; GNU ld; `make mains` but not
  `make test`; primitive arrays are scanned conservatively; no ASan.

#### G9 — Smaller semantic differences (documentation only)

- An exception thrown by `close()` while another propagates terminates the
  program instead of being recorded as suppressed.
- Every lambda evaluation creates a new object.
- For a bound method reference with a null receiver, the failure happens
  when it is called rather than when it is created.
- Reflection, serialization and class loading are unsupported.

The deferred items listed under Phase 5 also remain: falling back to
header-only output for dependency units with errors, and validating every
selected unit before writing anything.

#### G10 — Bound method reference on a reassigned local

**Status (2026-10-11):** Done. Found in Forge (5 sites, such as `forEach(cards::add)`).

- **Symptom:** `--check` reported "Local variable … must be final or
  effectively final" for code javac accepts. Java evaluates a bound
  receiver once, when the reference is created, so it may be any local.
  The rewrite stored it in a field initializer, which captures the local.
- **Fix:** when the receiver reads a local that is not effectively final,
  `LambdaDesugarer` passes it as an argument instead:
  `new Object() { I bind(final T receiver) { return new I() {...}; } }.bind(cards)`.
  Other references keep the field form, so their output is unchanged.
- **Test:** `Java17Lambdas.reassignedReceiver` and its smoke check (the
  first reference keeps the first receiver), plus a `test-cli.js` assertion.

#### G11 — Pattern as the third or later operand of `&&`

**Status (2026-10-11):** Done. Found in Forge (3 sites).

- **Symptom:** `if (a && b && o instanceof String s)` crashed translation
  with a `NullPointerException` in `DepInfo`. JDT gives pattern operands no
  type, and only the first two operands were guarded.
- **Fix:** the extended-operand loop in `ImplWriter` skips the dependency
  for an operand without a type, as the first two already did.
- **Test:** `Java17Patterns.thirdOperand` and its smoke check.

#### G12–G31 — C++ build errors found by the corpus (M2)

**Status (2026-10-11):** Done. Found by `npm run corpus --build`; each fix has
a fixture and a `test-cli.js` assertion, and runtime behavior is checked in
`Java17LambdasSmoke.cpp` where it needs no JDK code.

- **G12, updating a box** (semver4j, williamfiset/algorithms, zxing):
  `Integer x; x += 2`, `x++` and `++x` emitted `npc(x)->intValue() += 2`.
  Java replaces the box, so J2C now emits
  `x = Integer::valueOf(static_cast<int32_t>(x.intValue() + 2))`, with the
  Java integer helpers for `/`, `%` and shifts, and a lambda returning the
  old box for `x++` used as a value. Array elements are not handled yet.
- **G13, cast from a reference to a primitive** (zxing): `(int) object` is a
  checked cast to `Integer` and then unboxing, not a C++ `static_cast`.
- **G14, lambda in an instance field initializer** (williamfiset/algorithms):
  the generated class did not receive the enclosing instance. JDT has no
  declaring method there, and J2C fell back to the Eclipse Java model, which
  the CLI does not have; it now uses `ITypeBinding.getDeclaringMember()`.
- **G15, enum constructor parameter called `name` or `ordinal`**
  (commons-codec): clashed with the implicit enum parameters, now
  `j2c_name` and `j2c_ordinal`.
- **G16, default method implementing a generic superinterface method**
  (jsemver): `interface E extends Predicate<V> { default boolean test(V v) }`
  needs a bridge `test(Object*)` in the interface, which was defined but not
  declared, leaving implementing classes abstract. Interfaces now declare
  bridges for default methods.
- **G17, conditional with an intersection type** (zxing):
  `c ? "text" : 1` has type `Object & Serializable & Comparable<...>`. The
  branches now cast to its class bound (or `Object`), and an intersection
  dependency includes each bound's header instead of a missing
  `Object.hpp`.
- **G18, guessed stub bodies for getters and setters** (bcrypt): stubs for
  class path types guess `setX` and `getX` bodies from a field `x`, but
  compared the types backwards. commons-cli's `setType(Object)` beside a
  `Class` field became `this->type = a0`, and a getter whose return type is
  wider than its field got an empty body. The guess now needs the value's
  type to fit, and otherwise leaves the stub's `unimplemented_()` body.
- **G19, lambdas and anonymous classes in initializer blocks**
  (commons-codec): they did not capture the block's locals, and in an
  instance block did not receive the enclosing instance. Both decisions
  relied on the Eclipse Java model. Captures now compare where the variable
  is declared with the class body that uses it, and the outer instance
  comes from the initializer's static flag, which JDT reports through
  `getDeclaringMember()`.
- **G20, String `switch` without the `npc` helper** (jsoup): the case
  tests printed `npc(` without registering the helper, so a file whose only
  null check was a String switch did not compile.
- **G21, C++20 keywords** (bcrypt, jsoup, zxing): `requires`, `concept`,
  `co_await` and the other C++20 keywords are renamed like other C++
  keywords; `java.lang.module.ModuleDescriptor.requires()` broke stub
  headers.

G18's getters also register a hard dependency on the field type, since
converting to a wider return type needs the complete class.

Fixed by the first round of parallel fixer agents (see the orchestrator
workflow in VALIDATION_ROADMAP.md M2):

- **G22, inherited-method forwarders with erased parameters** (jsoup,
  RoaringBitmap): `Impl.printSuperCall` forwards an interface method to an
  inherited base method; when the base parameter is erased (`C` to
  `Object`), the upcast needs the argument's complete class, which is now
  included (`Spliterators.EmptySpliterator.OfRef` stub).
- **G23, enhanced for over a `new` expression** (javapoet):
  `for (T t : new TreeSet<>(...))` now emits `(new TreeSet(...))->iterator()`.
- **G24, inherited covariant return naming a subclass** (RoaringBitmap):
  the base's C++ signature returns the base type (to avoid a circular
  include), so the generated forwarder now downcasts with `java_cast`.

Round 2 (2026-10-11), from building the large tier (commons-lang, the Forge
engine); G28–G31 by four parallel fixer agents:

- **G25, enum `switch (this)` expression** (Forge): printed `npc(` without
  the helper.
- **G26, local declared in a switch case** (Forge): the hoisted
  initializer now includes the value's class (`ArrayList` to `List`).
- **G27, boxed array elements** (Forge): `counts[i]++` on an `Integer[]`
  stores a new box with `set()`, when the array and index can be evaluated
  twice. Also: guessed stub getters and setters never box (G18 follow-up).
- **G28, covariant returns in JDK headers** (java.time in commons-lang and
  Forge): `TransformUtil.returnType` already narrowed circular covariant
  returns, but the class path header and stub writers did not use it.
- **G29, enum constants with bodies and constructor arguments** (Forge
  `DeckFormat`): `asBaseConstructor` compared compilation units through the
  Eclipse Java model; without it, private constructors were never
  inherited by anonymous classes. It now compares top-level classes.
- **G30, array covariance and empty varargs** (commons-lang
  `StringEscapeUtils`): multi-dimensional array types now follow Java's
  `S[] <: T[]` rule (`Sub[][]` is a `Base[][]`), and `new X()` on a varargs
  constructor passes an empty array.
- **G31, default methods and record accessors implementing abstract
  methods** (commons-lang `MutableInt`, Forge `MutableBoolean` and
  `DefaultKeywordView`): an interface default overriding an inherited
  abstract method was treated as already declared, and record accessors
  were `const` and did not override.

#### G43 — Array bounds checks

**Status (2026-10-14):** Done. `Array.hpp`, `ObjectArray.hpp` and
`SubArray.hpp` check every element access (`[]`, `get`, `set`) with one
unsigned compare and throw `ArrayIndexOutOfBoundsException` with the JVM's
message (`Index 5 out of bounds for length 3`), before `ArrayStoreException`
as Java does. A negative size, in any dimension of `new int[a][b]`, throws
`NegativeArraySizeException` (`-1`) instead of C++'s `bad_array_new_length`.
The throwing helpers live in a generated `src/j2c_array.cpp`, so the headers
include no exception class; both exceptions are always dependencies. Checks
are on by default (Java semantics); `-DJ2C_NO_BOUNDS_CHECKS` in `CFLAGS`
removes the index checks for speed (size checks stay). Not covered: in
`a[i] += f()`, C++ evaluates `f()` before the check, where Java checks first.
Tested by `test/runtime/bounds`, `Java17Arithmetic`, its smoke harness and
`test-cli.js`.

#### G44–G48 — Found while writing the runtime (R2)

- **G44, surrogate escapes in string literals** (fixed 2026-10-12): a
  surrogate became a C++ `\x` escape, which absorbed a following hex digit,
  so a smiley followed by `b` ended in U+E00B. The literal is now split after
  the escape (`u"\xde00" u"b"_j`). Tested by `test/runtime/text` and
  `test-cli.js`.
- **G45, try-with-resources when `close()` throws** (fixed 2026-10-12):
  `close()` ran in a `finally_` destructor, which is implicitly `noexcept`,
  so a throwing `close()` called `std::terminate`, and `addSuppressed` was
  never used. Each resource now gets a guard that closes it on a normal exit
  (`on_normal_exit`, whose destructor may throw), and a `catch (Throwable*)`
  that closes it, adds a `close()` exception to the suppressed list and
  rethrows with `throw;` (JLS 14.20.3.1). A Java `finally` block that throws
  on a normal exit now works too. One case remains: a `finally` block that
  throws while another exception propagates still terminates, because the
  block runs in a destructor during unwinding. Tested by
  `test/runtime/rethrow` and `test-cli.js`.
- **G46, rethrowing a caught exception** (fixed 2026-10-12): `catch
  (Throwable t) { throw t; }` threw the static type `Throwable*`, so an outer
  handler for the subclass did not match. Every `Throwable` class now has
  `[[noreturn]] virtual void j2c_throw() { throw this; }`, and `throw e`
  becomes `npc(e)->j2c_throw();`, which also makes `throw null` raise
  `NullPointerException`. `throw new X(...)` keeps the plain `throw`, since
  its static type is exact. Tested by `test/runtime/rethrow` and
  `test-cli.js`.
- **G47, nested class names** (fixed 2026-10-13): `getClass().getName()`
  returned `Outer.Inner`, an empty name for anonymous and local classes, and
  `TODO[]` for every array of references (with a length of 31, reading past
  the end of the literal; `char[]` and others had wrong lengths too).
  `class_` now receives the binary name (`Outer$Inner`, `Outer$1`,
  `Outer$1Local`), primitive arrays are `[I` and the like, and an array's
  binary name (`[LOuter$Inner;`) is the first template argument of
  `SubArray`, a string literal (a C++20 class-type template argument), so
  every header's typedef of it names the same type. The runtime's
  `Class.getSimpleName` follows: `Local`, `""` for anonymous classes,
  `Inner[]`. Tested by `test/runtime/names` and `test-cli.js`.

- **G48, a base header that was never generated** (fixed 2026-10-12):
  hashids-java dropped to "checks" because `Class.hpp` included
  `java/lang/invoke/TypeDescriptor_OfField.hpp`, which was never written.
  JDT's `isEqualTo` reports the generic types `TypeDescriptor.OfField<F>`
  and `AbstractList<E>` as equal, so adding one replaced the other in a
  dependency set. `TransformUtil.addDep` now also compares binding keys.
  Tested by `test-cli.js` and the corpus.

#### G33–G35 — Found by the java.base experiment (Phase 10)

- **G33, `Iface.super.m()`** (fixed 2026-10-13): emitted `super::m`, which
  names the superclass, so it did not compile when the class's superclass
  lacks `m` (66 errors in java.base) and would call the wrong method when
  it has one. It now calls the named interface's implementation,
  `Iface::m(args)`, with the erased-return cast as before, for generic and
  nested interfaces, defaults inherited through another interface, and JDK
  interfaces (`Comparator.super.reversed()`). A lambda or method reference
  that uses `Iface.super` becomes a separate class, where Java cannot name
  it, so it is reported as the new `J2C-LAMBDA-INTERFACE-SUPER` instead of
  a compile error. Tested by `test/runtime/supers`, `Java17Features` and
  `test-cli.js`.
- **G34, a local class created from another class** (fixed 2026-10-13): a
  local class's constructor takes the variables it captures, and a
  creation site passed them by their local names, which do not exist in an
  anonymous class, a lambda's class or another local class (54 errors in
  java.base). `TypeInfoVisitor` now makes such a class capture the
  variables too, as javac does, repeating until every chain of creations
  is covered, and the creation passes its own copies. A class extending a
  local class (a local subclass or an anonymous `new Local() { ... }`) also
  captures the superclass's variables and passes them to its constructor.
  Creating a generic local class (`new Box<String>(...)`) passed neither
  the captured variables nor the outer instance; it now passes both.
  Tested by `test/runtime/locals`, `Java17Lambdas` and `test-cli.js`.
- **G35, interface diamonds with primitive specializations** (fixed
  2026-10-14): a class whose generic superclass implements an interface
  method stayed abstract in C++ when the method's erasure differs (65
  errors in java.base, all in `Spliterators` and `java.util.stream`).
  `Header.baseCallMethods` now forwards to the superclass:
  - Both erasures of an interface method that a primitive specialization
    redeclares (`Spliterator.OfInt.tryAdvance(IntConsumer)` and
    `OfPrimitive.tryAdvance(T_CONS)`); only methods with the same erased
    parameters are merged. Super calls declare no bridge.
  - A superclass's interface method that no superclass declares with the
    same erasure, implemented once the subclass gives the type arguments
    (`EmptyNode.OfRef extends EmptyNode<T, T[], ...>`). The argument is
    upcast to the superclass method's parameter type, so C++ does not pick
    the pure overload.

  Tested by `test/runtime/diamonds`, `Java17Lambdas`, its smoke harness
  and `test-cli.js`; java.base itself was not rebuilt.

#### G49–G50 — Found while writing the runtime (R4)

- **G49, numbers before the first string in a concatenation** (fixed
  2026-10-13): `i + j + " ints"` appended each operand, printing `12 ints`.
  Operands before the first `String` are now added as one C++ expression,
  whose promotions match Java's (`-fwrapv` wraps), and appended once.
  Tested by `test/runtime/concat`, `Java17Arithmetic` and `test-cli.js`.
- **G50, a default overriding a default** (fixed 2026-10-14): an interface
  default that overrode a super-interface's default was commented out as
  "already declared", so `Upper.level()` ran `Level.level()`'s body.
  `TransformUtil.baseDeclared` now declares every default method. Tested by
  `test/runtime/defaults`, `Java17Lambdas`, its smoke harness and
  `test-cli.js`.

Known runtime difference: `Double.toString` and `Float.toString` print the
shortest round-trip digits (the JDK 19+ algorithm), so a few values differ
from JDK 17 (`Double.MIN_VALUE` prints `5.0E-324` instead of `4.9E-324`).

### How to test a gap fix

Every fix goes through the same layers, each catching a different class of
failure:

1. **Fixture:** a `test/src/.../Java17<Feature>.java` file covering the form
   and its edge cases.
2. **CLI suite** (`test-cli.js`): translation exit code, key fragments of the
   generated C++, and `--check` messages for forms that stay unsupported.
3. **Corpus guard:** the whole `test/src` folder still translates, `--check`
   reports it clean, and no `MISSING` placeholders appear.
4. **Compile:** CI runs `make` on the fixture's generated project. This
   catches header and stub mismatches like the ones fixed in `daa16f1`.
5. **Runtime smoke harness** (`test/cpp/*Smoke.cpp`): link the generated
   libraries and assert results. It provides the `class_`, `unimplemented_`
   and `operator"" _j` hooks and avoids library calls until G7 exists. Run
   it under `-fsanitize=undefined,address`.
6. **Differential runs** (after G7): the same program on the JVM and
   natively, with identical output.

### How to find more gaps

- **Keep the probe suite.** Commit the feature probes used for this review
  under `test/probes/`, one file per feature area, with an expected-status
  list (translates, compiles, runs). Add a `probe.js` script that
  translates and compiles each probe in its own project and prints a
  pass/fail matrix with the first error per probe. Run it in CI as a
  non-blocking report, and promote a probe to a fixture when its gap is
  fixed.
- **Walk the JEPs.** Write one probe per language JEP from Java 8 to 17,
  including edge cases from each JEP's text:
  - 126: lambdas.
  - 213: private interface methods, anonymous diamond, and effectively
    final try-with-resources.
  - 286 and 323: `var`.
  - 361: switch expressions.
  - 378: text blocks.
  - 394: `instanceof` patterns.
  - 395: records.
  - 409: sealed classes.

  Also mine the JLS chapters on definite assignment and on scope, since
  pattern variables and switch rules interact with both.
- **Translate real code.** Run `--check` over small open-source Java 17
  projects and turn the per-kind summary into a ranked list. Then translate
  and compile them, and group the GCC errors by message to find systematic
  causes. That is how the `java.util` stub bugs show up.
- **Scan generated output.** Flag `MISSING`, `unimplemented_` calls in
  `src/` (not `ext/`), and GCC `-Wall` warnings such as `-Wreturn-type`.
- **Sanitize and fuzz.** Run every runtime harness under UBSan and ASan.
  Once G5 and G7 exist, generate random arithmetic and string expressions
  and compare JVM output with native output.
- **Track gaps.** Record each new gap as a GitHub issue labelled `language`,
  `semantics` or `runtime`, with the probe that shows it.

## Phase 10 — Feature roadmap: running translated programs

### Why

J2C turns Java into the same calls made on C++ versions of the Java classes.
`System.out.println("hi")` becomes
`npc(::java::lang::System::out())->println(u"hi"_j)`. For every JDK class the
code uses, J2C generates the C++ class's shape (a header) but not its
behavior: each method body is an `unimplemented_()` stub, and static fields
such as `System.out` are never initialized. Translated programs therefore
compile and link, but any call into the Java class library fails. C++'s own
library (`std::cout`, containers) can provide the behavior, but something has
to connect the Java API to it. That glue is a *runtime*.

The roadmap delivers this in two steps. Step 1 (option b) lets users bring
their own runtime. Step 2 (option a) ships one. Step 1 comes first because
it is small, useful on its own, and builds the mechanism step 2 needs.

### Experiment result (2026-10-11)

See [RUNTIME_EXPERIMENT.md](RUNTIME_EXPERIMENT.md). Translating OpenJDK 17's
java.base wholesale is not viable: J2C's dependency closure is the same
2,066 files (903k lines) for every program, needs 694 natives plus HotSpot
services (`Unsafe`, `VarHandle`, `MethodHandle`, module boot), and the stock
CLI cannot even resolve `java.lang` from sources. A curated runtime written
in Java and translated by J2C printed "hi" with 17 Java files (135 lines) and
70 lines of C++.

### Step 0 — Runtime-from-source support in J2C (target: 0.6.x, before step 1)

**Status (2026-10-11):** Done as `--runtime <directory>` (items 1–3 and
the native overlay of item 4), with `runtime/` and a CI step that runs a
translated Hello World under UBSan and compares its output with `java`.

1. **Sources replace the system library.** When the source path (or a new
   `--runtime <dir>`) provides `java/lang/Object.java`, call JDT's
   `setEnvironment(..., includeRunningVMBootclasspath = false)`. Today J2C
   always includes the running JDK, so JDT reports "package java.lang is
   accessible from more than one module" and translation fails with "Could
   not resolve Java type java.lang.ClassLoader".
2. **Hard dependencies match what the helpers include:** `java.lang.Class`
   (every class declares `class_()`/`getClass0()`) and
   `java.lang.ArrayStoreException` (included by `SubArray.hpp`), next to
   `ClassLoader`, `Cloneable` and `Serializable`.
3. **Link order:** runtime objects and libraries come before or around
   `-l$(NAME)` (or use `--start-group`), because the runtime refers back to
   classes in the program's libraries.
4. **Natives:** keep `-native.cpp` stubs, and let a runtime supply its own
   file per class (an overlay directory). Optionally support an OCNI-style
   C++ block inside the Java `native` method, like J2ObjC's `/*-[ … ]-*/`.

Tests: a fixture runtime of about 17 Java files (`test/runtime-mini/`) and a
CI job that translates Hello World against it, builds in WSL or Linux with
UBSan, and compares the output with `java`.

### Step 2 design (replaces "hand-written C++")

- The bundled runtime is **Java source translated by J2C**, plus a small
  C++ layer for natives and hooks (`class_`, `_j`, `init_jvm`, `make_args`,
  `unimplemented_`).
- The core classes are **hand-written, simplified Java**, written from the
  Java SE specification, J2ObjC-style: `Object`, `String` (UTF-16
  `char[]`, no compact strings), `System` (no initPhase1, properties or
  module boot), `PrintStream` (UTF-8 encoding in Java), `Class`, the boxed
  types, `Math`, and the `Throwable` family.
- **Plain-Java library classes** (`ArrayList`, `HashMap`, `TimSort`,
  `Collections`, `Arrays`, …) are **also written for the runtime from the
  specification. Decision (2026-10-11): no OpenJDK code.** OpenJDK is GPLv2
  with the Classpath Exception, and J2C's helper headers are EPL-1.0, which
  the FSF considers GPL-incompatible. Revisit only if the helper headers'
  copyright holders relicense them.
- Never translate java.base wholesale. The runtime manifest lists every
  provided class, and classes it does not list keep today's stubs.

### Translator gaps found by the experiment (add to Phase 9)

Each needs a fixture. Counts are from compiling the P1 closure of java.base.

- **G32, signature-polymorphic calls** (`MethodHandle.invoke*`,
  `VarHandle.*`) are emitted as `Object...` varargs (465 errors). Report a
  new problem ID, or lower `VarHandle` access modes to `std::atomic`.
- **G33, `Iface.super.m()`** emitted `super::m` (66 errors, 25 files).
  Fixed (2026-10-13; see Phase 9).
- **G34, a local class created from another class** did not pass its
  captured variables or outer instance (54 errors, 27 files). Fixed
  (2026-10-13; see Phase 9).
- **G35, interface diamonds with primitive specializations**: missing
  final overriders or bridges, so classes stayed abstract (65 errors).
  Fixed (2026-10-14; see Phase 9).
- **G36, unboxing a type-variable value to a primitive** emits
  unbalanced parentheses (12 errors).
- **G37, indexing an array whose type is a type variable** (3 errors).
- **G38, identifiers that are C macros** (`IOV_MAX`) need mangling
  (19 errors).
- **G39, a local variable named like a method that is called in its
  scope** (6 errors).
- **G40, a pattern variable hoisted into a clashing declaration**
  (2 errors).
- **G41, volatile fields as `std::atomic`**: `->` on arrays and compound
  assignment such as `|=` (2 errors).
- **G42, `LambdaDesugarer` output that JDT rejects**:
  - Diamond with constructor references (`Collectors.toMap`).
  - Member types of parameterized types (`Proxy`).
  - Captured-wildcard suppliers (`AbstractPipeline`).

  The writers also crash with "closures is null" if such a unit is ever
  written.

### Milestones (replace step 2's list)

1. **R1:** Hello World runs against `runtime/java` (step 0 tests).
   **Done (2026-10-11).**
2. **R2:** strings, `StringBuilder`, boxed types, `Math`, exceptions and the
   uncaught-exception printout.
   **Foundation done (2026-10-12):** concatenation of every type, Java's
   `Double`/`Float.toString` format, the boxing caches, `parseInt` and
   `parseDouble` with Java's messages, `System.arraycopy`, part of `Math`,
   the exception family, and the uncaught-exception printout with exit
   status 1; `npm run runtime` compares each `test/runtime` program with
   `java` in CI.
3. **R3:** collections and `Collections.sort` with comparators.
   **Done (2026-10-13):**
   - Collections: `ArrayList`, `LinkedList`, `ArrayDeque`,
     `PriorityQueue`, `HashMap`, `LinkedHashMap`, `TreeMap` (a red-black
     tree with sub-map views), and the hash, linked and tree sets.
   - Helpers: `Collections`, `Arrays` (stable merge sort), `Comparator` and
     its combinators, `List`/`Set`/`Map.of`, and `java.util.function`.
   - `HashMap` iterates in the JVM's order, including the head insertion
     of `computeIfAbsent`, `compute` and `merge`.
   - `toArray(T[])` and `Arrays.copyOf` keep the array's runtime type
     through a new virtual `ObjectArray::newInstance0`.
   - The runtime was written to avoid G33–G35, which stay open.
   - Tested by `test/runtime/collections` and `test/runtime/queues`,
     including 20,000 pseudo-random operations checked against the JVM.
4. **R4:** streams and a simplified `String.format`.
   **Done (2026-10-13):**
   - `java.util.stream`: `Stream`, `IntStream`, `LongStream`,
     `DoubleStream`, `Collectors`, `Collector` and `StreamSupport`, as
     sequential pull-based pipelines. Side effects run in the JVM's order,
     short-circuit operations end infinite streams, `count()` skips the
     pipeline when the size is known, and reusing a stream throws the
     JVM's `IllegalStateException`. Double sums use Kahan summation like
     the JDK.
   - `Optional` and its primitive forms, the summary statistics,
     `StringJoiner`, `Spliterator(s)`, `PrimitiveIterator`, `Enum`, and
     every `java.util.function` interface with its default methods.
   - `String.format`, `formatted`, `PrintStream.printf` and `Formatter`:
     every conversion but `%t` and `%a` with a precision, with flags,
     width, precision, argument indexes and the `IllegalFormatException`
     family with Java's messages.
     Rounding is half up from `Double.toString`'s digits, as in the JDK.
   - Tested by `test/runtime/optional`, `streams` and `format`;
     `runtime-programs.js` now runs `java` with `Locale.US` formatting.
   - Found G49 and G50 (Phase 9), since fixed; the runtime was written
     around both.
5. **R5:** optional Boehm GC (G8).
   **Done (2026-10-14):**
   - `make J2C_GC=boehm` links `-lgc`; the generated main file routes every
     `operator new` to `GC_MALLOC` and `operator delete` to `GC_FREE`, and
     a `--wrap=__cxa_throw` wrapper keeps Throwables in flight reachable.
   - `test/runtime/allocation` allocates about 1 GB with a bounded live set:
     16 MB peak RSS with the collector, 910 MB without; the bound is
     100 MB.
   - CI runs every runtime program again with the collector, at `-O2`
     under UBSan, collecting every 1000 allocations.

### Step 1 (option b) — Runtime integration (target: 0.6.x)

Goal: a user who has C++ implementations of some JDK classes can use them
instead of J2C's stubs, and anyone can see exactly what a runtime must
provide.

1. **`--runtime <dir>` CLI option and `j2c.runtime` setting.** The directory
   contains:
   - `include/`, with headers for the classes the runtime provides.
   - A library to link (`lib/libj2c-runtime.a`).
   - `j2c-runtime.properties`, listing the provided classes, the targeted
     Java API version (17), and the runtime version.
2. **Generator changes:**
   - For each class the manifest lists, generate no header or stub; include
     the runtime's header instead.
   - Generate stubs as today for everything else.
   - The generated Makefile adds the runtime's include path and library.
3. **Hooks become the runtime's job:** `class_`, string literals (`_j`),
   `unimplemented_`, and the `main` wrapper. A project without a runtime
   keeps today's behavior.
4. **Runtime contract document** (`docs/RUNTIME.md`):
   - The C++ conventions generated code relies on: name mangling (such as
     `toArray_`), `npc`, `clinit()`, the `default_init_tag` constructor,
     `class_()` and `getClass0()`, exceptions thrown as pointers, arrays
     (`Array.hpp`), and boxing calls.
   - How to start from J2C's own generated header for a class.
5. **Conformance check:** `j2c --runtime-check <dir>` translates a probe that
   touches every provided class and compiles it against the runtime. It
   reports any method the runtime's headers lack, compared with what J2C
   would declare.
6. **Pin the API:** in runtime mode, require the CLI to run on Java 17. J2C
   builds headers from the running JDK, and Java 11 and 17 differ.

Tests:

- A tiny fake runtime in `test/runtime-fake/` (for example, only
  `java.io.PrintStream` and `java.lang.System`).
- The CLI suite checks that no stubs are generated for the provided
  classes, the Makefile links the runtime, and `--runtime-check` reports a
  deliberately missing method.

Exit criteria:

- A Hello World translated with the fake runtime prints `hi` in CI.
- `docs/RUNTIME.md` is complete enough to write a runtime from.

Effort: about 3–5 days.

### Step 2 (option a) — Bundled `j2c-runtime` (target: 0.7.0 onwards)

Goal: "translate and run" works for ordinary programs without writing any
C++.

Design:

- The runtime is hand-written C++ built on the C++ standard library, not
  translated from OpenJDK. That keeps licensing simple (OpenJDK is GPL),
  keeps it small, and allows an idiomatic representation, such as UTF-16
  strings in a `std::u16string`.
- It lives in this repository (`runtime/`), is built by Maven or CMake for
  Linux, macOS and Windows, and is shipped in the release and the VSIX.
- `--runtime bundled` (the extension default) selects it. Classes it does not
  provide still get stubs, and `--check` lists library members a program
  uses that the runtime lacks, so users know before running.

Milestones, each finished only when its differential tests pass:

1. **Milestone 1 — Core (about 1–2 weeks).**
   - `Object`, `String`, `StringBuilder`, and the boxed types (with the
     `valueOf` cache and `parseInt`).
   - `Math`, and the `Throwable`, `Exception` and `RuntimeException`
     hierarchy, including the exceptions generated code throws.
   - `System.out` and `System.err` (`print` and `println`),
     `System.arraycopy`, and the clocks.
   - The hooks, and a `main` wrapper that prints "Exception in thread
     "main" …" with exit status 1.

   Done when Hello World, string processing, arithmetic and exception
   programs match the JVM's output.
2. **Milestone 2 — Collections and enums (about 2–3 weeks).**
   - `ArrayList`, `LinkedList`, `HashMap`, `HashSet`, and iterators.
   - `Arrays`, `Collections`, `Objects` and `Optional`.
   - Enum `values()` and `valueOf()`, and default methods such as
     `Comparator.comparing`.
3. **Milestone 3 — Memory (G8).** A `J2C_GC=boehm` build option that routes
   runtime and generated allocation through the Boehm collector. An
   allocation-loop test must keep resident memory bounded. Done
   (2026-10-14) as R5.
4. **Later, driven by probes and real programs:** streams, `String.format`,
   regular expressions, threads and `synchronized`, and file I/O.

Tests — differential testing:

- `test/programs/*.java` are small programs with `main`. For each, CI runs
  `java`, records the output and exit code, then translates the program,
  builds it with the runtime, runs it under UBSan and ASan, and requires
  identical output.
- Every runtime class lands with programs that exercise it, plus C++ unit
  tests for behavior that is easy to get subtly wrong: `String.hashCode`
  values, the `Integer` cache bounds, and `HashMap` iteration with
  collisions.
- `npm run probe` gains a "runs" outcome for probes with a `main`.

Risks:

- Fidelity to Java behavior is ongoing work. Each class needs its own tests
  against the JVM.
- The runtime's headers must keep matching what J2C generates. The step 1
  conformance check runs in CI to catch drift.
- Library breadth grows with demand. The probes and `--check` summaries over
  real code decide what comes next, rather than implementing the JDK
  wholesale.

### Order and release mapping

| Release | Delivers |
| --- | --- |
| 0.6.0 | Translator as documented; output compiles and links; runtime limitation stated |
| 0.6.x | Step 1: `--runtime`, `docs/RUNTIME.md`, conformance check, fake-runtime tests |
| 0.7.0 | Step 2, milestone 1: bundled core runtime; Hello World and string programs run |
| 0.8.0 | Milestone 2: collections and enums; milestone 3: optional garbage collection |

## Phase 11 — AI agent support (implemented 2026-10-11)

Goal: agents (Copilot agent mode, Claude Code, Claude Desktop, any MCP
client) can check Java for translation problems, fix them in the Java source
using J2C's hints, and translate, without parsing human-readable output.
User documentation is in `docs/AGENTS.md`.

1. **A — Agent-ready CLI:**
   - `--format json` for check and translate, with a versioned schema.
   - 1-based columns.
   - Stable problem IDs with explanations and hints (`ProblemCatalog`,
     `j2c explain`), and `j2c capabilities`.
   - Generated files listed in translate results.
   - An output-directory guard: translation moves a directory's contents
     aside, so it refuses a non-empty directory without the `.j2c-output`
     marker unless `--force` is given.
2. **B — MCP server:**
   - `j2c mcp [--root <dir>]` speaks JSON-RPC 2.0 over stdio, with protocol
     versions 2025-06-18, 2025-03-26 and 2024-11-05.
   - Four tools with input schemas and annotations: read-only `j2c_check`,
     `j2c_explain` and `j2c_capabilities`, and destructive `j2c_translate`.
   - Inputs, source paths and output must be inside the root. The
     translator's progress goes to stderr, so stdout carries only protocol
     messages.
   - The JSON code is hand-written (`Json`), adding no dependency.
3. **C — VS Code:**
   - The extension registers the bundled CLI as an MCP server per workspace
     folder (`contributes.mcpServerDefinitionProviders`,
     `onMcpCollection:j2c`, `j2c.mcp.enabled`). The API is detected at
     runtime, so the engine stays `^1.85`.
   - Commands use `--format json`, so diagnostics get exact columns, problem
     IDs as codes, and fix hints. Text output is still parsed for a custom
     older `j2c.cliJar`.
   - A modal confirmation is shown before passing `--force`.
4. **D — Agent guidance:**
   - `docs/AGENTS.md`: setup for each client, tool reference, workflow,
     safety.
   - `agents/claude/skills/j2c-translate/SKILL.md` and
     `agents/copilot/j2c.instructions.md`.
   - The repository's `AGENTS.md`, imported by `CLAUDE.md`, for agents
     working on J2C.

Tests:

- **CLI suite:**
  - The JSON schema, IDs, columns and hints.
  - `explain` (text, JSON, unknown ID).
  - `capabilities`, matched against the catalog.
  - JSON usage errors.
  - The guard refusing and preserving a user file, `--force`, and the marker
    making later translations unforced.
- **`test-mcp.js`:**
  - Initialize, including version fallback.
  - Tool listing and annotations.
  - A clean check, a check with problems (data, not an error), and a
    translation with generated files.
  - Writes and reads outside the root refused.
  - Explain, capabilities, unknown tool, invalid arguments, unknown method,
    ping, and a parse error.
  - Exactly one response per request, and only JSON-RPC on stdout.
- **Extension suite:**
  - JSON diagnostics.
  - The guard's decline, confirm and marker cases.
  - MCP definitions, refresh on settings change, disabled setting, and
    editors without the API.
- **Verified by hand:** the anonymous-`this` hint's fix translates and
  compiles.

Pending manual checks with real clients:

1. In VS Code with Copilot agent mode, the J2C tools appear for an open
   workspace. `j2c_check` returns problems, and `j2c_translate` asks for
   confirmation.
2. In Claude Code (`claude mcp add j2c -- java -jar <jar> mcp --root .`),
   the agent completes the check, fix and translate workflow on a project
   with a `J2C-LAMBDA-ANONYMOUS-THIS` problem.

## Phase 12 — Agent experience beyond the basics (planned)

Phase 11 gives agents typed access to J2C. These items make J2C do more of
the agent's work, ordered by value for effort:

1. **Structured quick fixes.** For mechanical problems (starting with
   `J2C-LAMBDA-ANONYMOUS-THIS` and `J2C-PATTERN-OUTSIDE-METHOD`), problems
   carry the exact text edits that fix them.
   - A `j2c_fix` MCP tool previews (by default) or applies them.
   - The VS Code extension offers them as quick fixes.
   - Effort: about 3–5 days.
2. **Project auto-setup.** Detect Maven and Gradle projects: use their source
   roots and test roots, and get the dependency class path from the build
   tool, so `paths: ["."]` just works in the CLI, MCP and the extension.
   Effort: 2–4 days.
3. **MCP prompts and resources.**
   - Prompts `/j2c-migrate` and `/j2c-assess`.
   - Resources: the problem catalog, the runtime contract, and generated
     files.
   - Effort: 1–2 days.
4. **Map C++ errors back to Java.** An option to emit `#line` directives, so
   compilers and debuggers report Java file and line numbers. A `j2c_build`
   tool runs `make` and returns errors mapped to Java. Effort: about one week.
5. **Migration readiness report.** A `j2c_report` tool and
   `j2c report --format markdown|json` show:
   - the percentage translatable;
   - blockers by kind and package;
   - JDK API use compared with runtime coverage, once the runtime exists.

   Effort: 2–3 days.
6. **Distribution.** Publish to the MCP registry, ship a Claude Code plugin
   bundling the skill and server configuration, and document configuration
   for Copilot's cloud coding agent. Effort: about 2 days.
7. **Faster re-checks.** Keep the parsed environment cached in the
   long-running MCP server, and re-check only changed files.
   Effort: 2–3 days.
8. **MCP progress and cancellation** for long translations. Effort: about one
   day.
9. **After the runtime:**
   - `j2c_run`: translate, build and run, and compare with the JVM.
   - An agent benchmark: Java projects with known problems, solved by an
     agent through the Claude Agent SDK, measuring success rate and cost.
     It runs nightly or on demand.

## Phase 13 — Unit test translation (JUnit to GoogleTest)

A project's own unit tests are the best evidence that a translation is
correct: the same tests pass on the JVM and then run against the C++.
GoogleTest runs and reports the tests. Assertions keep Java semantics,
because JUnit's assertion classes are implemented in the runtime rather than
mapped to `EXPECT_*` macros (Java equality, exceptions on failure, lambdas in
`assertThrows`). Mockito is reported, not translated.

| Step | Content | Depends on | Status |
| --- | --- | --- | --- |
| T1 | With `--tests`, report test features the wrappers cannot run: `J2C-TEST-*` problem IDs for mocking libraries, runners, rules and extensions, parameterized, repeated and dynamic tests, `@Nested`, timeouts, `@TestInstance`, private methods, and methods with parameters | — | Done (2026-10-11) |
| T2 | `--tests` generates a GoogleTest wrapper per runnable JUnit 4 or 5 class: a fresh instance per test; `@BeforeAll`, `@BeforeEach`, `@AfterEach` and `@AfterAll` in JUnit's order, including inherited and overridden methods; disabled tests skipped with their reason; `@Test(expected = ...)` checked with subclasses allowed; `j2c_gtest.hpp`; and Makefile `tests` and `test` targets (`GTEST_CFLAGS`, `GTEST_LIBS`, `TEST_LDLIBS`). Also exposed through MCP (`tests`, `testFiles`) and the `j2c.tests` setting. | — | Done (2026-10-11) |
| T3a | JUnit assertions and assumptions (JUnit 4 and 5) translated into `j2c_junit.hpp` helpers with JUnit's rules and messages, needing no runtime: a failed assertion throws a Java `AssertionError`; `assertThrows` uses the class literal's type; assumptions skip. Unsupported forms (`assertAll`, `assertTimeout`, lazy conditions, Hamcrest, AssertJ, Truth, non-literal `assertThrows` classes) are reported as `J2C-TEST-UNSUPPORTED-ASSERTION`. | — | Done (2026-10-11) |
| T3b | Runtime-backed parts: `equals` on JDK objects such as `String` and boxed numbers, and custom assertion and exception messages; then run the fixtures with real JUnit on the JVM and compare outcomes | Phase 10 milestone 1 | Planned |
| T4 | `j2c_test` agent tool: translate, build, run, and return results per test mapped to Java | T2, T3, Phase 12 item 4 | Planned |
| T5 | Optional gMock classes generated from Java interfaces, for new C++ tests | T2 | Planned |

Tests for T1 and T2:

- `test/junit/` has JUnit 5, JUnit 4 and inheritance fixtures, plus
  `expected-results.json` with the expected outcome of every test.
  `test/junit-api/` declares the JUnit annotations they use, so no download
  is needed.
- The CLI suite checks:
  - a clean `--check --tests`;
  - the generated wrapper list and their lifecycle order;
  - no wrappers without `--tests`;
  - every `J2C-TEST-*` problem at its exact line, and none without
    `--tests`.
- CI installs `libgtest-dev`, translates the fixtures with `--tests`, builds
  and runs them under UBSan, and compares every outcome with
  `vscode/scripts/check-gtest.js`. There are 34 tests, including 20 for
  assertions: 19 pass, 11 fail on purpose, and 4 are skipped. Verified
  locally with GoogleTest 1.15.2.
- The assertion fixtures (`Java17AssertionsTest`, `Java17Assert4Test`) cover
  every supported form:
  - passing and failing comparisons;
  - JUnit's double rules (NaN, -0.0);
  - user-defined `equals`;
  - arrays;
  - `assertThrows` with the right type, the wrong type, and nothing
    thrown;
  - assumptions;
  - an `AssertionError` caught by Java code;
  - JUnit 4's leading messages, and its always-failing double comparison.

  The CLI suite checks the translated calls and that each unsupported form
  is reported.

Not yet covered: running the same fixtures with real JUnit on the JVM and
comparing outcomes. That needs the JUnit JARs, so it is part of T3b.

## Go/no-go checklist

- [x] Destination repository owner/name/visibility selected.
- [x] Existing project history preserved; upstream repository left intact.
- [x] Release changes isolated, reviewed, and reproducible from a clean
      checkout; legacy edits parked off-branch.
- [x] Generated output excluded or explicitly justified.
- [x] Product scope and limitations are accurate in both READMEs.
- [x] Version and license information are consistent; third-party notices,
      license texts, and source locations ship in the JAR and VSIX.
- [ ] Marketplace publisher `juangp3` verified and publishing token available
      (needed only for Phase 8 step 6; the GitHub release can proceed
      without it).
- [ ] Default branch renamed to `main`.
- [x] CLI and Java 17 behavior tests pass.
- [x] Extension configuration and failure handling are tested (mocked host).
- [ ] Interactive VS Code translation verified in a clean profile.
- [x] Generated-C++ compile proof passes in CI for representative fixtures.
- [ ] CI passes on the exact frozen release commit (run ID recorded).
- [x] Release workflow builds artifacts from the tag, writes checksums and
      notes, and drafts a pre-release; rollback path documented.
- [ ] Draft release assets downloaded and verified against `SHA256SUMS`.
- [ ] Agent tools checked with a real client: Copilot agent mode lists the
      J2C tools, and Claude Code completes the check, fix and translate
      workflow (Phase 11).
- [x] Phase 9 gaps G1–G6 fixed and covered by fixtures, runtime smoke tests,
      and probes; G7 and G8 are documented as known limitations.

## Recommended release posture

Ship 0.6.0 as a preview: a GitHub pre-release, and a Marketplace listing with
the Preview badge. Do not market it as a production-grade,
semantics-preserving Java 17 compiler solely because the CLI and VS Code
extension build successfully.
