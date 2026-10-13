# Changelog

## 0.6.0 — Preview

First release of J2C Modern Edition: a standalone Java-to-C++ CLI and a VS
Code extension that bundles it, parsing Java 17 source and generating
C++20-targeted output.

### Command line and extension

- Standalone CLI with `--sourcepath`, `--classpath`, `--name`,
  `--sealed-types advisory|reject`, `--encoding`, `--check`, and `--version`.
- `--check` lists every compile error and unsupported construct as
  `file:line: message`, with a summary per kind, without writing output.
- Translation problems are reported as `file:line` diagnostics without a stack
  trace. Exit status is 0 on success, 1 for translation problems, and 2 for
  invalid arguments.
- Source directories skip `module-info.java` and `package-info.java`, which
  declare no types; a module declaration no longer fails the check.
- Sources are read as UTF-8 by default and generated C++ is always UTF-8,
  independent of the platform charset.
- VS Code: **J2C: Translate to C++** and **J2C: Check Translatability** in the
  editor and Explorer menus. Problems appear in the Problems panel; runs can
  be cancelled, and only one runs at a time.
- Problems have stable IDs with fix hints (`j2c explain`), and `--format json`
  prints machine-readable results with line and column. `j2c capabilities`
  describes supported features and limitations.
- Translation refuses to replace a non-empty output directory that J2C did
  not create (its contents would be moved to a backup) unless `--force` is
  given; the extension asks first.

### Runtime (preview)

- `--runtime <directory>` translates against a J2C runtime instead of the
  running JDK: its Java sources replace `java.*`, its C++ natives replace
  the generated stubs, and the Makefile builds its hooks.
- The repository's `runtime/` is written from the Java SE specification and
  covers:
  - `String`, `StringBuilder`, the boxed types, `Math` and `Objects`;
  - the exception classes, with Java's uncaught-exception printout and exit
    status;
  - the collections: `ArrayList`, `LinkedList`, `ArrayDeque`,
    `PriorityQueue`, `HashMap`, `LinkedHashMap`, `TreeMap`, their sets,
    `Collections`, `Arrays`, `Comparator` and `java.util.function`;
  - streams: `Stream`, `IntStream`, `LongStream`, `DoubleStream` and
    `Collectors`, sequential and lazy in the JVM's order, with `Optional`
    and its primitive forms and the summary statistics;
  - `String.format`, `formatted`, `printf` and `Formatter`, with Java's
    rounding and exception messages (no `%t` date conversions).

  `HashMap` and `HashSet` iterate in the JVM's order, so printed maps match.
- CI translates each program in `test/runtime`, builds it under
  UndefinedBehaviorSanitizer, and compares its output and exit status with
  `java`.
- Every translation now includes `java.lang.Class` and
  `java.lang.ArrayStoreException`, which generated code always uses, and
  programs link the program library again after the `-ext` library.

### Unit tests

- `--tests` translates JUnit 4 and 5 test classes and generates a GoogleTest
  wrapper for each runnable one, with JUnit's lifecycle order (including
  inherited methods), skipped disabled tests, and expected exceptions; the
  Makefile gains `tests` and `test` targets.
- With `--tests`, `--check` reports test features the wrappers cannot run
  (mocking libraries, runners and extensions, parameterized or repeated tests,
  `@Nested`, timeouts, `@TestInstance`, private or parameterized methods).
- JUnit assertions and assumptions (`assertEquals` with and without delta,
  `assertTrue`, `assertNull`, `assertSame`, `assertArrayEquals`, `fail`,
  `assertThrows`, `assertDoesNotThrow`, `assumeTrue`, and others) are
  translated into helpers that follow JUnit's rules and messages without a
  Java runtime. Other assertion forms are reported. Comparing JDK objects
  and showing custom messages need the runtime.

### AI agents

- `j2c mcp` serves J2C as a Model Context Protocol server with `j2c_check`,
  `j2c_translate`, `j2c_explain`, and `j2c_capabilities` tools, confined to a
  workspace root, for Claude Code, Claude Desktop, Copilot, and other MCP
  clients.
- The VS Code extension registers the server for each workspace folder
  (`j2c.mcp.enabled`), so Copilot agent mode gets the tools without setup.
- `docs/AGENTS.md`, a Claude Code skill, and Copilot instructions describe the
  check-fix-translate workflow; `AGENTS.md` and `CLAUDE.md` guide agents
  working on J2C itself.

### Java language support

- Lambda expressions and method references in every form, including generic
  functional methods and intersection-cast targets. Each is rewritten into the
  equivalent anonymous class.
- Pattern-matching `instanceof` in any expression position, with Java's flow
  scoping.
- Switch expressions and statements in arrow and colon form on primitive,
  boxed, enum, and string selectors.
- Records, including records with object, array, and nested-type components,
  and sealed hierarchies of records.
- Text blocks, try-with-resources (including `try (existingResource)`), and
  default, static, and private interface methods.
- Sealed and non-sealed declarations become ordinary C++ inheritance with
  advisory permits comments; `--sealed-types reject` rejects them instead.

### Java semantics in generated C++

- Integer division and remainder by zero throw `ArithmeticException`,
  `MIN_VALUE / -1` wraps, shift counts are masked, floating-point to integer
  casts saturate, and generated Makefiles build with `-fwrapv`.
- Generated constructors no longer bind a reference through a null pointer.
- Effectively final variables are captured by anonymous classes and lambdas,
  not only explicitly `final` ones.

### Fixes to the original translator

- Moving to the Java 17 AST no longer crashes on array class literals, and
  enum `switch` labels are no longer emitted as `MISSING`.
- Arrow-form switch statements no longer return from the enclosing method or
  fall through.
- `x %= y` on floating-point values computed `fmod(x, x)`.
- Casts to intersection types produced an invalid include and are now checked
  per component.
- JDK library stubs now match their headers: default and static interface
  methods get definitions, and inherited default methods forward to their
  interface, so programs using `java.util` collections compile and link.
- `Cloneable` and `Serializable` headers needed by `ObjectArray.hpp` are
  always generated.
- Rethrowing a caught exception (`throw t`) throws its dynamic type, so outer
  handlers for its class match, and `throw null` raises
  `NullPointerException`.
- A `close()` that throws in try-with-resources no longer terminates the
  program: its exception propagates, or is added to the suppressed list of
  the body's exception. A `finally` block may throw on a normal exit.
- Dependency headers are no longer dropped when two unrelated generic types
  compare equal in JDT (`TypeDescriptor.OfField` and `AbstractList`).
- Sources are parsed in batches with one shared compiler environment: faster,
  and dependency headers that per-file parsing omitted are generated.

### Quality checks and licensing

- CLI and extension integration suites run during packaging; CI compiles the
  generated C++ for every Java 17 fixture and runs native smoke tests under
  UndefinedBehaviorSanitizer.
- `test/probes` and `npm run probe` report which Java constructs translate and
  compile, to find and track translation gaps.
- The CLI JAR and VSIX ship a runtime dependency inventory, the full EPL-2.0
  and Apache-2.0 texts, and each dependency's original notice files.

### Known limitations

This preview is not a complete Java 17 translator. Generated programs need a
C++ implementation of the Java classes they use: JDK classes are generated as
stubs, so output compiles and links but does not run until a runtime is
provided. Objects are never freed without a garbage collector. Lambdas that
use `this` inside an anonymous class, and `instanceof` patterns in field
initializers, are reported instead of translated. Sealed restrictions are not
enforced by C++. The smoke tests cover the listed fixtures and do not
establish broad compatibility or semantic parity.
