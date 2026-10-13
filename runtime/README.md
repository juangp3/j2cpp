# J2C runtime

A C++ implementation of the Java class library for translated programs,
used with `j2c --runtime runtime`. It covers milestones R1 to R4 in
`docs/PUBLISHING_PLAN.md` (Phase 10):

- **`java.lang`:**
  - `String` (with `format` and `formatted`), `StringBuilder` and the boxed
    types;
  - `Math`, `StrictMath` and `System` (`out`, `err`, `arraycopy`, time);
  - `Throwable` and the exception classes, with Java's uncaught-exception
    printout;
  - `Iterable`, `Enum`, `Record` and the common annotations.
- **`java.io`:** `PrintStream`, with `printf` and `format`.
- **`java.util`:**
  - lists: `ArrayList`, `LinkedList`, and `Arrays.asList`;
  - queues: `ArrayDeque` and `PriorityQueue`;
  - maps: `HashMap`, `LinkedHashMap` and `TreeMap`, with their sets;
  - `List.of`, `Set.of` and `Map.of`;
  - `Optional`, `OptionalInt`, `OptionalLong` and `OptionalDouble`;
  - `Formatter` and the `IllegalFormatException` family;
  - `IntSummaryStatistics`, `LongSummaryStatistics`,
    `DoubleSummaryStatistics`, `StringJoiner`, `Spliterator(s)` and
    `PrimitiveIterator`;
  - the helpers `Collections`, `Arrays`, `Objects` and `Comparator`.
- **`java.util.function`:** every functional interface, with their default
  and static methods.
- **`java.util.stream`:** `Stream`, `IntStream`, `LongStream`,
  `DoubleStream`, `Collectors`, `Collector` and `StreamSupport`.

Everything else still gets J2C's generated stubs, which abort with "call to
an unimplemented Java library method".

Iteration order matches the JVM where programs can see it:
- `HashMap` uses the same table sizes, hash spreading and bin order, so
  printed maps and sets match.
- `PriorityQueue` uses the textbook heap moves, so a printed queue shows
  the same array.
- Collectors build the same maps and sets as the JDK (`groupingBy` a
  `HashMap`, `toSet` a `HashSet`) with the same calls, so their results
  print the same.

Streams run sequentially and pull elements one at a time through the
pipeline, so `peek` and `map` actions run in the JVM's order, short-circuit
operations (`findFirst`, `anyMatch`, `limit`, `takeWhile`) end infinite
streams, and `count()` skips the pipeline when the size is known, as the JVM
does. `parallel()` only sets the flag `isParallel()` reports.

`String.format` and `printf` format numbers like `Locale.US` (`1,234.50`);
there is no `Locale` class. Floating-point conversions round half up from
the digits of `Double.toString`, as the JDK does, so `%.2f` of `0.125` is
`0.13`. `npm run runtime` runs `java` with that locale.

Differences that remain:
- The JVM turns bins of 8 or more colliding keys into trees, which can
  reorder them; this runtime keeps lists.
- `Set.of` and `Map.of` with several elements iterate in insertion order,
  where the JVM's order is randomized per run. `Collectors.toUnmodifiableSet`
  and `toUnmodifiableMap` use them.
- `Double.toString` prints the shortest digits that round-trip (the JDK 19+
  algorithm), so a few values print differently from JDK 17, and `%e`, `%f`
  and `%g` of them too: `Double.MIN_VALUE` is `4.9E-324` and
  `4.900000e-324` on JDK 17, `5.0E-324` and `5.000000e-324` here.
- Not supported, and failing loudly: the date and time conversions (`%t`)
  and `%a` with a precision (`UnsupportedOperationException`), `Formattable`,
  `Stream.mapMulti`, `BaseStream.spliterator()`, the primitive spliterators,
  `groupingByConcurrent`, `toConcurrentMap`, `Enum.valueOf(Class, String)`
  and `Enum.getDeclaringClass()` (absent, so a program using them does not
  compile).
- `chars()` and `codePoints()` read a `StringBuilder` when they are called;
  the JVM reads it when the stream runs.

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
