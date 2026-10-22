# J2C runtime

A C++ implementation of the Java class library for translated programs,
used with `j2c --runtime bundled` (this directory, packaged in the CLI JAR)
or `j2c --runtime runtime` (a checkout's copy). It covers milestones R1 to R7 in
`docs/PUBLISHING_PLAN.md` (Phase 10), R6 being regular expressions, text
input and files, and R7 threads (see [Threads](#threads)):

- **`java.lang`:**
  - `String` (with `format` and `formatted`), `StringBuilder` and the boxed
    types;
  - `Math`, `StrictMath` and `System` (`out`, `err`, `arraycopy`, time);
  - `Throwable` and the exception classes, with Java's uncaught-exception
    printout;
  - `Iterable`, `Enum`, `Record` and the common annotations.
- **`java.io`:**
  - byte streams: `InputStream`, `OutputStream`, `FileInputStream`,
    `FileOutputStream`, `BufferedInputStream`, `BufferedOutputStream`,
    `ByteArrayInputStream`, `ByteArrayOutputStream` and the filter streams;
  - text: `Reader`, `Writer`, `InputStreamReader`, `OutputStreamWriter`,
    `BufferedReader` (with `lines()`), `BufferedWriter`, `StringReader`,
    `StringWriter`, `FileReader`, `FileWriter`, `PrintWriter`, and
    `PrintStream` (with `printf`) over any stream;
  - `File`, `FileDescriptor`, `Closeable`, `Flushable`, and the exceptions
    (`FileNotFoundException`, `EOFException`, `UncheckedIOException`, ...).
- **`java.lang`, more:** `System.in`, `setIn`, `setOut` and `setErr`,
  `Appendable`, and `String`'s byte constructors and `getBytes`.
- **`java.nio.charset`:** `Charset`, `StandardCharsets`, the coders and
  their exceptions, for UTF-8, ISO-8859-1 and US-ASCII.
- **`java.nio.file`:** `Path`, `Paths`, `Files`, the open options and the
  `FileSystemException` family.
- **`java.util`:**
  - `Scanner` over `System.in`, a `String`, a `File` or a `Path`;
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
- **`java.util.regex`:** `Pattern`, `Matcher`, `MatchResult` and
  `PatternSyntaxException`, behind `String.matches`, `split`, `replaceAll`
  and `replaceFirst`:
  - escapes, `.`, classes with ranges, negation, nesting and `&&`, the
    predefined classes (`\d \s \w \h \v` and their negations), POSIX
    (`\p{Alpha}`), `\p{javaLowerCase}` and the like, and the basic Unicode
    properties `L`, `Lu`, `Ll`, `N`, `Nd`, `Z`, `Zs` and `IsAlphabetic`;
  - anchors `^ $ \b \B \A \G \Z \z`, capturing, named, non-capturing
    and atomic groups, backreferences, look-ahead and look-behind, greedy,
    reluctant and possessive quantifiers, `\R` and `\Q...\E`;
  - the flags `CASE_INSENSITIVE`, `UNICODE_CASE`, `MULTILINE`, `DOTALL`,
    `UNIX_LINES`, `COMMENTS` and `LITERAL`, inline too;
  - `split` with Java's rules for limits, trailing empty strings and
    zero-width matches, replacement strings with `$n`, `${name}` and `\`,
    `replaceAll(Function)`, `appendReplacement`, `results()`, `region`,
    and `PatternSyntaxException` messages identical to the JDK's.

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
- Regular expressions:
  - The matcher backtracks on a stack on the heap; a quantified single
    character loops without a stack entry per character, and only
    look-around and atomic groups recurse (once per nesting level of the
    pattern). Where the JVM recurses per iteration and throws
    `StackOverflowError` on long inputs (`(a|b)*`), this runtime matches.
  - Groups keep the values the JDK's matcher leaves in them, which a plain
    backtracking matcher would not: groups captured in atomic groups and
    look-arounds stay set when the match backtracks past them, a repeated
    fixed-length group sets itself to its own last iteration once the rest
    has matched, and look-behind bounds are computed with the JDK's int
    overflow. `test/runtime/regex-fuzz` compares 4,000 random patterns with
    the JVM; in 8,000 more, the one difference left was which stale
    capture a group kept after atomic groups failed at several start
    positions.
  - Unicode properties and `UNICODE_CASE` use `Character`, exact for
    Latin-1 only. `\b` uses `Character.isLetterOrDigit`, as JDK 17 does.
  - A `Matcher` copies its input when it is created or reset, `results()`
    and `splitAsStream` find every match before the stream starts, and
    regions are always opaque and anchoring.
  - Not supported, and failing loudly: `CANON_EQ`,
    `UNICODE_CHARACTER_CLASS` and `(?U)`, `\X`, `\N{...}`, `\b{g}`,
    Unicode blocks, scripts and other property names
    (`UnsupportedOperationException`); `hitEnd`, `requireEnd`,
    `usePattern`, transparent bounds and the `StringBuffer` overloads
    (absent).

## Threads

`synchronized` blocks and methods, `Object.wait`, `notify` and `notifyAll`,
`java.lang.Thread` and `ThreadLocal` run on POSIX threads, and a subset of
`java.util.concurrent` is written on top of them in Java:

- **Monitors** are reentrant and live in a striped table
  (`cpp/src/j2c_threads.cpp`), not in each object: a monitor is taken from
  its stripe's free list when a thread first locks or waits on an object,
  and goes back when no thread holds it, enters it or waits on it. The
  generated code calls the `lock` and `unlock` hooks through an RAII
  object, so every exit releases the monitor, exceptions included. Each
  thread in `wait` has its own waiter node, so `notify` wakes exactly the
  longest waiting thread, and an interrupt or a timeout cannot swallow a
  notification meant for another one. `wait` and `notify` without the
  monitor throw `IllegalMonitorStateException("current thread is not
  owner")`, as on the JVM.
- **`Thread`**: `start`, `run`, `join` (with timeouts), `sleep`,
  `interrupt`, `isInterrupted` and `interrupted`, names (`main`,
  `Thread-0`, ...), daemons (inherited from the creating thread),
  priorities (recorded only), `holdsLock`, `yield`, `toString`, and the
  thread, default and printing uncaught-exception handlers ("Exception in
  thread "Thread-0" ..."). `sleep` and `join` wait on monitors, so an
  interrupt ends them, and `Object.wait`, with `InterruptedException`; it
  does not end blocking I/O. When `main` returns or throws, the program
  waits for the threads that are not daemons, as the JVM does, then ends
  even if daemon threads still run. `System.exit` ends the process at once
  from any thread.
- **`java.util.concurrent`**: `AtomicInteger`, `AtomicLong`,
  `AtomicBoolean` and `AtomicReference` (volatile fields, which J2C makes
  `std::atomic`, and natives for the read-modify-write operations);
  `ExecutorService` through `Executors.newFixedThreadPool` and
  `newSingleThreadExecutor`, with `submit`, `execute`, `invokeAll`,
  `invokeAny`, `shutdown`, `shutdownNow` and `awaitTermination`, threads
  named `pool-1-thread-1` and replaced when a task of `execute` throws, as
  in the JDK; `Future` and `FutureTask`, with `ExecutionException`,
  `CancellationException` and `TimeoutException`; `CountDownLatch`;
  `ReentrantLock` with `Condition`; `TimeUnit`; and `ConcurrentHashMap`.
- **Synchronized views**: `Collections.synchronizedList`,
  `synchronizedSet`, `synchronizedMap` and `synchronizedCollection`.
- **Shared state is thread-safe**: `PrintStream` (so `System.out`) and
  `BufferedOutputStream` hold their monitors as the JDK's do, so lines that
  threads print do not mix; `printStackTrace` holds the stream for the
  whole trace; string interning (every string literal) and the `class_`
  cache take locks; `Math.random` is synchronized; and static initializers
  run once, the other threads waiting for the first (the generated
  `clinit` keeps its recursion flag per thread).

`ConcurrentHashMap` guards its table with one lock instead of per-bin
locks, so threads do not update it in parallel; the compute methods run
their functions under the lock, which makes them atomic as in the JDK. Its
table grows as the JDK's does (initial sizes, the threshold, the order in
which a resize splits a bin), so a printed map lists entries in the JVM's
order. Its iterators walk a snapshot taken when they are created.

Limits, each failing loudly (absent, so a program using them does not
compile, unless noted):
- Thread groups, `getState`, `getStackTrace` of other threads, `stop`,
  `suspend` and `resume`; uncaught-exception handlers for the main thread
  (an exception `main` throws always prints); virtual threads.
- `Executors.newCachedThreadPool`, the scheduled and work-stealing pools,
  `ThreadPoolExecutor`, `CompletableFuture`, `Semaphore`, `CyclicBarrier`,
  the blocking queues, `ReadWriteLock`, `LongAdder`, the atomic arrays and
  field updaters, `Condition.awaitUntil` (no `java.util.Date`), and
  `ConcurrentHashMap`'s parallel bulk operations, `keys()` and
  `elements()`.
- `ReentrantLock` ignores fairness, and has no methods that inspect queued
  threads. `invokeAny` waits for its tasks in order and returns the first
  successful result, which the specification allows.
- Every atomic access is sequentially consistent, also the plain, opaque,
  acquire and release ones.
- POSIX threads only. A `native synchronized` method does not lock: its
  hand-written body must.

## Text input and files

Streams buffer as the JDK's do, so a program sees the same output and the
same pitfalls:
- `System.out` and `System.err` are `PrintStream`s with autoflush over a
  128-byte buffer, `System.in` a `BufferedInputStream`. Each `print` call
  reaches the file descriptor at once; `System.out.write(b)` waits for a
  newline or `flush()`.
- `InputStreamReader` reads 8192 bytes at a time and `Scanner` 1024
  characters, so a second reader or scanner on `System.in` misses the input
  the first one buffered, as on the JVM. `OutputStreamWriter` and
  `BufferedWriter` keep 8192 bytes or characters until `flush` or `close`,
  so a `PrintWriter` that is never flushed writes nothing.
- Decoding replaces malformed input with U+FFFD exactly where the JDK does,
  one per malformed sequence as the JDK measures it; encoding writes `?` for unpaired
  surrogates and unmappable characters. `Files` reads and writes strictly
  and throws `MalformedInputException`, as in the JDK, and
  `new Scanner(File)` stops at the first malformed byte (`ioException()`).
- `FileNotFoundException` and `FileSystemException` messages come from
  `strerror`, as the JVM's do on Linux: `missing.txt (No such file or
  directory)`, `work/x.txt: Not a directory`.

Limits, each failing loudly:
- Charsets other than UTF-8, ISO-8859-1 and US-ASCII (UTF-16 among them)
  throw `UnsupportedCharsetException`. There is no `ByteBuffer`,
  `CharBuffer` or channel, so `Charset.encode`, `decode`, the coders'
  buffer methods, `Readable`, `FileChannel` and `getChannel()` are absent,
  as is `StringWriter.getBuffer()` (no `StringBuffer`).
- `Scanner` does not use regular expressions: `useDelimiter(String)` takes
  literal text (with backslash-escaped punctuation, `\t`, `\n` and `\r`)
  and throws `UnsupportedOperationException` for any other pattern; the
  methods that take a `Pattern` or a regular expression (`hasNext(String)`,
  `findInLine`, `skip`, `delimiter()`), locales and `Scanner(Readable)` are
  absent. Numbers use the US format (`1,234.5`); `nextBigInteger` and
  `nextBigDecimal` are absent.
- `Character.isDigit` knows ASCII and full-width digits only, so
  `nextInt` rejects other scripts' digits that the JVM accepts.
- `Files` follows symbolic links (`LinkOption` is ignored) and has no
  `copy`, `move`, `walk`, `find`, attributes or temporary files;
  `DELETE_ON_CLOSE`, `SPARSE`, `SYNC` and `DSYNC` throw
  `UnsupportedOperationException`. `File.getCanonicalPath` resolves `.`
  and `..` but not links, and `File` has no permission setters, URIs or
  temporary files.
- POSIX only: the natives use `open`, `read`, `stat` and `readdir`.

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
                 unimplemented_, init_jvm, make_args, and lock and unlock
                 (synchronized), with the monitors and thread registry
```

With `--runtime`, J2C reads `java.*` from `runtime/java` instead of the
running JDK, translates the classes the program reaches, copies the C++
files into the output, and the generated Makefile builds them into the
`-ext` library.

```sh
java -jar target/j2c-cli.jar --runtime bundled --output out test/runtime/hello
make -C out all mains
out/bin/Hello one two
```

Maven packages this directory's `java/`, `cpp/`, `required-classes.txt`,
`api.txt` and this README into the CLI JAR under `META-INF/j2c-runtime/`
(and so into the VS Code extension, whose `j2c.runtime` setting defaults to
`bundled`). `--runtime bundled` extracts them into J2C's cache directory
(`~/.j2c/cache`, or `J2C_CACHE_DIR`) as `runtime-<version>-<hash>`, the hash
covering every file's name and contents, and uses that directory as
`--runtime runtime` would use this one; the output is the same. A copy is
reused while its files are exactly the packaged ones, and replaced when a
file is missing, changed or added. Extraction writes a temporary directory
next to it and renames it into place, so concurrent J2C processes never see
a partial copy. `--runtime runtime` reads a checkout directly, so edits here
take effect without rebuilding the JAR.

## Garbage collection

Objects are never freed unless the program is built with the Boehm
collector (`libgc-dev` on Debian and Ubuntu):

```sh
make -C out J2C_GC=boehm all mains
```

The Makefile then defines `J2C_GC_BOEHM` and links `-lgc` (from
`pkg-config bdw-gc` when it knows it; `GC_CFLAGS` and `GC_LIBS` override).
The program's main file (`Main.cpp.tmpl`) replaces every global
`operator new` and `operator delete`:

- All C++ allocations, the program's objects, the runtime's and the buffers
  of standard containers alike, come from the collector's scanned heap. An
  object stays alive while the stack, registers, static data (static
  fields, the `class_` and string literal caches) or another allocation
  points to it, including at a base class or interface subobject.
- `delete` (only standard containers call it) frees at once; everything
  else is reclaimed by collections. Finalizers never run.
- The exception object of a C++ `throw` is `malloc`'d, where the collector
  does not look, so a Throwable could be freed while `finally` blocks run
  during unwinding. The Makefile links with `-Wl,--wrap=__cxa_throw`, and
  the wrapper keeps the last 256 thrown pointers in static data.
- `J2C_GC_STRESS=n` (a macro, in `CFLAGS`) collects every `n` allocations,
  for tests.

Threads are registered with the collector: `Main.cpp` defines
`GC_THREADS` before including `gc.h`, and `Thread.start` calls
`pthread_create`, which `gc.h` turns into `GC_pthread_create`, so the new
thread's stack is scanned. (`std::thread` would not do: its state object
comes from the collector's heap and is reachable from nowhere until the new
thread registers.) Thread objects stay reachable from a registry while
they run, and monitors from the static stripe table.

Limits: it needs GNU ld for `--wrap`, only `make mains` programs get the
collector (not `make test`), and run `make clean` when switching
`J2C_GC`. Primitive arrays are scanned too, so an `int` that
happens to look like an address can keep an object alive (the collector is
conservative). AddressSanitizer cannot be combined with it.

`npm run runtime:gc` runs every program in `test/runtime` with
`J2C_GC=boehm` at `-O2`, collecting every 1000 allocations. CI runs
the allocation-heavy programs on each push and all of them nightly.
`test/runtime/allocation` allocates about 1 GB in a loop; its peak resident
memory must stay under `max-rss-mb.txt` (100 MB) with the collector and
exceed it without (16 MB and 910 MB when this was written).

## Rules for runtime classes

- Write them from the Java SE API specification, simplified where the JVM
  version depends on VM internals (no `Unsafe`, `VarHandle`, module boot or
  charset machinery). Behavior visible to programs must match the JVM; CI
  compares output with `java`.
- **Do not copy OpenJDK code.** OpenJDK is GPLv2 with the Classpath
  Exception, and J2C's C++ helper headers are EPL-1.0, which the FSF lists as
  incompatible with the GPL (`docs/RUNTIME_EXPERIMENT.md`, section 7).
- Every class lands with a test: a program in `test/runtime/<name>/` (with
  optional `args.txt`, and `input.txt` for standard input) that
  `npm run runtime` translates, builds, runs, and compares with `java`:
  stdout exactly, stderr without stack frames, and the exit status. Each
  side runs in a new, empty working directory, where the program may write
  files. CI runs it under UBSan.
- Natives only do the raw work (copying, conversion, system calls); checks
  and exceptions stay in Java, so a native never needs a class the program
  does not reach. Classes the hooks always call are listed in
  `required-classes.txt`.
