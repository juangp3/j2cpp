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
`j2c_translate` runs, because it writes files.

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
| `j2c_check` | No | `paths` (required), `sourcePaths`, `classPath`, `sealedTypes`, `encoding` | `status` (`ok` or `problems`), `problems`, `summary` |
| `j2c_translate` | Yes | The same, plus `output` (required), `name`, `force` | `status`, `output`, `makefile`, `generatedFiles`, `testFiles` |
| `j2c_explain` | No | `id` (optional) | What problem IDs mean and how to fix them |
| `j2c_capabilities` | No | none | Version, supported features, limitations, problem IDs |

Pass `tests: true` to both tools for projects with JUnit tests (JUnit JARs in
`classPath`). `j2c_check` then also reports test features that translated
tests cannot run, and `j2c_translate` generates GoogleTest wrappers, listed
in `testFiles`, that `make -C <output> test` builds and runs.

Each problem has a stable `id`, `file`, `line`, `column` (1-based), `message`
and `hint`. Problems in the Java code are a normal `j2c_check` result, not a
tool error. `j2c_translate` reports them as an error, because no output was
produced.

The output directory must be empty or earlier J2C output, which carries a
`.j2c-output` marker. Translation moves the directory's current contents to a
backup in the temporary folder. `force: true` allows any directory; agents
should ask the user before using it.

## Recommended workflow

1. Call `j2c_capabilities` once to learn what is supported.
2. Call `j2c_check` on the source folder.
3. For each problem, read its `hint` (or `j2c_explain` with its `id`) and fix
   the **Java** source. Never edit generated C++ to work around a check
   problem.
4. Repeat `j2c_check` until `status` is `ok`.
5. Call `j2c_translate` with an empty output directory.
6. Build the result with `make -C <output>` if a C++20 toolchain is available.

Translated programs compile and link, but JDK classes (`String`, collections,
`System.out`) are generated as stubs. The program needs a C++ implementation
of the classes it uses before it can run; see `limitations` in
`j2c_capabilities`.

## Command line for agents and scripts

Every command takes `--format json` and prints exactly one JSON object on
stdout; progress goes to stderr.

```sh
java -jar j2c-cli.jar --check --format json src/main/java
java -jar j2c-cli.jar --output build/cpp --format json src/main/java
java -jar j2c-cli.jar explain J2C-LAMBDA-ANONYMOUS-THIS
java -jar j2c-cli.jar capabilities
```

Exit status is 0 on success, 1 when problems stop a check or translation,
and 2 for invalid arguments. With `--format json`, invalid arguments produce
`{"status": "usage-error", "message": ...}`. The JSON shapes are versioned
by `schemaVersion`.

## Safety

- Only `j2c_translate` writes, only inside the workspace root, and it never
  deletes: replaced output is moved to a backup.
- J2C makes no network requests and never runs the code it translates.
- Tool results contain file paths and J2C's own messages, never text from
  the Java sources, so comments in translated code cannot reach the agent as
  instructions.
