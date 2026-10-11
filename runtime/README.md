# J2C runtime

A C++ implementation of the Java class library for translated programs,
used with `j2c --runtime runtime`. Today it is the first milestone (R1 in
`docs/PUBLISHING_PLAN.md`, Phase 10): enough for `System.out.println` and
`main`'s arguments. Everything else still gets J2C's generated stubs, which
abort with "call to an unimplemented Java library method".

## How it works

The runtime is mostly **Java source that J2C translates**, like the program
itself. Only the native bottom layer is hand-written C++. This is the
approach J2ObjC uses for its JRE emulation; see `docs/RUNTIME_EXPERIMENT.md`
for why translating OpenJDK's `java.base` wholesale is not viable.

```
runtime/
  java/          Java sources of the runtime's classes (java.lang, java.io, ...)
  cpp/native/    C++ for `native` methods; each file replaces the stub J2C
                 generates at the same path (native/java/lang/Object-native.cpp)
  cpp/src/       C++ hooks generated code calls: _j string literals, class_,
                 unimplemented_, init_jvm and make_args
```

With `--runtime`, J2C reads `java.*` from `runtime/java` instead of the
running JDK, translates the classes the program reaches, copies the C++
files into the output, and the generated Makefile builds them into the
`-ext` library.

```sh
java -jar target/j2c-cli.jar --runtime runtime --output out test/runtime/hello
make -C out all mains
out/bin/Hello one two
```

## Rules for runtime classes

- Write them from the Java SE API specification, simplified where the JVM
  version depends on VM internals (no `Unsafe`, `VarHandle`, module boot or
  charset machinery). Behavior visible to programs must match the JVM; CI
  compares output with `java`.
- **Do not copy OpenJDK code.** OpenJDK is GPLv2 with the Classpath
  Exception, and J2C's C++ helper headers are EPL-1.0, which the FSF lists as
  incompatible with the GPL (`docs/RUNTIME_EXPERIMENT.md`, section 7).
- Every class lands with a test: a program in `test/runtime/<name>/` (with
  optional `args.txt`) that `npm run runtime` translates, builds, runs, and
  compares with `java`: stdout exactly, stderr without stack frames, and the
  exit status. CI runs it under UBSan.
- Natives only do the raw work (copying, conversion, system calls); checks
  and exceptions stay in Java, so a native never needs a class the program
  does not reach. Classes the hooks always call are listed in
  `required-classes.txt`.
