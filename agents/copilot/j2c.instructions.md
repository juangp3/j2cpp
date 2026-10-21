---
applyTo: "**/*.java"
---

# Translating this Java code to C++ with J2C

This project's Java is translated to C++20 with J2C. When asked to translate
Java to C++, or to make Java code translatable:

- Use the J2C MCP tools: `j2c_check`, `j2c_fix`, `j2c_report`,
  `j2c_translate`, `j2c_build`, `j2c_explain`, and `j2c_capabilities`. Without them, run
  `java -jar j2c-cli.jar --check --format json <paths>` (and
  `java -jar j2c-cli.jar fix [--apply] <paths>`) in the terminal.
- For a Maven or Gradle project, pass the project folder (`paths: ["."]`):
  J2C uses the build's source roots and dependency class path. A
  `J2C-PROJECT-CLASSPATH-UNRESOLVED` warning means Maven or Gradle could not
  resolve the dependencies; report it instead of changing the Java code.
- To assess a migration (how much translates, what blocks it, which JDK API
  the J2C runtime lacks), call `j2c_report`, or run
  `java -jar j2c-cli.jar report <paths>` for a Markdown report.
- Run `j2c_check` first. Problems with `fixes` have a mechanical fix:
  preview it with `j2c_fix`, then write it with `j2c_fix` and `apply: true`.
  Fix every other problem in the **Java** source, using its `hint` at
  `file`:`line`:`column`; `j2c_explain` with the problem `id` gives more
  detail. Never edit generated C++ to work around a check problem.
- Repeat until `j2c_check` reports `status: ok`, then call `j2c_translate`
  with an empty output directory. Ask before passing `force: true`, which
  moves an existing directory's contents to a backup.
- To compile the translation, call `j2c_build` with the output directory
  (translate with `lineDirectives: true` first), or run
  `java -jar j2c-cli.jar build --output <output> --format json`. Its
  `diagnostics` give each compiler error's Java file and line (`java`) and
  generated C++ location (`cpp`); report them at the Java lines.
- A `J2C-LAMBDA-UNTRANSLATED` problem is a J2C bug: report it to the user
  instead of rewriting the code around it.
- Translated programs need a C++ implementation of the JDK classes they use
  before they can run. Mention this when reporting a translation.

Typical fixes:

- `J2C-LAMBDA-ANONYMOUS-THIS`: `j2c_fix` assigns `this` to a local variable
  (`var self = this;`) first in the method and uses it in the lambda.
- `J2C-PATTERN-OUTSIDE-METHOD`: `j2c_fix` moves the field initializer into a
  private helper method.
- `J2C-COMPILE-ERROR`: fix the Java error, or add missing source roots or
  JARs.
