# Changelog

## 0.6.0 — Preview

- Add `--check`, which lists every compile error and unsupported construct
  with `file:line` and a per-kind summary without writing output, and
  `--version`.
- VS Code: add **J2C: Check Translatability** and show problems from checks
  and failed translations in the Problems panel.
- Translate text blocks, try-with-resources (previously emitted as invalid
  C++, in all forms), `case X ->` switch expressions on enums and strings,
  and `switch` statements on strings.

- Add a standalone Java-to-C++ CLI and a VS Code extension that bundles it.
- Enable Java 17 source parsing and C++20 Makefile targets.
- Add initial translation for supported `instanceof` patterns and switch
  expressions, plus Java records.
- Translate sealed and non-sealed declarations as ordinary C++ inheritance by
  default, with advisory permits comments; add `--sealed-types reject` and the
  matching VS Code setting.
- Add focused CLI integration checks, C++20 compilation smoke tests for
  control-flow and record fixtures, and a GitHub Actions build/package workflow.
- Fail with `file:line` diagnostics, instead of emitting invalid C++, for
  lambdas, method references, and switch expressions on boxed selectors or in
  colon form on enums and strings. Java compile errors and unsupported syntax
  are reported without a stack trace.
- Fix regressions from moving to the Java 17 AST: array class literals and
  array types no longer crash translation, and enum `switch` labels are no
  longer emitted as `MISSING`.
- Fix arrow-form (`case X ->`) switch statements, which returned from the
  enclosing method or fell through instead of breaking.
- Parse each batch of sources in one shared compiler environment: faster, and
  dependency headers that per-file parsing omitted are now generated.
- Read Java sources as UTF-8 by default (`--encoding` overrides) and always
  write generated C++ as UTF-8, independent of the platform charset.
- Always generate the `Cloneable` and `Serializable` headers that the bundled
  `ObjectArray.hpp` includes, so small projects using enums or object arrays
  compile.
- VS Code: translation can be cancelled, concurrent runs are prevented, and
  translator output is decoded as UTF-8 streams.
- Ship a runtime dependency inventory, full EPL-2.0 and Apache-2.0 texts, and
  each dependency's original notice files in both the CLI JAR and the VSIX.

This preview is not a complete Java 17 translator. Generated C++ may need
manual changes and a compatible Java runtime implementation. Sealed
restrictions are not enforced by C++; compilation smoke tests cover only the
listed fixtures and do not establish broad compatibility or semantic parity.
