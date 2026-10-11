# Runtime experiment: can J2C translate the JDK's own class library?

Date: 2026-10-11. Branch `modern-release` at `6ab4667`, CLI JAR `target/j2c-cli.jar` (0.6.0).
Audience: the J2C maintainer and future agents working on Phase 10 of
[PUBLISHING_PLAN.md](PUBLISHING_PLAN.md).

This report measures whether J2C can provide the Java class library by
translating OpenJDK 17's own `java.base` sources, as J2ObjC, GCJ and the
original J2C (on OpenJDK 6) did, and how large that is. Numbers marked
**(estimate)** are judgements. All other numbers were measured.

## Recommendation and key numbers

**Recommendation: a curated hybrid runtime.** Write the runtime mostly as
Java that J2C itself translates, plus a small hand-written C++ native layer.
Start from hand-written, simplified Java classes for the core (`Object`,
`String`, `System`, `PrintStream` and the boxed types). Bring in plain-Java
OpenJDK classes such as `ArrayList`, `HashMap` and `TimSort` one at a time,
patched where needed, and only after the license question below is settled.
Do not translate `java.base` wholesale.

Why:

1. **Translating `java.base` as a whole cannot be made small.** J2C
   translates every type that a translated class's code mentions, so its
   closure fills up almost at once. Hello World and a class that uses no JDK
   API at all pull in the same **2,066 files (903k lines, about 67% of
   java.base)**. The JVM itself loads only 320 classes to run Hello World.
2. **The full closure nearly compiles, but it cannot run.** After 10 patched
   source files, J2C reports no problems and **97.8% of the 5,467 C++ files
   compile**. The other 121 fail for **10 distinct translator causes**. To
   run, though, the code needs a JVM underneath: **694 native methods**,
   `Unsafe` (used in 167 files), `VarHandle` and `MethodHandle` (165 files),
   and the `System.initPhase1` and module boot sequence. That amounts to
   rewriting HotSpot's runtime services in C++.
3. **The curated route works today.** 17 hand-written Java files (135
   lines) plus 70 lines of C++ (hooks and two natives) gave a J2C closure of
   14 classes. It built without errors in WSL, and **P1 printed `hi` and
   exited with 0, built with UBSan**. Making that work needed only small J2C
   changes, listed below. Writing the runtime in Java that J2C translates
   also means its headers always match what J2C generates, which removes the
   header-drift risk in the current Phase 10 plan.
4. **License.** Translated OpenJDK code stays GPLv2 with the Classpath
   Exception. J2C and its C++ helper headers (`Array.hpp`, `npc.hpp` and
   others, which every build includes) are EPL-1.0, and the FSF considers
   EPL-1.0 incompatible with the GPL. A runtime that mixes OpenJDK code with
   those headers needs a licensing decision first. Hand-written Java has no
   such problem.

| Measurement | Value |
| --- | --- |
| java.base in JDK 17.0.18 `lib/src.zip` (Windows build) | 3,064 files, 1,223,572 lines |
| J2C closure, P0 (no JDK use) to P4 (streams, `String.format`) | **2,063 files** (unpatched) or 2,066 (patched); the same set for every program |
| Closure size | 901,755 lines, 35 MB, about 2.64M Java tokens excluding comments, about 8.8M LLM tokens at 4 bytes per token **(estimate)** |
| Classes in the closure (patched) | 2,096 top-level types, plus 1,644 nested, 44 local and 1,599 anonymous |
| Classes the JVM loads (top-level, from java.base) | P1 320, P2 355, P3 367, P4 466 (P1 is 216k lines) |
| Stock CLI with JDK sources on `--sourcepath` | **Fails at startup**: `Could not resolve Java type java.lang.ClassLoader` (package conflict with the running JDK) |
| `--check` problems, with the system library off | 5 compile errors (sealed `ConstantDesc`); after patching that file, **26 problems in 9 files**; after **10 patched files**, 0 |
| Generated C++ for the P1 closure | 5,467 `.cpp` and 5,500 `.hpp` files, 1.19M lines |
| `make -k -j8` in WSL (GCC 13.3) | about 4 min 20 s; **121 of 5,467 translation units fail, 695 errors, 10 causes** |
| Native methods in the closure | **694 stubs in 94 `-native.cpp` files** (J2C's count); a source scan finds 655 declarations in 89 classes, 84 of them `@IntrinsicCandidate` |
| Natives the JVM actually links | P1: 63 called plus 136 registered at startup; P3: 65 plus 136 |
| Curated prototype | 17 Java files, 135 lines, plus 70 lines of C++; closure 14 classes; **prints `hi`** |

### Effort estimates (all **estimates**)

| Route | Up to P1 running | Up to P3 running | Notes |
| --- | --- | --- | --- |
| Curated hybrid (recommended) | 3–5 days, including the J2C changes below | 4–7 weeks | About 80–150 runtime classes for P3, about 30–40 natives |
| Fully hand-written C++ (current plan, step 2) | 1–2 weeks (milestone 1) | 3–5 weeks | Every API by hand; matching Java behavior is the main risk; headers can drift from J2C's |
| Translate java.base wholesale | Not reachable in months | — | Needs HotSpot services in C++: `Unsafe`, `VarHandle`, `MethodHandle`, module boot, class loaders |
| Apache Harmony or Android libcore as the source | — | — | Harmony stopped at Java 5/6 (no streams or default methods); libcore's current code is OpenJDK-derived, under the same GPLv2 with Classpath Exception |

**First milestone (R1):** `--runtime` mode with an in-repository runtime
source tree (`runtime/java/`), so that P1 translates, builds and prints `hi`
in CI, compared with `java` output. The experiment already did this by hand;
see section 3.3.

## 1. Closure size

### Method

- JDK sources: `C:\Program Files\Microsoft\jdk-17.0.18.8-hotspot\lib\src.zip`.
  Only `java.base/**/*.java` was extracted. This is the **Windows** build of
  the sources, so it includes `WinNTFileSystem`, `WindowsNativeDispatcher`
  and similar classes. A Linux runtime would use the `unix` variants
  (`src/java.base/unix/classes` in jdk17u).
- Programs (`P1` to `P4` as specified, plus `P0`, a class with one method
  that returns 1 and uses no JDK API) were translated with
  `--sourcepath "PROG;overlay;java.base" PROG`.
- **The stock CLI cannot do this.** `SourceProject.newParser()` passes
  `includeRunningVMBootclasspath = true` to JDT. JDT then sees `java.lang`
  in two places, the running JDK's `java.base` module and the unnamed module
  of the source path, and reports `The package java.lang is accessible from
  more than one module: <unnamed>, java.base`. Every JDK type then fails to
  resolve, and translation stops with `Could not resolve Java type
  java.lang.ClassLoader`. `--check` on a JDK file reports `J2C-COMPILE-ERROR`
  "The package java.lang conflicts with a package accessible from another
  module". Keeping `module-info.java` does not help.
- To measure anyway, a **scratch harness** (outside the repository; the JAR
  and sources were not modified) put two shadowing classes ahead of the JAR
  on the classpath:
  - `SourceProject`, with the running VM's boot classpath switched off
    (`-Dj2c.noSystem=true`). The sources then become the only Java library.
  - `Transformer`, which logs each unit and its problems to a file and keeps
    going instead of stopping at the first problem, so that the whole
    closure can be measured.
- Token counts are Java lexical tokens with comments removed. The LLM token
  figure is bytes divided by 4 **(estimate)**.

### Results

| Program | J2C closure (JDK files) | Lines | JVM-loaded top-level classes (`-verbose:class`, `-Xshare:off`) | Their source lines |
| --- | --- | --- | --- | --- |
| P0: no JDK use | 2,063 | 901,755 | — | — |
| P1: `println("hi")` | 2,063 | 901,755 | 320 | 215,704 |
| P2: plus concatenation, `StringBuilder`, `parseInt`, `Math.max` | 2,063 | 901,755 | 355 | 238,489 |
| P3: plus `ArrayList`, `HashMap`, iteration, `Collections.sort` with a lambda | 2,063 | 901,755 | 367 | 246,288 |
| P4: plus `String.format`, `stream().filter().count()` | 2,063 | 901,755 | 466 | 304,450 |
| After the 10-file patch overlay | 2,066 | 903,465 | | |

The J2C closure is the same for every program, because it depends only on
the hard dependencies (`Object`, `String`, `ClassLoader`, `Cloneable`,
`Serializable`). `Object` refers to `Class`; `Class` refers to reflection,
`ClassLoader`, modules and annotations; `String` refers to `Formatter`,
`Locale`, regular expressions and streams. Translating any class from
source therefore pulls in everything its method bodies mention.

The JVM's set is not a good guide either. Most of what it loads for P1 is
module boot (`jdk.internal.module`), class loaders, reflection and CDS.
P2 and P3 add mainly the `invokedynamic` machinery for string concatenation
and lambdas (`java.lang.invoke`, ASM), which J2C does not need, because it
compiles concatenation to `StringBuilder` and lambdas to anonymous classes.
P4 adds streams, `Formatter`, locale providers and `jdk.internal.jimage`:
`String.format` loads locale data from the runtime image.

Packages that dominate the J2C closure (unpatched run; files, lines, tokens):

| Package group | Files | Lines | Java tokens |
| --- | --- | --- | --- |
| `sun.security.*` | 180 | 57,770 | 196,736 |
| `java.lang` | 127 | 78,627 | 184,636 |
| `java.util` | 110 | 101,874 | 306,371 |
| `java.nio` (generated buffers) | 83 | 48,690 | 74,339 |
| `java.security` and subpackages | 133 | 36,965 | 67,401 |
| `java.io` | 74 | 33,033 | 80,810 |
| `java.net` and `sun.net` | 139 | 51,484 | 135,737 |
| `jdk.internal.reflect` and `sun.reflect.*` | 130 | 18,867 | 67,804 |
| `java.lang.invoke` | 62 | 48,482 | 235,345 |
| `java.time.*` | 83 | 57,906 | 145,120 |
| `java.util.concurrent` and subpackages | 59 | 45,269 | 151,639 |
| `java.util.stream` | 36 | 25,704 | 81,519 |
| `sun.nio.*` (channels, file systems, charsets) | 139 | 50,632 | 188,857 |
| `jdk.internal.*` (other: module, loader, access, icu, ASM, util) | 202 | 71,706 | 231,486 |
| `java.text` and `sun.util.locale.*` | 72 | 34,762 | 109,559 |

## 2. Translatability (`--check`)

| Stage | Result |
| --- | --- |
| Stock CLI | Every JDK file reports `J2C-COMPILE-ERROR` (package conflict with the running JDK). Translation stops at startup. |
| System library off, no patches | 5 `J2C-COMPILE-ERROR`s in `java/lang/constant/ConstantDesc.java`: a sealed interface permits `Double`, `Integer`, `String` and others from other packages, which JDT rejects for the unnamed module |
| Patch `ConstantDesc` | **26 problems in 9 files** |
| Overlay of 10 patched files | **0 problems** in 2,067 units |

The 26 problems, by ID and root cause:

| Problem ID | Count | Files | Root cause |
| --- | --- | --- | --- |
| `J2C-LAMBDA-INTERSECTION-TYPE-VARIABLE` | 12 | `Comparator` (6), `Map` (4), `TreeMap`, `ConcurrentSkipListMap` | `(Comparator<T> & Serializable) (a, b) -> …`: a serializable lambda whose intersection cast uses a type variable |
| `J2C-LAMBDA-NON-DENOTABLE-TYPE` | 2 | `CompletionStage` | A nested lambda whose target involves a captured wildcard (`exceptionallyComposeAsync`) |
| `J2C-COMPILE-ERROR` | 4 | `Collectors` | **J2C bug:** the desugared form of `new CollectorImpl<>(HashMap::new, …)` and of `map -> …` passed to `collectingAndThen` fails JDT's type inference |
| `J2C-COMPILE-ERROR` | 6 | `Proxy` | **J2C bug:** `LambdaDesugarer` writes the member type `AbstractClassLoaderValue.Sub<…>` without qualifying it with the parameterized outer type |
| `J2C-COMPILE-ERROR` | 1 | `AbstractPipeline` | **J2C bug:** the desugared `() -> sourceSpliterator(0)` names `Supplier<Spliterator<?>>` where a capture is needed |
| `J2C-COMPILE-ERROR` | 1 | `DynamicConstantDesc` | `non-sealed` becomes invalid once `ConstantDesc` is patched |

The 10 patched files (in `overlay/`, which comes first on the source path):

- `ConstantDesc` and `DynamicConstantDesc`: removed `sealed`, `permits` and
  `non-sealed`.
- `Comparator`, `Map`, `TreeMap`, `ConcurrentSkipListMap`: removed
  `& Serializable` from the casts.
- `CompletionStage`, `Collectors`, `Proxy`, `AbstractPipeline`: rewrote the
  lambdas as explicit anonymous classes with explicit type arguments.

This is the kind of patching J2ObjC does to its OpenJDK and Android files.

Separately from `--check`, the run also showed:

- **`Collectors` was parsed and reported twice**, in two batches, so the raw
  log shows 30 problem lines for 26 distinct problems.
- **The writers crash** (`NullPointerException: this.closures is null`) when
  they are given a unit that has lambda problems. That only happens because
  the harness kept going past the problems; the stock CLI stops before it.

## 3. Compilability

### 3.1 Full P1 closure, patched

`make -k -j8` in WSL (Ubuntu, GCC 13.3, 12 cores, 7 GB) took about 4 min
20 s. 121 of the 5,467 translation units failed, with 695 `error:` lines.
`libout1b.a` (the program) was archived; the `-ext` library was not, so
nothing was linked. Grouped by cause:

| # | Cause | Errors | Files reporting errors | Where |
| --- | --- | --- | --- | --- |
| A | **Signature-polymorphic calls** (`MethodHandle.invokeBasic/invokeExact`, `VarHandle.compareAndSet/get/set`) are translated as ordinary `Object...` varargs: `new ObjectArray({static_cast<Object*>(intValue), …})` | 465 | 17 | `java.lang.invoke`, `FutureTask`, `Striped64`, `ForkJoinPool`, `AbstractSelectionKey` and others |
| B | **`Iface.super.m()`** (calling a default method of a superinterface) emits `super::m`, which names the superclass | 66 | 25 | `java.time.*` (`Temporal.super.with`), `Class`, `Package`, `AccessibleObject` (`isAnnotationPresent`), `BootstrapLogger` |
| C | **A local class created from inside another class** (an anonymous class or a sibling) does not get its captured variables or outer instance passed | 54 | 27 | `ReduceOps` (`supplier`, `accumulator`, `combiner`), `Collectors`, `SpinedBuffer.Splitr`, `SliceOps` |
| D | **Interface diamonds with primitive specializations**: "no unique final overrider" and "abstract class type" (missing bridges or overriders for `Node.OfInt` / `Spliterator.OfInt` style hierarchies) | 65 | 34 | `java.util.stream.Nodes`, `StreamSpliterators`, `SpinedBuffer`, `Spliterators` |
| E | **Unboxing a type-variable value to a primitive** emits unbalanced parentheses: `(npc(java_cast<Double*>(value)) : …` | 12 | 3 | `FindOps` |
| F | **Indexing an array whose type is a type variable** (`T_ARR spine`): `(*spine)[ch]` on `Object` | 3 | 3 | `SpinedBuffer.OfPrimitive` |
| G | **A Java name clashes with a C macro**: `IOV_MAX` (from `<limits.h>`) | 19 | 3 | `sun.nio.ch.IOUtil` |
| H | **A local variable hides a method of the same name**: `int next; … next(…)` | 6 | 1 | `sun.nio.fs.Globs` |
| I | **A pattern variable is hoisted** into a declaration that clashes with a later local of the same name | 2 | 1 | `Provider.Service` |
| J | **A `volatile` field becomes `std::atomic`**, so `->` on a volatile array field and `\|=` on a volatile `boolean` do not compile | 2 | 2 | `BigInteger`, `HttpClient` |

One more error line is two compiler outputs interleaved by `-j8` and is not
a separate cause.

Causes B to J are ordinary translator gaps that real user code can also hit
(B, C, H, I and J especially). They belong in Phase 9 as new G-items, each
with a fixture. Cause A needs a decision: signature-polymorphic methods
cannot be translated as varargs. Either report them as a new problem ID or
lower `VarHandle` operations to `std::atomic` operations.

An unpatched run (with problem units translated anyway) failed far more
widely, with 1,566 error lines. Most were cascading `fatal error:
java/util/Map.hpp: No such file or directory`, because the units that
crashed wrote no output. This confirms that the problem gate is needed.

### 3.2 Even a clean build would not run

- `main` calls `init_jvm()` and `make_args()`, and the generated code calls
  `class_()`, `operator""_j` and `unimplemented_()`. A runtime must provide
  all of them.
- All 694 natives are stubs that call `unimplemented_()`.
- `System.out` is declared `public static final PrintStream out = null;`.
  The translated `clinit()` assigns `out_ = nullptr`, and only
  `System.initPhase1()` (called by the VM) together with the native
  `setOut0` sets it (see section 5).

### 3.3 Curated prototype: P1 runs

A hand-written source tree, `mini/` (17 files, 135 lines), took the place of
`java.base` on the source path:

- `Object`, with natives `getClass`, `hashCode` and `clone`.
- `String`, holding `char[]`.
- `Class`, `ClassLoader`, `Cloneable`, `Serializable`.
- `Throwable`, `Exception`, `RuntimeException`, `NullPointerException`,
  `ClassCastException`, `ArrayStoreException`,
  `CloneNotSupportedException`.
- `OutputStream`, and `FileOutputStream` with a native `writeBytes(fd, …)`.
- `PrintStream`, which encodes UTF-8 itself.
- `System`, with `out` and `err` initialized directly and no `initPhase1`.

It was translated with the same harness (system library off), giving 14
classes and 36 files. Three hand-written C++ files completed it:

- `j2c_runtime.cpp` (36 lines): `class_`, `unimplemented_`, `_j`,
  `init_jvm` and `make_args`.
- `Object-native.cpp` and `FileOutputStream-native.cpp`, which replaced the
  generated stubs.

The build had no errors, and `./bin/P1` printed `h i \n` and exited with 0
(built with `-fsanitize=undefined`).

Issues found on the way, all small J2C changes:

1. The **system library switch** described in section 1 (required).
2. **`Class` has to reach the closure.** Every generated class declares
   `class_()` and `getClass0()` returning `java::lang::Class*`, but `Class`
   is not a hard dependency. It got in only because `Object.getClass()`
   mentions it.
3. **`SubArray.hpp` includes `java/lang/ArrayStoreException.hpp`**, but J2C
   does not make that class a hard dependency. It had to be passed as an
   explicit input.
4. **`Object.clone()` must exist**, because `Array<T>::clone() override`
   requires it.
5. **Link order:** the generated Makefile links `-l$(NAME) $(EXTRA_LIBS)`.
   Runtime objects that refer back to `lib$(NAME)` need to come before it,
   or the library has to be listed twice.

## 4. Native and VM surface

### 4.1 Native methods in the closure

694 native stubs in 94 files (J2C's count), or 655 declarations in 89
classes by a source scan. By package:

| Package | Natives |
| --- | --- |
| `java.lang` (`Class` 33, `StrictMath` 16, `Thread` 15, `System` 9, `ClassLoader` 7, `ProcessImpl`, `Runtime`, `Object`, `Float`/`Double`, `String.intern`, `StringUTF16.isBigEndian`, …) | 132 |
| `sun.nio.ch` (sockets, selectors, file dispatch) | 130 |
| `jdk.internal.misc` (`Unsafe` 70, `CDS` 9, `VM` 8, `Signal`, `ScopedMemoryAccess`) | 92 |
| `sun.nio.fs` (`WindowsNativeDispatcher`) | 70 |
| `java.net` | 62 |
| `java.io` (`WinNTFileSystem` 19, `FileInputStream`/`FileOutputStream`, `RandomAccessFile`, `FileDescriptor`, `Console`) | 52 |
| `java.lang.reflect` (`Array` 21, `Executable`, `Field`, `Proxy`) | 24 |
| `jdk.internal.reflect` (`ConstantPool` 18, `Reflection`, `NativeAccessors`) | 23 |
| `java.lang.invoke` (`MethodHandleNatives`; the scan misses the annotated signature-polymorphic `MethodHandle`/`VarHandle` methods) | 14 |
| `java.util.zip` (14), `jdk.internal.perf`, `jdk.internal.loader`, `java.lang.ref` and others | 56 |

The JVM (P1, `-Xlog:jni+resolve=debug`) **calls** 63 natives:

- Startup: `VM.initialize`, `SystemProps$Raw.platformProperties` and
  `vmProperties`, `CDS.*`, `Signal`, `Win32ErrorMode`, `Runtime.maxMemory`
  and `availableProcessors`.
- Standard streams: `FileDescriptor.initIDs`, `getHandle` and `getAppend`;
  `System.setIn0`, `setOut0` and `setErr0`; `FileOutputStream.writeBytes`.
- Module and class loading: `Module.defineModule0` and the `addExports*`
  natives, `ClassLoader.findBootstrapClass` and `defineClass1`, and
  `FileInputStream.open0` and `readBytes` for reading `P1.class`.
- Reflection: `Class.forName0`, `getPrimitiveClass` and
  `isAssignableFrom`, `Reflection.getCallerClass`,
  `NativeConstructorAccessorImpl.newInstance0`, `Array.newArray`.
- Bits: `Float.floatToRawIntBits`, `Double.doubleToRawLongBits`, and
  `String.intern`.

It also registers 136 natives at startup that it may call through
intrinsics: `Unsafe` 69, `Class` 28, `Thread` 13, `MethodHandleNatives` 13,
`Object` 5 and `System` 3. P3 adds only `Class.isInstance` and
`ClassLoader.defineClass0` (lambda classes).

### 4.2 JVM internals the closure relies on

| Internal | Files in the J2C closure | Files in P1's JVM-loaded set |
| --- | --- | --- |
| `jdk.internal.misc.Unsafe` | 167 | 40 |
| `SharedSecrets` (JavaLangAccess and others) | 118 | 54 |
| `AccessController.doPrivileged` | 160 | 31 |
| `MethodHandle` / `MethodHandles` | 100 | 35 |
| `VarHandle` | 65 | 9 |
| Reflective lookup (`getDeclaredField/Method`, `newInstance`) | 96 | 28 |
| `@CallerSensitive` / `Reflection.getCallerClass` | 26 | 19 |
| `synchronized` | 288 | 82 |
| `ServiceLoader` | 34 | 6 |
| `Class.forName` | 52 | 11 |

### 4.3 What a hand-written C++ layer must provide **(estimate)**

- **P1 on translated OpenJDK code:**
  - Real `Class` objects. `class_()` must return distinct instances,
    because the code compares `Object[].class` and asks for component
    types.
  - `Unsafe`: at least field offsets, `compareAndSet` and volatile
    get/put, since `String`, `AbstractStringBuilder`, `ConcurrentHashMap`
    and `Thread` reach it.
  - The `SystemProps` natives, `VM.initialize`, `FileDescriptor`, and
    `setIn0`/`setOut0`/`setErr0`.
  - `Thread.currentThread`, plus a main `Thread` and `ThreadGroup`.
  - `Reflection.getCallerClass`.
  - `String.intern`, plus `Float`/`Double` bits and `StringUTF16`.
  - A replacement for module boot.

  That is about 60–80 natives and several VM services, before charset
  lookup (`Charset.forName` goes through `ServiceLoader` and
  `doPrivileged`) works. **Estimate: 3–6 weeks** just for Hello World, with
  high risk of further blockers.
- **P1 on the curated route:** 3 natives (`Object.hashCode`,
  `Object.getClass`, `FileOutputStream.writeBytes`) plus the 5 hooks.
  Already shown working.
- **P3 on the curated route:** about 30–40 natives **(estimate)**:
  - `System.arraycopy`, `identityHashCode`, `currentTimeMillis` and
    `nanoTime`.
  - `Float`/`Double` bit conversions and the `Math`/`StrictMath`
    functions.
  - `Class` basics: name, `isInstance`, `isArray`, component type, and
    `Array.newInstance`.
  - `Throwable.fillInStackTrace` (a no-op), the uncaught-exception
    printout, and `Object.clone` for arrays.

  On top of that come plain-Java classes, which can be imported from
  OpenJDK with light patches: `ArrayList`, `HashMap`, `AbstractList`,
  `AbstractMap`, iterators, `Collections`, `Arrays`, `TimSort`,
  `ComparableTimSort`, `Objects`, `Integer`/`Long` parsing and
  formatting, `AbstractStringBuilder` and `StringBuilder`. **Estimate:
  80–150 runtime classes.**

## 5. Startup path for `System.out`

In JDK 17, `System.out` is `public static final PrintStream out = null;`.
The VM sets it during startup:

1. `System.registerNatives()` runs in `<clinit>`.
2. The VM calls `System.initPhase1()`, which does the following:
   - `setJavaLangAccess()` installs `SharedSecrets`.
   - `SystemProps.initProperties()` calls the native `Raw.platformProperties`
     and `vmProperties`, then `VersionProps.init`, `VM.saveProperties`,
     `createProperties` and `StaticProperty`.
   - It builds `FileInputStream(FileDescriptor.in)` and
     `FileOutputStream(FileDescriptor.out/err)`; `FileDescriptor` needs the
     natives `initIDs`, `getHandle` and `getAppend`.
   - `newPrintStream(fos, props.getProperty("sun.stdout.encoding"))` creates
     `new PrintStream(new BufferedOutputStream(fos, 128), true[, enc])`.
     `PrintStream` creates an `OutputStreamWriter`, then a `StreamEncoder`,
     a `CharsetEncoder` for the platform charset (`MS1252` or `UTF_8`
     here), and `ByteBuffer`/`CharBuffer`/`HeapByteBuffer`, `CoderResult`
     and `CodingErrorAction`.
   - `setIn0`, `setOut0` and `setErr0` (natives) write the final fields.
   - `Terminator.setup()` installs signal handlers through
     `jdk.internal.misc.Signal`; then come `VM.initializeOSEnvironment()`,
     thread-group registration and `VM.initLevel(1)`.
3. `initPhase2` runs `ModuleBootstrap.boot()`, the boot module layer. It is
   the source of most of the 320 classes loaded for P1.
4. `initPhase3` sets up the security manager, the system class loader and
   `VM.initLevel(4)`.

The `println` path itself, in P1's loaded set, uses about 70 classes:
`java.io` (23), `java.nio` and `java.nio.charset` (12), `sun.nio.cs` (14),
`String` / `StringLatin1` / `StringUTF16` / `StringCoding`, `Character` /
`CharacterData`, and `jdk.internal.util` (4).

**A smaller route exists, and it is how J2ObjC works:**

- Replace `System` with a version that builds `out` and `err` directly,
  with no properties, `initPhase1` or module boot.
- Replace `PrintStream` (or `StreamEncoder`) with a version that encodes
  UTF-8 itself and writes through one native.
- Use a simplified `String` that keeps its UTF-16 `char[]`. This removes
  OpenJDK's `coder`/Latin-1 compact strings, `StringLatin1`,
  `StringUTF16` intrinsics and `StringConcatHelper`.

The prototype in section 3.3 is exactly this: three small Java classes in
place of about 70 classes and initPhase1/2/3.

## 6. Comparison with J2ObjC's `jre_emul`

- **Layout and sources.** `jre_emul` keeps Java sources by origin, so that
  each upstream is credited:
  - `android/`: AOSP libcore, with newer code in `android/platform/`.
  - `openjdk/`: synced from OpenJDK and "modified in places".
  - `apache_harmony/`: being migrated to the Android equivalents.
  - `apple_apsl/`.
  - `Classes/`: Objective-C written for the project, including
    "drop-in replacements" for translated classes, such as a faster
    `java/util/HashMap.h/.m`.
  - `stub_classes/`: empty Java stand-ins for classes that are mapped to
    Foundation or implemented natively, so that `jre_emul.jar` defines
    them during translation.

  Sources: [jre_emul tree](https://github.com/google/j2objc/tree/master/jre_emul),
  [jre_emul/README](https://github.com/google/j2objc/blob/master/jre_emul/README).
- **How classes are chosen.** `jre_sources.mk` lists every file
  explicitly, in areas such as `JAVA_PUBLIC_SOURCES_CORE`, `_IO`, `_NET`,
  `_UTIL`, `_CONCURRENT`, `_SECURITY`, `_TIME`, `_ZIP` and `_XML`. Next to
  those are `JAVA_PRIVATE_SOURCES_*` (`libcore.*`, `sun.*`, a few
  `jdk.internal` classes such as `Preconditions`) and `NATIVE_JRE_SOURCES_*`
  (`.m` files). `EMULATED_JAVA_SOURCES` (`Object`, `String`, `Class`,
  `Number` and others) are hand-written in Objective-C and not translated.
  The lists total roughly 1,000 or more Java files **(rough count from the
  listing)**. Source: [jre_sources.mk](https://github.com/google/j2objc/blob/master/jre_emul/jre_sources.mk).
- **Replaced or patched classes.** The root object is
  `NSObject+JavaObject`. `String` maps onto `NSString`
  (`NSString+JavaString.m`). Boxed numbers use `NSNumber+JavaNumber`.
  `IOSClass`, `IOSArray` and `IOSObjectArray` stand in for `Class` and
  arrays; reflection and `Thread` are native `.m` files. Many OpenJDK and
  Android files carry local modifications.
- **Natives (OCNI).** Native method bodies are written inside the Java file
  as comments delimited by `/*-[` and `]-*/`; J2ObjC copies the body into
  the generated method. `/*-HEADER[` blocks write into the header. A
  `native` method without an OCNI comment is treated as plain JNI. javac
  ignores the comments, so the same file still compiles for the JVM. This
  maps directly onto J2C: a `/*-{ … }-*/` C++ block could replace the
  `-native.cpp` stub. Source: [Writing Native Methods](https://developers.google.com/j2objc/guides/writing-native-methods).
- **Size control.** The emulation library ships as subset libraries:
  `jre_core` with most classes, then `jre_io`, `jre_concurrent`,
  `jre_security`, `jre_zip` and others. Apps link `jre_core` and add
  subsets when symbols are missing. Source: [Required link flags](https://developers.google.com/j2objc/guides/required-link-flags).
- **License.** The top-level `LICENSE` bundles Apache 2.0 for J2ObjC's own
  code, GPLv2 with the Classpath Exception for the Oracle/OpenJDK-derived
  files, APSL 2.0, the ICU license, CPL 1.0 and others. The runtime is a
  mixture, with the OpenJDK parts under GPLv2+CE. Source: [j2objc LICENSE](https://github.com/google/j2objc/blob/master/LICENSE).

**Lesson for J2C:** J2ObjC does not translate the JDK wholesale. It
translates a hand-picked, patched list and writes the root types (`Object`,
`String`, `Class`, arrays) natively. J2C already writes arrays and the hooks
in C++ (`Array.hpp`, `ObjectArray.hpp`), so it is already halfway there.

## 7. License

*This is not legal advice. Have the maintainer or counsel confirm before
shipping a runtime.*

- **OpenJDK's license.** java.base is GPLv2 with the Classpath Exception;
  every one of the 2,066 closure files carries the exception notice.
  GPLv2 §0 defines a "work based on the Program" to include the Program or
  a portion of it "either verbatim or with modifications and/or translated
  into another language". **A C++ translation of OpenJDK classes is
  therefore a derivative work and must be distributed under GPLv2**, with
  source (§3). You may keep the Classpath Exception on your version, but
  you are not obliged to. Source: [GPLv2](https://www.gnu.org/licenses/old-licenses/gpl-2.0.html).
- **What the Classpath Exception does.** "Linking this library statically
  or dynamically with other modules is making a combined work based on
  this library", but the copyright holders give you "permission to link
  this library with independent modules to produce an executable,
  regardless of the license terms of these independent modules". The
  resulting executable may be distributed "under terms of your choice",
  provided each independent module's own license is met. An independent
  module is one "not derived from or based on this library". So a user's
  translated program can link a GPLv2+CE runtime and keep its own license.
  The runtime itself, including any changes to it, stays GPLv2(+CE).
  Source: [OpenJDK GPLv2 + Classpath Exception](https://openjdk.org/legal/gplv2+ce.html).
- **Open questions:**
  - **J2C's own helper headers.** `Array.hpp`, `ObjectArray.hpp`,
    `SubArray.hpp`, `npc.hpp`, `java_cast.hpp` and the Makefile template
    are EPL-1.0 like the rest of J2C, and every runtime file compiles
    against them. The FSF lists EPL-1.0 as GPL-incompatible
    ([FSF license list, EPL](https://www.gnu.org/licenses/license-list.html#EPL)).
    A GPL runtime built on EPL-1.0 headers is at least questionable.
    Possible fixes: relicense or dual-license those headers (this needs the
    copyright holders), or keep any OpenJDK-derived code in a separately
    distributed package and ask counsel.
  - **Headers generated from OpenJDK.** Generated headers for JDK classes
    contain OpenJDK-derived declarations, and user code includes them.
    The Classpath Exception is meant for exactly this kind of linking, but
    say so explicitly in the runtime's notices.
  - **Hand-written runtime code** (Java or C++) written from the API
    specification rather than copied has none of these problems, and can
    use J2C's license or a permissive one. The current Phase 10 plan
    assumes this, and the curated route keeps it.
- **Other sources:**
  - **Apache Harmony** is Apache-2.0 and was retired in 2011
    ([Apache Attic](https://attic.apache.org/projects/harmony.html)). Its
    API level is Java 5/6: no lambdas, streams, `java.time`, or default
    methods on `Collection`/`Map`. It is usable only for isolated
    algorithms.
  - **Android libcore** has used OpenJDK-derived code (`ojluni`, GPLv2+CE)
    for most of `java.*` since Android 7, so it brings the same license as
    OpenJDK, plus Android-specific natives
    ([libcore ojluni](https://android.googlesource.com/platform/libcore/+/refs/heads/main/ojluni/)).

## 8. Recommendation for Phase 10

**Adopt the curated hybrid.** The runtime is a source tree of Java
classes, hand-written or (license permitting) lightly patched OpenJDK, that
J2C translates with the same writers as user code. A small C++ layer
provides the natives and the hooks.

This replaces step 2's "hand-written C++ classes", but keeps its
milestones, tests and differential-testing approach. Step 1
(`--runtime <dir>`) is still the delivery mechanism. The runtime's headers
are generated by J2C, so the conformance check becomes a regression test
instead of a drift detector.

Reasons:

- **Measured:** wholesale translation is about 900k lines with a VM-shaped
  dependency (natives, `Unsafe`, `VarHandle`, module boot) and no small
  subset: J2C's closure is the same for every program. Even with every
  translator bug fixed, months of VM emulation would come before Hello
  World.
- **Measured:** the curated route prints `hi` today with 135 lines of Java
  and 70 lines of C++, after small J2C changes.
- **Behavior matching** is better than a C++ rewrite. Where a class is
  plain Java (collections, `TimSort`, `Integer.parseInt`), the runtime can
  use the reference algorithm. Where it is VM-bound (`String` internals,
  `System`, `Class`, `Thread`), the runtime uses a simplified Java version,
  as J2ObjC does.
- **Dogfooding:** every runtime class is also a large J2C fixture. The
  build-error causes found here (section 3.1) are J2C bugs that user code
  can hit too.

### Effort (**estimates**)

| Work | Estimate |
| --- | --- |
| J2C changes for runtime-from-source mode (below) | 2–4 days |
| R1: P1 runs in CI against a `runtime/java` tree | 1–2 days after that |
| R2: P2-level programs (`StringBuilder`, boxed types, `Integer` parsing and formatting, `Math`, exceptions and the uncaught-exception printout) | 1–2 weeks |
| R3: P3-level programs (collections, iterators, `Collections.sort`, `Comparator` default methods); needs causes B, C and D fixed | 2–3 weeks |
| R4: P4-level programs (streams, a simplified `Formatter`); streams need causes C to F, and `Formatter` needs a locale-free rewrite | 3–5 weeks |
| Fixing build causes A–J in the translator | about 1–2 weeks in total; A (signature-polymorphic calls) is a design decision |

## Proposed Phase 10 changes

The block below can be pasted into `docs/PUBLISHING_PLAN.md`, Phase 10. It
replaces the "Design" bullets of step 2 and adds a step 0.

```markdown
### Experiment result (2026-10-11)

See [RUNTIME_EXPERIMENT.md](RUNTIME_EXPERIMENT.md). Translating OpenJDK 17's
java.base wholesale is not viable: J2C's dependency closure is the same
2,066 files (903k lines) for every program, needs 694 natives plus HotSpot
services (`Unsafe`, `VarHandle`, `MethodHandle`, module boot), and the stock
CLI cannot even resolve `java.lang` from sources. A curated runtime written
in Java and translated by J2C printed "hi" with 17 Java files (135 lines) and
70 lines of C++.

### Step 0 — Runtime-from-source support in J2C (target: 0.6.x, before step 1)

1. **Sources replace the system library.** When the source path (or a new
   `--runtime <dir>`) provides `java/lang/Object.java`, call JDT's
   `setEnvironment(..., includeRunningVMBootclasspath = false)`. Today J2C
   always includes the running JDK, so JDT reports "package java.lang is
   accessible from more than one module" and translation fails with "Could
   not resolve Java type java.lang.ClassLoader".
2. **Hard dependencies match what the helpers include:** `java.lang.Class`
   (every class declares `class_()`/`getClass0()`) and
   `java.lang.ArrayStoreException` (included by `SubArray.hpp`), next to
   `ClassLoader`, `Cloneable` and `Serializable`.
3. **Link order:** runtime objects and libraries come before or around
   `-l$(NAME)` (or use `--start-group`), because the runtime refers back to
   classes in the program's libraries.
4. **Natives:** keep `-native.cpp` stubs, and let a runtime supply its own
   file per class (an overlay directory). Optionally support an OCNI-style
   C++ block inside the Java `native` method, like J2ObjC's `/*-[ … ]-*/`.

Tests: a fixture runtime of about 17 Java files (`test/runtime-mini/`) and a
CI job that translates Hello World against it, builds in WSL or Linux with
UBSan, and compares the output with `java`.

### Step 2 design (replaces "hand-written C++")

- The bundled runtime is **Java source translated by J2C**, plus a small
  C++ layer for natives and hooks (`class_`, `_j`, `init_jvm`, `make_args`,
  `unimplemented_`).
- The core classes are **hand-written, simplified Java**, written from the
  Java SE specification, J2ObjC-style: `Object`, `String` (UTF-16
  `char[]`, no compact strings), `System` (no initPhase1, properties or
  module boot), `PrintStream` (UTF-8 encoding in Java), `Class`, the boxed
  types, `Math`, and the `Throwable` family.
- **Plain-Java library classes** (`ArrayList`, `HashMap`, `TimSort`,
  `Collections`, `Arrays`, …) are written for the runtime or, only after a
  license decision, imported from OpenJDK 17 into a separately licensed
  `runtime/openjdk/` tree (GPLv2 with the Classpath Exception), each listed
  explicitly with its patches, as J2ObjC's `jre_sources.mk` does. Blocker:
  J2C's helper headers are EPL-1.0, which the FSF considers GPL-incompatible.
- Never translate java.base wholesale. The runtime manifest lists every
  provided class, and classes it does not list keep today's stubs.

### Translator gaps found by the experiment (add to Phase 9)

Each needs a fixture. Counts are from compiling the P1 closure of java.base.

- **G32, signature-polymorphic calls** (`MethodHandle.invoke*`,
  `VarHandle.*`) are emitted as `Object...` varargs (465 errors). Report a
  new problem ID, or lower `VarHandle` access modes to `std::atomic`.
- **G33, `Iface.super.m()`** emits `super::m` (66 errors, 25 files).
- **G34, a local class created from another class** does not pass its
  captured variables or outer instance (54 errors, 27 files).
- **G35, interface diamonds with primitive specializations**: missing
  final overriders or bridges, so classes stay abstract (65 errors).
- **G36, unboxing a type-variable value to a primitive** emits
  unbalanced parentheses (12 errors).
- **G37, indexing an array whose type is a type variable** (3 errors).
- **G38, identifiers that are C macros** (`IOV_MAX`) need mangling
  (19 errors).
- **G39, a local variable named like a method that is called in its
  scope** (6 errors).
- **G40, a pattern variable hoisted into a clashing declaration**
  (2 errors).
- **G41, volatile fields as `std::atomic`**: `->` on arrays and compound
  assignment such as `|=` (2 errors).
- **G42, `LambdaDesugarer` output that JDT rejects**:
  - Diamond with constructor references (`Collectors.toMap`).
  - Member types of parameterized types (`Proxy`).
  - Captured-wildcard suppliers (`AbstractPipeline`).

  The writers also crash with "closures is null" if such a unit is ever
  written.

### Milestones (replace step 2's list)

1. **R1:** Hello World runs against `runtime/java` (step 0 tests). About
   1 week **(estimate)**, including step 0.
2. **R2:** strings, `StringBuilder`, boxed types, `Math`, exceptions and the
   uncaught-exception printout. About 1–2 weeks **(estimate)**.
3. **R3:** collections and `Collections.sort` with comparators; needs
   G33–G35. About 2–3 weeks **(estimate)**.
4. **R4:** streams and a simplified `String.format`. About 3–5 weeks
   **(estimate)**.
5. **R5:** optional Boehm GC (G8), unchanged.
```

## Appendix: how to reproduce

The scratch folder used was
`%LOCALAPPDATA%\Temp\claude\…\scratchpad\runtime-exp\`. Nothing there is
part of the repository. To repeat the experiment:

1. Extract `java.base/**/*.java` from `lib/src.zip`.
2. Shadow `SourceProject` (passing
   `!Boolean.getBoolean("j2c.noSystem")` as the last `setEnvironment`
   argument) and `Transformer` (log the problems of each unit and keep
   going), compile both against `target/j2c-cli.jar`, and put them first on
   the classpath:
   `java -Xmx8g -Dj2c.noSystem=true -Dj2c.experimentLog=log.tsv -cp shadow;j2c-cli.jar se.arnetheduck.j2c.cli.Main --output OUT --sourcepath "PROG;overlay;java.base" PROG`.
3. Translation takes about 68 s per program. Build with
   `MSYS_NO_PATHCONV=1 wsl -d Ubuntu make -k -j8 -C /mnt/c/...` (about
   4–5 min).
4. For the JVM comparison, run `java -Xshare:off -verbose:class` and
   `-Xlog:jni+resolve=debug`.
