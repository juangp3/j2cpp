// Drives `j2c mcp` over stdin/stdout the way an MCP client (Claude Code,
// VS Code agent mode) does, and checks the protocol and tool behavior.
const assert = require("assert");
const childProcess = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const projects = require("./test-cli-projects");

const extensionDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..");
const cliJar = path.join(repositoryRoot, "target", "j2c-cli.jar");
const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), "j2c mcp test "));
const root = path.join(tempDirectory, "workspace root");
const outside = path.join(tempDirectory, "outside");

function session(messages, options = {}) {
	const input = messages.map((message) =>
		typeof message === "string" ? message : JSON.stringify(message)
	).join("\n") + "\n";
	const result = childProcess.spawnSync("java", ["-jar", cliJar, "mcp", "--root",
		options.root || root, ...(options.args || [])], {
		encoding: "utf8",
		input,
		env: { ...process.env, J2C_CACHE_DIR: path.join(tempDirectory, "cache"), ...(options.env || {}) }
	});
	assert.strictEqual(result.status, 0, result.stderr);
	const lines = result.stdout.split(/\r?\n/).filter((line) => line.trim() !== "");
	// Nothing but protocol messages may reach stdout
	const responses = lines.map((line) => {
		const response = JSON.parse(line);
		assert.strictEqual(response.jsonrpc, "2.0", line);
		return response;
	});
	return new Map(responses.map((response) => [response.id, response]));
}

function call(id, name, args) {
	return { jsonrpc: "2.0", id, method: "tools/call", params: { name, arguments: args } };
}

try {
	assert(fs.existsSync(cliJar), `CLI JAR not found: ${cliJar}`);
	const sourceDirectory = path.join(root, "src", "demo");
	fs.mkdirSync(sourceDirectory, { recursive: true });
	fs.mkdirSync(path.join(root, "out"));
	fs.mkdirSync(outside);
	fs.writeFileSync(path.join(sourceDirectory, "Hi.java"), [
		"package demo;",
		"public class Hi {",
		"\tstatic int twice(int x) {",
		"\t\tjava.util.function.IntUnaryOperator f = v -> v * 2;",
		"\t\treturn f.applyAsInt(x);",
		"\t}",
		"}",
		""
	].join("\n"));
	fs.writeFileSync(path.join(sourceDirectory, "Broken.java"), [
		"package demo;",
		"public class Broken {",
		"\tObject o = new Object() {",
		"\t\tRunnable r = () -> System.out.println(this);",
		"\t};",
		"}",
		""
	].join("\n"));
	const hi = path.join("src", "demo", "Hi.java");
	const broken = path.join("src", "demo", "Broken.java");

	const responses = session([
		{
			jsonrpc: "2.0", id: 1, method: "initialize",
			params: { protocolVersion: "2025-06-18", capabilities: {}, clientInfo: { name: "test", version: "1" } }
		},
		{ jsonrpc: "2.0", method: "notifications/initialized" },
		{ jsonrpc: "2.0", id: 2, method: "tools/list" },
		call(3, "j2c_check", { paths: [hi] }),
		call(4, "j2c_check", { paths: [broken] }),
		call(5, "j2c_translate", { paths: [hi], output: "out" }),
		call(6, "j2c_translate", { paths: [hi], output: outside }),
		call(7, "j2c_check", { paths: [path.join("..", "outside")] }),
		call(8, "j2c_explain", { id: "J2C-LAMBDA-ANONYMOUS-THIS" }),
		call(9, "j2c_capabilities", {}),
		call(10, "j2c_unknown", {}),
		call(11, "j2c_check", { paths: "not an array" }),
		{ jsonrpc: "2.0", id: 12, method: "unknown/method" },
		{ jsonrpc: "2.0", id: 13, method: "ping" },
		"{ not json",
		{
			jsonrpc: "2.0", id: 14, method: "initialize",
			params: { protocolVersion: "1999-01-01", capabilities: {} }
		}
	]);

	const init = responses.get(1).result;
	assert.strictEqual(init.protocolVersion, "2025-06-18");
	assert.strictEqual(init.serverInfo.name, "j2c");
	assert(init.capabilities.tools);
	assert(init.instructions.includes("j2c_check"));
	// An unsupported version is answered with the newest supported one
	assert.strictEqual(responses.get(14).result.protocolVersion, "2025-06-18");

	const tools = responses.get(2).result.tools;
	assert.deepStrictEqual(tools.map((tool) => tool.name),
		["j2c_check", "j2c_translate", "j2c_fix", "j2c_explain", "j2c_capabilities"]);
	const byName = new Map(tools.map((tool) => [tool.name, tool]));
	assert.strictEqual(byName.get("j2c_check").annotations.readOnlyHint, true);
	assert.strictEqual(byName.get("j2c_translate").annotations.readOnlyHint, false);
	assert.strictEqual(byName.get("j2c_translate").annotations.destructiveHint, true);
	assert.deepStrictEqual(byName.get("j2c_translate").inputSchema.required, ["paths", "output"]);
	assert.strictEqual(byName.get("j2c_translate").inputSchema.properties.tests.type, "boolean");
	// Project auto-setup may run Maven or Gradle, which can download
	assert.strictEqual(byName.get("j2c_check").inputSchema.properties.project.type, "string");
	assert.strictEqual(byName.get("j2c_check").inputSchema.properties.buildTool.type, "boolean");
	assert.strictEqual(byName.get("j2c_check").annotations.openWorldHint, true);
	assert.strictEqual(byName.get("j2c_explain").annotations.openWorldHint, false);
	// j2c_fix writes Java files when apply is true, so clients confirm it
	assert.strictEqual(byName.get("j2c_fix").annotations.readOnlyHint, false);
	assert.strictEqual(byName.get("j2c_fix").annotations.destructiveHint, true);
	assert.deepStrictEqual(byName.get("j2c_fix").inputSchema.required, ["paths"]);
	assert.strictEqual(byName.get("j2c_fix").inputSchema.properties.apply.type, "boolean");
	assert.strictEqual(byName.get("j2c_fix").annotations.openWorldHint, true);

	const clean = responses.get(3).result;
	assert.strictEqual(clean.isError, false);
	assert.strictEqual(clean.structuredContent.status, "ok");
	assert.deepStrictEqual(JSON.parse(clean.content[0].text), clean.structuredContent);

	const problems = responses.get(4).result.structuredContent;
	assert.strictEqual(responses.get(4).result.isError, false, "Problems are data, not a tool failure");
	assert.strictEqual(problems.status, "problems");
	assert.strictEqual(problems.problems[0].id, "J2C-LAMBDA-ANONYMOUS-THIS");
	assert.strictEqual(problems.problems[0].line, 4);
	assert(problems.problems[0].column > 1);
	assert(problems.problems[0].hint.includes("local variable"));

	const translated = responses.get(5).result;
	assert.strictEqual(translated.isError, false, JSON.stringify(translated));
	assert.strictEqual(translated.structuredContent.makefile, "Makefile");
	assert(translated.structuredContent.generatedFiles.includes("src/demo/Hi.cpp"));
	assert(fs.existsSync(path.join(root, "out", "Makefile")));
	assert(fs.existsSync(path.join(root, "out", ".j2c-output")));

	// Writes and reads stay inside the workspace root
	for (const id of [6, 7]) {
		const denied = responses.get(id).result;
		assert.strictEqual(denied.isError, true);
		assert.strictEqual(denied.structuredContent.status, "usage-error");
		assert(denied.structuredContent.message.includes("outside the workspace root"),
			denied.structuredContent.message);
	}
	assert.deepStrictEqual(fs.readdirSync(outside), []);

	assert.strictEqual(responses.get(8).result.structuredContent.problems[0].id, "J2C-LAMBDA-ANONYMOUS-THIS");
	assert(responses.get(9).result.structuredContent.problemIds.includes("J2C-COMPILE-ERROR"));
	assert.strictEqual(responses.get(10).error.code, -32602);
	assert.strictEqual(responses.get(11).result.isError, true);
	assert.strictEqual(responses.get(12).error.code, -32601);
	assert.deepStrictEqual(responses.get(13).result, {});
	assert.strictEqual(responses.get(null).error.code, -32700);
	// Every request with an id got exactly one response; the notification none
	assert.strictEqual(responses.size, 15);

	// paths: ["."] in a Maven project: its source roots and class path
	const mavenRoot = projects.copyFixtures(path.join(tempDirectory, "projects"))["maven-simple"];
	fs.mkdirSync(path.join(mavenRoot, "out"));
	const maven = projects.mavenAvailable();
	const projectResponses = session([
		call(1, "j2c_check", { paths: ["."], buildTool: false }),
		call(2, "j2c_check", { paths: ["."], project: "none" }),
		call(3, "j2c_check", { paths: ["."], buildTool: "yes" }),
		...(maven ? [
			call(4, "j2c_check", { paths: ["."], tests: true }),
			call(5, "j2c_translate", { paths: ["."], output: "out" })
		] : [])
	], { root: mavenRoot });
	const unresolved = projectResponses.get(1).result;
	assert.strictEqual(unresolved.isError, false);
	assert.strictEqual(unresolved.structuredContent.status, "problems");
	assert.strictEqual(unresolved.structuredContent.warnings[0].id, "J2C-PROJECT-CLASSPATH-UNRESOLVED");
	assert.strictEqual(unresolved.structuredContent.projects[0].buildTool, "maven");
	assert.strictEqual(unresolved.structuredContent.projects[0].sourceRoots.length, 1);
	assert.deepStrictEqual(projectResponses.get(2).result.structuredContent.projects, []);
	assert.strictEqual(projectResponses.get(3).result.isError, true);
	if (maven) {
		const resolved = projectResponses.get(4).result.structuredContent;
		assert.strictEqual(resolved.status, "ok", JSON.stringify(resolved));
		assert.strictEqual(resolved.files, 2);
		assert.strictEqual(resolved.projects[0].classPathSource, "maven");
		const translatedProject = projectResponses.get(5).result;
		assert.strictEqual(translatedProject.isError, false, JSON.stringify(translatedProject));
		assert(translatedProject.structuredContent.generatedFiles.includes("src/simple/App.cpp"));
	} else {
		console.log("MCP Maven class path checks skipped (mvn not found on the PATH)");
	}

	// The server's defaults: j2c mcp --project none, --no-build-tool
	const defaults = session([
		call(1, "j2c_check", { paths: ["."] }),
		call(2, "j2c_check", { paths: ["."], project: "auto" })
	], { root: mavenRoot, args: ["--project", "none", "--no-build-tool"],
		env: { J2C_CACHE_DIR: path.join(tempDirectory, "empty cache") } });
	assert.deepStrictEqual(defaults.get(1).result.structuredContent.projects, []);
	assert(defaults.get(2).result.structuredContent.warnings[0].message.includes("buildTool: false"));
	// j2c_fix previews by default, applies with apply: true, and stays inside
	// the root; test/fix holds the fixture
	const fixture = path.join(repositoryRoot, "test", "fix", "src", "fixdemo", "AnonymousThis.java");
	const fixDirectory = path.join(root, "src", "fixdemo");
	fs.mkdirSync(fixDirectory, { recursive: true });
	const fixable = path.join(fixDirectory, "AnonymousThis.java");
	fs.copyFileSync(fixture, fixable);
	const outsideFixable = path.join(outside, "fixdemo", "AnonymousThis.java");
	fs.mkdirSync(path.dirname(outsideFixable));
	fs.copyFileSync(fixture, outsideFixable);
	const original = fs.readFileSync(fixable, "utf8");
	const relativeFixable = path.join("src", "fixdemo", "AnonymousThis.java");
	const previews = session([call(1, "j2c_fix", { paths: [relativeFixable] })]);
	assert.strictEqual(original, fs.readFileSync(fixable, "utf8"), "A preview writes nothing");
	const fixes = session([
		call(2, "j2c_fix", { paths: [relativeFixable], apply: true }),
		call(3, "j2c_check", { paths: [relativeFixable] }),
		call(4, "j2c_fix", { paths: [relativeFixable], apply: true }),
		call(5, "j2c_fix", { paths: [outsideFixable], apply: true }),
		call(6, "j2c_fix", { paths: [relativeFixable], apply: "yes" })
	]);

	const preview = previews.get(1).result;
	assert.strictEqual(preview.isError, false, JSON.stringify(preview));
	assert.strictEqual(preview.structuredContent.command, "fix");
	assert.strictEqual(preview.structuredContent.applied, false);
	assert.strictEqual(preview.structuredContent.status, "ok");
	assert.strictEqual(preview.structuredContent.fixed.length, 6);
	assert(preview.structuredContent.fixed.every((fixed) => fixed.id === "J2C-LAMBDA-ANONYMOUS-THIS"));
	assert(preview.structuredContent.diff.startsWith("--- a/src/fixdemo/AnonymousThis.java\n+++ b/src/fixdemo/AnonymousThis.java\n"),
		preview.structuredContent.diff);
	assert(preview.structuredContent.diff.includes("+\t\t\t\tvar self = this;"), preview.structuredContent.diff);

	const applied = fixes.get(2).result.structuredContent;
	assert.strictEqual(fixes.get(2).result.isError, false, JSON.stringify(applied));
	assert.strictEqual(applied.applied, true);
	assert.strictEqual(applied.status, "ok");
	assert.strictEqual(applied.diff, preview.structuredContent.diff);
	assert.deepStrictEqual(applied.changedFiles, [fixable]);
	assert(fs.readFileSync(fixable, "utf8").includes("var self = this;"));
	assert.strictEqual(fixes.get(3).result.structuredContent.status, "ok");
	// Applying again finds nothing to do
	assert.strictEqual(fixes.get(4).result.structuredContent.diff, "");
	assert.deepStrictEqual(fixes.get(4).result.structuredContent.fixed, []);

	const outsideFix = fixes.get(5).result;
	assert.strictEqual(outsideFix.isError, true);
	assert(outsideFix.structuredContent.message.includes("outside the workspace root"),
		outsideFix.structuredContent.message);
	assert.strictEqual(fs.readFileSync(outsideFixable, "utf8"), original);
	// apply must be a boolean; anything else previews, like an omitted one
	assert.strictEqual(fixes.get(6).result.structuredContent.applied, false);

	console.log("MCP server checks passed");
} finally {
	fs.rmSync(tempDirectory, { recursive: true, force: true });
}
