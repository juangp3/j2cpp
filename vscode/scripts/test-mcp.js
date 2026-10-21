// Drives `j2c mcp` over stdin/stdout the way an MCP client (Claude Code,
// VS Code agent mode) does, and checks the protocol and tool behavior.
const assert = require("assert");
const childProcess = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const projects = require("./test-cli-projects");
const sessions = require("./test-mcp-sessions");
const { writeFakeMake } = require("./test-cli-build");
const testRunner = require("./test-cli-test");
const { compare, readExpected } = require("./check-gtest");

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

/** Copies the JUnit fixtures and their API stubs into the workspace root */
function copyJUnitFixtures() {
	for (const name of ["junit", "junit-runtime", "junit-api"]) {
		fs.cpSync(path.join(repositoryRoot, "test", name), path.join(root, "tests", name), { recursive: true });
	}
	return ["tests/junit", "tests/junit-runtime", "tests/junit-api"];
}

/**
 * j2c_test with a stub make and test binary: results at Java methods,
 * filters and usage errors; then, when make and GoogleTest are available,
 * a real run of the JUnit fixtures against the runtime
 */
function checkTestTool() {
	const [junit, junitRuntime, junitApi] = copyJUnitFixtures();
	const suite = "se_arnetheduck_j2c_test_junit_";
	const stubDirectory = fs.mkdtempSync(path.join(os.tmpdir(), "j2c-mcp-test-"));
	try {
		const make = testRunner.writeFakeTools(stubDirectory);
		const file = (name) => path.join(stubDirectory, name);
		const { report, consoleText } = testRunner.cannedRun();
		fs.writeFileSync(file("report.json"), JSON.stringify(report));
		fs.writeFileSync(file("console.txt"), consoleText);
		fs.mkdirSync(path.join(root, "tested"));
		const outsideBefore = fs.readdirSync(outside);
		const base = { paths: [junit], sourcePaths: [junit, junitApi], output: "tested", name: "stubbed" };
		const tests = session([
			call(1, "j2c_test", { ...base, jobs: 2, filter: ["DerivedTest", "Java17AssertionsTest#primitiveMismatchFails"] }),
			call(2, "j2c_test", { ...base, filter: ["NoSuchTest"] }),
			call(3, "j2c_test", { ...base, output: outside }),
			call(4, "j2c_test", { ...base, timeout: "long" }),
			call(6, "j2c_test", { ...base, timeout: 30 })
		], { env: { J2C_MAKE: make, J2C_WSL: "never", J2C_FAKE_MAKE_ARGS: file("make-args.json"),
			J2C_FAKE_GTEST_ARGS: file("gtest-args.json"), J2C_FAKE_GTEST_CONSOLE: file("console.txt"),
			J2C_FAKE_GTEST_REPORT: file("report.json") } });
		const ran = tests.get(1).result;
		assert.strictEqual(ran.isError, false, "Failed tests are data, not a tool failure");
		const result = ran.structuredContent;
		assert.strictEqual(result.schemaVersion, 5);
		assert.strictEqual(result.command, "test");
		assert.strictEqual(result.status, "failed");
		assert.strictEqual(result.gtestFilter, `${suite}DerivedTest.*:${suite}Java17AssertionsTest.primitiveMismatchFails`);
		// Later calls overwrite the stubs' argument files
		assert(result.testCommand.includes(`--gtest_filter=${result.gtestFilter}`), JSON.stringify(result.testCommand));
		assert.deepStrictEqual(result.build.makeCommand, [make, "-k", "-j2", "--output-sync=target", "tests"]);
		const mismatch = result.tests.find((entry) => entry.javaMethod === "primitiveMismatchFails");
		const assertionsJava = path.join(root, junit, "se", "arnetheduck", "j2c", "test", "junit", "Java17AssertionsTest.java");
		assert.strictEqual(mismatch.javaClass, "se.arnetheduck.j2c.test.junit.Java17AssertionsTest");
		assert.strictEqual(mismatch.file, assertionsJava);
		assert.strictEqual(mismatch.line, fs.readFileSync(assertionsJava, "utf8").split(/\r?\n/)
			.findIndex((line) => line.includes("void primitiveMismatchFails()")) + 1);
		assert.strictEqual(mismatch.message, "expected: <4> but was: <5>");
		assert.strictEqual(result.summary.exitCode, 1);
		// The translation is offered as resources
		assert(fs.existsSync(path.join(root, "tested", ".j2c-output")));
		assert(tests.get(2).result.structuredContent.message.includes("NoSuchTest matches no JUnit test"));
		assert(tests.get(3).result.structuredContent.message.includes("outside the workspace root"));
		assert(tests.get(4).result.structuredContent.message.includes("timeout must be an integer"));
		for (const id of [2, 3, 4]) {
			assert.strictEqual(tests.get(id).result.isError, true, `${id}`);
			assert.strictEqual(tests.get(id).result.structuredContent.status, "usage-error", `${id}`);
		}
		assert.strictEqual(tests.get(6).result.structuredContent.timeout, 30);
		assert.deepStrictEqual(fs.readdirSync(outside), outsideBefore);
	} finally {
		fs.rmSync(stubDirectory, { recursive: true, force: true });
	}

	// A real run: make, g++ and GoogleTest (in WSL on Windows)
	fs.mkdirSync(path.join(root, "junit-cpp"));
	const env = process.platform === "win32" && !process.env.J2C_WSL_DISTRO ? { J2C_WSL_DISTRO: "Ubuntu" } : {};
	const real = session([
		call(1, "j2c_test", { paths: [junit, junitRuntime], sourcePaths: [junit, junitRuntime, junitApi],
			output: "junit-cpp", name: "junit", runtime: path.join(repositoryRoot, "runtime") })
	], { env }).get(1).result.structuredContent;
	const missing = testRunner.missingToolchain(real);
	if (missing) {
		console.log(`j2c_test with a real GoogleTest run skipped: ${missing}`);
		return;
	}
	const problems = compare(testRunner.outcomesOf(real), readExpected([path.join(root, junit), path.join(root, junitRuntime)]), true);
	assert.deepStrictEqual(problems, [], JSON.stringify(real).slice(0, 2000));
	assert(real.tests.every((entry) => entry.file && entry.line > 0), "Every test has its Java location");
	console.log(`j2c_test ran ${real.tests.length} translated JUnit tests; every outcome and message matches`);
}

async function main() {
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
		["j2c_check", "j2c_translate", "j2c_fix", "j2c_report", "j2c_build", "j2c_test", "j2c_explain", "j2c_capabilities"]);
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
	assert.strictEqual(byName.get("j2c_report").annotations.readOnlyHint, true);
	assert.strictEqual(byName.get("j2c_report").annotations.destructiveHint, false);
	assert.deepStrictEqual(byName.get("j2c_report").inputSchema.required, ["paths"]);
	assert.strictEqual(byName.get("j2c_report").inputSchema.properties.output, undefined);
	// Prompts and resources are advertised in every protocol version
	for (const capability of ["tools", "prompts", "resources"]) {
		assert.strictEqual(init.capabilities[capability].listChanged, false, capability);
	}
	assert.strictEqual(init.capabilities.resources.subscribe, false);
	assert.strictEqual(init.serverInfo.title, "J2C Java to C++");
	// j2c_build writes only build products into the output directory, and
	// never runs Maven or Gradle
	const buildTool = byName.get("j2c_build");
	assert.strictEqual(buildTool.annotations.readOnlyHint, false);
	assert.strictEqual(buildTool.annotations.destructiveHint, false);
	assert.strictEqual(buildTool.annotations.openWorldHint, false);
	assert.deepStrictEqual(buildTool.inputSchema.required, ["output"]);
	assert.deepStrictEqual(buildTool.inputSchema.properties.target.enum, ["all", "mains", "tests"]);
	assert.strictEqual(buildTool.inputSchema.properties.jobs.type, "integer");
	assert.strictEqual(buildTool.inputSchema.properties.buildTool, undefined);
	assert.strictEqual(byName.get("j2c_translate").inputSchema.properties.lineDirectives.type, "boolean");
	// j2c_test translates with tests and directives, builds and runs the
	// tests in the output directory, and never runs Maven or Gradle
	const testTool = byName.get("j2c_test");
	assert.strictEqual(testTool.annotations.readOnlyHint, false);
	assert.strictEqual(testTool.annotations.destructiveHint, false);
	assert.strictEqual(testTool.annotations.openWorldHint, false);
	assert.deepStrictEqual(testTool.inputSchema.required, ["paths", "output"]);
	for (const [property, type] of [["filter", "array"], ["timeout", "integer"], ["runtime", "string"], ["jobs", "integer"]]) {
		assert.strictEqual(testTool.inputSchema.properties[property].type, type, property);
	}
	for (const property of ["tests", "buildTool", "lineDirectives", "target"]) {
		assert.strictEqual(testTool.inputSchema.properties[property], undefined, property);
	}
	assert(init.instructions.includes("j2c_test"));

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

	// j2c_report: the readiness report as JSON and Markdown, read-only
	const reportRoot = path.join(tempDirectory, "report root");
	fs.cpSync(path.join(repositoryRoot, "test", "report"), reportRoot, { recursive: true });
	fs.mkdirSync(path.join(reportRoot, "out"));
	const reportFile = path.join(reportRoot, "src", "shop", "ui", "Panel.java");
	const reportBefore = fs.readFileSync(reportFile, "utf8");
	const reported = session([
		call(1, "j2c_report", { paths: ["src"] }),
		call(2, "j2c_report", { paths: [outside] }),
		call(3, "j2c_report", {})
	], { root: reportRoot });
	const readiness = reported.get(1).result;
	assert.strictEqual(readiness.isError, false, JSON.stringify(readiness));
	assert.strictEqual(readiness.structuredContent.command, "report");
	assert.strictEqual(readiness.structuredContent.schemaVersion, 5);
	assert.deepStrictEqual(readiness.structuredContent.totals.files, { total: 3, translatable: 1, percent: 33.3 });
	assert.deepStrictEqual(readiness.structuredContent.blockers.byId.map((entry) => [entry.id, entry.count]),
		[["J2C-COMPILE-ERROR", 1], ["J2C-LAMBDA-ANONYMOUS-THIS", 1], ["J2C-PATTERN-OUTSIDE-METHOD", 1]]);
	assert.strictEqual(readiness.structuredContent.jdk.typesUsed, 10);
	assert.deepStrictEqual(JSON.parse(readiness.content[0].text), readiness.structuredContent);
	assert.strictEqual(readiness.content.length, 2);
	assert(readiness.content[1].text.startsWith("# J2C migration readiness report\n"));
	// Markdown names files relative to the workspace root
	assert(readiness.content[1].text.includes("- `src/shop/ui/Panel.java:19:25`: "), readiness.content[1].text);
	assert.strictEqual(fs.readFileSync(reportFile, "utf8"), reportBefore, "The report writes nothing");
	assert.strictEqual(reported.get(2).result.isError, true);
	assert(reported.get(2).result.structuredContent.message.includes("outside the workspace root"));
	assert.strictEqual(reported.get(3).result.isError, true);
	assert.strictEqual(reported.get(3).result.structuredContent.message, "Missing paths");

	// Prompts and resources, with titles only from 2025-06-18 on
	fs.writeFileSync(path.join(reportRoot, "notes.txt"), "not generated");
	fs.writeFileSync(path.join(outside, "secret.txt"), "outside");
	const mainSource = path.join("src", "shop", "Main.java");
	const content = session([
		{ jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-06-18", capabilities: {} } },
		{ jsonrpc: "2.0", id: 2, method: "prompts/list" },
		{ jsonrpc: "2.0", id: 3, method: "prompts/get", params: { name: "j2c-migrate", arguments: { path: "src", tests: "true" } } },
		{ jsonrpc: "2.0", id: 4, method: "prompts/get", params: { name: "j2c-assess", arguments: { path: "src" } } },
		{ jsonrpc: "2.0", id: 5, method: "prompts/get", params: { name: "j2c-unknown", arguments: { path: "src" } } },
		{ jsonrpc: "2.0", id: 6, method: "prompts/get", params: { name: "j2c-migrate", arguments: {} } },
		{ jsonrpc: "2.0", id: 7, method: "resources/list" },
		{ jsonrpc: "2.0", id: 8, method: "resources/templates/list" },
		{ jsonrpc: "2.0", id: 9, method: "resources/read", params: { uri: "j2c://problems" } },
		{ jsonrpc: "2.0", id: 10, method: "resources/read", params: { uri: "j2c://problems/J2C-TEST-TIMEOUT" } },
		{ jsonrpc: "2.0", id: 11, method: "resources/read", params: { uri: "j2c://problems/J2C-NO-SUCH-ID" } },
		{ jsonrpc: "2.0", id: 12, method: "resources/read", params: { uri: "j2c://runtime" } },
		{ jsonrpc: "2.0", id: 13, method: "resources/read", params: { uri: "j2c://unknown" } },
		call(14, "j2c_translate", { paths: [mainSource], output: "out" }),
		{ jsonrpc: "2.0", id: 15, method: "resources/list" },
		{ jsonrpc: "2.0", id: 16, method: "resources/read", params: { uri: "j2c://output/out/src/shop/Main.cpp" } },
		{ jsonrpc: "2.0", id: 17, method: "resources/read", params: { uri: "j2c://output/out%2FMakefile" } },
		{ jsonrpc: "2.0", id: 18, method: "resources/read", params: { uri: "j2c://output/notes.txt" } },
		{ jsonrpc: "2.0", id: 19, method: "resources/read", params: { uri: "j2c://output/../outside/secret.txt" } },
		{ jsonrpc: "2.0", id: 20, method: "resources/read", params: { uri: "j2c://output/out/.j2c-output" } },
		{ jsonrpc: "2.0", id: 21, method: "resources/read", params: { uri: "j2c://output/out/missing.cpp" } },
		{ jsonrpc: "2.0", id: 22, method: "resources/read", params: {} }
	], { root: reportRoot });
	const prompts = content.get(2).result.prompts;
	assert.deepStrictEqual(prompts.map((prompt) => [prompt.name, prompt.title,
		prompt.arguments.map((argument) => `${argument.name}${argument.required ? "!" : ""}`)]), [
		["j2c-migrate", "Migrate Java to C++", ["path!", "output", "tests"]],
		["j2c-assess", "Assess a Java to C++ migration", ["path!", "tests"]]
	]);
	const migrate = content.get(3).result;
	assert.strictEqual(migrate.messages.length, 1);
	assert.strictEqual(migrate.messages[0].role, "user");
	const migrateText = migrate.messages[0].content.text;
	for (const step of ["j2c_check with paths: [\"src\"], tests: true", "j2c_fix", "apply: true",
		"j2c_translate with paths: [\"src\"], output: \"build/cpp\", tests: true", "make -C build/cpp",
		"j2c_test with paths: [\"src\"], output: \"build/cpp\""]) {
		assert(migrateText.includes(step), `${step} missing from ${migrateText}`);
	}
	assert(content.get(4).result.messages[0].content.text.includes("j2c_report with paths: [\"src\"]."));
	assert.strictEqual(content.get(5).error.code, -32602);
	assert(content.get(5).error.message.includes("Unknown prompt"));
	assert.strictEqual(content.get(6).error.code, -32602);
	assert(content.get(6).error.message.includes("path"));

	assert.deepStrictEqual(content.get(7).result.resources.map((resource) => [resource.uri, resource.title, resource.mimeType]), [
		["j2c://problems", "J2C problem catalog", "application/json"],
		["j2c://runtime", "J2C runtime contract", "text/markdown"]
	]);
	assert.deepStrictEqual(content.get(8).result.resourceTemplates.map((template) => template.uriTemplate),
		["j2c://problems/{id}", "j2c://output/{path}"]);
	const catalog = JSON.parse(content.get(9).result.contents[0].text);
	assert.strictEqual(catalog.command, "explain");
	assert(catalog.problems.some((problem) => problem.id === "J2C-LAMBDA-ANONYMOUS-THIS"));
	const timeout = JSON.parse(content.get(10).result.contents[0].text);
	assert.deepStrictEqual(timeout.problems.map((problem) => problem.id), ["J2C-TEST-TIMEOUT"]);
	assert.strictEqual(content.get(11).error.code, -32002);
	const runtimeText = content.get(12).result.contents[0].text;
	assert.strictEqual(content.get(12).result.contents[0].mimeType, "text/markdown");
	assert(runtimeText.startsWith("# J2C runtime\n"), runtimeText.slice(0, 100));
	assert(runtimeText.includes("## Types the runtime provides"));
	assert(runtimeText.includes("\n### `java.util`\n"));
	assert(/ArrayList \(\d+\)/.test(runtimeText));
	assert.strictEqual(content.get(13).error.code, -32002);
	assert.strictEqual(content.get(14).result.isError, false, JSON.stringify(content.get(14).result));
	const listed = content.get(15).result.resources.map((resource) => resource.uri);
	assert(listed.includes("j2c://output/out/src/shop/Main.cpp"), listed.join("\n"));
	assert(listed.includes("j2c://output/out/Makefile"));
	assert(!listed.includes("j2c://output/out/.j2c-output"));
	const generated = content.get(16).result.contents[0];
	assert.strictEqual(generated.mimeType, "text/x-c++src");
	assert.strictEqual(generated.text, fs.readFileSync(path.join(reportRoot, "out", "src", "shop", "Main.cpp"), "utf8"));
	assert.strictEqual(content.get(17).result.contents[0].mimeType, "text/x-makefile");
	// Only J2C's output, inside the root, can be read
	for (const id of [18, 19, 20]) {
		assert.strictEqual(content.get(id).error.code, -32602, JSON.stringify(content.get(id)));
	}
	assert(content.get(18).error.message.includes(".j2c-output"));
	assert(content.get(19).error.message.includes("outside the workspace root"));
	assert.strictEqual(content.get(21).error.code, -32002);
	assert.strictEqual(content.get(22).error.code, -32602);

	// Older protocol versions get no titles
	const older = session([
		{ jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-03-26", capabilities: {} } },
		{ jsonrpc: "2.0", id: 2, method: "prompts/list" },
		{ jsonrpc: "2.0", id: 3, method: "resources/list" },
		{ jsonrpc: "2.0", id: 4, method: "resources/templates/list" }
	], { root: reportRoot });
	assert.strictEqual(older.get(1).result.protocolVersion, "2025-03-26");
	assert(older.get(1).result.capabilities.prompts && older.get(1).result.capabilities.resources);
	assert.strictEqual(older.get(1).result.serverInfo.title, undefined);
	for (const [id, key] of [[2, "prompts"], [3, "resources"], [4, "resourceTemplates"]]) {
		assert(older.get(id).result[key].every((item) => item.title === undefined), key);
	}

	// j2c_build runs make (a stub here) in earlier J2C output and maps the
	// compiler's errors to Java through the #line directives
	const stubDirectory = fs.mkdtempSync(path.join(os.tmpdir(), "j2c-mcp-make-"));
	try {
		const make = writeFakeMake(stubDirectory);
		const canned = path.join(stubDirectory, "make-output.txt");
		const argsFile = path.join(stubDirectory, "make-args.json");
		fs.mkdirSync(path.join(root, "lines"));
		fs.mkdirSync(path.join(root, "built"));
		const javaPath = ["..", "src", "demo", "Hi.java"].join("/");
		fs.writeFileSync(canned, [
			"Compile src/demo/Hi.cpp",
			`${javaPath}:5:10: error: 'f' was not declared in this scope`,
			""
		].join("\n"));
		const outsideBefore = fs.readdirSync(outside);
		const builds = session([
			call(1, "j2c_translate", { paths: [hi], output: "lines", lineDirectives: true }),
			call(2, "j2c_build", { output: "lines", target: "mains", jobs: 2 }),
			call(3, "j2c_build", { output: "src" }),
			call(4, "j2c_build", { output: outside }),
			call(5, "j2c_build", { output: "lines", target: "test" }),
			call(6, "j2c_build", { output: "lines", jobs: "many" }),
			call(7, "j2c_build", { paths: [hi], output: "built" }),
			call(8, "j2c_build", { paths: [hi], output: outside })
		], { env: { J2C_MAKE: make, J2C_WSL: "never", J2C_FAKE_MAKE_OUTPUT: canned, J2C_FAKE_MAKE_ARGS: argsFile } });
		const linesReport = builds.get(1).result.structuredContent;
		assert.strictEqual(linesReport.lineDirectives, true);
		assert(fs.readFileSync(path.join(root, "lines", "src", "demo", "Hi.cpp"), "utf8").includes(` "${javaPath}"`));

		const failed = builds.get(2).result;
		assert.strictEqual(failed.isError, false, "A failed build is data, not a tool failure");
		const report = failed.structuredContent;
		assert.strictEqual(report.command, "build");
		assert.strictEqual(report.status, "failed");
		assert.strictEqual(report.exitCode, 2);
		assert.strictEqual(report.translated, false);
		assert.deepStrictEqual(report.makeCommand, [make, "-k", "-j2", "--output-sync=target", "mains"]);
		const [error] = report.diagnostics;
		assert.deepStrictEqual(error.java, { file: path.join(root, "src", "demo", "Hi.java"), line: 5 });
		assert.strictEqual(error.cpp.file, path.join(root, "lines", "src", "demo", "Hi.cpp"));
		assert.strictEqual(error.mapped, "line-directive");
		for (const id of [3, 4, 5, 6, 8]) {
			const refused = builds.get(id).result;
			assert.strictEqual(refused.isError, true, `${id}: ${JSON.stringify(refused)}`);
			assert.strictEqual(refused.structuredContent.status, "usage-error", JSON.stringify(refused));
		}
		assert(builds.get(3).result.structuredContent.message.includes("Not a J2C output directory"));
		assert(builds.get(4).result.structuredContent.message.includes("outside the workspace root"));
		assert(builds.get(8).result.structuredContent.message.includes("outside the workspace root"));
		assert.deepStrictEqual(fs.readdirSync(outside), outsideBefore);
		// With paths it translates first, with directives
		const translatedBuild = builds.get(7).result.structuredContent;
		assert.strictEqual(translatedBuild.translated, true, JSON.stringify(translatedBuild));
		assert(translatedBuild.generatedFiles.includes("src/demo/Hi.cpp"));
		assert(fs.readFileSync(path.join(root, "built", "src", "demo", "Hi.cpp"), "utf8").includes("#line "));
	} finally {
		fs.rmSync(stubDirectory, { recursive: true, force: true });
	}
	checkTestTool();

	for (const value of ["many", "-1"]) {
		const badCache = childProcess.spawnSync("java", ["-jar", cliJar, "mcp", "--root", root,
			"--check-cache", value], { encoding: "utf8", input: "" });
		assert.strictEqual(badCache.status, 2, badCache.stderr);
		assert(badCache.stderr.includes("--check-cache needs a number of files"), badCache.stderr);
		assert.strictEqual(badCache.stdout, "");
	}

	// A long-running server: incremental re-checks equal fresh checks, and
	// progress, ping during a call, and cancellation
	const sessionEnv = { J2C_CACHE_DIR: path.join(tempDirectory, "cache") };
	const rechecked = await sessions.checkIncremental(cliJar, tempDirectory, sessionEnv);
	console.log("Incremental re-checks equal fresh checks: " + rechecked
		.filter((entry) => entry.paths === "src")
		.map((entry) => `${entry.step} ${entry.rechecked}/${entry.selected}`).join(", "));
	const timing = await sessions.checkProgressAndCancellation(cliJar, tempDirectory, sessionEnv);
	console.log(`Cancelled translation stopped in ${timing.stopTime} ms (a full one takes ${timing.fullTime} ms)`);
	await sessions.checkOldProgress(cliJar, tempDirectory, sessionEnv);
	const gradleStop = await sessions.checkBuildToolCancellation(cliJar, tempDirectory, sessionEnv);
	console.log(`Cancelled check stopped the build tool in ${gradleStop} ms`);
	const makeStop = await sessions.checkBuildCancellation(cliJar, tempDirectory, sessionEnv);
	console.log(`Cancelled build stopped make in ${makeStop} ms`);
	const testStop = await sessions.checkTestCancellation(cliJar, tempDirectory, sessionEnv);
	console.log(`Cancelled tests stopped the test binary in ${testStop} ms`);

	console.log("MCP server checks passed");
} finally {
	fs.rmSync(tempDirectory, { recursive: true, force: true });
}
}

main().catch((error) => {
	console.error(error);
	process.exitCode = 1;
});
