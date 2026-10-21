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
    rounding and exception messages (no `%t` date conversions);
  - `java.util.regex` (`Pattern`, `Matcher`, `MatchResult`,
    `PatternSyntaxException` with the JDK's messages), a backtracking
    matcher whose stack lives on the heap, used by `String.matches`,
    `split`, `replaceAll` and `replaceFirst`: classes with intersection,
    groups (named, atomic), backreferences, look-around, greedy, reluctant
    and possessive quantifiers, and the inline flags.
  - text input and files: `System.in`, the `java.io` streams, readers and
    writers (`BufferedReader`, `PrintWriter`, `FileWriter` and the rest),
    `File`, `Scanner` (whitespace or literal delimiters, no regular
    expressions), `java.nio.file.Files` and `Path`, and the UTF-8,
    ISO-8859-1 and US-ASCII charsets, with the JVM's buffering, malformed
    input handling and exception messages.

  `HashMap` and `HashSet` iterate in the JVM's order, so printed maps match.
- `make J2C_GC=boehm` links a program with the Boehm garbage collector,
  which then serves every allocation (Java objects and the runtime's C++
  containers), so memory is reclaimed. Exceptions in flight stay reachable
  while `finally` blocks run. Programs must be single-threaded, and
  `make test` binaries are not covered.
- CI translates each program in `test/runtime`, builds it under
  UndefinedBehaviorSanitizer, and compares its output and exit status with
  `java`, then does the same with `J2C_GC=boehm`, collecting every 1000
  allocations. `test/runtime/allocation` allocates about 1 GB and must stay
  under 100 MB of resident memory with the collector. A program's
  `input.txt` is its standard input, and each run starts in an empty
  working directory, so programs can write files.
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
  JUnit 4's `@Test(timeout = ...)` is reported too (it was missed when the
  timeout was written as an `int`).
- JUnit assertions and assumptions (`assertEquals` with and without delta,
  `assertTrue`, `assertNull`, `assertSame`, `assertArrayEquals`, `fail`,
  `assertThrows`, `assertDoesNotThrow`, `assumeTrue`, and others) are
  translated into helpers that follow JUnit's rules and messages without a
  Java runtime. Other assertion forms are reported. Comparing JDK objects
  and showing custom messages need the runtime.
- With `--runtime`, translated tests compare JDK objects (strings, boxed
  numbers, lists, maps) with Java's `equals`, and failure messages match
  JUnit's: values shown with `toString`, JUnit 5's `message ==>` prefix,
  `Supplier<String>` messages called only on failure, and JUnit 4's own
  forms (`expected:<a> but was:<b>`, strings compacted as
  `ComparisonFailure` does, its `assertNull`, `assertSame`, `assertNotEquals`
  and array messages). The `AssertionError` carries the message, and
  GoogleTest reports every exception's message. JUnit 4's assertions now
  translate into `j2c::junit::junit4` helpers.
- JUnit 4's `@Test(expected = ...)` failures use JUnit's messages
  (`Expected exception: X`, `Unexpected exception, expected<X> but was<Y>`).
- The JUnit fixtures run with real JUnit 5.10 and 4.13 on the JVM
  (`npm run junit:jvm`) and, translated, with GoogleTest against stubs and
  against the runtime; CI checks that all three give the outcomes in
  `expected-results.json`, and that the JVM and the runtime give its
  failure messages (73 tests, 44 messages).

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
- Project auto-setup: a Maven or Gradle project directory as input (`.` on
  the command line, `paths: ["."]` in MCP, the workspace folder in VS Code)
  uses the build's source roots (Maven modules, `<sourceDirectory>`,
  `build-helper-maven-plugin` sources; Gradle includes and source sets),
  test roots with `--tests`, and the dependency class path from the
  project's wrapper or `mvn`/`gradle`, cached by a hash of the build files.
  `--project auto|none|<directory>` and `--no-build-tool` control it (MCP:
  `project`, `buildTool`, and `j2c mcp --project none --no-build-tool`;
  VS Code: `j2c.project`). A missing or failing build tool is the warning
  `J2C-PROJECT-CLASSPATH-UNRESOLVED`; JSON results gain `projects` and
  `warnings`, and `j2c capabilities` lists `buildTools`.
- Structured quick fixes: problems with a mechanical fix carry the exact
  edits to the Java source in `--format json` output (`fixes`, JSON schema
  version 2): `J2C-LAMBDA-ANONYMOUS-THIS` (`var self = this;`),
  `J2C-LAMBDA-INTERFACE-SUPER` (a private helper method),
  `J2C-PATTERN-OUTSIDE-METHOD` (the initializer moved into a method),
  `J2C-TEST-TIMEOUT` and `J2C-TEST-PRIVATE-METHOD`.
- `j2c fix [--apply] <paths>` previews the fixes as a unified diff, or with
  `--apply` writes them and checks again until no fixable problem is left. It
  exits with 0 when no problem needs changes by hand, else 1. The MCP server
  adds a `j2c_fix` tool (preview by default, `apply: true` writes), and the
  VS Code extension offers the fixes as quick fixes on J2C diagnostics.
- Migration readiness report: `j2c report [--format markdown|json] <paths>`
  (the same inputs and options as `--check`, plus `--runtime`) and the
  read-only `j2c_report` MCP tool show the files, types and methods that
  translate (those with no problem from `--check` within them) with
  percentages, the blockers by problem ID and by package with their top
  locations and quick-fix counts, test-feature blockers with `--tests`, the
  JDK types and members the code uses (by use count) compared with what the
  J2C runtime provides, and next steps. The Markdown reads on GitHub; the
  command exits with 0 whenever the report is produced. The bundled
  runtime's API is generated into `runtime/api.txt` and shipped in the JAR.
  JSON schema version 3.
- MCP prompts and resources: the `j2c-migrate` (check, fix, translate,
  build) and `j2c-assess` (report and summarize) prompts, and the resources
  `j2c://problems`, `j2c://problems/{id}`, `j2c://runtime` (the runtime's
  contract and types) and `j2c://output/{path}` (translated files inside an
  output directory with J2C's marker, under the workspace root).
- Distribution, ready to publish: `server.json` for the MCP registry
  (`io.github.juangp3/j2c`), the npm package `@juangp3/j2c-mcp` it names
  (`npx @juangp3/j2c-mcp`, bundling the CLI JAR; attached to the release),
  a Claude Code plugin (`agents/claude/plugin`, with the skill and the
  server) listed in `.claude-plugin/marketplace.json`, and a configuration
  for Copilot's cloud coding agent (`agents/copilot/mcp-config.json`).
  `vscode/scripts/check-distribution.js` checks that they agree with the
  release version.
- Faster re-checks in the MCP server: check results are kept between calls,
  and a re-check compiles only the changed files and the files that may use
  them (an edit inside method bodies compiles just that file), with the same
  result as a fresh check. `j2c mcp --check-cache <files>` bounds what is
  kept (default 20000 files, `0` turns it off). On joda-time, a re-check
  after a body edit takes 0.13 s instead of 1.2 s (2.6 s from the command
  line).
- MCP progress and cancellation: tool calls run on a worker thread, so
  `ping` is answered while one runs (other requests run in order); a call with
  `_meta.progressToken` gets `notifications/progress` (files checked, build
  tool running, files translated) at most every 100 ms; and
  `notifications/cancelled` stops a call at its next file or phase, stopping
  the build tool, with no response. A cancelled translation leaves the files
  written so far and the `.j2c-output` marker.
- Problems are listed by file path in every check (they were in JDT's
  order). The `.j2c-output` marker is written before translation starts, so
  a failed or cancelled translation's partial output is replaced next time
  without `--force`.
- C++ errors mapped back to Java: `--line-directives` adds `#line`
  directives to the generated `.cpp` and `.hpp` files (method bodies, field
  initializers and declarations, including lambdas, which map to the
  lambda's own lines), so GCC, Clang and gdb name Java files and lines
  (`break Foo.java:21` works). Each C++ line maps to the Java statement it
  came from; generated boilerplate maps back to the C++ file's own lines.
  Paths are relative to the output directory, where `make` runs. Without the
  option the output is unchanged, byte for byte.
- `j2c build --output <directory> [inputs]` translates the inputs with
  directives (or uses earlier J2C output), runs `make -k` (in WSL on Windows
  when `make` is missing; `--make`, `--wsl auto|always|never`,
  `--wsl-distro`, `--target`, `--jobs`, or `J2C_MAKE`, `J2C_WSL`,
  `J2C_WSL_DISTRO`) and reports GCC and Clang errors, warnings and notes
  (with their include chains and linker errors) at their Java file and line,
  with the C++ location of each and the end of the log. It exits with 0 when
  `make` succeeds, else 1. The `j2c_build` MCP tool does the same (not
  destructive, closed-world: it never runs Maven or Gradle; progress per
  compiled file, and cancellation stops `make` and its compilers),
  `j2c_translate`
  takes `lineDirectives`, and VS Code adds **J2C: Build Translated C++**
  (diagnostics in the Problems panel) and the `j2c.lineDirectives` and
  `j2c.build.*` settings. JSON schema version 4.

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
- Array accesses throw `ArrayIndexOutOfBoundsException` with the JVM's
  message, and negative array sizes `NegativeArraySizeException`. Define
  `J2C_NO_BOUNDS_CHECKS` (`CFLAGS=-DJ2C_NO_BOUNDS_CHECKS make`) to index
  without checks.
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
- `getClass().getName()` returns binary names, as on the JVM: `Outer$Inner`,
  `Outer$1` for anonymous and `Outer$1Local` for local classes, and `[I` or
  `[LOuter$Inner;` for arrays, which were all `TODO[]`. The
  uncaught-exception printout names nested exceptions the same way.
- `Iface.super.m()` calls the named interface's default method instead of
  the superclass's `m`. Inside a lambda or method reference it is reported
  as `J2C-LAMBDA-INTERFACE-SUPER`.
- A local class created from an anonymous class, a lambda or another local
  class, or extended by one, gets the variables it captures; generic local
  classes get their outer instance.
- Numbers before the first string in a concatenation are added, as in Java:
  `1 + 2 + " ints"` is `3 ints`, not `12 ints`.
- A default method overriding another default replaces it: it was left out
  of the header as "already declared", so callers ran the base's body.
- A class whose generic superclass implements an interface method with
  another erasure is no longer abstract in C++, as in `Spliterator.OfInt`
  implementations that extend a generic `OfPrimitive` class.
- Java names that are C library macros, `errno` and the errno codes such as
  `ENOENT`, are renamed with a trailing `_` like C++ keywords, so they no
  longer expand inside generated code.
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
provided. Objects are never freed unless the program is built with
`make J2C_GC=boehm`. Lambdas that
use `this` inside an anonymous class, and `instanceof` patterns in field
initializers, are reported instead of translated. Sealed restrictions are not
enforced by C++. The smoke tests cover the listed fixtures and do not
establish broad compatibility or semantic parity.
