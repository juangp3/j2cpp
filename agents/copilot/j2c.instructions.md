---
applyTo: "**/*.java"
---

# Translating this Java code to C++ with J2C

This project's Java is translated to C++20 with J2C. When asked to translate
Java to C++, or to make Java code translatable:

- Use the J2C MCP tools: `j2c_check`, `j2c_translate`, `j2c_explain`, and
  `j2c_capabilities`. Without them, run
  `java -jar j2c-cli.jar --check --format json <paths>` in the terminal.
- For a Maven or Gradle project, pass the project folder (`paths: ["."]`):
  J2C uses the build's source roots and dependency class path. A
  `J2C-PROJECT-CLASSPATH-UNRESOLVED` warning means Maven or Gradle could not
  resolve the dependencies; report it instead of changing the Java code.
- Run `j2c_check` first. Fix every reported problem in the **Java** source,
  using its `hint` at `file`:`line`:`column`; `j2c_explain` with the problem
  `id` gives more detail. Never edit generated C++ to work around a check
  problem.
- Repeat until `j2c_check` reports `status: ok`, then call `j2c_translate`
  with an empty output directory. Ask before passing `force: true`, which
  moves an existing directory's contents to a backup.
- A `J2C-LAMBDA-UNTRANSLATED` problem is a J2C bug: report it to the user
  instead of rewriting the code around it.
- Translated programs need a C++ implementation of the JDK classes they use
  before they can run. Mention this when reporting a translation.

Typical fixes:

- `J2C-LAMBDA-ANONYMOUS-THIS`: inside the anonymous class, assign `this` to a
  local variable before the lambda and use the variable.
- `J2C-PATTERN-OUTSIDE-METHOD`: move the field initializer into a constructor
  or initializer block.
- `J2C-COMPILE-ERROR`: fix the Java error, or add missing source roots or
  JARs.
