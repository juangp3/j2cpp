---
name: j2c-translate
description: Translate Java 17 code to C++20 with J2C, and make Java code translatable by fixing the problems J2C reports. Use when the user asks to convert, port or translate Java to C++, or to check whether Java code can be translated with J2C.
---

# Translating Java to C++ with J2C

J2C translates Java 17 source into C++20. Use its MCP tools (`j2c_check`,
`j2c_translate`, `j2c_explain`, `j2c_capabilities`) when they are
available. Otherwise run the CLI with `--format json`
(`java -jar j2c-cli.jar --check --format json <paths>`), which prints one
JSON object with the same fields.

## Workflow

1. **Learn the limits once.** Call `j2c_capabilities`. Note `limitations`:
   translated programs need a C++ implementation of the JDK classes they use,
   so they compile and link but do not run on their own.
2. **Check before translating.** Call `j2c_check` with the source folder. For
   a Maven or Gradle project, pass the project folder itself
   (`paths: ["."]`): J2C uses the build's source roots and gets the
   dependency class path from Maven or Gradle (`projects` in the result shows
   what it found). Otherwise pass dependency JARs in `classPath`; compile
   errors about missing types usually mean one is missing. A
   `J2C-PROJECT-CLASSPATH-UNRESOLVED` warning means the build tool is
   missing or failed: tell the user, or pass the JARs in `classPath`, rather
   than changing the Java code.
3. **Fix the Java, not the C++.** For each problem, apply its `hint` to the
   Java source at `file`:`line`:`column`. Call `j2c_explain` with the
   problem's `id` when the hint is not enough. Keep the Java behavior the
   same, and keep fixes minimal.
4. **Check again** until `status` is `ok`. If a problem's `id` is
   `J2C-LAMBDA-UNTRANSLATED`, stop and tell the user: it is a J2C bug, not a
   code problem.
5. **Translate.** Call `j2c_translate` with an output directory that is empty
   or earlier J2C output. If J2C refuses because the directory has other
   content, ask the user before retrying with `force: true`: the contents
   would be moved to a backup.
6. **Include the tests.** If the project has JUnit tests, pass `tests: true`
   to both `j2c_check` and `j2c_translate`. A Maven or Gradle project folder
   then adds its test roots and test class path by itself; otherwise add the
   test source folder to `paths` and the JUnit JARs to `classPath`. Fix reported test
   problems the same way (for example, replace Mockito mocks with small
   hand-written fakes). The generated GoogleTest wrappers are listed in
   `testFiles`.
7. **Build if possible.** If a C++20 toolchain is available, run
   `make -C <output>` (and `make -C <output> test` with tests) and report
   compiler errors to the user, quoting the generated file and line.

## Common fixes

| Problem id | Fix in the Java source |
| --- | --- |
| `J2C-LAMBDA-ANONYMOUS-THIS` | Inside the anonymous class, before the lambda, add a local typed as the implemented interface (for example `Runnable self = this;`) and use `self` in the lambda, or make the anonymous class a named nested class. |
| `J2C-PATTERN-OUTSIDE-METHOD` | Move the field initializer that uses `instanceof` with a pattern into a constructor, an initializer block, or a static helper method. |
| `J2C-LAMBDA-NON-DENOTABLE-TYPE` | Give the lambda parameters explicit types, or assign the lambda to a variable with an explicit functional interface type. |
| `J2C-LAMBDA-INTERFACE-SUPER` | Move the `Iface.super.m()` call into a private method of the enclosing class and call that method from the lambda. |
| `J2C-LAMBDA-INTERSECTION-TYPE-VARIABLE` | Cast to a single functional interface, or use concrete types in the intersection. |
| `J2C-COMPILE-ERROR` | Fix the Java error; add missing source roots (`sourcePaths`) or JARs (`classPath`). |

## Report back

Tell the user which Java changes you made and why, the output directory, the
number of generated files, and that the result needs a C++ runtime for the
JDK classes it uses before it can run.
