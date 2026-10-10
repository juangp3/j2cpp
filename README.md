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

Check whether code can be translated before writing anything:

```sh
java -jar target/j2c-cli.jar --check --sourcepath src/main/java src/main/java
```

`--check` takes the same inputs as a translation but no `--output`. It prints
every compile error and unsupported construct in the selected files as
`file:line: message`, followed by a summary per kind, and writes no files.
Select a source root to check a whole project. `--version` prints the CLI
version.

The CLI exits with status 0 on success (or a clean check), 1 when translation
fails or a check finds problems, and 2 for invalid arguments.

## VS Code extension

The extension provides **J2C: Translate to C++** from Java editor and Explorer
context menus. Select a Java file or source folder, then choose an existing
output directory.

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

## Java feature support

Java 17 parsing is enabled, but feature translation is incomplete. The current
documented modern-language slice includes:

- Pattern-matching `instanceof` in supported `if` conditions.
- Switch expressions on primitive values, and `case X ->` switch expressions
  on enums and strings.
- Arrow-form (`case X ->`) switch statements, and `switch` statements on
  strings.
- Text blocks.
- Lambda expressions and method references (static, bound, unbound, `this::`,
  `super::`, constructor, and array constructor references). Each is
  rewritten into the anonymous class Java defines it to be equivalent to and
  translated like any anonymous class, so every lambda becomes a small C++
  class. A bound receiver (`expr::method`) is evaluated once, when the
  reference is created; a null receiver fails when the reference is invoked,
  not when it is created.
- Default and static interface methods of library types get stub definitions
  like class methods do.
- Try-with-resources, including `try (existingResource)`. Resources are closed
  in reverse order before `catch` and `finally` clauses run. An exception from
  `close()` while another exception is propagating terminates the program
  instead of being recorded as suppressed.
- Records, including generated component storage/accessors, construction,
  `equals`, `hashCode`, and `toString`.
- Sealed and non-sealed declarations under the advisory/reject policy above.

Review the generated C++ for each project. In particular, record component
assignment, array identity hashing, and reference stringification can differ
from JVM behavior. The generated program may depend on Java runtime classes
and helpers that need to be implemented or replaced for the target project.
Unsupported Java syntax or runtime behavior may require manual translation.

The following syntax is detected and stops translation with a `file:line`
diagnostic instead of producing incorrect C++:

- Lambdas and method references whose target is an intersection type (for
  example `(Runnable & Serializable) () -> ...`), whose functional interface
  method is generic, or that use `this` inside an anonymous class (which
  cannot be named from the generated class).
- Switch expressions on boxed values (for example `Integer`), and colon-form
  (`case X:`) switch expressions on enums or strings.

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
been validated for every Java feature or compiler/toolchain combination. CI
translates the Java 17 control-flow and records fixtures, then compiles both
generated C++20 projects with their generated Makefiles. This smoke check is
not a guarantee of semantic parity or general Java compatibility.
Running `npm run vscode:prepublish` from `vscode` also runs focused CLI
integration checks for records and sealed-type policies before bundling the
CLI.

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
