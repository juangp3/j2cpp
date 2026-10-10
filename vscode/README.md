# J2C Java to C++ for VS Code

This extension adds **J2C: Translate to C++** and **J2C: Check
Translatability** to Java editor and Explorer context menus.

- **Translate**: select a Java source file or source folder, then choose an
  existing output directory. Generated implementation files are written below
  `src`; dependency headers and stubs are written below `ext`.
- **Check**: reports every compile error and unsupported construct in the
  selection without writing anything. Run it on a source folder to see whether
  a project can be translated.

Problems from either command appear in the **Problems** panel at their source
lines; a clean run clears them.

The extension requires VS Code 1.85 or newer and runs the bundled J2C CLI,
which requires Java 11 or newer. The
translator accepts Java 17 source and generated Makefiles target C++20. Java
17 feature translation is incremental; records are translated. Sealed types
default to advisory translation as ordinary C++ inheritance, with comments
listing permitted direct subtypes; C++ does not enforce the permits restriction.
Set `j2c.sealedTypes` to `reject` to stop translation on sealed or non-sealed
declarations. Lambdas and method references are not supported yet; they are
reported in the Problems panel. Sources are read as UTF-8.

Commands run with a progress notification that can be cancelled; a cancelled
translation may leave a partially written output directory. Only one command
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

To run the focused integration checks separately, first build the CLI from the
repository root with `mvn package`, then run `npm run test:cli` and
`npm run test:extension` here; `npm run test:licenses` checks the bundled
license notices against the resolved dependencies.
