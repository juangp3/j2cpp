# J2C Java to C++ for VS Code

This extension adds **J2C: Translate to C++** to Java editor and Explorer
context menus. Select a Java source file or source folder, then choose an
existing output directory. Generated implementation files are written below
`src`; dependency headers and stubs are written below `ext`.

The extension runs the standalone J2C CLI and requires Java 11 or newer. The
translator accepts Java 17 source and generated Makefiles target C++20. Java
17 feature translation is incremental; records are translated. Sealed types
default to advisory translation as ordinary C++ inheritance, with comments
listing permitted direct subtypes; C++ does not enforce the permits restriction.
Set `j2c.sealedTypes` to `reject` to stop translation on sealed or non-sealed
declarations.

The VSIX includes the project license and third-party dependency inventory;
see `LICENSE.txt` and `THIRD-PARTY-NOTICES.txt`.

Generated record headers warn that C++ assignment can replace component
values. Review generated value methods against translated component runtime
types; array identity hashes and reference `toString()` results may differ from
JVM behavior.

To prepare the extension for local development or packaging, run
`npm run vscode:prepublish` in this directory. This builds the CLI with Maven,
runs focused CLI integration checks, and stages the executable JAR in the
extension package.

The extension uses source roots from `j2c.sourcePaths`, then
`java.project.sourcePaths`, and finally existing conventional `src` folders.
Set `j2c.sourcePaths` to the source roots for the Java project if automatic
detection does not match the project layout. Dependency jars and class
directories can be supplied with `j2c.classPath`.

To run the focused CLI integration checks separately, first build the CLI from
the repository root with `mvn package`, then run `npm run test:cli` here.
