# J2C Modern Edition: Repository and Publication Plan

## Goal

Publish the modern Java-to-C++ CLI and VS Code extension as a distinct project
with its own repository, release process, documentation, and issue tracking.
Preserve the original repository as the upstream/legacy Eclipse project, and
retain Git history in the new repository.

The first public release is a **preview**. It must not imply complete
Java-to-C++ semantic equivalence.

## Current known state

Last rechecked on 2026-10-10 against local `modern-release`.

- **Repositories.** `juangp3/j2cpp` is public and has a single branch,
  `modern-release`, which is therefore its default branch. Locally, `origin`
  still points to `arnetheduck/j2c` (left intact) and `modern` points to
  `juangp3/j2cpp`. No release tags exist.
- **Pushed state.** `modern/modern-release` is at `409a0be`. The release
  infrastructure and licensing changes described below are local and not yet
  pushed.
- **Worktree.** Clean on `modern-release`. The legacy Eclipse IDE metadata
  (`plugin/.project`, `test/.project`, `plugin/META-INF/MANIFEST.MF`) and the
  `HandlerHelper.java` handler edit are parked on the local, unpushed branch
  `legacy/eclipse-ide-edits` (`e4261f0`). Generated `plugin/bin`, `test/bin`,
  `target`, the staged CLI JAR, copied license files, and VSIX packages are
  ignored.
- **Documentation.** The root README and VS Code README describe the CLI,
  extension, and incremental Java 17 support. `CHANGELOG.md` has a `0.6.0 —
  Preview` section that the release workflow uses as release notes.
- **Versions.** Maven and the VS Code extension are both `0.6.0`. The extension
  is marked `"preview": true`, so the Marketplace shows a Preview badge. The
  CLI is compiled with `--release 11`, matching the documented Java 11
  minimum.
- **Licensing.** The project's EPL-1.0 text is in `LICENSE`.
  `THIRD-PARTY-NOTICES.txt` inventories the 16 runtime dependencies (14 under
  EPL-2.0, 2 under Apache-2.0) and says where to get their sources (Maven
  Central sources JARs, each verified to exist). `licenses/` holds the full
  EPL-2.0 and Apache-2.0 texts plus every dependency's unmodified notice
  files. Those include the Apache-2.0 OSGi materials and Jakarta Commons code
  that some Eclipse bundles embed, along with the NOTICE that clause 4(d) of
  the Apache license requires us to keep. Shading previously kept only one of
  the 16 `about.html` files and dropped the rest. Shading now excludes those
  files, and the originals ship under `META-INF/j2c-licenses/` in the JAR and
  at the VSIX root.
- **Checks.** `vscode:prepublish` runs, in order:
  1. The Maven build.
  2. `check-licenses.js`: the inventory, `licenses/dependencies/` and the
     shaded JAR must all match `mvn dependency:list`, and no unattributed
     notice may remain at the JAR root.
  3. The CLI integration suite.
  4. The extension-logic suite.

  The extension-logic suite runs `extension.js` against a mocked `vscode`
  module. It covers settings pass-through, failure surfacing, and
  process-start failures, but not real editor integration.
- **CI.** `ci.yml` builds and tests on Java 11 and 17 and compiles the
  generated C++20 fixtures on Java 17. Runs `38058979996` and `38059779192`
  passed. The latter ran on `789d478`. HEAD `409a0be` changes only
  documentation and has no CI run of its own on record. `ci.yml` is now also
  reusable, and pushing a tag no longer triggers a separate CI run.
- **Release automation.** `release.yml` runs on `v*` tags:
  1. It runs the full CI matrix.
  2. It checks that the tag, Maven version, extension version, and changelog
     heading agree.
  3. It rebuilds from the tag and checks that the VSIX's bundled JAR matches
     the standalone JAR byte for byte.
  4. It writes `SHA256SUMS` and creates a **draft pre-release** with the JAR,
     VSIX, checksums, and notes.

  The version check, artifact staging, checksums, and notes steps were
  dry-run locally. The `gh release` step has not run yet.
- **Marketplace.** Publisher is `juangp3` at the user's direction. The item URL
  for `juangp3.j2c` returns 404; publisher ownership and a publishing token
  remain unverified.
- **Not yet done.** Interactive translation from a real VS Code window. The
  local `gh` CLI token is invalid (HTTP 401), so CI runs and releases can't be
  queried or created from this machine until `gh auth login` is run.

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
- Switch statements on boxed selectors (`Integer` and so on) still emit a
  native C++ `switch`, which does not compile. `String` switches are now
  translated as `equals` chains. This behavior predates the modernization.
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

## Recommended release posture

Ship 0.6.0 as a preview: a GitHub pre-release, and a Marketplace listing with
the Preview badge. Do not market it as a production-grade,
semantics-preserving Java 17 compiler solely because the CLI and VS Code
extension build successfully.
