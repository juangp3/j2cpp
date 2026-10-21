# Using a J2C runtime

Translated programs need a C++ implementation of the Java classes they use.
J2C translates against one of these:

| `--runtime` | `java.*` comes from | JDK classes the program uses |
| --- | --- | --- |
| (none, the CLI's default) | the running JDK | generated as stubs that call `unimplemented_()` and fail at run time |
| `bundled` (the VS Code extension's default, `j2c.runtime`) | the J2C runtime packaged in the CLI JAR | translated from the runtime's Java sources; classes it lacks are compile errors (`J2C-COMPILE-ERROR`) |
| `<directory>` | that runtime directory | the same, from that directory |

The MCP tools take the same choice as their `runtime` argument. What the J2C
runtime covers, and how it differs from the JVM, is in
[runtime/README.md](../runtime/README.md) (also the MCP resource
`j2c://runtime`); `j2c report` compares the JDK API a program uses with it.

## The bundled runtime

`--runtime bundled` needs no repository checkout. The CLI JAR contains the
repository's `runtime/` directory (`java/`, `cpp/`, `required-classes.txt`,
`api.txt` and `README.md`) under `META-INF/j2c-runtime/`. On first use J2C
extracts it into its cache directory, `~/.j2c/cache` (or `J2C_CACHE_DIR`,
which also holds resolved project class paths), as
`runtime-<version>-<hash>`, where the hash is a SHA-256 of the packaged
files' names and contents. Translating with it gives the same output as
`--runtime` with a checkout's `runtime/` directory.

- **Reuse:** each run compares the extracted files with the packaged ones
  and uses the copy as it is when they match.
- **Repair:** when a file is missing, changed or added (an added hook would
  be compiled into every program), J2C extracts a new copy and replaces the
  damaged one.
- **Concurrency:** a copy is written to a temporary directory next to its
  final name and renamed into place in one step, so processes that start at
  the same time never read a partial runtime. The first rename wins; the
  others delete their copies and use the winner's. Temporary and replaced
  copies that a killed process left behind are deleted after an hour.
- **Cleanup:** copies for older versions stay until the cache directory is
  deleted; each is about 1.4 MB.

A directory that happens to be called `bundled` is selected as
`--runtime ./bundled`.

## A runtime directory

```
<runtime>/
  java/                  Java sources of the provided classes (java/lang/Object.java is required)
  cpp/native/            C++ replacing the generated -native.cpp stub at the same path
  cpp/src/               C++ hooks generated code calls (_j, class_, unimplemented_, init_jvm, make_args)
  required-classes.txt   classes the hooks call, always translated (optional)
```

With a runtime, J2C reads `java.*` from `java/` instead of the running JDK,
translates the classes the program reaches, copies `cpp/native` files over
the stubs of those classes and `cpp/src` into the output's `runtime/`, and
the generated Makefile builds them into the `-ext` library. The rules for
writing runtime classes are in [runtime/README.md](../runtime/README.md).
