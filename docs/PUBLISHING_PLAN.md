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
  (Phase 11), and `--tests` with JUnit assertions (Phase 13 T1–T3b).
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
  UBSan, runs the translated JUnit fixtures with GoogleTest, against stubs
  (34 outcomes) and against the runtime (73 outcomes and 44 failure
  messages), runs the same fixtures with real JUnit on the JVM, and runs the
  probes. Last verified locally on 2026-10-11
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
9. **npm (user step).** Publish the tarball attached to the release, not a
   rebuild, from an account that owns the `@juangp3` scope:

   ```sh
   npm login
   npm publish --access public juangp3-j2c-mcp-0.6.0.tgz
   npx -y -p @juangp3/j2c-mcp@0.6.0 j2c --version
   ```

10. **MCP registry (user step).** After npm, from a checkout of the tag
    (the registry checks that the npm package's `mcpName` matches):

    ```sh
    # mcp-publisher from https://github.com/modelcontextprotocol/registry/releases
    mcp-publisher login github     # as juangp3, for the io.github.juangp3 namespace
    mcp-publisher publish          # reads server.json
    curl "https://registry.modelcontextprotocol.io/v0/servers?search=io.github.juangp3/j2c"
    ```

11. **Claude Code plugin (user step).** The marketplace is this repository:
    once the tag is on the default branch, check in a clean Claude Code
    (`claude plugin validate .` first) that `/plugin marketplace add
    juangp3/j2cpp` and `/plugin install j2c@j2cpp` load the skill and the
    `j2c` server, and that `/mcp` lists its tools. Optionally submit the
    plugin to Anthropic's plugin directory.
12. **Copilot cloud agent (user step).** In a test repository, paste
    `agents/copilot/mcp-config.json` into **Settings > Copilot > MCP servers**,
    add `copilot-setup-steps.yml`, and assign an issue that needs
    `j2c_check`; the session log should show the J2C tools.

**Gate:** GitHub artifacts, the Marketplace package and the npm package come
from the same tag and commit, verify against `SHA256SUMS`, install cleanly,
and match the documented requirements.

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
  --gc boehm` (`npm run runtime:gc`; CI runs four programs per push and
  every one nightly) builds every runtime program at
  `-O2` with UBSan, collecting every 1000 allocations (`J2C_GC_STRESS`),
  compares the output with `java`, and requires the allocation program's
  peak RSS to stay under 100 MB (16 MB measured; 910 MB without the
  collector, which the non-GC run requires to exceed the bound). Without
  the `__cxa_throw` wrapper, the program prints corrupt exception messages
  at `-O2`.
- **Limits:** GNU ld; `make mains` but not `make test`; primitive arrays
  are scanned conservatively; no ASan. Threads (R7, 2026-10-21) are
  registered with the collector: `Thread.start` calls `pthread_create`,
  which `gc.h` with `GC_THREADS` turns into `GC_pthread_create`.

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

#### G61 — Found while writing the runtime (text input and files)

- **G61, Java names that are `<cerrno>` macros** (fixed 2026-10-15): a
  parameter named `errno` (`static native String errorText(int errno)`)
  or a constant named `ENOENT` expanded as the C library's macros, which
  every generated file sees because `<string>` includes `<cerrno>`; the
  header then declared `errorText(int32_t* (*)())`. `CName.keywords` now
  renames `errno` and the Linux errno codes with a trailing `_`, as it
  does for C++ keywords. Tested by `Java17Features.errnoNames` (built in
  CI) and `test-cli.js`.

#### G62 — Found while bundling the runtime

- **G62, include order that depends on where sources are** (fixed
  2026-10-21): JDT puts the compilation unit's path into the keys of
  generic source types (`Ljava/util/C:\...\HashMap~HashMap<TK;TV;>;`), and
  `BindingComparator` ordered dependencies, and so includes, by those keys.
  The same runtime in two directories, or a runtime against
  `LambdaDesugarer`'s temporary `j2c-desugar` directory, then gave a
  different include order (43 files of `test/runtime/hello`). The
  comparator now compares unit paths by file name and uses the full key
  only to break ties. Only include order changes (none in `test/src`'s
  output); tested by `test-cli-runtime.js`, which translates with copies of
  the runtime before and after `j2c-desugar` in the temporary folder.

#### G63–G64 — Found while writing the runtime (threads, R7)

- **G63, synchronized methods did not lock** (fixed 2026-10-21): the
  `synchronized` modifier was dropped silently, so a synchronized method
  ran unlocked, and `synchronized (null)` did not throw. A synchronized
  method now opens its body with the RAII helper of synchronized blocks
  (`synchronized synchronized_method_(this)`, or `(class_())` when static),
  released on every exit, exceptions included; a block's expression goes
  through `npc`. The `lock` and `unlock` hooks come from the runtime
  (`runtime/cpp/src/j2c_threads.cpp`), or from the user's runtime without
  `--runtime`. Tested by `test/runtime/threads` (counters, reentrancy,
  release on exception, `synchronized (null)`), `Java17Features` (built in
  CI) and `test-cli.js`. A `native synchronized` method does not lock; its
  hand-written body must.
- **G64, static initializers were not thread-safe** (fixed 2026-10-21):
  `clinit()` guarded recursion with a shared `static bool in_cl_init`, so a
  second thread arriving while the first ran the initializer skipped it and
  saw uninitialized static fields. The flag is now `thread_local`: the
  initializing thread still sees its class as initialized when it recurses,
  as in Java, and other threads block on the guard of the function-local
  static until the initializer finishes. Tested by `Java17Features` and
  `test-cli.js`.

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
   - CI runs the allocation-heavy runtime programs again with the
     collector on each push, and every program nightly, at `-O2` under
     UBSan, collecting every 1000 allocations.
6. **R6:** regular expressions, text input and files.
   **Regular expressions done (2026-10-15):**
   - `java.util.regex`: `Pattern`, `Matcher`, `MatchResult` and
     `PatternSyntaxException`, written in Java. The parser reports the
     JDK's descriptions and indexes; the matcher backtracks on a heap stack
     (single-character loops push one entry, not one per character), so
     100,000-character inputs do not overflow the C++ stack.
   - Syntax: escapes, classes with ranges, nesting and `&&`, the predefined,
     POSIX, `java*` and basic Unicode classes, anchors, capturing, named,
     non-capturing and atomic groups, backreferences, look-ahead and
     look-behind, all three quantifier modes, `\R`, `\Q...\E`, and the
     flags (inline too) but `CANON_EQ` and `UNICODE_CHARACTER_CLASS`, which
     throw `UnsupportedOperationException` like `\X` and Unicode scripts.
   - `String.matches`, `split`, `replaceAll` and `replaceFirst` use it;
     `split` keeps a fast path for literal patterns.
   - The JDK's capture quirks are emulated (groups set in atomic groups and
     look-arounds survive backtracking; a repeated fixed-length group
     resets itself to its last iteration; look-behind bounds overflow).
   - Tested by `test/runtime/regex`, `regex-split`, and `regex-fuzz`
     (4,000 pseudo-random patterns compared with the JVM).
   **Text input and files done (2026-10-15):**
   - `java.io`: byte and character streams with the JDK's buffering
     (`System.in`, `File*Stream`, `Buffered*`, `ByteArray*`,
     `InputStreamReader`, `OutputStreamWriter`, `BufferedReader`,
     `BufferedWriter`, `StringReader`, `StringWriter`, `PrintWriter`,
     `FileReader`, `FileWriter`), `File`, and `PrintStream` over any stream;
     `System.out` is a `PrintStream` over a 128-byte buffer with autoflush,
     as on the JVM. One native class, `java.io.NativeIo`, makes the POSIX
     calls and returns errno; exceptions carry the JVM's messages
     (`name (No such file or directory)`).
   - `java.nio.charset`: UTF-8, ISO-8859-1 and US-ASCII, with the JDK's
     measurement of malformed input (one U+FFFD per malformed sequence, as
     `String` and readers each place them) and `?` for unpaired surrogates.
   - `java.util.Scanner` without regular expressions: whitespace or literal
     delimiters, the US-locale integer and float syntax, and the JDK's
     exceptions and position after a failed `nextInt`.
   - `java.nio.file`: `Path`, `Paths`, and `Files` for reading, writing,
     listing, creating and deleting, with strict decoding and the JDK's
     `FileSystemException` subclasses.
   - Tested by `test/runtime/stdin`, `stdin-reader`, `files` and
     `streams-io`; `runtime-programs.js` feeds `input.txt` to standard input
     and runs each side in a new working directory, and runs WSL's `java`
     when the C++ program runs in WSL. Found G61 (Phase 9), now fixed.
7. **R7:** threads and `synchronized`.
   **Done (2026-10-21):**
   - Monitors: a reentrant monitor per object in a striped table
     (`runtime/cpp/src/j2c_threads.cpp`), so `Object` does not grow; a
     stripe's monitor is taken from a free list when an object is first
     locked or waited on and returned when it is idle. `synchronized`
     blocks and methods (G63) lock through the `lock`/`unlock` hooks;
     `Object.wait`, `notify` and `notifyAll` keep a waiter node per thread,
     so a notification goes to exactly one waiter and is not lost to a
     timeout or interrupt, and throw `IllegalMonitorStateException` with
     the JVM's message.
   - `java.lang.Thread` on POSIX threads: `Runnable` and subclass forms,
     `start`, `join`, `sleep`, `interrupt` (it ends `wait`, `sleep` and
     `join`; not blocking I/O), names (`Thread-0`, `main`), daemons,
     priorities (recorded), `holdsLock`, the uncaught-exception handlers
     and the JVM's "Exception in thread ..." printout; `ThreadLocal`.
     Program exit waits for the non-daemon threads, then abandons daemon
     ones; `System.exit` ends the process at once.
   - `java.util.concurrent`: the atomics (`std::atomic` natives),
     `ExecutorService` through `Executors.newFixedThreadPool` and
     `newSingleThreadExecutor` (JDK thread names, dead workers replaced),
     `Future`/`FutureTask`, `invokeAll`/`invokeAny`, `CountDownLatch`,
     `ReentrantLock` with `Condition`, `TimeUnit`, and `ConcurrentHashMap`
     under one lock but with the JDK's table growth and resize order, so a
     printed map matches; `Collections.synchronizedList`, `Set`, `Map` and
     `Collection`.
   - Thread safety of shared state: `PrintStream` and
     `BufferedOutputStream` hold their monitors as the JDK's do (lines from
     threads do not mix), `printStackTrace` holds the stream, string
     interning and the `class_` cache take locks, `Math.random` is
     synchronized, and static initializers run once (G64).
   - With `J2C_GC=boehm`, `Main.cpp` defines `GC_THREADS` and threads start
     through `GC_pthread_create`, so their stacks are scanned; the Makefile
     builds with `-pthread`.
   - Tested by `test/runtime/threads`, `threads-wait` (producers and
     consumers, ping-pong with `notify`, interrupts, timed waits, nested
     monitors), `threads-uncaught`, `threads-daemon` and `concurrent`, with
     and without the collector, and locally under ThreadSanitizer.

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

**Status (2026-10-21): bundling done.** `--runtime bundled` selects the
runtime packaged in the CLI JAR (`runtime/`'s `java/`, `cpp/`,
`required-classes.txt`, `api.txt` and README under `META-INF/j2c-runtime/`,
about 250 KB more JAR: 11,016,533 to 11,276,329 bytes), and so in the VSIX.
It is extracted to `~/.j2c/cache/runtime-<version>-<hash>` (or
`J2C_CACHE_DIR`), verified file by file on each use, replaced when damaged,
and installed by an atomic rename of a temporary directory, so concurrent
processes are safe (`BundledRuntime`). The MCP tools take
`runtime: "bundled"`, and the extension's new `j2c.runtime` setting
defaults to `bundled`. The CLI's default stays "no runtime": with a
runtime, `java.*` comes from it, so code using JDK classes it lacks
(`java.time`, AWT) would get compile errors instead of stubs; the
extension's default changes its output that way, as `CHANGELOG.md` states.
Tests: `test-cli-runtime.js` (same output as `--runtime runtime`, reuse,
four kinds of damage repaired, six concurrent processes on an empty and on
a damaged cache), `test-mcp.js`, `test-extension.js`, `test:licenses` (the
JAR's runtime files equal `runtime/` and carry no foreign notice), and a CI
step running four `test/runtime` programs with `--runtime bundled`; all 26
pass with it. The C++ is still built by the generated Makefile with each
program, not prebuilt per platform. See `docs/RUNTIME.md`.

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
   regular expressions (done as R6), threads and `synchronized` (done as
   R7), and file I/O.

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
| 0.7.0 | Step 2, milestone 1: bundled core runtime; Hello World and string programs run (`--runtime bundled` done 2026-10-21) |
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

   **Done (2026-10-19):**
   - Problems in JSON output carry `fixes: [{title, edits: [{file, range:
     {start: {line, column}, end: {line, column}}, newText}]}]`, 1-based and
     end-exclusive like problem columns (JSON schema version 2). Fixes are
     computed on the original source (`QuickFixes`): the lambda ones in
     `LambdaDesugarer`, with bindings, and the others after parsing, on a
     reparse of the original text, since problem columns come from the
     rewritten text.
   - Covered: `J2C-LAMBDA-ANONYMOUS-THIS` (`var self = this;` first in the
     method, `self` for `this`, `self.` for calls the generated class would
     capture, `this::m` as `(p0) -> self.m(p0)`), `J2C-LAMBDA-INTERFACE-SUPER`
     (a private helper at the end of the class), `J2C-PATTERN-OUTSIDE-METHOD`
     (the initializer moved into a private method), `J2C-TEST-TIMEOUT` and
     `J2C-TEST-PRIVATE-METHOD`. No fix is offered where the rewrite could
     change meaning: lambdas in anonymous-class field initializers, `super`
     in lambdas, generic or varargs `Iface.super` methods, and non-denotable
     helper types.
   - Not mechanical: compile errors, unresolved or untranslated lambdas,
     non-denotable types and intersection casts with type variables (they
     need a chosen type), sealed rejections (a CLI option, not the source),
     and the test problems that need a different design (mocking,
     extensions, parameterized tests, `@Nested`, `@TestInstance`, test
     parameters, unsupported assertions).
   - `j2c fix [--apply] [--format json|text]` (exit 0 when nothing is left
     that needs changes by hand, else 1), the `j2c_fix` MCP tool (destructive
     annotation, root-restricted writes), and VS Code quick fixes. Applying
     repeats check and fix until no fixable problem is left, and undoes a
     round that adds compile errors.
   - Tests: `test/fix` fixtures; `test-cli.js` checks exact ranges, the diff
     (applied with `git apply --check`), that applied fixes compile with
     javac, check clean and translate, idempotence, CRLF and BOM files, and
     unfixable problems; `test-mcp.js` and `test-extension.js` cover the
     tool and the code actions. The fixed fixtures' C++ builds with GCC
     (checked by hand in WSL).
   - Found on the way: JUnit 4 `@Test(timeout = ...)` was not reported when
     the timeout was an `int` constant (fixed).
2. **Project auto-setup.** Detect Maven and Gradle projects: use their source
   roots and test roots, and get the dependency class path from the build
   tool, so `paths: ["."]` just works in the CLI, MCP and the extension.
   Effort: 2–4 days. **Done (2026-10-19):**
   - `ProjectSetup` detects a build in an input directory (never in its
     parents): Maven `<modules>` (recursively), `<sourceDirectory>`,
     `<testSourceDirectory>` and `build-helper-maven-plugin` sources, with
     `${...}` properties; Gradle `include`s (and `projectDir` overrides) in
     `settings.gradle(.kts)`, with the source sets Gradle reports replacing
     the `src/main/java` defaults.
   - The class path comes from the wrapper (`mvnw`, `gradlew`) or the tool on
     the `PATH`: Maven `dependency:build-classpath` (pinned 3.7.0; compile or
     test scope), with the `compile` phase (compilation and resources
     skipped) when modules depend on each other, since Maven 3 resolves
     reactor dependencies only then; Gradle through an `--init-script` task
     printing `compileClasspath`/`testCompileClasspath` (lenient), retried
     with `--offline`. The build's own output (`target/`, `build/`, reactor
     artifacts in `~/.m2`) is dropped. Results are cached in `~/.j2c/cache`
     (`J2C_CACHE_DIR`) keyed by a SHA-256 of the build files, root and scope.
   - A missing, failing or disabled tool is the warning
     `J2C-PROJECT-CLASSPATH-UNRESOLVED`; the source roots are still used.
   - Options: `--project auto|none|<directory>` and `--no-build-tool` (CLI),
     `project` and `buildTool` arguments plus `j2c mcp --project none
     --no-build-tool` (MCP; `j2c_check`/`j2c_translate` now have
     `openWorldHint: true`), and `j2c.project` (VS Code, also passed to the
     MCP server). JSON results have `projects` and `warnings` (also on a
     failed translation); `capabilities` lists `buildTools`. Detection is
     printed to stderr.
   - Tests: `test/projects/maven-simple`, `maven-multi`, `gradle-simple`
     (commons-cli, JUnit); `test-cli-projects.js` (run by `test:cli`) checks
     roots, scopes, the cache and its invalidation, `--project`, a missing
     Maven (PATH with only Java), `--no-build-tool`, and Gradle through a stub
     `gradlew` (offline retry, failure); `test-mcp.js` `paths: ["."]` and
     server defaults; `test-extension.js` the setting and warning
     diagnostics. The Maven checks are skipped with a message without `mvn`.
     The init script was also verified by hand with Gradle 8.0.
   - Open: Maven profiles' modules and `test-jar` dependencies between
     modules, Gradle source sets other than `main`/`test`, and Kotlin or
     Groovy sources are not handled; Gradle's configuration cache may reject
     the init script on old Gradle versions (reported as the warning).
3. **MCP prompts and resources.**
   - Prompts `/j2c-migrate` and `/j2c-assess`.
   - Resources: the problem catalog, the runtime contract, and generated
     files.
   - Effort: 1–2 days.

   **Done (2026-10-20):**
   - `prompts/list` and `prompts/get`: `j2c-migrate` (`path`, `output`
     (default `build/cpp`), `tests`: check, preview and apply fixes with the
     user's consent, fix the rest by hand, translate, build) and `j2c-assess`
     (`path`, `tests`: run `j2c_report` and summarize, changing nothing).
     Unknown prompts and missing arguments are `-32602`.
   - `resources/list`, `resources/templates/list` and `resources/read`
     (`McpContent`): `j2c://problems` and `j2c://problems/{id}` (the catalog
     as `j2c explain --format json`), `j2c://runtime` (`runtime/README.md`
     and the types of the runtime's API by package, both bundled in the JAR
     under `META-INF/j2c-runtime/`), and `j2c://output/{path}`: a file
     inside the root and inside a directory with the `.j2c-output` marker
     (text, or a base64 blob for build products, at most 8 MB). The list
     shows the files of each translation made in the session. Unknown URIs
     and missing files are `-32002`; paths outside the root or J2C's output
     are `-32602`.
   - `initialize` advertises `prompts` and `resources` (`subscribe` and
     `listChanged` false) for all three protocol versions; `title` fields
     (server, prompts, resources, templates) are sent only for 2025-06-18,
     which added them.
   - Tests: `test-mcp.js` lists and gets both prompts, an unknown prompt and
     a missing argument, lists resources and templates, reads the catalog, an
     id, an unknown id, the runtime and an unknown URI, lists and reads
     translated files (also with `%2F`), and refuses a file without the
     marker, a path outside the root, the marker itself and a missing file;
     and checks that 2025-03-26 gets no titles.
4. **Map C++ errors back to Java.** An option to emit `#line` directives, so
   compilers and debuggers report Java file and line numbers. A `j2c_build`
   tool runs `make` and returns errors mapped to Java. Effort: about one week.

   **Done (2026-10-21):**
   - `--line-directives` (`lineDirectives` in `j2c_translate`,
     `j2c.lineDirectives` in VS Code). The writers wrap the C++ of every
     statement, method, field, field initializer and enum constant in marks
     with its Java line (`TransformWriter.preVisit2`/`postVisit`; in
     `ImplWriter` before hoisted patterns and boxing calls), which survive
     the buffers files are assembled from; `LineDirectives.resolve` replaces
     them when `Impl` or `Header` writes a file. Each non-blank C++ line
     gets the Java line of the innermost mark around its first code (a line
     starting with boilerplate, such as a field name before its initializer,
     takes the first mark on it); later C++ lines of the same node may
     count on within the node's Java lines, so most statements need one
     directive. Boilerplate outside every mark is mapped back to the C++
     file at its real line (`#line <next line> "src/.../Foo.cpp"`), chosen
     over the nearest Java line so errors in generated helpers stay
     findable; directives are only written where the compiler's numbering
     would be wrong, never inside block comments, continued lines or before
     access specifiers. Paths are relative to the output directory (where
     `make` runs, also in WSL), absolute on another drive.
   - Lambdas: `LambdaDesugarer` keeps every line in place (replacements keep
     the original line breaks), so the rewritten AST's line numbers are the
     original ones; the CompilationUnit property names the original file.
     Generated helpers (`Array.hpp`, `j2c_array.cpp`, stubs of classes
     without source, the Makefile, GoogleTest wrappers) have no directives.
   - Without the option the output is byte-identical: checked on the whole
     `test/src` tree against the previous JAR (and by `test-cli.js`, which
     also checks that removing the directives gives the plain output).
   - `j2c build --output <dir> [inputs]` (`Builder`): translates with
     directives, or requires the `.j2c-output` marker; runs `make -k -jN`
     (`--output-sync=target` with GNU make 4 and more than one job) with
     `LC_ALL=C`, natively or `wsl [-d distro] -- env LC_ALL=C make -C
     /mnt/...` on Windows when `make` is missing (`--make`, `--wsl`,
     `--wsl-distro`, `--target`, `--jobs`, `J2C_MAKE`, `J2C_WSL`,
     `J2C_WSL_DISTRO`); parses GCC and Clang `file:line[:col]: severity:`
     lines, notes and instantiation backtraces (nested as `notes`),
     `In file included from` chains, `In function` context lines and `ld`
     errors. A Java location is matched back to its generated line through
     the files' directives (the unit being compiled, from the Makefile's
     `Compile` line, or with an include chain the headers first, using the
     column to choose between C++ lines of one Java line); a C++ location
     gets the nearest Java line before it (`mapped: "nearest"`). JSON
     schema version 4. `j2c_build` (not read-only, not destructive,
     closed-world: never runs Maven or Gradle; targets `all`, `mains`,
     `tests`), and **J2C: Build Translated C++** in VS Code with its own
     diagnostic collection, notes as related information.
   - Tests: `test-cli-build.js` (run by `test:cli`): directives only add
     lines over the whole `test/src` translation, every directive back to a
     generated file names its real next line and every Java path exists,
     statements, declarations and lambda bodies (desugared) at their Java
     lines, no directives in helpers; parsing against a stub `make` with
     canned GCC output (statement, note, header warning with include chain,
     boilerplate, fatal error without directives, linker errors, text
     output, `-j1` without `--output-sync`, usage errors); and a real build
     of `test/build` with an error put into a statement's C++, reported at
     its Java line (skipped without `make`; in WSL on Windows).
     `test-mcp.js` and `test-extension.js` cover the tool and the command.
     CI builds `Java17Lambdas` with `--line-directives` under UBSan and runs
     its smoke harness. Checked by hand: every `Java17*` fixture builds and
     its smoke tests pass with directives, and gdb stops at
     `break Java17Lambdas.java:21` and lists the Java source.
   - Open: Java columns are not mapped (the compiler's column is the C++
     one); `j2c build` does not stop `make` when the CLI is killed; the
     nearest Java line of boilerplate can be the previous method's last
     line; errors inside macros spanning lines are not specially handled.
5. **Migration readiness report.** A `j2c_report` tool and
   `j2c report --format markdown|json` show:
   - the percentage translatable;
   - blockers by kind and package;
   - JDK API use compared with runtime coverage, once the runtime exists.

   Effort: 2–3 days.

   **Done (2026-10-20):**
   - `j2c report [--format markdown|json]` takes `--check`'s inputs and
     options (and `--runtime`) and exits with 0 whenever it prints a report;
     the read-only `j2c_report` tool returns the JSON as structured content
     and the Markdown as a second text block. JSON schema version 3.
   - Translatable is defined by lines: a file, type or method is
     translatable when no problem `--check` reports lies within it, so a
     problem blocks its file, every type around it and the method around it.
     Types are named type declarations (nested and local ones too); methods
     are the methods and constructors they declare, not those of anonymous
     classes (the lambdas rewritten into anonymous classes among them). The
     report gets each unit's AST from the check (`Transformer.CheckListener`),
     whose lines match the original source.
   - Blockers by id (count, files, quick fixes, hint, first five locations)
     and by package; with `--tests`, the `J2C-TEST-*` problems again as test
     blockers.
   - JDK use: references to `java.*`, `javax.*` and `jdk.*` types (type
     names, calls, constructors, `super(...)` calls, fields), counted by
     the receiver's type when it is a JDK type, and members keyed by erased
     parameter types. Coverage is the runtime's API, generated from
     `runtime/java` with bindings by `RuntimeApi` into `runtime/api.txt`
     (453 types), bundled in the JAR; `--runtime <dir>` reads that runtime
     instead. A member is provided when the type or a supertype in the
     runtime declares it. Implicit uses (concatenation, boxing, enhanced
     `for`) are not counted, and runtime methods that throw
     `UnsupportedOperationException` count as provided.
   - Next steps: an unresolved class path first, then `j2c fix --apply` for
     the fixable problems, a hint per problem id left, the JDK members the
     runtime lacks, and the command to run next.
   - Tests: `test/report` (three files and a JUnit test); `test-cli.js`
     checks the Markdown lines and the exact JSON (files 4/1, types 6/3,
     methods 11/8 with tests; 11 JDK types, 10 provided; `LocalDate.now()`
     missing), `runtime/api.txt` being current, `--runtime`, a clean file,
     usage errors, and, when the corpus is cloned (or `J2C_CORPUS_ROOT`
     points at one), semver4j (5 files, 11 types, 97 methods, all
     translatable; `java.util.Stack` not in the runtime); `test-mcp.js`
     checks `j2c_report`, root confinement and that it writes nothing.
6. **Distribution.** Publish to the MCP registry, ship a Claude Code plugin
   bundling the skill and server configuration, and document configuration
   for Copilot's cloud coding agent. Effort: about 2 days.

   **Prepared (2026-10-20); publishing is a manual step (Phase 8 steps 9–11):**
   - MCP registry: `server.json` (schema 2025-12-11) as
     `io.github.juangp3/j2c`, a GitHub namespace that `mcp-publisher login
     github` proves. The package is npm, `@juangp3/j2c-mcp` (`agents/npm`):
     the registry has no package type for a JAR, `mcpb` bundles need a
     `.mcpb` with "mcp" in its name or repository and a manifest made for
     Node, Python or binaries, and an OCI image would need a JRE and volume
     mounts for the workspace. The npm package bundles the CLI JAR and a
     Node launcher that runs it with `J2C_JAVA`, `JAVA_HOME` or `java` (bins
     `j2c-mcp` and `j2c`), and claims the name with `mcpName`. Clients still
     need Java 11+.
   - Claude Code plugin: `agents/claude/plugin` (`.claude-plugin/plugin.json`,
     `skills/j2c-translate/SKILL.md`, a copy of the maintained skill, and
     `.mcp.json` running `npx -y @juangp3/j2c-mcp@<version> --root .`),
     listed by `.claude-plugin/marketplace.json` (`j2cpp`), so users run
     `/plugin marketplace add juangp3/j2cpp` and `/plugin install j2c@j2cpp`.
     Layout checked against the Claude Code plugin and marketplace
     references; `claude plugin validate` was not run (no working Claude
     Code CLI on the development machine).
   - Copilot cloud coding agent: `agents/copilot/mcp-config.json` (pasted in
     the repository's Copilot MCP settings, with the tools to allow) and
     `copilot-setup-steps.yml` (Java, Node.js and a C++ compiler), documented
     in `docs/AGENTS.md`; the agent uses MCP tools only.
   - `vscode/scripts/check-distribution.js` (`npm run test:distribution`,
     run by `prepare.js` and by the release workflow with the tag's version)
     checks that the files parse, that their versions match `pom.xml` and
     `vscode/package.json`, that `server.json` and the npm package agree on
     name, identifier and repository, that the plugin's skill is the
     maintained one, and that the launcher starts the server from the JAR
     with the tools the Copilot configuration allows. The release workflow
     packs the npm package (`juangp3-j2c-mcp-<version>.tgz`, checked to hold
     the same JAR) and attaches it to the draft release.
7. **Faster re-checks.** Keep the parsed environment cached in the
   long-running MCP server, and re-check only changed files.
   Effort: 2–3 days. **Done (2026-10-20):**
   - JDT cannot resolve bindings incrementally, and its class path
     environment lives inside each `createASTs` call, so `CheckCache` keeps
     per-file check results instead and compiles only the files whose
     result may differ, with the others found through the source path as
     JDT does for files outside the batch. A session holds one environment:
     source paths, class path entries (size and modification time),
     options, project build files (by content); any difference is a new
     session, and sessions are dropped least recently used beyond
     `j2c mcp --check-cache <files>` (default 20000; 0 turns it off).
   - Every call hashes every Java file under the source paths. A file's API
     is a hash of its tokens outside method and initializer bodies (JDT's
     scanner: no comments or whitespace). A body edit re-checks the file; an
     API change, an added or a deleted file also re-checks, transitively,
     every file that mentions a declared type name as a word (and a new or
     vanished package's names), so a use of an inherited member is reached
     through the subclass. This is the conservative dependency rule: no
     binding-level graph.
   - A fresh check compiles all selected files together, so files a lookup
     by name cannot find (secondary top-level types, a package not matching
     the folder, a package also on the class path or in the JDK, syntax
     errors) are compiled alongside every re-check, and results are kept by
     that set. `module-info.java`, `package-info.java` changes and duplicate
     types check everything.
   - To make results comparable, problems are now sorted by file path in
     every check (stable, so a file's problems keep their order); before,
     the order depended on the desugared files' temporary paths.
   - Measured (warm server, Windows): joda-time (166 files, 0 problems)
     fresh CLI check 2.6 s, warm full check 1.2 s, re-check after a body or
     comment edit 0.10–0.23 s, after an API change 0.7–1.2 s (160 of 166
     files mention it); javaparser (740 files, 431 problems) fresh 10 s,
     body or comment edit 0.47–1.2 s, API change 4.2–6.6 s (all 740).
   - Tests: `test-mcp-sessions.js` (run by `test:mcp`) checks a sequence of
     16 edits through one server against a fresh `j2c --check --format
     json` for two selections (the folder, one file with an inferred source
     path): a body edit, a compile error in a body, a method removed from an
     indirect superclass (used through a subclass that never names it),
     restored, a file added and deleted, a type renamed and its uses
     updated, secondary types, a qualified name of a missing package and
     the package added, a syntax error and its fix, and a J2C problem; it
     also checks how many files each step compiled, `--check-cache 3` and
     `0`. By hand: random body, comment and API edits and their reverts on
     joda-time and javaparser compared with fresh checks (all equal).
   - Open: the API rule is coarse for tightly connected code (most of
     javaparser is re-checked after any API change); translation is not
     incremental.
8. **MCP progress and cancellation** for long translations. Effort: about one
   day. **Done (2026-10-20):**
   - `McpServer` reads stdin on its own thread and runs tool calls one at a
     time, in order, on a worker thread with the other requests (prompts,
     resources, `tools/list`), so results follow request order; `ping`,
     `initialize` and notifications are handled at once, and every message is written
     whole under one lock. Tools are registered in a table (`Tool`: the
     `tools/list` definition and a `call(arguments, progress)`) so new tools
     get progress and cancellation the same way. On end of input the server
     answers the calls it already received, then exits.
   - `Progress` counts phases and items: the CLI passes `Progress.NONE`, the
     server one per call that sends `notifications/progress`
     (`progressToken`, increasing `progress`, `total` when known, `message`
     from protocol version 2025-03-26 on) at most every 100 ms and never
     after the response. Reported: looking for changed files, the build
     tool resolving the class path, files checked (with the total), and
     translated files and dependency headers (no total).
   - `notifications/cancelled` (2025-06-18, 2025-03-26 and 2024-11-05 all
     define it the same way: `requestId`, optional `reason`; the receiver
     should stop and not respond) sets the call's flag. The translator
     checks it between files and headers, JDT through the monitor, and
     `ProjectSetup` polls it while the build tool runs and then kills the
     tool's process tree. A cancelled call gets no response; a queued one
     never starts; unknown or finished ids are ignored.
   - State after cancelling: a check changes nothing (that environment's
     kept results are dropped); a translation leaves the `.j2c-output`
     marker (now written before translating, also after a failure) and the
     files written so far, with the previous contents in the backup, and is
     replaced next time without `force`; `j2c_fix` keeps the rounds whose
     check finished and undoes the one being checked.
   - Tests (`test-mcp-sessions.js`): progress of a check of `test/src` has
     the token, increases, stays within `total`, and ends before the
     response; `ping` is answered while `test/src` is being translated; a
     cancelled translation stops (the next call is answered within a few
     milliseconds, against about 2.5–4 s for a full one), gets no response,
     leaves the marker without a `Makefile`, and the next translation
     replaces it without `force`; a queued call cancelled gets no response;
     a cancelled check stops a Gradle wrapper that sleeps for a minute
     within 0.1 s; 2024-11-05 progress has no `message`; exactly one
     response for every other request.
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
| T3b | With `--runtime`, assertions compare JDK objects (`String`, boxed numbers, lists, maps) with Java's `equals` and give JUnit's messages: values shown with `toString`, JUnit 5's `message ==>` prefix, `Supplier<String>` messages called only on failure, and JUnit 4's own forms (in `j2c::junit::junit4`), including `ComparisonFailure`'s compacted strings; the `AssertionError` carries the message. JUnit 4's expected-exception failures use JUnit's messages. The fixtures run with real JUnit 5.10 and 4.13 on the JVM (`junit-jvm.js`), and every outcome and recorded message is compared with `expected-results.json`, as are the GoogleTest runs against stubs and against the runtime (`junit-gtest.js`) | Phase 10 milestone 1 | Done (2026-10-15) |
| T4 | `j2c_test` agent tool: translate, build, run, and return results per test mapped to Java | T2, T3, Phase 12 item 4 | Done (2026-10-21) |
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
  `vscode/scripts/check-gtest.js` (through `junit-gtest.js` since T3b).
  There are 34 tests, including 20 for
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

Tests for T3b:

- `test/junit-runtime/` has a JUnit 5 and a JUnit 4 fixture of assertions on
  JDK objects (39 tests): strings, boxed numbers, lists and maps that are
  equal and that differ, custom and `Supplier<String>` messages (and a
  supplier that must not be called), blank messages, Java's `double` and
  `char` text, `assertNull`, `assertNotEquals`, `assertSame`, arrays of
  strings, JUnit 4's compacted strings and array lengths, an exception's
  own message, and Java code reading `AssertionError.getMessage()`. They
  need the runtime, so only the runtime and JVM runs include them.
- `expected-results.json` entries can be `{ "outcome": "fail", "message":
  "..." }`; 44 failures record JUnit's message, including the T3a ones.
- `vscode/scripts/junit-jvm.js` downloads
  `junit-platform-console-standalone` 1.10.5, `junit` 4.13.2 and
  `hamcrest-core` 1.3 with Maven into `target/junit-jvm/lib` (never into
  the shipped JAR), compiles both fixture directories against them (not
  `test/junit-api`), runs the console launcher with XML reports, and
  compares. Real JUnit agreed with every T3a expectation as written; the
  messages it reports are now recorded and checked.
- `vscode/scripts/junit-gtest.js` replaces CI's inline GoogleTest step:
  without `--runtime` it translates `test/junit` against stubs and compares
  outcomes only; with `--runtime` it translates both directories against
  `runtime/` and also compares messages. CI runs both under UBSan, then the
  JVM run. Verified locally with GoogleTest 1.14.0 and GCC 13.3.
- The CLI suite checks the `junit4::` calls, a `Supplier<String>` message
  passed as a lambda object, JUnit 4's wrapper messages, and a clean
  `--check --tests --runtime` of both directories.

T4 (done 2026-10-21):

- `j2c test --output <dir> [options] <inputs>` and the `j2c_test` MCP tool
  (`TestRunner`) translate with `--tests` and `--line-directives` (and
  `--runtime`/`runtime`), build the `tests` target with `Builder` (make, the
  WSL fallback, `--make`, `--wsl`, `--wsl-distro`, `--jobs`; a failed build
  is `status: "build-failed"` with `build.diagnostics` at their Java lines
  and `build.log`), run `bin/<name>-tests --gtest_output=json:...
  --gtest_color=no` (in WSL with `env LC_ALL=C timeout -k 5 <seconds>`), and
  parse the report.
- The translator lists the tests it wraps (`Transformer.generatedTests()`,
  `GeneratedTest`): GoogleTest suite and name, test class, method, declaring
  class, and the file and line of the method's name, recorded from every
  parsed unit, so an inherited test points at its superclass. The generated
  C++ is unchanged.
- Each test: `javaClass`, `javaMethod`, `declaringClass`, `file`, `line`,
  `status` (`passed`, `failed`, `skipped`, `disabled` for Java-disabled
  tests with the reason as `message`, `notRun`), `message` (JUnit's: the
  `AssertionError`'s, or an unexpected exception's own, as
  `check-gtest.js` reads them), `exception`, `failure` (every failure's
  text), `disabledReason`, `timeMillis`, `cppTest`, `cppFile`. `summary`:
  `total`, a count per status, `timeMillis` and `exitCode`. Top-level
  `status` is `ok`, `failed`, `timeout` or `build-failed`; JSON schema
  version 5.
- The JSON report lacks skip messages, so they come from the console
  output, which also gives the results when there is no report (a crash or
  a timeout): the test that was running fails with a message naming the
  exit code or the timeout, and the others are `notRun`.
- `--filter`/`filter`: test classes (simple or qualified names),
  `Class.method`, `Class#method` or method names, resolved against the
  generated tests into `--gtest_filter` (`Suite.*` for whole classes); a
  name matching no test is a usage error. `--timeout`/`timeout` (default
  300 s, 0 for none) kills the test process tree (and `timeout(1)` in WSL).
  The tool reports progress per compiled file and per finished test, and
  cancelling stops `make` or the tests. Like `j2c_build`, it never runs
  Maven or Gradle. The `j2c-migrate` prompt calls it when `tests` is set.
- Tests: `test-cli-test.js` (run by `test:cli`) uses a stub make that
  writes a stub test binary with a canned report and console: results at
  their Java lines (an inherited test at `AbstractBaseTest`), messages,
  exceptions and several failures, disabled and skipped tests, text output,
  exit code 0 when everything passes, filters and an unknown name, a crash,
  the timeout (the stub process is gone afterwards), a failed build, usage
  errors and inputs without tests; then a real run of `test/junit` and
  `test/junit-runtime` against `runtime/` (73 tests, in WSL with
  GoogleTest 1.14.0 and GCC 13.3) whose outcomes and messages all match
  `expected-results.json`, with every test's line naming its method.
  `test-mcp.js` covers the schema, a stubbed call with a filter, refused
  arguments and outputs, and the same real run; `test-mcp-sessions.js`
  progress per test and cancellation.
- Open: an output directory whose name has spaces breaks the Makefile's
  `NAME` (pass `--name`; already so for `j2c build`); assertion failures are
  reported at the test method, not the assertion's line (no stack traces);
  JUnit assumption messages are not shown (the wrapper says "Assumption
  failed"); without `--runtime` the binary needs the hooks a runtime
  provides (`junit-gtest.js` links `test/cpp/j2c_test_hooks.cpp`), which
  `j2c test` cannot add; a native Windows toolchain is untested.

Not yet covered: `assertIterableEquals`, `assertLinesMatch`, nested-array
`assertArrayEquals`, JUnit 4's `assertThrows` (`ThrowingRunnable`) and the
remaining forms listed under `J2C-TEST-UNSUPPORTED-ASSERTION`. When two
values print alike, JUnit 5 adds their identity hashes, which differ from
the JVM's.

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
- [x] Distribution files prepared and checked against the release version
      (`server.json`, `@juangp3/j2c-mcp`, Claude Code plugin and
      marketplace, Copilot cloud agent configuration; Phase 12 item 6).
- [ ] npm package published from the release tarball, and the MCP registry
      entry published with `mcp-publisher` (Phase 8 steps 9–10; user steps,
      after the GitHub release).
- [ ] Claude Code plugin installed from the marketplace and the Copilot
      cloud agent configuration tried in a test repository (Phase 8 steps
      11–12; user steps).
- [x] Phase 9 gaps G1–G6 fixed and covered by fixtures, runtime smoke tests,
      and probes; G7 and G8 are documented as known limitations.

## Recommended release posture

Ship 0.6.0 as a preview: a GitHub pre-release, and a Marketplace listing with
the Preview badge. Do not market it as a production-grade,
semantics-preserving Java 17 compiler solely because the CLI and VS Code
extension build successfully.
