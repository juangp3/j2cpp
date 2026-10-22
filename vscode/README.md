# J2C Java to C++ for VS Code

This extension adds **J2C: Translate to C++** and **J2C: Check
Translatability** to Java editor and Explorer context menus.

- **Translate**: select a Java source file or source folder, then choose an
  existing output directory. Generated implementation files are written below
  `src`; dependency headers and stubs are written below `ext`. By default
  (`j2c.runtime` is `bundled`) Java code is translated against the J2C
  runtime shipped with the extension, so `make all mains` in the output
  builds programs that run: it covers the core of `java.lang`, the
  collections, streams, `String.format`, regular expressions, text input and
  files, and JDK classes it does not provide are compile errors (Check lists
  them). Set `j2c.runtime` to `none` to translate against the JDK instead,
  where every JDK class is a stub that fails at run time (the CLI's
  default), or to the path of another runtime directory.
  Objects are never freed unless the program is built with
  `make J2C_GC=boehm`, which links the Boehm garbage collector.
- **Check**: reports every compile error and unsupported construct in the
  selection without writing anything. Run it on a source folder to see whether
  a project can be translated.
- **Build Translated C++** (command palette, or a J2C output folder in the
  Explorer): runs `make` in a J2C output directory through the CLI's
  `j2c build` and shows the compiler's errors and warnings in the
  **Problems** panel. With `j2c.lineDirectives` on when translating, the
  generated C++ carries `#line` directives, so they appear at their Java
  lines (with the C++ location in the message), and debuggers such as gdb
  step through the Java source. On Windows, `make` runs in WSL when it is not
  on the `PATH` (`j2c.build.wsl`, `j2c.build.wslDistribution`; WSL needs GNU
  make and g++ 13 or newer); `j2c.build.make` names another make program.

Problems from either command appear in the **Problems** panel at their source
lines; a clean run clears them. Problems with a mechanical fix (such as `this`
used in a lambda inside an anonymous class, or an `instanceof` pattern in a
field initializer) offer it as a quick fix (**Ctrl+.** on the problem), which
edits the Java source. A file's quick fixes are withdrawn once it changes, or
if it had unsaved changes when it was checked; check again to get new ones.

The extension requires VS Code 1.85 or newer and runs the bundled J2C CLI,
which requires Java 11 or newer. The
translator accepts Java 17 source and generated Makefiles target C++20. Java
17 feature translation is incremental; records are translated. Sealed types
default to advisory translation as ordinary C++ inheritance, with comments
listing permitted direct subtypes; C++ does not enforce the permits restriction.
Set `j2c.sealedTypes` to `reject` to stop translation on sealed or non-sealed
declarations. Lambdas and method references are translated into small
generated classes; the few forms that cannot be are reported in the Problems
panel. Sources are read as UTF-8.

The extension also offers J2C to AI agents: with `j2c.mcp.enabled` (on by
default), it registers the bundled CLI as an MCP server for each workspace
folder, so Copilot agent mode can check, fix and translate Java through the
`j2c_check`, `j2c_fix`, `j2c_report`, `j2c_translate`, `j2c_build`,
`j2c_test`, `j2c_run`, `j2c_explain`, and `j2c_capabilities` tools. `j2c_build` compiles translated
code and returns the compiler's errors at their Java lines. `j2c_test`
translates, builds and runs JUnit tests with GoogleTest and returns each
result at its Java class, method, file and line, with JUnit's failure
message. `j2c_run` translates a program against the J2C runtime, builds and
runs it, runs the same main class on the JVM, and returns both runs' output
and exit codes with the first line that differs. `j2c_report` assesses a migration: how much
translates, what blocks the rest, and the JDK API the code uses that the
J2C runtime lacks. The server's prompts `j2c-migrate` and `j2c-assess` appear
among Copilot Chat's slash commands in VS Code versions that support MCP
prompts.
This needs a VS Code version with MCP support. The server keeps check
results between calls, so checking again after an edit compiles only the
changed files and the files that use them; its calls report progress and can
be cancelled. See `docs/AGENTS.md` in the repository (it also describes
`j2c mcp --check-cache`).

If the chosen output folder is not empty and was not created by J2C, the
extension asks before translating, because translation moves the folder's
contents to a backup in the temporary folder.

Commands run with a progress notification that can be cancelled; a cancelled
translation may leave a partially written output directory. A translation
with problems in any selected file writes nothing. A dependency file with
Java errors only gets a header, with a `J2C-DEPENDENCY-ERRORS` warning at
its first error. Only one command
runs at a time. The J2C output channel logs the extension version with each
run.

The VSIX includes the project license, the third-party dependency inventory,
and the dependencies' license texts and original notices; see `LICENSE.txt`,
`THIRD-PARTY-NOTICES.txt`, and `licenses/`.

Generated record headers warn that C++ assignment can replace component
values. Review generated value methods against translated component runtime
types; array identity hashes and reference `toString()` results may differ from
JVM behavior.

To prepare the extension for local development or packaging, run
`npm run vscode:prepublish` in this directory. This builds the CLI with Maven,
runs the CLI and extension configuration/failure checks, and stages the
executable JAR in the extension package.

The extension uses source roots from `j2c.sourcePaths`, then
`java.project.sourcePaths`, and finally existing conventional `src` folders.
Set `j2c.sourcePaths` to the source roots for the Java project if automatic
detection does not match the project layout. Dependency jars and class
directories can be supplied with `j2c.classPath`. Set `j2c.javaExecutable`
if `java` on the `PATH` is not Java 11 or newer, and `j2c.cliJar` to use a
CLI JAR other than the bundled one.

When the workspace folder is a Maven or Gradle project (it has a `pom.xml`,
`build.gradle(.kts)` or `settings.gradle(.kts)`), `j2c.project` (`auto` by
default) lets the build supply the source roots, the test roots with
`j2c.tests`, and the dependency class path, unless `j2c.sourcePaths` is set.
The class path is resolved by running the project's wrapper (`mvnw`,
`gradlew`) or `mvn`/`gradle`, which may download dependencies, and is cached
in `~/.j2c/cache` by a hash of the build files; the J2C output channel shows
what was detected and the command run. If the build tool is missing or
fails, a `J2C-PROJECT-CLASSPATH-UNRESOLVED` warning appears on the build
file. Set `j2c.project` to `none` to turn this off, for commands and for the
MCP server's tools.

To run the focused integration checks separately, first build the CLI from the
repository root with `mvn package`, then run `npm run test:cli` and
`npm run test:extension` here; `npm run test:licenses` checks the bundled
license notices against the resolved dependencies.
