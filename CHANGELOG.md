# Changelog

## 0.6.0 — Preview

- Add a standalone Java-to-C++ CLI and a VS Code extension that bundles it.
- Enable Java 17 source parsing and C++20 Makefile targets.
- Add initial translation for supported `instanceof` patterns and switch
  expressions, plus Java records.
- Translate sealed and non-sealed declarations as ordinary C++ inheritance by
  default, with advisory permits comments; add `--sealed-types reject` and the
  matching VS Code setting.
- Add focused CLI integration checks, C++20 compilation smoke tests for
  control-flow and record fixtures, and a GitHub Actions build/package workflow.
- Ship a runtime dependency inventory, full EPL-2.0 and Apache-2.0 texts, and
  each dependency's original notice files in both the CLI JAR and the VSIX.

This preview is not a complete Java 17 translator. Generated C++ may need
manual changes and a compatible Java runtime implementation. Sealed
restrictions are not enforced by C++; compilation smoke tests cover only the
listed fixtures and do not establish broad compatibility or semantic parity.
