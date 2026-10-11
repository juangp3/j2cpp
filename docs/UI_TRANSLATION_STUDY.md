# Study: translating Java user interfaces with J2C

Question: how should J2C, and agents using it, handle a Java application's
user interface when translating the application to C++?

Status: study, 2026-10-11. Case study: Forge (Card-Forge/forge, GPL-3.0) at
`ca64cc9` (2026-10-10). Measurement scripts and raw outputs were kept in a
session scratch folder, not in the repository. Every number below was
measured unless it is marked as an estimate.

## Summary and recommendation

**Do not try to translate UI toolkits in general.** A UI toolkit is a
library like any other JDK library, but its behavior sits on native window
systems, rendering pipelines and a threading model. A C++ runtime cannot
supply that cheaply or faithfully. Swing alone is about 1.0M lines of Java in
OpenJDK 17's `java.desktop` module, plus native code.

**Support the boundary split first (strategy c), then agent-driven screen
rewrites on top of it (strategy d).** J2C translates everything below the
UI: model, rules and the toolkit-independent presentation logic. J2C then
gives the agent what it needs to rewrite the UI against a C++ toolkit cheaply:

1. A binding-based report of which code uses which UI APIs.
2. A proposed engine/UI boundary, with every leak listed.
3. A C++ facade for the boundary interfaces.
4. One-screen context slices served over MCP.

This matches J2C's goal (J2C does the mechanical bulk, the agent does the
judgment work) and J2ObjC's long-standing scope decision. It also needs no
work on J2C's Java semantics.

**Two narrow exceptions are worth supporting later:**

- **Headless AWT value types** (`java.awt.geom.*`, `Point`, `Dimension`,
  `Rectangle`, `Color`) belong in the Phase 10 runtime. Non-UI code uses
  them, and they are small value classes.
- **Toolkits that are pure Java over a thin native layer** can be translated
  by J2C itself (strategy a), with a small hand-written C++ backend. libGDX
  is the clear case. Its core is 609 Java files, about 150k lines, and
  Apache-2.0. Its native surface is 32 `native` methods in 4 files, plus
  backend interfaces such as `GL20` (162 methods). J2C's `--check` already
  passes on it, except for one missing loader artifact. For Forge, this is
  the most promising long-term route, because Forge's libGDX UI already runs
  on desktop, Android and iOS.

An API-compatible Swing shim (strategy b) is not recommended. Forge's desktop
UI alone calls 1,556 distinct Swing/AWT members across 251 types, and its
look depends on 90 custom `paint*` overrides. Faithful emulation would mean
reimplementing Swing. Keeping the UI in Java over JNI or FFM (strategy e) is a
valid incremental-migration pattern to document, not a first feature.

**For Forge, the measured boundary is unusually clean:**

- The engine (forge-core, forge-game, forge-ai) now compiles and links as
  C++ (1,165 files).
- The shared UI-logic module `forge-gui` (449 main files, 79k lines) checks
  clean with J2C: 431 translated files, 0 problems, 18 s.
- `forge-gui` touches a UI toolkit in exactly one file, `AutoUpdater.java`,
  through `java.awt.Desktop` and one `SwingUtilities.invokeLater` call.
  `IGuiBase` already has the replacements (`browseToUrl`,
  `invokeInEdtLater`).
- So "Forge core" is engine plus forge-gui, about 266k lines. Two UIs sit on
  top of it: Swing (90k lines) and libGDX (109k lines).

## 1. Taxonomy of Java UI stacks

| Stack | What the program codes against | What is underneath | License | Where it is used |
| --- | --- | --- | --- | --- |
| **AWT** | `java.awt` components, `Graphics`, events | Heavyweight native peers per platform (Win32, X11/XToolkit, macOS), Java2D rendering pipelines and native rendering loops, font rasterizing, image codecs; one event dispatch thread (`EventQueue`) | OpenJDK: GPLv2 with Classpath Exception | Rarely on its own today; its value types (`geom`, `Color`, `BufferedImage`) are widely used headlessly |
| **Swing** | `javax.swing` lightweight components, pluggable look and feel, `paintComponent` overrides | Swing is painted in Java onto `Graphics2D`; only top-level windows have AWT peers; every UI call must run on the EDT | Same as AWT | Legacy and in-house desktop applications, IDEs, tools; part of the JDK, so the default Java desktop toolkit |
| **JavaFX (OpenJFX)** | Scene graph, controls, CSS, FXML, properties and bindings | Prism renderer (Direct3D, OpenGL ES2, software), Glass windowing, WebKit for `WebView`, GStreamer for media | GPLv2 with Classpath Exception; separate from the JDK since 11 | Newer desktop apps; smaller than Swing |
| **SWT** | `org.eclipse.swt` widgets | Thin Java over native widgets through JNI (Win32, Cocoa, GTK) | EPL | Eclipse IDE and Eclipse RCP applications |
| **libGDX** | `ApplicationListener`, `SpriteBatch`, Scene2D UI (`Stage`, `Actor`, `Table`), assets | Java core over backends: LWJGL3 (GLFW and OpenGL) on desktop, Android, iOS (RoboVM/MobiVM), web (GWT, TeaVM); small native helpers (gdx2d image loading, ETC1, buffers, matrix math), FreeType, Box2D | Apache-2.0 | The main Java game framework; Forge's mobile and "adventure" UI |
| **Android views** | `android.view`, `android.widget`, activities | Android framework and runtime (HWUI, Skia, Binder services); exists only on Android | Apache-2.0 (AOSP) | All classic Android apps; not a candidate for C++ translation, because the platform is the dependency |

### How common each one is

I found no reliable survey of Java GUI toolkit usage. The pages that quote
percentages cite no source, so none are used here. Two measured indicators:

- **Libraries rarely touch UI toolkits.** Across J2C's 26 corpus projects
  (`target/corpus/src`), no project imports Swing, JavaFX, SWT, libGDX or
  Android. Only two algorithm collections import `java.awt`, in 25 files,
  and those are headless value types: `java.awt.geom.Point2D` (14 imports),
  `java.awt.geom.*` (5), `Point` (3), `Color` (3), `BufferedImage` (2). So
  "imports `java.awt`" does not mean "has a UI", and the classification has
  to work at the type level.
- **Applications often carry more than one UI.** Forge has a Swing desktop
  UI, a libGDX UI, a Swing level editor (`adventure-editor`, which mixes
  Swing and libGDX), and Android and iOS shells.

The likely J2C users are therefore libraries, which need no UI work, and
applications with a Swing or libGDX front end. JavaFX and SWT are less
common. Android UIs are out of scope.

## 2. Strategies

### Comparison

| | a. Translate the toolkit | b. API-compatible C++ shim | c. Boundary split | d. Agent-driven UI rewrite | e. Keep UI in Java, bridge |
| --- | --- | --- | --- | --- | --- |
| **Feasible for Swing** | No (natives, size) | Only as a Swing reimplementation | Yes | Yes | Yes |
| **Feasible for libGDX** | Yes, with a C++ backend | Possible, but (a) is cheaper | Yes | Yes | Awkward (GL context ownership) |
| **Fidelity** | High once the backend works | Low on native widgets; high only with full Swing emulation | Engine: J2C fidelity | Same function, new look | Unchanged UI |
| **Effort** | Swing: very large. libGDX: backend plus runtime breadth | Large, open-ended | Small to medium (J2C tooling) | Medium per app; scales with screens | Medium: bridge generation and object lifetimes |
| **Agent tokens** | Low (mechanical) | High output, high debugging | Low | About 3–5M for Forge's desktop UI (estimate below) | Medium |
| **Upstream changes** | Flow through J2C automatically | Flow through J2C automatically | Engine flows through J2C | Each UI change is ported by hand or by agent | UI keeps tracking upstream |
| **Licensing** | Swing: translated OpenJDK code stays GPLv2+CE. libGDX: Apache-2.0 | Free to choose (Qt LGPLv3/commercial, wxWidgets licence, ImGui MIT) | No change | Free to choose | No change |
| **J2C needs** | Runtime breadth (`java.nio` buffers, threads), backend contract | Runtime mechanism (Phase 10 step 1), conformance check | Library-use report, boundary analysis, facade | Facade, MCP screen slices, build mapping (A1) | C-ABI facade, binding generation |

### a. Translate the toolkit's Java source too

**Swing and AWT (OpenJDK).** Measured in JDK 17's `src.zip` (Windows build):

| Package group | Files | Lines |
| --- | ---: | ---: |
| `java.desktop`, all Java | 2,632 | 1,008,683 |
| `javax.swing` | 758 | 391,887 |
| `java.awt` | 405 | 181,746 |
| `com.sun` (look-and-feel implementations and more) | 526 | 144,608 |
| `sun.awt` | 238 | 71,567 |
| `sun.java2d` | 186 | 53,555 |
| `javax.imageio` | 71 | 34,365 |
| `sun.font` | 71 | 26,719 |

The native C and C++ code for peers, Java2D loops, fonts and image codecs is
not in `src.zip` and is not counted.

What breaks:

- **Native methods.** Peers, rendering loops, font scaling and color
  management are native. They would all need to be rewritten in C++ against
  a window system, so that rewrite is strategy (b) at the lowest layer.
- **Reflection and dynamic loading.** `UIManager` and `UIDefaults` load
  look-and-feel classes by name. `java.beans` and accessibility rely on
  reflection.
- **Runtime breadth.** It needs threads, `AppContext`, security checks,
  weak references, `java.util.concurrent`, and image I/O. All of that is
  Phase 10 milestone 4 and beyond.
- **Size.** About 1M lines of input. Swing would be the largest corpus
  project by far, before any application code.
- **License.** OpenJDK is GPLv2 with the Classpath Exception (verified in
  `JComponent.java`'s header). Translated output stays GPLv2+CE. The
  exception still allows linking with independent code under other terms.
  Phase 10 deliberately chose a hand-written runtime rather than translated
  OpenJDK, to keep licensing simple. Translating `java.desktop` would undo
  that choice.

Prior art shows that "keep Swing's Java, replace what is underneath" works
only with a whole JVM:

- **CheerpJ** runs unmodified OpenJDK Swing in the browser. It is a full
  OpenJDK runtime, and its window manager turns windows into HTML elements
  and canvases.
- **GNU Classpath and libgcj** spent years on GTK and Cairo peers, and Swing
  was still described as "very buggy" in 2004.
- **GraalVM Native Image** supports AWT and Swing on Linux and Windows. It
  keeps the JDK's native code and needs reflection configuration.

**libGDX.** Here translation is practical. Measured on `gdx-1.14.2-sources.jar`:

| Item | Measured |
| --- | --- |
| Java files, lines | 609, 149,552 |
| `native` methods | 32, in 4 files: `Gdx2DPixmap` (17), `ETC1` (8), `BufferUtils` (4), `Matrix4` (3) |
| `GL20` interface methods | 162 (a backend forwards them straight to OpenGL or GLES) |
| Largest parts | `utils` 31k lines, `scenes/scene2d` 23k, `graphics/g3d` 22k, `math` 15k, `graphics/g2d` 14k |
| `j2c --check` | 608 files, 13 problems in 2 files, all because `SharedLibraryLoader` and `Os` live in the separate `gdx-jnigen-loader` artifact, which loads the JNI natives that a C++ backend replaces |

The native side is already C or C++ (gdx2d is built on stb_image; FreeType
and Box2D are C/C++ libraries). A C++ backend implements `Application`,
`Graphics`, `GL20`/`GL30`, `Input`, `Files` and `Audio`, for example on SDL2
or GLFW with OpenGL. The hard dependency is runtime breadth: direct
`java.nio` buffers, threads, and collections.

**Verdict:** No for Swing, AWT, JavaFX and SWT. Worth a probe for libGDX
once the runtime has collections and buffers.

### b. API-compatible C++ shims

Approach: implement, in C++, the subset of the Java UI API the program uses,
on top of a C++ toolkit. The implementation is delivered through the Phase 10
runtime mechanism (`--runtime`, manifest, conformance check), so translated
UI code calls the shim instead of `unimplemented_()` stubs.

Scope measured on Forge's desktop UI. J2C's bundled JDT resolved every call,
constructor, field access, `extends`/`implements` and override against
`java.awt`, `javax.swing`, `javax.sound`, `javax.imageio`, MigLayout and the
image-scaling library:

| Measure | Desktop (Swing) | Mobile (libGDX and TextraTypist) |
| --- | ---: | ---: |
| Files scanned / touching the API | 408 / 301 | 382 / 267 |
| Distinct types used | 251 | 247 |
| Distinct members used | 1,556 | 1,555 |
| Use sites | 13,384 | 11,051 |

Desktop by package: `javax.swing` 79 types and 672 members; `java.awt` 40 and
432; `java.awt.event` 25 and 157; `javax.swing.text` 17 and 65;
`javax.swing.table` 8 and 42; `javax.sound.sampled` 10 and 31. The
most-used types are `JComponent` (223 files), `Container` (189), `Component`
(180), `MigLayout` (100), `AbstractButton` (94) and `JPanel` (87).

For comparison, translating the desktop UI generated **2,152 library stub
classes with 25,097 `unimplemented_()` methods**. That includes 480 Swing
classes (5,530 methods) and 163 AWT classes (2,586 methods). Stubs cover
the whole signature closure. The 1,556 members actually used are the real
shim scope, and only a binding-level report shows it.

What makes a Swing shim hard:

- **Custom painting.** 50 desktop files override `paint`,
  `paintComponent`, `paintChildren` or `paintBorder` (90 overrides). Forge's
  skin is drawn by the program itself. A shim on native widgets (Qt,
  wxWidgets) cannot honor that. A faithful shim must implement Swing's
  lightweight component tree, repaint manager, layout and `Graphics2D` on a
  2D canvas (QPainter, Skia). That amounts to reimplementing Swing.
- **Threading and events.** 91 desktop files use `SwingUtilities.invoke*`,
  `SwingWorker` or Forge's `FThreads`. The EDT semantics must be reproduced.
- **Third-party Swing libraries.** MigLayout (BSD, pure Java, used in 100
  files) could itself be translated by J2C once the shim exists.
- **Long tail.** Each application uses a different subset. The next app
  needs new members, so a shim is never finished.

Prior art:

- **SwingWT** implemented the Swing/AWT API on SWT. It drove native widgets
  so that Swing apps could ship native via GCJ. Its FAQ answers "Is this
  project dead?" with "Yes, I'm afraid so."
- **GNU Classpath** implemented AWT peers on GTK.
- **J2ObjC** deliberately ships no UI toolkit, and says iOS UI must be
  written natively.
- **C++ libraries with a libGDX-like API:** I found no C++ library that
  reproduces Scene2D. raylib (C) and raylib-cpp cover the SDL/SFML level
  (windows, input, 2D drawing), not an actor and stage UI. bgfx and Oryol are
  rendering layers, not checked further here. So libGDX's API does not map
  onto an existing C++ engine. Its graphics layer maps closely onto OpenGL,
  which is why translating it (strategy a) beats shimming it.

**Verdict:** Not a J2C product goal. The only shim J2C should ship is the
headless AWT value types in the runtime. They have small, stable semantics
and appear in non-UI code (25 corpus files).

### c. Boundary split

Approach: J2C translates the engine. The UI is kept or rewritten separately
against a C++ API that J2C generates for the engine. This is the J2ObjC
model: share the business logic, write the UI natively. In MVC or MVVM
terms, the model and the view-model or presenter layer is translated, and
the view is not.

**Detecting the boundary automatically.** Three lessons came from measuring
Forge:

1. **Imports are a weak signal.** Wildcard imports (`javax.swing.*` in 23
   desktop files) hide which types are used. Fully qualified names bypass
   imports. Commented-out code matches greps: `OperatingSystem.java`'s only
   `java.awt.Robot` use is inside a comment block. `java.awt` includes
   headless types. JDT bindings, which J2C already has, answer each of these
   exactly.
2. **Class-level transitive closure over-approximates badly.** A naive
   closure ("a class is UI if it mentions a UI class") marks 341 of
   forge-gui's 449 files, because hub classes such as `GuiBase` and
   `OperatingSystem` touch a UI class once. The useful unit is the **direct
   use site** (member, file, line), together with whether the dependency is
   in a **signature** (field type, parameter, supertype), which forces the
   class to the UI side, or only in a **method body**, which can be moved
   behind an interface.
3. **The leak list should be short and actionable.** For forge-gui it is one
   file with four call sites (below), each with an existing replacement in
   `IGuiBase`.

**How Forge's layers fit:**

| Layer | Modules | Main files / lines | UI toolkit use |
| --- | --- | ---: | --- |
| Engine | forge-core, forge-game, forge-ai | 1,165 / 213k (all lines, comments included; `VALIDATION_PROJECTS.md` lists 187k by a different count) | None (0 files) |
| Shared UI logic | forge-gui | 449 / 79k | 1 file: `forge/download/AutoUpdater.java` |
| Desktop UI | forge-gui-desktop | 408 / 90k | 305 files import Swing, AWT, MigLayout or javax.sound |
| Mobile UI | forge-gui-mobile | 382 / 109k | 271 files import libGDX |
| Shells | forge-gui-android, forge-gui-ios, forge-gui-mobile-dev | 8 / 3.8k, 53 / 3.2k, 3 / 0.4k | Android, RoboVM, LWJGL3 launchers |
| Editor | adventure-editor | 39 / 6k | Swing and libGDX |

forge-gui is built as a presenter layer behind interfaces that each UI
implements:

- `IGuiBase` (about 45 methods): threads (`invokeInEdtLater`,
  `isGuiThread`), dialogs, images (`ISkinImage`), audio, and
  `browseToUrl`. Implemented by `GuiDesktop` and `GuiMobile`.
- `IGuiGame` (about 80 methods): everything a match asks of a UI, such as
  prompts, choices, combat damage assignment and highlighting. Implemented
  through `AbstractGuiGame` by `CMatchUI` (desktop) and `MatchController`
  (mobile).
- Widget interfaces (`IButton`, `ICheckBox`, `IComboBox`, `ITextField`,
  `IProgressBar`, and others).
- **A view-model layer in the engine:** `GameView`, `CardView`, `PlayerView`,
  `CombatView`, `StackItemView` and `SpellAbilityView` are
  `TrackableObject`s. The UIs read game state through them, not through the
  live `Card` or `Game`. Desktop imports `Card` in 10 places and `Game` in
  one.
- **A wire protocol** (`ProtocolMethod`, 58 methods) already sends
  `IGuiGame` calls between a server and remote clients. It uses Java object
  serialization (`CompatibleObjectEncoder` over Netty), so only Java can
  read it today.

Cross-module use (main sources, binding-free import resolution):

- Desktop uses 157 forge-gui types and 108 engine types.
- Mobile uses 167 forge-gui types and 104 engine types.
- 128 forge-gui types are used by both UIs.
- Only 9 (desktop) and 14 (mobile) engine types are used that forge-gui does
  not itself use.

The public signatures of all forge-gui and engine types the desktop imports
come to about **84k tokens**, against about 770k tokens for their full
source. That is the size of the facade an agent needs.

**What J2C already does here:**

- forge-gui: `--check` passes with its dependencies (xstream, Netty, jupnp,
  lz4, gson): **431 files, 0 problems, 18 s**.
- forge-gui-desktop: **388 files, 4 problems**, all
  `J2C-LAMBDA-ANONYMOUS-THIS` on `super::processMouseEvent` inside anonymous
  Swing subclasses in `MenuUtil.java`. With those 4 lines neutralized in a
  scratch copy, the whole desktop UI translates: 16,429 output files in
  about 6 minutes, with Swing as stubs.
- forge-gui-mobile: **382 files, 2 problems**: ECJ rejects two
  `Suppliers.memoize(() -> new ArrayList<>(n))` fields whose element type is
  an inner class of the generic `ImageView<T>`. I did not verify whether
  javac accepts them, so this is a possible ECJ or J2C gap to probe.
- The Forge engine now compiles and links as C++ (1,165 files), so the
  engine side of the split is proven to build.

**Verdict:** Recommended first. It is cheap for J2C, and it is the
foundation for (d) and (e).

### d. Agent-driven UI rewrite

Approach: an LLM agent rewrites the UI screen by screen against a C++
toolkit, such as Qt Widgets (LGPLv3 or commercial), wxWidgets (wxWindows
Library Licence), or Dear ImGui (MIT). J2C supplies compact context: the
screen's Java source and only the facade signatures it uses.

Forge desktop inventory:

- 13 top-level screens (`FScreen` constants) and 64 dockable documents
  (`EDocID`).
- 68 `V*` view and 82 `C*` controller classes under `forge/screens`; the
  code is already MVC-shaped.
- A custom widget library: `forge/toolbox`, 49 files, about 12.6k lines.
- A card renderer: `forge/view/arcane`, 16 files, 5.6k lines.

**Token estimate for the desktop UI (estimate, about 4 characters per
token):**

| Item | Tokens |
| --- | ---: |
| Desktop main Java, raw (408 files, 3.42M characters) | 855k |
| The same without comments, imports and blank lines | 672k |
| Facade signatures per screen (about 90 units × about 8k, a subset of the 84k total) | about 720k |
| Toolkit conventions and examples per unit (about 90 × 3k) | about 270k |
| **Read, first pass** | **about 1.7M** |
| Generated C++ (about 1.0–1.3 × the Java) | about 0.7–0.9M output |
| Build and fix iterations with mapped errors (M7 A1): +50–100% input | about 0.9–1.7M |
| **Total** | **about 2.6–3.4M input, 0.8–1.2M output** |

Without J2C slicing, the agent has to discover the engine API by reading
source. The 265 types the desktop UI imports are 770k tokens of full source,
and they get re-read across many screens. That plausibly makes the input
5–10M tokens (estimate). The mobile UI is larger: 957k tokens stripped.

**Compared with (b):**

- A Swing shim of the scope Forge needs would be an estimated 30–60k lines
  of C++, about 0.3–0.6M output tokens. On top of that come selective
  reading of Swing's behavior (javax.swing alone is 392k lines) and visual
  debugging, which agents do poorly without screenshots.
- The shim's cost is per toolkit and is reused across apps, but each app
  extends it. The rewrite's cost is per app, but it is bounded and its
  result is idiomatic C++.
- On maintenance, (b) wins. Forge's desktop main code saw 222 commits and
  +14.7k/−4.7k lines in the year to 2026-10-10, about 16% of its size. With
  (d), each of those changes has to be ported. J2C can make that cheap by
  reporting which screens' Java changed since the last port (M7 A6).

**Verdict:** The right default for Swing, JavaFX and SWT applications, on
top of (c).

### e. Keep the UI in Java and call the translated engine

Approach: the JVM keeps running the UI and calls the C++ engine through JNI,
through the Foreign Function & Memory API (final in Java 22, JEP 454), or
over IPC. This enables incremental migration, because each layer can be
moved and tested on its own.

What it needs from J2C:

- A **C-ABI facade**: `extern "C"` wrappers over the boundary interfaces,
  with opaque handles for objects. On the Java side, `jextract` or a
  generated FFM binding.
- **Upcalls**: the engine calls the UI through `IGuiGame`, so the engine
  needs C callbacks that FFM upcall stubs or JNI implement.

What is hard:

- **Lifetimes.** The UI reads graphs of view objects (`CardView` and its
  states). Every C++ object reachable from Java needs a handle and a
  lifetime rule, while J2C's C++ has no garbage collector yet (G8).
- **Chattiness and threads.** Many small calls cross the boundary, and the
  EDT and the engine thread each call into the other.
- **IPC.** For Forge, the existing client/server protocol is the natural
  seam, but it uses Java serialization. It would first need a neutral wire
  format (JSON or protobuf) in upstream Java.

**Verdict:** Document it as a pattern, and possibly generate the C-ABI
facade later. Not the first feature.

## 3. Recommendation for J2C

Support **c then d**. Add headless AWT value types to the runtime. Keep a
libGDX translation probe as a separate experiment. Concretely:

| ID | Feature | How it is tested |
| --- | --- | --- |
| U1 | **Library-use report.** `--check --report library-use` (and an MCP `j2c_check` option) lists, per package and file, the members used from configured toolkit groups (Swing, AWT-UI, AWT-headless, JavaFX, SWT, libGDX, Android), from JDT bindings, separating signatures from bodies. It is a report section in the JSON, not a problem, so the ladder's "checks" step is unaffected. | A new `test/src` fixture with Swing use, headless `java.awt.geom` use, a wildcard import, a fully qualified use and a commented-out use; `test-cli.js` asserts exact counts. Corpus: Forge records the counts measured here (desktop 251 types and 1,556 members; forge-gui 1 file) and they may only change deliberately. |
| U2 | **`j2c boundary`** (CLI and read-only MCP `j2c_boundary`). Proposes engine and UI sets from U1. Lists every **leak**: an engine-side class with direct UI use, with members, lines, and whether the use is in a signature or a body. Suggests a fix (move the class, or use an existing interface method). Never uses class-level transitive closure. | A fixture mini-project (`test/boundary/`) with model, view and one leak; the CLI suite asserts the exact leak list. Corpus: Forge, all modules, expects one leak, `AutoUpdater.java` (4 use sites). |
| U3 | **`--forbid-api <prefixes>`.** Uses of listed packages in the selected files become problems with a new ID, `J2C-FORBIDDEN-API` (added to `ProblemCatalog` with a hint), so a boundary is enforced loudly in CI. | Fixture and CLI assertions; MCP schema test; corpus run of engine plus forge-gui with `--forbid-api java.awt,javax.swing` expects exactly the `AutoUpdater` problems. |
| U4 | **Engine facade for hand-written C++.** `--facade <types>` emits a documented header set for the boundary interfaces and view classes, plus string helpers (`java::lang::String` to and from `std::u16string`). `docs/RUNTIME.md` gains "Implementing a translated Java interface in C++". | A `test/cpp` smoke harness where a hand-written C++ class implements a translated Java interface and translated Java code calls it (primitives only, as the harness rules require); built under UBSan in CI. |
| U5 | **MCP `j2c_ui_inventory` and `j2c_ui_slice`.** The inventory lists UI units (top-level classes extending toolkit containers, plus their controllers) with token sizes. A slice returns one unit within a token budget: its Java (comments stripped), the facade signatures it uses, the toolkit members it uses, and, with M7 A6, the Java diff since a given commit. | `test-mcp.js` on the boundary fixture; a size check that a Forge desktop screen slice stays under its budget. |
| U6 | **Token benchmark for UI.** Extend M7 A7: an agent ports 2–3 Forge desktop screens to Dear ImGui or Qt, with and without U5; record tokens and whether the screen builds against the facade. | The benchmark itself; results recorded in `VALIDATION_PROJECTS.md`. |
| U7 | **Headless AWT value types in the runtime** (Phase 10, after milestone 2): `java.awt.geom.*`, `Point`, `Dimension`, `Rectangle`, `Color`. Swing is explicitly out of scope. | Differential `test/programs` against the JVM, as Phase 10 specifies. |
| U8 | **libGDX probe** (experiment). Add libGDX core to the corpus (with `gdx-jnigen-loader`), climb to "builds", then prototype a C++ backend (SDL2 or GLFW with OpenGL) after the runtime has collections, buffers and threads. | Corpus ladder; later, a libGDX sample that renders a frame, with a screenshot comparison. |

U1–U3 are small and fit the M7 philosophy: J2C answers a question
precisely, so the agent does not read code to find out.

## 4. Forge-specific plan

### Measured facts

| Fact | Number |
| --- | --- |
| Engine (core, game, ai) | 1,165 files, 213k lines; checks clean; now compiles and links as C++ |
| forge-gui | 449 main files, 79k lines; `--check` 431 files, 0 problems |
| forge-gui UI toolkit use | 1 file, `AutoUpdater.java`: `java.awt.Desktop` (3 sites), `SwingUtilities.invokeLater` (1) |
| Desktop UI | 408 main files, 90k lines, 855k tokens; 305 files import UI packages; 251 types and 1,556 members used; 13,384 use sites |
| Desktop imports | `java.awt` 937, `javax.swing` 911, MigLayout 101, `javax.sound` 18, `javax.imageio` 5 |
| Desktop tests | 138 files, 29k lines (TestNG), almost all non-UI |
| Mobile UI | 382 files, 109k lines, 1.1M tokens; 271 files import libGDX (1,088 imports); 247 types and 1,555 members used |
| Desktop `--check` | 388 files, 4 problems (`super::` method references in anonymous Swing subclasses) |
| Mobile `--check` | 382 files, 2 problems (ECJ type mismatch, to probe) |
| libGDX core `--check` | 608 files, 13 problems, all from the missing loader artifact |
| Boundary interfaces | `IGuiBase` about 45 methods, `IGuiGame` about 80, `ProtocolMethod` 58 |
| Upstream churn, desktop main, last year | 222 commits, +14.7k/−4.7k lines |

### Recommended path

1. **Make "Forge core" the J2C unit.** Add a corpus entry for engine plus
   forge-gui with its dependency coordinates. Expect 0 problems, and climb
   the ladder as the engine did.
2. **Close the one leak upstream-style.** In `AutoUpdater`, replace
   `Desktop.browse` with `GuiBase.getInterface().browseToUrl(...)` and
   `SwingUtilities.invokeLater` with `invokeInEdtLater`. Both already exist in
   `IGuiBase`. Enforce the result with `--forbid-api java.awt,javax.swing`
   (U3).
3. **First runnable target: headless simulation.** `forge.view.SimulateMatch`
   (413 lines, no UI imports, run by `forge sim`) plays AI-against-AI
   matches. Translate it with Forge core. Then compare game logs with the
   JVM under a fixed seed (`MyRandom`) as the "tests match" step. This
   needs the Phase 10 runtime (collections, streams, file I/O for card
   scripts, threads) and garbage collection (milestone 3), but no UI.
4. **Implement `IGuiBase` and `IGuiGame` in C++** through the facade (U4).
   Start with a console implementation (text prompts), which also serves as
   a test UI.
5. **Choose the UI route with a one-screen experiment** on the match screen,
   the most complex one:
   - **Route L, preferred for Forge:** translate forge-gui-mobile and libGDX
     core with J2C, and write a C++ libGDX backend (U8). The libGDX UI
     already runs on desktop (`forge-gui-mobile-dev` starts it through
     `Lwjgl3Application`), on Android and on iOS. One translated UI would
     therefore cover every platform, and upstream UI changes would flow
     through J2C with no rewrite.
   - **Route D:** agent-driven rewrite of the desktop screens (U5) into Qt or
     Dear ImGui, using the about 3M-token budget estimated above. This is the
     general J2C story for Swing apps. For Forge, it duplicates a UI that
     route L gets for free.
   - Drop the Swing desktop UI from a C++ Forge either way.

Risks:

- Route L depends on runtime breadth (direct buffers, threads) and on a
  graphics backend nobody has written yet.
- Forge's card scripts and assets (34k files) are data, and only the runtime
  file I/O has to read them.
- The C++ output of GPL-3.0 Forge stays GPL.

## Proposed roadmap items

Paste as a new milestone in `docs/VALIDATION_ROADMAP.md`.

### M8 — UI boundaries for applications

J2C does not translate UI toolkits. It translates everything below the UI,
and makes the UI rewrite cheap for an agent. Study and measurements:
[UI_TRANSLATION_STUDY.md](UI_TRANSLATION_STUDY.md).

| ID | Item | Done when |
| --- | --- | --- |
| U1 | Library-use report: `--check --report library-use` and an MCP option; member-level, from bindings; toolkit groups (Swing, AWT-UI, AWT-headless, JavaFX, SWT, libGDX, Android); signature or body use | Fixture assertions pass; Forge desktop reports 251 types and 1,556 members, and forge-gui reports 1 file |
| U2 | `j2c boundary` and the `j2c_boundary` MCP tool: proposed engine and UI sets, the leak list with evidence and fix hints; no class-level transitive closure | The boundary fixture's leak list matches exactly; Forge reports only `AutoUpdater` |
| U3 | `--forbid-api <prefixes>` with a new `J2C-FORBIDDEN-API` problem ID | CLI, MCP and corpus checks pass |
| U4 | Engine facade: `--facade <types>` headers, string helpers, and a `RUNTIME.md` section on implementing translated interfaces in C++ | A smoke harness implements a Java interface in C++, called from translated code, under UBSan |
| U5 | MCP `j2c_ui_inventory` and `j2c_ui_slice` with a token budget, and the diff since a commit (with A6) | `test-mcp.js` passes; a Forge screen slice fits its budget |
| U6 | UI token benchmark, an extension of A7: 2–3 Forge screens ported with and without U5 | Tokens and build results recorded |
| U7 | Runtime: headless AWT value types (`java.awt.geom`, `Point`, `Dimension`, `Rectangle`, `Color`) | Differential programs match the JVM |
| U8 | libGDX probe: libGDX core in the corpus, then a C++ backend prototype | libGDX core reaches "builds"; later, a sample renders a frame |

Order: U1–U3 alongside M2 and M3 (small, read-only analysis); U4 with
Phase 10 step 1; U5 and U6 after M7 A1 and A6; U7 after Phase 10 milestone
2; U8 after milestone 4 (threads, buffers).

Forge track: a corpus entry for engine plus forge-gui (expect clean), the
`AutoUpdater` fix, then `SimulateMatch` as the first runnable target. The UI
route is decided by a one-screen experiment: translated libGDX or an agent
rewrite.

## Sources

- J2ObjC scope (no UI toolkit; business logic only):
  https://developers.google.com/j2objc/guides/why-use-j2objc ,
  https://developers.google.com/j2objc
- GNU Classpath AWT/Swing status (GTK peers, Cairo, Swing maturity):
  https://gnu.org/software/classpath/announce/20050906.html ,
  https://mail.gnu.org/archive/html/classpath/2004-05/msg00132.html
- SwingWT (Swing/AWT API over SWT, project dead):
  https://swingwt.sourceforge.net/ , https://swingwt.sourceforge.net/faq.php
- CheerpJ architecture (OpenJDK-based, Swing windows rendered to HTML
  canvases): https://cheerpj.com/docs/explanation/architecture ,
  https://cheerpj.com/java-swing/
- GraalVM Native Image and AWT (Linux and Windows):
  https://bell-sw.com/blog/how-to-turn-awt-applications-into-native-images ,
  https://github.com/oracle/graal/issues/2696
- OpenJDK Classpath Exception:
  https://mail.openjdk.org/pipermail/jdk-dev/2019-January/002561.html ;
  java.desktop's scope and size:
  https://mail.openjdk.org/pipermail/awt-dev/2017-March/012646.html
- JEP 454, Foreign Function & Memory API (final in 22):
  https://openjdk.org/jeps/454
- libGDX backends (LWJGL3, Android, RoboVM/MobiVM, GWT, TeaVM):
  https://libgdx.com/news/2024/10/gdx-1-13 ,
  https://gamefromscratch.com/libgdx-1-8-released/
- raylib and raylib-cpp: https://en.wikipedia.org/wiki/Raylib
- Qt licensing: https://doc.qt.io/qt-6/licensing.html ; wxWidgets licence:
  https://www.wxwidgets.org/about/licence/ ; Dear ImGui (MIT):
  https://github.com/ocornut/imgui/blob/master/LICENSE.txt
- Incremental Swing migration, screen by screen (vendor material):
  https://blog.vaadin.com/from-swing-to-web-one-view-at-a-time ,
  https://www.webswing.org/webswing-migration-framework

Measured locally: Forge `origin/master` at `ca64cc9`, read with `git grep`,
`git cat-file` and `git archive` (nothing written to the Forge clone);
OpenJDK 17.0.18 `lib/src.zip`; `gdx-1.14.2-sources.jar`; J2C 0.6.0
`--check` and translation; a JDT binding scanner run on J2C's bundled JDT.
