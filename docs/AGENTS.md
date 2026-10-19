# Using J2C with AI agents

J2C can be driven by AI coding agents: GitHub Copilot agent mode in VS Code,
Claude Code, Claude Desktop, and any other client of the Model Context
Protocol (MCP). The agent can check Java code for translation problems, fix
them in the Java source using J2C's hints, and translate it to C++, without
parsing human-readable output.

There are two ways in:

- **MCP server** (`j2c mcp`): the agent gets J2C as typed tools. This is the
  recommended setup.
- **Command line with `--format json`**: for agents that run shell commands
  and for scripts.

## Setup

### VS Code with Copilot agent mode

The J2C extension registers its bundled CLI as an MCP server for each
workspace folder automatically, rooted at that folder. In Copilot Chat,
switch to agent mode and open the tools picker. The J2C tools appear under
**J2C (*folder name*)**. VS Code asks for confirmation before
`j2c_translate` and `j2c_fix` run, because they can write files.

The setting `j2c.mcp.enabled` (default on) turns the server off. It uses the
same `j2c.javaExecutable` and `j2c.cliJar` settings as the commands. This
needs a VS Code version with MCP support; older versions simply don't show the
tools.

To configure the server by hand instead, for example for a CLI JAR outside the
extension, add `.vscode/mcp.json`:

```json
{
  "servers": {
    "j2c": {
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "/path/to/j2c-cli.jar", "mcp", "--root", "${workspaceFolder}"]
    }
  }
}
```

### Claude Code

Download `j2c-cli-<version>.jar` from the GitHub release, then, from your
project directory:

```sh
claude mcp add j2c -- java -jar /path/to/j2c-cli.jar mcp --root .
```

Add `--scope project` to share it with your team through the project's
`.mcp.json`. The [J2C skill](../agents/claude/skills/j2c-translate/SKILL.md)
teaches Claude the workflow below; copy its folder into `.claude/skills/` in
your project, or `~/.claude/skills/` for all projects.

### Claude Desktop and other MCP clients

Add a stdio server to the client's configuration; for Claude Desktop that is
`claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "j2c": {
      "command": "java",
      "args": ["-jar", "/path/to/j2c-cli.jar", "mcp", "--root", "/path/to/project"]
    }
  }
}
```

### Copilot instructions

[`agents/copilot/j2c.instructions.md`](../agents/copilot/j2c.instructions.md)
tells Copilot how to make Java translatable. Copy it into
`.github/instructions/` in your repository; it applies to Java files.

## Tools

All paths are relative to the workspace root given with `--root`. Inputs,
source paths and the output directory must be inside it. Class path entries
(dependency JARs) may be anywhere, because they are only read.

| Tool | Writes | Arguments | Result |
| --- | --- | --- | --- |
| `j2c_check` | No | `paths` (required), `sourcePaths`, `classPath`, `sealedTypes`, `encoding`, `tests`, `project`, `buildTool` | `status` (`ok` or `problems`), `problems`, `summary`, `projects`, `warnings` |
| `j2c_translate` | Yes | The same, plus `output` (required), `name`, `force` | `status`, `output`, `makefile`, `generatedFiles`, `testFiles`, `projects`, `warnings` |
| `j2c_fix` | With `apply: true` | The same as `j2c_check`, plus `apply` | `status`, `applied`, `fixed`, `changedFiles`, `diff`, `deferred`, `problems`, `summary` |
| `j2c_explain` | No | `id` (optional) | What problem IDs mean and how to fix them |
| `j2c_capabilities` | No | none | Version, supported features, limitations, problem IDs |

Pass `tests: true` to both tools for projects with JUnit tests (JUnit JARs in
`classPath`). `j2c_check` then also reports test features that translated
tests cannot run, and `j2c_translate` generates GoogleTest wrappers, listed
in `testFiles`, that `make -C <output> test` builds and runs.

Each problem has a stable `id`, `file`, `line`, `column` (1-based), `message`,
`hint` and `fixes`. Problems in the Java code are a normal `j2c_check` result,
not a tool error. `j2c_translate` reports them as an error, because no output
was produced.

### Maven and Gradle projects

Pass the project folder itself, usually `paths: ["."]`. When a folder in
`paths` has a `pom.xml`, `build.gradle(.kts)` or `settings.gradle(.kts)`,
J2C uses the build's source roots (every Maven module, Gradle subproject or
source set), its test roots with `tests: true`, and the dependency class
path, so `sourcePaths` and `classPath` are rarely needed. `projects` in the
result shows what was detected: `buildTool`, `modules`, `sourceRoots`,
`testRoots`, `classPath`, `classPathSource` (`maven`, `gradle`, `cache`,
`none` or `unresolved`) and the `command` run.

To get the class path, J2C runs the project's wrapper (`mvnw`, `gradlew`) or
`mvn`/`gradle`, which may download dependencies; the result is cached by a
hash of the build files. `buildTool: false` never runs it (a cached class
path is still used); `project: "none"` turns detection off; `project:
"<folder>"` uses that folder's build for other `paths`. `j2c mcp --project
none --no-build-tool` changes the server's defaults.

If the build tool is missing or fails, the source roots are still used and
`warnings` holds `J2C-PROJECT-CLASSPATH-UNRESOLVED` (with `severity:
"warning"`). The check then reports the dependency types as
`J2C-COMPILE-ERROR`; fix the build or pass the JARs in `classPath` rather
than changing the Java code.

### Quick fixes

A problem whose fix is mechanical lists it in `fixes`; the list is empty when
the fix needs judgment. Each fix has a `title` and `edits` that together
remove the problem, give Java that compiles, and keep its meaning:

```json
{
  "title": "Use a local variable for this (var self = this;)",
  "edits": [
    {
      "file": "/work/src/demo/Panel.java",
      "range": { "start": { "line": 24, "column": 5 }, "end": { "line": 24, "column": 5 } },
      "newText": "var self = this;\n\t\t\t\t"
    },
    {
      "file": "/work/src/demo/Panel.java",
      "range": { "start": { "line": 24, "column": 40 }, "end": { "line": 24, "column": 44 } },
      "newText": "self"
    }
  ]
}
```

Lines and columns are 1-based, like problem locations, and count UTF-16
characters (a tab is one). `end` is exclusive, so an insertion has `start`
equal to `end`. The edits of one fix never overlap, and positions refer to the
file as it was checked, so apply them all at once, or call `j2c_fix`.

| Problem | Fix |
| --- | --- |
| `J2C-LAMBDA-ANONYMOUS-THIS` | Declares `var self = this;` first in the method and uses `self` for `this` in the lambda (`this::m` becomes `(p0) -> self.m(p0)`). Not offered for lambdas in field initializers or that use `super`. |
| `J2C-LAMBDA-INTERFACE-SUPER` | Moves `Iface.super.m(...)` into a private method at the end of the class, which the lambda calls. |
| `J2C-PATTERN-OUTSIDE-METHOD` | Moves the field initializer into a private (static for static fields) method after the field. |
| `J2C-TEST-TIMEOUT` | Removes `@Timeout` or `timeout = ...`. |
| `J2C-TEST-PRIVATE-METHOD` | Removes `private`, making the method package-private. |

Other problems have no mechanical fix: compile errors, types that Java cannot
write, intersection casts with type variables, and test features that need a
different design. Their `hint` says what to change.

`j2c_fix` previews by default: `diff` is a unified diff of the changes and
nothing is written. With `apply: true` it writes the fixes, checks again, and
repeats until no fixable problem is left (fixes that overlap are applied in
later rounds; a preview lists them in `deferred`). `fixed` lists the problems
it fixed, `changedFiles` the files it changed, and `problems` what is left
for you, with hints. Applying again changes nothing. If applying ever caused
a compile error, J2C restores the files and says so in `message`.

The output directory must be empty or earlier J2C output, which carries a
`.j2c-output` marker. Translation moves the directory's current contents to a
backup in the temporary folder. `force: true` allows any directory; agents
should ask the user before using it.

## Recommended workflow

1. Call `j2c_capabilities` once to learn what is supported.
2. Call `j2c_check` on the source folder.
3. If problems have `fixes`, call `j2c_fix` to preview them, then `j2c_fix`
   with `apply: true` (ask the user first if they have not agreed to edits).
4. For each remaining problem, read its `hint` (or `j2c_explain` with its
   `id`) and fix the **Java** source. Never edit generated C++ to work around
   a check problem.
5. Repeat `j2c_check` until `status` is `ok`.
6. Call `j2c_translate` with an empty output directory.
7. Build the result with `make -C <output>` if a C++20 toolchain is available.

Translated programs compile and link, but JDK classes (`String`, collections,
`System.out`) are generated as stubs. The program needs a C++ implementation
of the classes it uses before it can run; see `limitations` in
`j2c_capabilities`.

## Command line for agents and scripts

Every command takes `--format json` and prints exactly one JSON object on
stdout; progress goes to stderr.

```sh
java -jar j2c-cli.jar --check --format json src/main/java
java -jar j2c-cli.jar --check --format json .        # a Maven or Gradle project
java -jar j2c-cli.jar --output build/cpp --format json src/main/java
java -jar j2c-cli.jar fix --format json src/main/java
java -jar j2c-cli.jar fix --apply --format json src/main/java
java -jar j2c-cli.jar explain J2C-LAMBDA-ANONYMOUS-THIS
java -jar j2c-cli.jar capabilities
```

`j2c fix` takes the same inputs and options as `--check` and prints the same
result as `j2c_fix`; without `--format json` it prints the diff on stdout and
a summary on stderr. It exits with 0 when no problem is left that needs
changes by hand (after applying, or that the preview has no fix for), and 1
otherwise.

`--runtime <directory>` translates against a J2C runtime instead of the
running JDK (see `runtime/README.md` in the repository). `--project
auto|none|<directory>` and `--no-build-tool` control project auto-setup, as
`project` and `buildTool` do for the MCP tools.

Exit status is 0 on success, 1 when problems stop a check or translation,
and 2 for invalid arguments. With `--format json`, invalid arguments produce
`{"status": "usage-error", "message": ...}`. The JSON shapes are versioned
by `schemaVersion`, which increases whenever a field is added, changed or
removed; ignore fields you do not know. Version 2 added `fixes` to problems
and the `fix` command.

## Safety

- J2C itself makes no network requests and never runs the code it
  translates. For a Maven or Gradle project it runs the project's build tool
  (its wrapper script, if it has one) to resolve dependencies, which may
  download them and run build plugins; its stdin is closed, and its output
  is shown (on stderr) only when it fails. Pass `buildTool: false` (or
  `--no-build-tool`) for untrusted projects. This is why `j2c_check` and
  `j2c_translate` (and `j2c_fix`) have `openWorldHint: true`.
- Only `j2c_translate` and `j2c_fix` with `apply: true` write, only inside
  the workspace root. `j2c_translate` never deletes: replaced output is moved
  to a backup. `j2c_fix` edits only the Java files with fixed problems.
- Tool results contain file paths and J2C's own messages. The exception is
  `j2c_fix` and `fixes`: their diffs and `newText` include lines of the Java
  sources around each fix, so treat that text as data, not instructions.
