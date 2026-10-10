# J2C Modern Edition: Repository and Publication Plan

## Goal

Publish the modern Java-to-C++ CLI and VS Code extension as a distinct project
with its own repository, release process, documentation, and issue tracking.
Preserve the original repository as the upstream/legacy Eclipse project, and
retain Git history in the new repository.

The first public release should be described as a preview unless the validation
gates below establish broader confidence. It must not imply complete
Java-to-C++ semantic equivalence.

## Current known state

- The public destination `juangp3/j2cpp` exists and is empty. The original
  `arnetheduck/j2c` repository remains intact.
- The local branch `modern-release` has an additional `modern` remote pointing
  to the new repository; `origin` still points to the original. No history or
  worktree changes have been pushed.
- The current worktree is based on `arnetheduck/j2c` and contains modernization
  source changes, documentation, generated files, and IDE metadata. Generated
  `plugin/bin` and `test/bin` are ignored, not deleted. IDE-generated
  `.project` and Eclipse manifest edits and a legacy handler edit are being
  kept out of the modern product scope.
- The root README and VS Code README describe the CLI, extension, and
  incremental Java 17 support. EPL-1.0 text is in the root `LICENSE` and
  copied into the VSIX. `THIRD-PARTY-NOTICES.txt` inventories the CLI's 16
  runtime dependencies and is copied into the VSIX; legal notice completeness
  still needs review.
- A Node CLI integration suite checks CLI help, sealed advisory/reject/invalid
  policies, record translation, and output paths containing spaces.
  `vscode:prepublish` builds the CLI and runs this suite.
- A fresh VSIX was packaged and installed in an isolated VS Code profile. It
  includes the extension, bundled CLI JAR, and project license. `npm audit`
  reports no vulnerabilities for the current dependency tree.
- Maven has no JVM test sources; integration tests are Node-based. Generated
  Java 17 control-flow and record fixtures both compile as C++20 with Ubuntu
  WSL's GCC 13.3.0; the same checks are now included in CI.
- Maven and VS Code extension versions are both `0.6.0`.
- The extension publisher is set to `juangp3` at the user's direction.
  Marketplace publication and interactive extension translation have not
  been performed.

These observations are a starting inventory, not an assertion that no other
release blockers exist. Recheck them on the release branch.

## Phase 1 — Decide repository identity and create the destination

1. **Done:** The destination is `juangp3/j2cpp`, public, and was created
   without an auto-generated README or license.
2. **Done:** A distinct repository was chosen instead of a GitHub fork.
3. **Done locally:** Preserve `arnetheduck/j2c`; local `origin` is unchanged
   and the new repository has an additional `modern` remote.
4. **Pending:** Carry the existing Git history into the new repository, then
   push only the reviewed modernization/release branch and necessary tags.
   Do not push unreviewed worktree output.

**Gate:** Destination identity and visibility are intentional; the original
repository remains intact; the new repository's history and remotes are
verified before publishing. Repository creation and local remote setup are
complete; migration/push remains pending.

## Phase 2 — Isolate and review the modernization changes

1. **Done:** Recorded the current branch, remotes, tracked diff, untracked
   files, and recent commits before changing repository configuration.
2. **Done:** Classified the staged changes as CLI/extension behavior,
   tests/fixtures, documentation, or release infrastructure; legacy IDE and
   handler edits remain outside the staged set.
3. **Done:** Reviewed generated Eclipse output and package paths. Added ignore
   rules for `plugin/bin`, `test/bin`, Maven output, the staged CLI JAR, and
   VSIX packages. Generated bin contents remain on disk and are not deleted.
4. **Done:** Preserved unrelated work. Eclipse IDE metadata and a legacy
   handler integration change remain unstaged and untouched.
5. Create focused commits for the CLI/extension, tests, documentation, and
   release infrastructure as appropriate. Review the final branch diff against
   the intended upstream base.
6. **Done:** Added and checked ignore rules for generated Maven output,
   Eclipse bin folders, extension dependencies, copied CLI JAR, generated
   license copy, and VSIX packages.

**Gate:** Every staged file is explained by the release and unrelated work is
preserved. The release set is staged and under final review; commit,
clean-checkout verification, and push remain pending.

## Phase 3 — Define product scope and project documentation

Document a narrow and honest first-release support contract:

- The CLI parses Java 17 source and generates C++20-targeted output.
- Java 17 feature support is incremental. Document the verified support for
  pattern-matching `instanceof`, switch expressions, records, and sealed-type
  advisory metadata separately; do not imply complete Java 17 support.
- Sealed types default to ordinary C++ inheritance with comments listing
  permitted direct subtypes. The generated C++ does not enforce Java's
  `permits` restriction. `--sealed-types reject` opts into rejecting sealed
  and non-sealed declarations.
- Generated output may require manual correction and a compatible Java runtime
  implementation. State known semantic gaps for records and any other
  verified limitations.
- State the Java runtime required to run the CLI and the C++20 toolchain
  requirement for generated Makefiles.

Then:

1. **Done locally:** Rewrote the root README as the entry point for the modern
   CLI and VS Code extension.
2. **Done locally:** Legacy Eclipse install/release steps are no longer in the
   modern root README; the original project history remains upstream.
3. **Done locally:** Updated the VS Code README to explain invocation, settings,
   source/class paths, CLI prerequisites, packaging, and limitations.
4. **Done locally:** Added `CHANGELOG.md` with preview scope and limitations.
5. **Done locally:** Documented the relationship to `arnetheduck/j2c`.

**Gate:** A new user can identify the supported entry points, run a documented
example, and understand what the translator does not guarantee. Documentation
was revised locally and should receive a final review before the repository is
made public through a push.

## Phase 4 — Verify identity, version, and licensing

1. **Partially done:** GitHub repo is `juangp3/j2cpp`; extension publisher is
   `juangp3` by user choice. Publisher registration/ownership still needs user
   verification before Marketplace publishing.
2. **Done for the candidate:** Maven and VS Code versions are both `0.6.0`;
   confirm this version before creating a release tag.
3. **Partially done:** Added EPL-1.0 text, consistent with the upstream
   README's license declaration. Confirm contributor authority and source
   licensing before publication.
4. **Done locally:** Generated a 16-artifact runtime dependency inventory and
   added it to the repository and VSIX. Confirm legal notice requirements and
   the authority to publish before release.
5. Verify the VS Code engine range, Java runtime requirement, command name,
   settings schema, and publisher ownership.

**Gate:** The source, CLI artifact, VSIX, Marketplace metadata, and release
page have consistent names, versions, requirements, and license information.
Still pending publisher verification, source-license authority, and legal
review of dependency notices.

## Phase 5 — Build automated behavior regression checks

Add repeatable tests and wire them into a documented command and CI. Initial
CLI integration coverage is in `vscode/scripts/test-cli.js` and runs during
extension prepublish:

### CLI behavior

- `--help`, missing required input, missing option values, and invalid
  `--sealed-types` values.
- Successful translation of a small Java input and source-dependency
  resolution.
- Paths containing spaces and platform-specific path-list behavior where
  supported.
- Expected non-zero exit status and useful diagnostics on translation errors.

### Java 17 fixtures

- **Covered by the CLI integration script:** pattern-matching `instanceof` and
  switch expressions translate successfully; expected switch-case return
  expressions are checked in the generated C++.
- Records with primitive, object, array, float, and double components.
- Canonical, compact, auxiliary, and explicit method cases where applicable.
- Generated record members do not collide with explicitly implemented
  `equals`, `hashCode`, or `toString`.
- Sealed advisory output contains the permitted direct subtypes and says that
  C++ does not enforce the restriction.
- Non-sealed output is identified as an open inheritance branch.
- Reject mode rejects both sealed and non-sealed declarations.

### Extension behavior

- The `j2c.sealedTypes` setting is passed to the bundled CLI.
- The packaged extension includes and invokes the bundled CLI.
- CLI failures surface to the user rather than looking like successful
  translation.

**Gate:** CLI integration checks and package generation pass locally.
Extension setting pass-through and UI-level failure behavior coverage remain
pending; CI has been configured but not yet run in GitHub.

## Phase 6 — Validate generated C++ and practical end-to-end use

1. **Done locally:** Translated `Java17ControlFlow.java` and
   `Java17Records.java` into separate generated projects using the CLI.
2. **Done locally:** Both projects compiled with `make`/GCC 13.3.0 in Ubuntu
   24.04 WSL using `-std=c++20`.
3. **Done locally:** Ran a C++ harness against generated `switchValue` output;
   cases `0`, `1`, `2`, and the default case returned their expected values.
4. **Done:** Added translation, compilation, and switch behavior checks to the
   Java 17 CI job.
5. One generated Java runtime stub emitted a pre-existing missing-return
   warning; compilation succeeded. This proof covers these fixtures only,
   not arbitrary generated Java.

**Gate:** Local compile proof passes. GitHub CI still must pass on the reviewed
release commit; keep preview limitations explicit.

## Phase 7 — Add clean-checkout CI and package verification

1. **Done locally:** Added a GitHub Actions workflow for build, CLI integration
   checks, and VSIX packaging.
2. **Configured:** CI matrix covers Java 11 and Java 17 runtimes; the CLI
   integration checks parse Java 17 source on both.
3. **Done locally:** Pinned `@vscode/vsce` 4.0.0 and produced a fresh VSIX.
   `npm audit` reports zero vulnerabilities.
4. **Done locally:** VSIX includes extension code, CLI JAR, README, project
   license, and a third-party runtime dependency inventory.
5. **Partially done:** Installed the fresh VSIX into an isolated profile and
   confirmed the `juangp3.j2c` extension ID and version. Interactive translation
   with the extension and configured path behavior remain unverified.
6. Build from a clean clone or CI checkout rather than relying on local
   `target`, Eclipse `bin`, or stale `vscode/lib` contents.

**Gate:** Local packaging, dependency audit, and clean-profile installation
pass. GitHub CI and interactive extension translation remain pending.

## Phase 8 — Release and post-release verification

1. Freeze the reviewed release commit; ensure the worktree is clean.
2. Run the full release-check command and retain its output.
3. Tag the agreed version only after all go/no-go gates pass.
4. Build the CLI JAR and VSIX from that tag in a clean environment.
5. Create a draft GitHub release with both artifacts, release notes,
   prerequisites, support limits, and checksums.
6. Verify the artifacts by downloading them and checking their checksums.
7. Publish the Marketplace extension only after verifying the publisher,
   extension ID, package contents, and authorization to publish.
8. Install the published Marketplace version in a clean profile and perform a
   final translation smoke test.
9. Keep a rollback/superseding-release procedure ready and monitor initial
   issues.

**Gate:** GitHub artifacts and the Marketplace package correspond to the same
tag and commit, can be downloaded/installed, and match the documented
requirements.

## Go/no-go checklist

- [x] Destination repository owner/name/visibility selected.
- [x] Existing project history preserved locally; upstream repository left
      intact.
- [ ] Release changes isolated, reviewed, and reproducible from a clean
      checkout.
- [x] Generated output excluded or explicitly justified.
- [x] Product scope and limitations are accurate in both READMEs.
- [ ] Project identity, version, publisher, license, and notices verified.
- [x] CLI and Java 17 behavior tests pass.
- [ ] Extension configuration and failure handling are tested.
- [ ] Generated-C++ compile proof passes in CI for representative fixtures.
- [ ] CI builds and tests the exact release commit.
- [ ] Fresh VSIX is packaged, inspected, installed, and exercised.
- [ ] Release artifacts, checksums, notes, and rollback path are ready.

## Recommended release posture

Treat the first independent release as a preview/beta unless all relevant
behavior, packaging, and generated-C++ gates pass with sufficient coverage.
Do not market it as a production-grade, semantics-preserving Java 17 compiler
solely because the CLI and VS Code extension build successfully.
