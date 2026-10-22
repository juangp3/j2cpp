# J2C Modern Edition

J2C translates Java source code to C++. This repository packages the
standalone command-line translator and the **J2C Java to C++** VS Code
extension. It is a modernized continuation of
[arnetheduck/j2c](https://github.com/arnetheduck/j2c), whose original Eclipse
plugin remains in that repository.

> **Preview:** J2C is a source translator, not a complete Java compiler or
> runtime. Java feature support is incremental. Generated C++ may need manual
> changes and a compatible implementation of Java runtime behavior. Do not
> assume that arbitrary Java programs compile or preserve all JVM semantics.

## Requirements

- Java 11 or newer to run the CLI and VS Code extension.
- Maven to build the CLI from source.
- A C++20 compiler for generated Makefiles and C++ output.
- Node.js 22 or newer and npm to package the VS Code extension.

## CLI

Build the executable JAR from the repository root:

```sh
mvn package
```

Create an output directory and translate a Java file or source directory:

```sh
mkdir generated
java -jar target/j2c-cli.jar \
  --output generated \
  --sourcepath test/src \
  test/src/se/arnetheduck/j2c/test/Hello.java
```

Use the platform path separator between multiple source roots or classpath
entries (`;` on Windows, `:` on macOS/Linux). Source paths help resolve Java
source dependencies; classpath entries should identify dependency JARs or
class directories. If `--sourcepath` is omitted, J2C infers roots from the
selected inputs.

The `--sealed-types` policy defaults to `advisory`. Sealed declarations are
translated using ordinary C++ inheritance and generated comments list
permitted direct subtypes; **C++ does not enforce Java's `permits` restriction**.
Choose `--sealed-types reject` to stop translation when sealed or non-sealed
declarations are encountered.

Additional optional arguments are `--classpath <paths>`, `--name <name>`,
`--sealed-types advisory|reject`, and `--encoding <charset>`. Java sources are
read as UTF-8 unless `--encoding` says otherwise; generated C++ is always
written as UTF-8.

Translate against the J2C runtime so the program runs without writing any
C++:

```sh
java -jar target/j2c-cli.jar --runtime bundled --output generated test/runtime/hello
make -C generated all mains && generated/bin/Hello
```

`--runtime bundled` uses the runtime packaged in the CLI JAR (and in the VS
Code extension), so no repository checkout is needed: on first use it is
extracted to `~/.j2c/cache/runtime-<version>-<hash>` (or `J2C_CACHE_DIR`),
reused while its files are intact, and replaced when they are not.
`--runtime <directory>` uses another runtime directory, such as a checkout's
`runtime/` (name a directory called `bundled` as `./bundled`). Without
`--runtime`, the default, `java.*` comes from the running JDK and every JDK
class is a stub that fails at run time; with it, `java.*` comes from the
runtime, so JDK classes it does not provide are compile errors that
`--check` lists. See [runtime/README.md](runtime/README.md) for what it
covers.

`j2c run` translates, builds and runs a program in one step, runs the same
main class on the JVM, and compares the two:

```sh
java -jar target/j2c-cli.jar run --output generated test/runtime/hello -- one "two words"
java -jar target/j2c-cli.jar run --output generated --format json   --main Stdin --stdin test/runtime/stdin/input.txt test/runtime/stdin test/runtime/hello
```

It translates against the runtime (`--runtime`, default `bundled`) with
`--line-directives`, builds the main class's binary (`make bin/<Class>`, run
as `j2c build` runs `make`, so a failed build reports the compiler's errors
at their Java lines), runs it in a fresh working directory with the
arguments after `--` and the bytes of `--stdin <file>` as standard input,
compiles the same sources with the `javac` of the JDK running J2C, and runs
the class with `java` (in WSL when the build ran there: `--java <program>`,
else `J2C_WSL_JAVA`, else `java` on WSL's `PATH`, else the Windows JDK
running J2C, with a note). It compares standard output, standard error
without the JVM's stack frames, and the exit code, and prints the C++
program's output, how each run ended and the first line that differs.
`--main <class>` chooses among several classes with a main method,
`--no-compare` only runs the C++ program, `--timeout <seconds>` (default 60,
0 for none) stops each run and everything it started, and
`--output-limit <characters>` (default 20000) caps the output reported for
each stream. With `--format json` the result has `status` (`ok`, `differs`,
`failed`, `crashed`, `timeout` or `build-failed`), `cpp` and `jvm` (exit
code, `signal`, `timedOut`, `durationMillis`, `stdout` and `stderr` with
their byte counts and whether they were truncated), `comparison` (`equal`,
`differs` or `skipped`, with `firstDifference` of each stream: `line`, `cpp`
and `jvm`) and `build`.

Check whether code can be translated before writing anything:

```sh
java -jar target/j2c-cli.jar --check --sourcepath src/main/java src/main/java
```

`--check` takes the same inputs as a translation but no `--output`. It prints
every compile error and unsupported construct in the selected files as
`file:line: message`, followed by a summary per kind, and writes no files.
Select a source root to check a whole project. `--version` prints the CLI
version.

### Maven and Gradle projects

Pass a project directory (one with a `pom.xml`, `build.gradle(.kts)` or
`settings.gradle(.kts)`) and J2C sets itself up from the build:

```sh
java -jar target/j2c-cli.jar --check .
java -jar target/j2c-cli.jar --tests --output generated .
```

- **Source roots:** Maven's `src/main/java` (or `<sourceDirectory>`) of
  every module listed in `<modules>`, plus roots added by
  `build-helper-maven-plugin`; Gradle's `src/main/java` of the root project
  and each `include`d subproject, or the source sets Gradle reports. With
  `--tests`, the test roots (`src/test/java`, `<testSourceDirectory>`) too.
- **Class path:** J2C runs the project's wrapper (`mvnw`, `gradlew`) or else
  `mvn`/`gradle` from the `PATH`. Maven runs `dependency:build-classpath`
  (compile scope, or test scope with `--tests`); in a multi-module build
  whose modules depend on each other it also runs the `compile` phase with
  compilation skipped, so plugins bound to earlier phases run. Gradle gets an
  init script (`--init-script`) that prints `compileClasspath` and
  `testCompileClasspath`, retried with `--offline` if it fails. The build may
  download dependencies. Other modules' output is left out, because their
  sources are translated.
- What was detected, and the command run, is printed to stderr, and is in
  the JSON output as `projects`. Resolved class paths are cached in
  `~/.j2c/cache` (or `J2C_CACHE_DIR`), keyed by a hash of the build files.
- If the tool is missing or fails, J2C still uses the source roots and
  reports the warning `J2C-PROJECT-CLASSPATH-UNRESOLVED` (in `warnings` in
  JSON); dependency types then fail to resolve.

`--no-build-tool` never runs Maven or Gradle (a cached class path is still
used). `--project none` turns detection off, and `--project <directory>`
uses that directory's build for other inputs, such as one file in it.
Detection only looks at the input directories themselves, never at their
parents.

The output directory must be empty or earlier J2C output, which is marked with
a `.j2c-output` file. Translation first moves the directory's current
contents to a backup in the temporary folder, so J2C refuses other non-empty
directories unless you pass `--force`. Every selected file is checked before
that: when one has problems, the translation fails with all of them and
leaves the directory as it was.

Files that are only dependencies (found on the source path, not selected)
and do not compile are translated like library classes, as a header and
stubs without an implementation, with the warning `J2C-DEPENDENCY-ERRORS`
(in `warnings` in JSON) instead of an error.

Each problem has a stable ID (such as `J2C-LAMBDA-ANONYMOUS-THIS`).
`j2c explain <id>` describes it and how to fix it; `j2c explain` lists them
all. `--format json` makes translation and `--check` print a single JSON
object (problems with ID, file, line, column, message and hint; or the
generated files). `j2c capabilities` prints the supported features and
limitations as JSON.

Some problems have a mechanical fix, which `j2c fix` makes in the Java source:

```sh
java -jar target/j2c-cli.jar fix src/main/java           # preview as a unified diff
java -jar target/j2c-cli.jar fix --apply src/main/java   # write the fixes
```

It takes the same inputs and options as `--check` (except `--output`, `--name`
and `--force`). The preview prints a diff on stdout that `git apply` accepts;
`--apply` writes the fixes, checks again, and repeats until no fixable
problem is left. Fixes exist for `J2C-LAMBDA-ANONYMOUS-THIS` (a
`var self = this;` local used in the lambda), `J2C-LAMBDA-INTERFACE-SUPER`
(the `Iface.super.m()` call moved into a private method),
`J2C-PATTERN-OUTSIDE-METHOD` (the field initializer moved into a private
method), `J2C-TEST-TIMEOUT` (the timeout removed) and
`J2C-TEST-PRIVATE-METHOD` (`private` removed). With `--format json`, every
problem lists its fixes as exact edits (`file`, a `range` with 1-based
`line` and `column`, and `newText`).

`j2c report` assesses a migration before you start it:

```sh
java -jar target/j2c-cli.jar report . > migration-report.md      # Markdown
java -jar target/j2c-cli.jar report --format json src/main/java  # JSON
```

It takes the same inputs and options as `--check` (and `--runtime`) and shows
the files, types and methods that translate as they are (no problem from
`--check` lies within them), the blockers by problem ID and by package with
their top locations and how many `j2c fix` can fix, test-feature blockers
with `--tests`, the JDK types and members the code uses compared with what
the J2C runtime provides (the rest would be stubs that fail at run time),
and next steps.

`--line-directives` adds `#line` directives to the generated C++, so compiler
errors and debuggers (`gdb`: `break Foo.java:21`, `list`) refer to Java files
and lines. Each C++ line maps to the Java statement it came from, also in
lambdas; generated boilerplate keeps its C++ file and line. Paths in the
directives are relative to the output directory, where `make` runs. Without
the option the generated files are unchanged.

`j2c build` compiles translated code and reports the compiler's errors at
their Java lines:

```sh
java -jar target/j2c-cli.jar build --output build/cpp src/main/java  # translate with directives, then make
java -jar target/j2c-cli.jar build --output build/cpp --format json  # build earlier J2C output
```

It runs `make -k` in the output directory (in WSL on Windows when `make` is
not on the PATH; `--wsl auto|always|never`, `--wsl-distro <name>`,
`--make <program>`, `--target <target>`, `--jobs <n>`, or the variables
`J2C_MAKE`, `J2C_WSL` and `J2C_WSL_DISTRO`), parses GCC and Clang
diagnostics, and prints each at its Java file and line with its C++
location. A diagnostic in generated code outside any Java statement is
reported in C++ with the nearest Java line before it. With `--format json`
the result has `status`, `exitCode`, `diagnostics` (`severity`, `message`,
`java`, `cpp`, `mapped`, `notes`, `includedFrom`), `summary` and the end of
the `log`.

The CLI exits with status 0 on success (or a clean check), 1 when translation
fails or a check finds problems, and 2 for invalid arguments. `j2c build`
exits with 0 when `make` succeeds, else 1; `j2c test` with 0 when no test
failed, else 1; `j2c run` with 0 when the program behaved as on the JVM (or,
with `--no-compare`, exited with 0), else 1. `j2c fix` exits
with 0 when no problem is left that needs changes by hand (after applying,
or that the preview cannot fix), else 1. `j2c report` exits with 0 whenever
it prints a report.

## Unit tests (JUnit to GoogleTest)

`--tests` translates JUnit 4 and JUnit 5 test classes and generates a
GoogleTest file for each runnable one, so the project's own tests can run
against the C++ translation:

```sh
java -jar target/j2c-cli.jar --tests --output generated \
  --sourcepath src/main/java:src/test/java \
  --classpath junit-jupiter-api.jar src/main/java src/test/java
make -C generated test
```

GoogleTest only runs the tests and reports results. Each test gets a new
instance, as in JUnit; `@BeforeAll`, `@BeforeEach`, `@AfterEach` and
`@AfterAll` (and their JUnit 4 equivalents, including inherited ones) run in
JUnit's order. `@Disabled` and `@Ignore` tests are skipped with their reason,
and `@Test(expected = ...)` checks the thrown exception. A test fails when it
throws a Java exception, as in JUnit.

JUnit assertions and assumptions are translated into C++ helpers that follow
JUnit's rules, so they work without a Java runtime:

- `assertTrue` and `assertFalse`; `assertNull` and `assertNotNull`.
- `assertEquals` and `assertNotEquals`, including floating-point delta
  forms. Doubles compare as JUnit compares them: NaN equals NaN, and 0.0
  differs from -0.0.
- `assertSame` and `assertNotSame`; `assertArrayEquals`; `fail`.
- `assertThrows` with a class literal, which returns the exception, and
  `assertDoesNotThrow`.
- `assumeTrue` and `assumeFalse`, which skip the test.

These work from both JUnit 5's `Assertions` and JUnit 4's `Assert`. A failed
assertion throws a Java `AssertionError` with the message that version of
JUnit gives, such as `expected: <4> but was: <5>` (JUnit 5) or
`expected:<4> but was:<5>` (JUnit 4). Object equality calls the translated
`equals()`, so it works for the project's own classes.

Translated with the J2C runtime (`--tests --runtime bundled`), assertions
also compare JDK objects with Java's `equals` (strings, boxed numbers,
lists, maps) and show values and messages as JUnit does:

- values print with their Java `toString`, so `expected: <[1, 2]> but was:
  <[1, 3]>` and `expected: <0.3> but was: <0.30000000000000004>`;
- JUnit 5's custom message comes first, as in `names ==> expected: <a> but
  was: <b>`, and a `Supplier<String>` message is called only when the
  assertion fails;
- JUnit 4 compacts two strings around their difference, as in
  `expected:<ab[c]def> but was:<ab[X]def>`, and prefixes its message, as in
  `name expected:<[a]> but was:<[b]>`;
- the `AssertionError` carries the message, so Java code that catches it
  sees it, and GoogleTest shows it with any other exception's message.

Without a runtime, JDK objects are stubs: messages describe objects by
type, and custom messages are not shown. Other forms (`assertAll`,
`assertTimeout`, lazy conditions, Hamcrest, AssertJ, Truth) are reported by
`--check --tests`.

`j2c test` does all of this in one step and reports each test at its Java
method:

```sh
java -jar target/j2c-cli.jar test --output generated --runtime bundled \
  --sourcepath src/main/java:src/test/java src/main/java src/test/java
java -jar target/j2c-cli.jar test --output generated --format json \
  --filter CounterTest,ParserTest.parsesEmpty src/main/java src/test/java
```

It translates with `--tests` and `--line-directives`, builds the GoogleTest
binary (`make tests`, run as `j2c build` runs `make`, so a failed build
reports the compiler's errors at their Java lines), runs it, and prints one
line per test that did not pass, at its Java file and line with JUnit's
message, then the counts. With `--format json` each test has `javaClass`,
`javaMethod`, `file`, `line`, `status` (`passed`, `failed`, `skipped`,
`disabled`, or `notRun` when the test process crashed or timed out first),
`message`, `exception`, `timeMillis` and `cppTest`, and `summary` has the
counts and the test binary's exit code. `--filter` takes test classes,
`Class.method`, `Class#method` or method names (comma separated);
`--timeout <seconds>` (default 300, 0 for none) stops the test process and
everything it started.

`make test` needs GoogleTest (found with `pkg-config`, or set `GTEST_CFLAGS`
and `GTEST_LIBS`) and, like any translated program, a C++ implementation of
the JDK classes the tests use: the J2C runtime, or your own passed with
`TEST_LDLIBS`.

The repository's own JUnit fixtures (`test/junit`, and `test/junit-runtime`
for JDK objects) are run three ways, and every outcome and recorded failure
message must equal `expected-results.json`: with real JUnit on the JVM
(`npm run junit:jvm`, which downloads JUnit with Maven into `target/`), and
translated, with GoogleTest, against stubs (`npm run junit:gtest`) and
against the runtime (`npm run junit:gtest:runtime`).

With `--tests`, `--check` also reports test features the wrappers cannot run:
mocking libraries (Mockito, PowerMock, EasyMock), runners, rules and
extensions, parameterized, repeated and dynamic tests, `@Nested` classes,
timeouts, `@TestInstance(PER_CLASS)`, and private or parameterized test
methods. Each has a problem ID and a suggested fix.

## Using J2C with AI agents

`j2c mcp` serves J2C as a Model Context Protocol server, so agents such as
GitHub Copilot agent mode, Claude Code and Claude Desktop can check, fix,
translate and explain problems through typed tools. The VS Code extension registers it
automatically for each workspace folder. For Claude Code:

```sh
claude mcp add j2c -- java -jar /path/to/j2c-cli.jar mcp --root .
```

The tools are `j2c_check`, `j2c_fix`, `j2c_report`, `j2c_translate`,
`j2c_build`, `j2c_test`, `j2c_run`, `j2c_explain` and `j2c_capabilities`; the server also has the prompts
`j2c-migrate` and `j2c-assess`, and resources for the problem catalog, the
runtime's contract and translated files.
The server keeps check results between calls, so checking again after an
edit compiles only the changed files and the files that use them, with the
same result as a fresh check (`--check-cache <files>` bounds what is kept;
`0` turns it off). Tool calls report progress to clients that ask for it,
can be cancelled, and `ping` is answered while one runs.

See [docs/AGENTS.md](docs/AGENTS.md) for setup in each client, the tool
reference, and the recommended workflow. A ready-made
[Claude Code skill](agents/claude/skills/j2c-translate/SKILL.md), a
[Claude Code plugin](agents/claude/plugin) with the skill and the server,
[Copilot instructions](agents/copilot/j2c.instructions.md) and a
[configuration for Copilot's cloud coding agent](agents/copilot/mcp-config.json)
are included.

## VS Code extension

The extension provides **J2C: Translate to C++** from Java editor and Explorer
context menus. Select a Java file or source folder, then choose an existing
output directory. Problems appear in the Problems panel, and those with a
mechanical fix offer it as a quick fix. **J2C: Build Translated C++** runs
`make` in an output directory and shows the compiler's errors in the
Problems panel, at their Java lines when `j2c.lineDirectives` is on.

The Marketplace release is not published yet. To install a locally packaged
VSIX after building it:

```sh
code --install-extension <packaged-extension.vsix>
```

The extension invokes the bundled CLI and therefore requires Java 11 or newer.
To prepare the extension and its bundled CLI locally:

```sh
cd vscode
npm run vscode:prepublish
```

This builds and stages the CLI JAR in the extension directory. Package the
extension with the VS Code Marketplace packaging tool (`vsce`) before
installing the VSIX.

Configuration:

| Setting | Default | Purpose |
| --- | --- | --- |
| `j2c.sourcePaths` | `[]` | Java source roots; defaults to `java.project.sourcePaths`, then conventional `src` folders. |
| `j2c.classPath` | `[]` | Dependency JARs or class directories. |
| `j2c.cliJar` | `""` | Optional path to a custom CLI JAR; otherwise the packaged CLI is used. |
| `j2c.javaExecutable` | `"java"` | Java executable used to run the CLI. |
| `j2c.sealedTypes` | `"advisory"` | `advisory` emits ordinary inheritance and permits comments; `reject` rejects sealed and non-sealed declarations. |
| `j2c.runtime` | `"bundled"` | `bundled` translates against the J2C runtime shipped in the extension, so programs run (JDK classes it lacks are compile errors); `none` translates against the JDK with stubs, the CLI's default; or a runtime directory. |
| `j2c.lineDirectives` | `false` | Add `#line` directives, so compiler errors and debuggers refer to Java lines. |
| `j2c.build.make`, `j2c.build.wsl`, `j2c.build.wslDistribution` | `""`, `"auto"`, `""` | How **J2C: Build Translated C++** runs `make` (in WSL on Windows when `make` is missing). |
| `j2c.project` | `"auto"` | `auto` uses a Maven or Gradle build in the workspace folder for source roots and the dependency class path (see [Maven and Gradle projects](#maven-and-gradle-projects)); `none` turns this off. |

## Java feature support

Java 17 parsing is enabled. Translated language features:

- Pattern-matching `instanceof` anywhere a condition or expression can
  appear (`&&`, `?:`, loops, negated `if`), with Java's flow scoping: after
  `if (!(o instanceof T t)) return;`, `t` remains usable.
- Switch expressions and statements in both arrow (`case X ->`) and colon
  (`case X:`) form, on primitive, boxed, enum, and string selectors.
- Text blocks.
- Lambda expressions and method references (static, bound, unbound, `this::`,
  `super::`, constructor, array constructor, and generic functional
  methods), including intersection-cast targets such as
  `(Runnable & Serializable) () -> ...`. Each is rewritten into the anonymous
  class Java defines it to be equivalent to and translated like any anonymous
  class, so every lambda becomes a small C++ class. A bound receiver
  (`expr::method`) is evaluated once, when the reference is created; a null
  receiver fails when the reference is invoked, not when it is created.
- Default, static, and private interface methods.
- Try-with-resources, including `try (existingResource)`. Resources are closed
  in reverse order before `catch` and `finally` clauses run. An exception from
  `close()` while another exception is propagating terminates the program
  instead of being recorded as suppressed.
- Records, including generated component storage/accessors, construction,
  `equals`, `hashCode`, and `toString`.
- Sealed and non-sealed declarations under the advisory/reject policy above.

Integer arithmetic follows Java rather than C++: division and remainder by
zero throw `ArithmeticException`, `MIN_VALUE / -1` wraps, shift counts are
masked to the type width, and floating-point to integer casts saturate
(NaN becomes 0). Generated Makefiles compile with `-fwrapv` so overflow wraps;
keep that flag if you build the output another way.

Array accesses are checked as in Java: an index out of range throws
`ArrayIndexOutOfBoundsException` (`Index 5 out of bounds for length 3`), and
a negative size throws `NegativeArraySizeException`. To trade the index
checks for speed, build with `CFLAGS=-DJ2C_NO_BOUNDS_CHECKS make`; a bad
index is then undefined behavior, as in C++.

Review the generated C++ for each project. In particular, record component
assignment, array identity hashing, and reference stringification can differ
from JVM behavior. **The generated program does not run on its own:** JDK
classes (`String`, collections, `System.out`, and so on) are generated as
stubs that call `unimplemented_()`, so a runtime implementation of the Java
classes you use must be provided. `--runtime bundled` uses J2C's own
runtime, packaged in the CLI ([runtime/README.md](runtime/README.md)): the
core of `java.lang`, the collections, streams, `String.format`, regular
expressions, text input and files.

Generated objects are never freed by default. `make J2C_GC=boehm` links the
programs with the Boehm garbage collector (`libgc-dev` on Debian and Ubuntu,
found with `pkg-config bdw-gc`, or set `GC_CFLAGS` and `GC_LIBS`), which then
serves every allocation, so memory is reclaimed as in Java. Run `make clean`
when switching it on or off. It needs GNU ld (`--wrap`), covers `make mains`
but not `make test`, and finalizers never run. Threads that the runtime's
`Thread.start` creates are registered with the collector.

The following syntax is detected and stops translation with a `file:line`
diagnostic instead of producing incorrect C++:

- Lambdas and method references that use `this` inside an anonymous class
  (which cannot be named from the generated class), or whose
  intersection-cast target uses a type variable.
- `instanceof` patterns outside method bodies, such as in field
  initializers.

Use `--check` to list all of these at once.

## Build and validation

Build the CLI:

```sh
mvn package
```

Prepare the extension:

```sh
cd vscode
npm run vscode:prepublish
```

The `test/src` directory contains translator fixtures. Generated output has not
been validated for every Java feature or compiler/toolchain combination.
Running `npm run vscode:prepublish` from `vscode` runs the license check and
the CLI and extension integration suites. CI also translates the Java 17
fixtures, compiles each generated project, and runs native smoke tests for
control flow, records, lambdas, `instanceof` patterns, and integer arithmetic
under UndefinedBehaviorSanitizer. These checks are not a guarantee of semantic
parity or general Java compatibility.

`test/probes` holds feature probes, each declaring on its first line whether
it should compile or be rejected. `npm run probe` (from `vscode`) translates
every probe, compiles it when `make` is available, and fails when an outcome
changes, which surfaces both new gaps and fixed ones. Add a probe for any Java
construct you want to check.

See [CHANGELOG.md](CHANGELOG.md) for the preview candidate's scope and changes.

## Project relationship and licensing

J2C Modern Edition is a separate project derived from
[arnetheduck/j2c](https://github.com/arnetheduck/j2c). The original project
history and Eclipse plugin remain upstream. The project is licensed under the
Eclipse Public License 1.0; see [LICENSE](LICENSE). The extension packaging
step copies that license into the VSIX. Third-party dependencies have their
own licenses; see [THIRD-PARTY-NOTICES.txt](THIRD-PARTY-NOTICES.txt) and
[licenses/](licenses/), which hold the full license texts, each dependency's
original notice files, and where to obtain their source code. Both are
included in the CLI JAR (under `META-INF/j2c-licenses/`) and in the VSIX.
