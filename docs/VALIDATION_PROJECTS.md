# Validation projects

Open-source Java projects chosen to validate J2C end to end. The goal is to
translate each project fully to C++20, build it, and run its own unit tests
against the C++. That is stronger evidence than our fixtures: real code
written by other people, with a test suite that already states what correct
means.

The list has three tiers by size. Start small, and move up a tier only once
the tier below translates, builds and passes.

Measured on 2026-10-11 from shallow clones of each default branch. Details
are in [Methodology and caveats](#methodology-and-caveats).

## How to read this

- **Main / test lines:** non-blank lines in `.java` files outside and inside
  test directories.
- **Java:** the source level in the build file. J2C reads Java 17. A project
  at 8 or 11 is fine; one at 21 is not (see TheAlgorithms).
- **Deps:** non-test runtime dependencies. They must be on `--classpath`
  (or added to `--sourcepath`) before `--check` can pass.
- **Baseline:** the first measurement (2026-10-11), the result of
  `j2c --check --format json --sourcepath <main roots> <main roots>`, with
  **no dependencies supplied**. The [scoreboard](#scoreboard) has the
  current results, with dependencies. Most problems are therefore unresolved
  imports, not J2C gaps. Each project's notes give the real cause.
- **Tests:** the test frameworks and features found, checked against what
  `--tests` supports today (Phase 13 T1–T3a): JUnit 4 and 5 `@Test`,
  lifecycle methods, `@Disabled`/`@Ignore`, `expected`, plus JUnit
  assertions and assumptions. Mockito, parameterized tests, `@Nested`,
  Hamcrest, AssertJ and Truth are reported as `J2C-TEST-*`, not translated.
- **Rating:** how soon the project can be a full validation target.
  - **A:** translates clean now; no deps; mostly supported tests.
  - **B:** needs dependency JARs or a few test features.
  - **C:** needs the Phase 10 runtime for much of its behavior, or large
    unsupported test areas.
  - **D:** stretch target.

All of these projects call the JDK (`String`, collections, I/O). Until the
Phase 10 runtime exists, translated code compiles and links, but JDK calls
reach `unimplemented_()` stubs. So "translates clean" here means `--check`
passes and the C++ is expected to build; running the tests for real needs
Phase 10 step 1 and Phase 13 T3b.

## Scoreboard

How far each project gets, from `test/corpus/projects.json`. `npm run corpus`
(in `vscode/`) checks every project at its pinned commit and fails when a
problem count rises or a project drops a step. `--build` also translates and
compiles. `--update` raises the expectations and rewrites this table.

<!-- corpus-scoreboard:begin -->
| Project | Tier | Problems | Ladder step |
| --- | --- | ---: | --- |
| hashids-java | small | 0 | builds |
| jBCrypt | small | 0 | builds |
| semver4j | small | 0 | builds |
| bcrypt | small | 0 | builds |
| javapoet | small | 0 | checks |
| jsemver | small | 0 | builds |
| minimal-json | small | 0 | builds |
| JavaHamcrest | small | 0 | builds |
| JSON-java | medium | 0 | builds |
| javaewah | medium | 0 | builds |
| gson | medium | 8 | none |
| commons-codec | medium | 0 | builds |
| williamfiset-algorithms | medium | 0 | builds |
| jsoup | medium | 0 | checks |
| commons-text | medium | 4 | none |
| RoaringBitmap | medium | 0 | checks |
| zxing-core | medium | 0 | builds |
| jackson-core | medium | 3 | none |
| joda-time | large | 0 | checks |
| commons-collections | large | 4 | none |
| TheAlgorithms-Java | large | 32 | none |
| commons-lang | large | 0 | checks |
| javaparser | large | 431 | none |
| commons-math | large | 918 | none |
| guava | xl | 17 | none |
| forge-engine | xl | 0 | checks |
<!-- corpus-scoreboard:end -->

## Summary

### Small (under 10k main lines)

| Project | License | Main / test lines | Java | Deps | Baseline | Tests | Rating |
| --- | --- | ---: | --- | --- | --- | --- | --- |
| [yomorun/hashids-java](https://github.com/yomorun/hashids-java) | MIT | 322 / 180 | – | none | **clean** (1 file) | JUnit 4 | A |
| [jeremyh/jBCrypt](https://github.com/jeremyh/jBCrypt) | ISC-style | 727 / 183 | – | none | **clean** (1 file) | JUnit 3 | A |
| [vdurmont/semver4j](https://github.com/vdurmont/semver4j) | MIT | 1,412 / 1,198 | – | none | **clean** (5 files) | JUnit 4, Mockito (2), parameterized (1) | A |
| [patrickfav/bcrypt](https://github.com/patrickfav/bcrypt) | Apache-2.0 | 1,917 / 1,736 | – | bytes, commons-cli | 79 in 6/11 files (deps) | JUnit 4, Mockito (1) | B |
| [square/javapoet](https://github.com/square/javapoet) | Apache-2.0 | 4,447 / 6,451 | 8 | none | **clean** (17 files) | JUnit 4, Truth (17), Mockito (3) | C |
| [zafarkhaja/jsemver](https://github.com/zafarkhaja/jsemver) | MIT | 4,504 / 2,650 | – | none | **clean** (25 files) | JUnit 5, parameterized (4), `@Nested` (2) | A |
| [ralfstx/minimal-json](https://github.com/ralfstx/minimal-json) | MIT | 5,361 / 3,851 | – | none | **clean** (15 files) | JUnit 4, Mockito (7) | A |
| [hamcrest/JavaHamcrest](https://github.com/hamcrest/JavaHamcrest) | BSD-3-Clause | 9,324 / 4,238 | 8 | jMock 1, EasyMock (integration module only) | 16 in 4/105 files (deps) | JUnit 5, Hamcrest itself | B |

### Medium (10k–60k main lines)

| Project | License | Main / test lines | Java | Deps | Baseline | Tests | Rating |
| --- | --- | ---: | --- | --- | --- | --- | --- |
| [stleary/JSON-java](https://github.com/stleary/JSON-java) | Public Domain | 10,835 / 14,460 | 8 | none | **clean** (26 files) | JUnit 4, Mockito (1) | A |
| [lemire/javaewah](https://github.com/lemire/javaewah) | Apache-2.0 | 12,891 / 6,891 | 8 | none | **clean** (62 files) | JUnit 4 and 3 | A |
| [google/gson](https://github.com/google/gson) | Apache-2.0 | 21,403 / 29,373 | 8 / 11 | Error Prone annotations; extras: Jackson annotations, Guava, protobuf | 430 in 28/102 files (deps) | JUnit 4, Truth (133) | C |
| [apache/commons-codec](https://github.com/apache/commons-codec) | Apache-2.0 | 25,280 / 24,989 | 8 | none | **clean** (90 files) | JUnit 5, parameterized (36) | A |
| [williamfiset/algorithms](https://github.com/williamfiset/algorithms) | MIT | 25,409 / 13,973 | – | none | **clean** (216 files) | JUnit 5, Truth (90) | B |
| [jhy/jsoup](https://github.com/jhy/jsoup) | MIT | 26,713 / 26,352 | 8 / 11 | JSpecify annotations, re2j (optional) | 345 in 56/96 files (deps) | JUnit 5, parameterized (18) | B |
| [apache/commons-text](https://github.com/apache/commons-text) | Apache-2.0 | 27,622 / 21,630 | 8 | commons-lang3 | 250 in 34/113 files (deps) | JUnit 5, parameterized (15), Mockito (4) | B |
| [RoaringBitmap/RoaringBitmap](https://github.com/RoaringBitmap/RoaringBitmap) | Apache-2.0 | 45,743 / 57,674 | – | none | 4 in 1/118 files (module layout) | JUnit 5, parameterized (35) | B |
| [zxing/zxing](https://github.com/zxing/zxing) | Apache-2.0 | 52,350 / 14,034 | 8 | GWT, Jakarta, Android (non-core modules) | 973 in 27/284 files (deps) | JUnit 4 | B (core only) |
| [FasterXML/jackson-core](https://github.com/FasterXML/jackson-core) | Apache-2.0 | 54,727 / 66,892 | 17 | fastdoubleparser | 20 in 7/139 files (deps, generated source) | JUnit 5, parameterized (9) | B |

### Large (over 60k main lines)

| Project | License | Main / test lines | Java | Deps | Baseline | Tests | Rating |
| --- | --- | ---: | --- | --- | --- | --- | --- |
| [JodaOrg/joda-time](https://github.com/JodaOrg/joda-time) | Apache-2.0 | 68,794 / 64,556 | 5 | joda-convert (optional) | 74 in 24/166 files (deps) | JUnit 3 (137 files) | B |
| [apache/commons-collections](https://github.com/apache/commons-collections) | Apache-2.0 | 74,987 / 54,179 | 8 | commons-codec (optional) | 4 in 3/367 files (**J2C gaps**) | JUnit 5, `@Nested` (15), parameterized (17) | B |
| [TheAlgorithms/Java](https://github.com/TheAlgorithms/Java) | MIT | 78,979 / 51,281 | **21** | commons-lang3, commons-collections4 | 101 in 24/847 files (deps, Java 21) | JUnit 5, parameterized (206) | C |
| [apache/commons-lang](https://github.com/apache/commons-lang) | Apache-2.0 | 98,564 / 90,577 | 8 | none | **clean** (264 files) | JUnit 5, parameterized (36) | B |
| [javaparser/javaparser](https://github.com/javaparser/javaparser) | Apache-2.0 or LGPL-3.0 | 122,258 / 196,246 | 8 | javassist; JavaCC-generated parser | 893 in 58/757 files (deps, generated source, J2C gaps) | JUnit 5, Mockito (23), `@Nested` (20) | C |
| [apache/commons-math](https://github.com/apache/commons-math) | Apache-2.0 | 132,887 / 85,514 | 8 | commons-numbers, commons-rng, commons-statistics | 918 in 92/658 files (deps) | JUnit 4 (324 files) | B |
| [google/guava](https://github.com/google/guava) | Apache-2.0 | 400,140 / 305,905 | 8 | Error Prone, JSpecify, j2objc annotations, failureaccess | not run | JUnit 3 and 4, Truth (707) | D |

### Extra large: real application

| Project | License | Main / test lines | Java | Deps | Baseline | Tests | Rating |
| --- | --- | ---: | --- | --- | --- | --- | --- |
| [Card-Forge/forge](https://github.com/Card-Forge/forge) | GPL-3.0 | 443,054 / 143 test files | 17 | Guava, commons-lang3, commons-text, jgrapht, commons-math3, sentry, tinylog; Swing and libGDX in the UI modules | engine **clean** (1,165 files, with dependency JARs) | TestNG (95 files) | D (engine B) |

## Project notes

Shared facts are in the tables. These notes cover what each project
exercises, what will get in the way, and the real cause of its baseline
problems.

### Small

**hashids-java.** One class encoding numbers as short strings. Exercises
`long` arithmetic, `String` and `char` handling, and arrays. A good first
end-to-end target: one file, one test class.

**jBCrypt.** The original single-file bcrypt. Heavy on `int` bit operations,
shifts, unsigned right shift and arrays, which checks `java_math.hpp`'s
`jshl`/`jushr` and the `-fwrapv` overflow rules. Its JUnit 3 test is a
`TestCase` subclass; the wrappers support JUnit 4 and 5 only, so JUnit 3
support (or a hand port) is needed to run it.

**semver4j.** Version parsing: enums, `switch`, string splitting, and
`equals`/`hashCode`/`compareTo`. Two test files use Mockito and are
reported. The rest are plain JUnit 4.

**patrickfav/bcrypt.** A modern bcrypt with a CLI module. All 79 problems
are dependencies: `at.favre.lib:bytes` in the core and commons-cli in the
CLI module. Uses `java.security` (`SecureRandom`, `MessageDigest`), so it
needs the runtime early.

**javapoet.** Generates Java source. It works on
`javax.lang.model.*` types and does much reflective `Class` handling
(11 files), so its behavior depends heavily on the JDK. All its tests use
Truth, which is not translated. Clean `--check` makes it a good translation
and build target, but a poor test target.

**jsemver.** Semantic versions with a hand-written expression parser.
Exercises a lexer, a recursive-descent parser, and one lambda. Its tests are JUnit 5; the parameterized tests (4)
and `@Nested` classes (2) are reported.

**minimal-json.** A small, fast JSON parser and writer. Exercises `Reader`
and `Writer` (31 of 37 files import `java.io`), character buffers, and
number formatting. Seven test files use Mockito.

**JavaHamcrest.** The matcher library: deep generic hierarchies and
wildcards (`Matcher<? super T>`), varargs and many static factory methods. A
strong test of generics translation. The 16 problems all come from the
legacy `hamcrest-integration` module (jMock 1 and EasyMock adapters). Skip
that module.

### Medium

**JSON-java.** The reference `org.json`. Exercises `HashMap`, `ArrayList`,
`BigDecimal`/`BigInteger`, reflection-based bean conversion (2 files) and
number parsing. Plain JUnit 4 tests make it a very good test target once a
runtime provides the collections and `String`.

**javaewah.** Compressed bitmaps: `long` bit manipulation, iterators,
`Cloneable`, and `Externalizable` serialization. No dependencies. An
excellent arithmetic test, since its tests check exact bit patterns.

**gson.** JSON to objects through reflection: 33 main files use
`java.lang.reflect`, `TypeToken` generics, and `sun.misc.Unsafe` (3 files).
Reflection is the hard part: J2C has no reflection runtime, so much of gson
cannot work even after translating. The core `gson` module needs only the
Error Prone annotations. Most of the 430 problems come from the `extras`,
`proto` and `metrics` modules (Jackson annotations, protobuf, Guava).
Almost all tests use Truth.

**commons-codec.** Base64, hex, digests, phonetic encoders. Exercises
`byte` arithmetic, lookup tables and `java.security.MessageDigest`. Clean
with no dependencies. Many tests are parameterized (36 files), which T1
reports; the rest are plain JUnit 5.

**williamfiset/algorithms.** Data structures and graph algorithms, written
as one class per algorithm. Wide coverage of collections, generics,
recursion and 31 files with lambdas. Its tests use Truth (90 files), so
running them needs Truth support or a conversion to JUnit assertions. The
repository uses Bazel, not Maven or Gradle.

**jsoup.** An HTML parser and DOM. A large state machine (the tokenizer and
tree builder are big `enum` classes with abstract methods), many
`switch`es, and `StringBuilder` work. Of the 345 problems, 335 are the
JSpecify `@Nullable`/`@NullMarked` annotations: add `jspecify.jar` to the
class path. The remaining few are the optional re2j regex.

**commons-text.** Text algorithms: string similarity, escaping, string
substitution. Every one of the 250 problems is commons-lang3, so translate
it together with commons-lang (below), which is already clean. Two
`J2C-LAMBDA-UNRESOLVED` problems are follow-ons from the missing types.

**RoaringBitmap.** Compressed bitmaps, buffer-backed (`ByteBuffer`,
`LongBuffer`) and performance-tuned. A strong test of `long`/`char`
arithmetic and `java.nio`. The 4 problems are `module-info.java` exports
that fail because the baseline passes the modules' roots together; check
each module separately, or leave `module-info.java` out.

**zxing.** Barcode reading and writing. The `core` module is pure Java
image math on `int` arrays with no dependencies. All 973 problems are in
other modules (GWT web UI, Jakarta servlets, Android, `javase` CLI).
Target `core/` only; its JUnit 4 tests compare decoded images and need
image I/O, so start with its pure-math tests.

**jackson-core.** The streaming JSON parser, already at Java 17. The 20
problems: the fastdoubleparser dependency, and a `PackageVersion` class
that Maven generates from a template at build time. Run the Maven build's
`generate-sources` phase, or write that class by hand. Exercises heavy
`byte`/`char` buffer work and number parsing.

### Large

**joda-time.** The classic date-time library. Exercises large class
hierarchies (chronologies, fields), serialization, time-zone data loaded
from compiled resources, and `ConcurrentHashMap`. All 74 problems are
joda-convert annotations: add the JAR. Its tests are JUnit 3 `TestCase`
classes (137 files), so JUnit 3 support would unlock nearly the entire
suite at once.

**commons-collections.** Collection decorators, maps, bags, tries and
Bloom filters. Generics-heavy. Already found J2C bugs: checking it found
the captured-wildcard lambda target bug, now fixed. The 4 remaining
problems are correct reports of real gaps:

- Two method references whose target type is the non-denotable
  intersection `Number & Comparable<?> & Constable`.
- One method reference whose target is `ToIntFunction<capture of ?>`.
- One lambda that refers to an anonymous class
  (`CellExtractor$1`).

Fixing these is Phase 9 work. Tests are JUnit 5 with many `@Nested` classes
(15 files).

**TheAlgorithms/Java.** Hundreds of small, independent algorithm classes,
which makes failures easy to isolate. But it targets **Java 21**, and J2C parses at Java 17. The most common of
the 101 problems are the commons-lang3 and commons-collections4
dependencies (`Pair`, `MutablePair`) and their follow-ons; Java 21 syntax
may show up once those are supplied. Use it once J2C supports Java 21, or
check out a Java 17-era commit.

**commons-lang.** `StringUtils`, `ArrayUtils`, reflection builders,
concurrency utilities and functional interfaces (94 files with lambdas).
**Clean with no dependencies**, after the captured-wildcard fix. The biggest
already-clean target, and a dependency of commons-text and TheAlgorithms.
It uses `sun.misc.Unsafe`-style access in one file and reflection in 30,
so expect runtime gaps there.

**javaparser.** A Java parser written in Java, a natural self-test for a
Java translator. The problems have three causes:

- Generated source: the JavaCC parser (`GeneratedJavaParserConstants`
  and others) is generated at build time. Run the build's
  generate-sources phase first.
- Dependencies: javassist, used by the symbol solver.
- J2C gaps: 45 `J2C-LAMBDA-UNRESOLVED` and 3
  `J2C-LAMBDA-NON-DENOTABLE-TYPE`. Recheck the lambda problems once the
  dependencies are supplied, since most are probably follow-ons from
  unresolved types.

Heavy use of streams (135 files) and lambdas (226 files).

**commons-math.** Linear algebra, statistics, optimization, random
numbers. Mostly `double` arithmetic, a strong test of floating-point
semantics. All 918 problems come from the missing Commons Numbers, RNG and
Statistics dependencies, which are themselves pure Java and could be
translated alongside. Tests are mostly JUnit 4 (324 files).

**Guava.** The stretch target: 400k main lines, every collection and
concurrency abstraction, `Unsafe` (12 files), reflection (149 files). Its
tests are mostly JUnit 3 and Truth. `--check` was not run on it. Try it last.


### Extra large

**Forge.** A community-driven Magic: The Gathering engine, with daily
commits. Measured at `ca64cc9` (2026-10-10):

| Part | Files | Lines | Approx. tokens |
| --- | ---: | ---: | ---: |
| Engine: forge-core, forge-game, forge-ai | 1,165 | 187k | 2.13M |
| UI: forge-gui-desktop (Swing), forge-gui-mobile (libGDX), platform shells | ~850 | 177k | ~2.0M |
| Card scripts (`forge-gui/res/cardsfolder`, data) | 34,087 | – | 6.2M |

- **Engine:** `--check` with the real dependency JARs takes 11 s and finds
  no problems. Full translation takes about 60 s and writes 5,153 C++ files
  (about 5.1M tokens). Forge found two J2C bugs, G10 and G11 in Phase 9,
  both fixed.
- **Hardest parts:** the largest files are `Card.java` (about 80k tokens),
  `CardFactoryUtil` and `AbilityUtils`. Guava is used in 226 engine files,
  so Guava itself must be translated too. Streams (65 files) and threads
  need the runtime. Long games allocate heavily, so garbage collection
  (Phase 10 milestone 3) is needed early.
- **UI:** Swing and libGDX have no C++ equivalent. Any migration rewrites
  the UI against a C++ toolkit, on top of the translated engine.
- **Tests:** TestNG, which `--tests` does not support yet (M6).
- **License:** GPL-3.0. Translating it for validation is fine. Generated
  code derived from it stays under the GPL.

## Recommended order

1. **hashids-java, jBCrypt:** one file each. Use them to prove the whole
   loop (translate, build, run tests, compare with the JVM) once the
   Phase 10 runtime starts.
2. **semver4j, jsemver, minimal-json:** small, clean, real parsers.
3. **javaewah, JSON-java, commons-codec:** medium, clean, no deps,
   arithmetic- and collection-heavy.
4. **jsoup** (add JSpecify) and **RoaringBitmap** (per module).
5. **commons-lang**, then **commons-text** on top of it.
6. **joda-time** (needs JUnit 3 support) and **commons-collections**
   (Phase 9 gaps).
7. **jackson-core, zxing core, commons-math** with their dependencies.
8. **javaparser**, then **Guava**.
9. **Forge engine**, after Guava and the runtime's collections.

Two follow-up items would unlock much of the list:

- **JUnit 3 support in `--tests`:** jBCrypt, javaewah, joda-time and Guava
  have JUnit 3 suites.
- **Truth and Hamcrest assertions:** used by javapoet, gson,
  williamfiset/algorithms and Guava.

## Methodology and caveats

- **Selection:** well-known, maintained projects with a permissive license
  (Apache-2.0, MIT, BSD, ISC, public domain; javaparser is dual Apache-2.0
  or LGPL-3.0). Mostly self-contained, with unit tests, spread over three
  sizes. jgrapht was considered but left out because its repository and
  license could not be verified.
  Forge is the exception: GPL-3.0, included as a real application
  rather than a library.
- **Licenses:** as reported by GitHub. Where GitHub said "Other", the
  `LICENSE` file was read: JSON-java is Public Domain, jBCrypt has an
  ISC-style license, and javaparser is dual-licensed.
- **Clones:** shallow, sparse clones (`.java` and build files only) of each
  default branch on 2026-10-11. Nothing was built or executed except J2C's
  `--check`. Numbers change as projects evolve.
- **Line counts:** non-blank lines in `.java` files. Files under `test/`,
  `tests/`, `src/test/`, `jmh/` or `benchmark(s)/` count as test code;
  everything else, including examples, counts as main.
- **Feature counts:** the number of files matching a regular expression
  (for example `import org.mockito`, `@ParameterizedTest`, `->`). They are
  indicators, not exact counts.
- **Java version:** read from `pom.xml` and Gradle files. "–" means none is
  declared and the toolchain default is used.
- **Dependencies:** non-test dependencies from the top-level `pom.xml`, plus
  what the baseline's unresolved imports show. Multi-module and Gradle
  projects may have more.
- **Baseline:** `--check` on every `src/main/java` root (excluding
  examples, benchmarks and test modules), or `src/` without that layout.
  The roots share one `--sourcepath`, and no class path is given. The
  baseline was rerun after the captured-wildcard fix
  (`fix: ground captured wildcards in lambda target types`).
- **Re-measure:** use the same `--check` command. Supply dependencies with
  `--classpath` to see the J2C-specific problems alone.
