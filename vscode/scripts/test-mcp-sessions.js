// test-mcp.js checks that need a long-running `j2c mcp` server:
//
// - Incremental re-checks: a sequence of edits (bodies, APIs used through
//   inheritance, added, deleted and renamed types, secondary types, a new
//   package, syntax errors) is checked through MCP after each edit, and every
//   result must equal a fresh `j2c --check --format json`.
// - Progress and cancellation: progress notifications carry the token and
//   increase, ping answers during a long call, a cancelled translation stops
//   quickly without a response and leaves the server usable, and every other
//   request gets exactly one response.
const assert = require("assert");
const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");
const { McpClient } = require("./mcp-client");

const repositoryRoot = path.resolve(__dirname, "..", "..");

/** A fresh check from the command line, as a promise of its JSON */
function cliCheck(cliJar, root, paths) {
	return new Promise((resolve, reject) => {
		childProcess.execFile("java", ["-jar", cliJar, "--check", "--format", "json", ...paths],
			{ cwd: root, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 }, (error, stdout, stderr) => {
				if (error && error.code !== 1) {
					reject(new Error(`j2c --check failed (${error.code}): ${stderr}`));
					return;
				}
				resolve(JSON.parse(stdout));
			});
	});
}

function write(root, file, lines) {
	const target = path.join(root, file);
	fs.mkdirSync(path.dirname(target), { recursive: true });
	fs.writeFileSync(target, lines.join("\n") + "\n");
}

function edit(root, file, from, to) {
	const target = path.join(root, file);
	const text = fs.readFileSync(target, "utf8");
	assert(text.includes(from), `${file} does not contain ${from}`);
	fs.writeFileSync(target, text.replace(from, to));
}

/** The "re-checked N of M" lines the server logged */
function statsLines(client) {
	return client.stderr.split(/\r?\n/).filter((line) => line.includes("J2C: re-checked "));
}

async function checkIncremental(cliJar, workspace, env) {
	const root = path.join(workspace, "incremental");
	write(root, "src/shapes/Shape.java", [
		"package shapes;",
		"public abstract class Shape {",
		"\tpublic abstract double area();",
		"\tpublic String name() { return \"shape\"; }",
		"}"
	]);
	write(root, "src/shapes/Rect.java", [
		"package shapes;",
		"public class Rect extends Shape {",
		"\tprotected final double w, h;",
		"\tpublic Rect(double w, double h) { this.w = w; this.h = h; }",
		"\tpublic double area() { return w * h; }",
		"}"
	]);
	write(root, "src/shapes/Square.java", [
		"package shapes;",
		"public class Square extends Rect {",
		"\tpublic Square(double side) { super(side, side); }",
		"}"
	]);
	write(root, "src/shapes/Circle.java", [
		"package shapes;",
		"import java.util.function.DoubleUnaryOperator;",
		"public class Circle extends Shape {",
		"\tprivate final double r;",
		"\tpublic Circle(double r) { this.r = r; }",
		"\tpublic double area() {",
		"\t\tDoubleUnaryOperator square = x -> x * x;",
		"\t\treturn Math.PI * square.applyAsDouble(r);",
		"\t}",
		"}"
	]);
	// Util.java also declares Helper, which a lookup by name cannot find
	write(root, "src/app/Util.java", [
		"package app;",
		"public class Util {",
		"\tstatic int twice(int x) { return Helper.twice(x); }",
		"}",
		"class Helper {",
		"\tstatic int twice(int x) { return 2 * x; }",
		"}"
	]);
	// Main uses Shape's name() through Square and Rect, never naming Shape
	write(root, "src/app/Main.java", [
		"package app;",
		"import shapes.Circle;",
		"import shapes.Square;",
		"public class Main {",
		"\tpublic static void main(String[] args) {",
		"\t\tSquare square = new Square(2);",
		"\t\tSystem.out.println(square.name() + square.area());",
		"\t\tSystem.out.println(new Circle(1).area() + Util.twice(3) + Helper.twice(1));",
		"\t\tSystem.out.println(new Extra());",
		"\t}",
		"}"
	]);
	write(root, "src/app/Broken.java", [
		"package app;",
		"public class Broken {",
		"\tObject o = new Object() {",
		"\t\tRunnable r = () -> System.out.println(this);",
		"\t};",
		"}"
	]);

	const client = new McpClient(cliJar, root, { env });
	const selections = [["src"], [path.join("src", "app", "Main.java")]];
	let id = 0;
	const rechecked = [];
	async function compare(step) {
		const fresh = selections.map((paths) => cliCheck(cliJar, root, paths));
		for (const [index, paths] of selections.entries()) {
			const before = statsLines(client).length;
			client.call(++id, "j2c_check", { paths });
			const response = await client.response(id);
			assert.strictEqual(response.result.isError, false, JSON.stringify(response));
			assert.deepStrictEqual(response.result.structuredContent, await fresh[index],
				`${step}, paths ${paths.join(" ")}: MCP and a fresh check differ`);
			await client.until(() => statsLines(client).length > before, "the re-check statistics");
			const stats = /re-checked (\d+) of (\d+)/.exec(statsLines(client)[before]);
			rechecked.push({ step, paths: paths.join(" "), rechecked: +stats[1], selected: +stats[2],
				problems: response.result.structuredContent.problems.length });
		}
	}
	function last(step, paths) {
		return rechecked.find((entry) => entry.step === step && entry.paths === paths);
	}

	try {
		await compare("initial");
		assert(last("initial", "src").problems > 0, "Broken and the missing Extra are problems");
		// The same files again: nothing is compiled
		await compare("unchanged");
		assert.strictEqual(last("unchanged", "src").rechecked, 0);

		edit(root, "src/shapes/Circle.java", "Math.PI * square", "3.14 * square");
		await compare("body edit");
		// Circle, and Util, which a check from scratch always compiles alongside
		assert.strictEqual(last("body edit", "src").rechecked, 2);

		edit(root, "src/shapes/Circle.java", "return 3.14", "return undefinedName + 3.14");
		await compare("compile error in a body");

		edit(root, "src/shapes/Shape.java", "\tpublic String name() { return \"shape\"; }\n", "");
		await compare("method removed from an indirect superclass");
		assert(last("method removed from an indirect superclass", "src").problems
			> last("compile error in a body", "src").problems);

		edit(root, "src/shapes/Shape.java", "public abstract double area();",
			"public abstract double area();\n\tpublic String name() { return \"shape\"; }");
		await compare("method restored");

		write(root, "src/app/Extra.java", ["package app;", "public class Extra {}"]);
		await compare("file added");
		assert(last("file added", "src").rechecked < last("file added", "src").selected);

		fs.unlinkSync(path.join(root, "src", "app", "Extra.java"));
		await compare("file deleted");

		// Rename Circle to Round: Main still names Circle
		fs.unlinkSync(path.join(root, "src", "shapes", "Circle.java"));
		write(root, "src/shapes/Round.java", [
			"package shapes;",
			"public class Round extends Shape {",
			"\tpublic double area() { return 3; }",
			"}"
		]);
		await compare("type renamed");
		edit(root, "src/app/Main.java", "import shapes.Circle;", "import shapes.Round;");
		edit(root, "src/app/Main.java", "new Circle(1)", "new Round()");
		await compare("uses of the renamed type updated");

		// Another file with a secondary type, compiled alongside from now on
		write(root, "src/app/More.java", ["package app;", "class More {}", "class MoreHelper {}"]);
		await compare("secondary types added");

		// A qualified name whose package does not exist yet
		edit(root, "src/app/Main.java", "new Extra()", "new extra.pkg.Thing()");
		await compare("qualified name of a missing package");
		write(root, "src/extra/pkg/Thing.java", ["package extra.pkg;", "public class Thing {}"]);
		await compare("package added");

		edit(root, "src/shapes/Rect.java", "public double area() { return w * h; }",
			"public double area() { return w * h; ");
		await compare("syntax error");
		edit(root, "src/shapes/Rect.java", "return w * h; ", "return w * h; }");
		await compare("syntax error fixed");

		edit(root, "src/app/Broken.java", "System.out.println(this)", "System.out.println(o)");
		await compare("J2C problem edited");
		await client.close();
	} catch (error) {
		client.process.kill();
		throw error;
	}

	// --check-cache bounds the files kept: over the limit nothing is kept,
	// and 0 turns the cache off
	for (const [limit, expected] of [["3", /re-checked 9 of 9/], ["0", null]]) {
		const limited = new McpClient(cliJar, root, { env, args: ["--check-cache", limit] });
		try {
			for (const callId of [1, 2]) {
				limited.call(callId, "j2c_check", { paths: ["src"] });
				await limited.response(callId);
			}
			await limited.close();
		} catch (error) {
			limited.process.kill();
			throw error;
		}
		const lines = statsLines(limited);
		if (expected) {
			assert.strictEqual(lines.length, 2, limited.stderr);
			assert.match(lines[1], expected);
		} else {
			assert.deepStrictEqual(lines, []);
		}
	}
	return rechecked;
}

async function checkProgressAndCancellation(cliJar, workspace, env) {
	const root = path.join(workspace, "progress");
	fs.cpSync(path.join(repositoryRoot, "test", "src"), path.join(root, "src"), { recursive: true });
	fs.mkdirSync(path.join(root, "big out"));
	fs.mkdirSync(path.join(root, "small out"));
	write(root, "small/demo/Small.java", ["package demo;", "public class Small {}"]);

	const client = new McpClient(cliJar, root, { env });
	try {
		await client.request(1, "initialize", { protocolVersion: "2025-06-18", capabilities: {} });

		// A check reports its progress with the token, increasing
		client.call(2, "j2c_check", { paths: ["src"] }, { progressToken: "check-2" });
		const checked = await client.response(2);
		assert.strictEqual(checked.result.isError, false, JSON.stringify(checked));
		const checkProgress = client.progress("check-2");
		assert(checkProgress.length >= 2, `Progress notifications: ${checkProgress.length}`);
		for (let i = 1; i < checkProgress.length; ++i) {
			assert(checkProgress[i].params.progress > checkProgress[i - 1].params.progress,
				JSON.stringify(checkProgress.map((message) => message.params)));
		}
		for (const message of checkProgress) {
			assert(message.params.total === undefined || message.params.total >= message.params.progress);
			assert.strictEqual(typeof message.params.message, "string");
		}
		assert(checkProgress.some((message) => message.params.total > 100),
			"Counting the files of test/src");
		// Nothing after the response
		const responseIndex = client.messages.indexOf(checked);
		assert(checkProgress.every((message) => client.messages.indexOf(message) < responseIndex));

		// Ping answers while a translation runs
		client.call(3, "j2c_translate", { paths: ["src"], output: "big out" }, { progressToken: 3 });
		await client.until(() => client.progress(3).length >= 2, "translation progress");
		const ping = await client.request(4, "ping");
		assert.deepStrictEqual(ping.result, {});
		const translated = await client.response(3);
		assert(client.messages.indexOf(ping) < client.messages.indexOf(translated),
			"ping was answered before the running translation");
		assert.strictEqual(translated.result.isError, false, JSON.stringify(translated.result));
		assert(fs.existsSync(path.join(root, "big out", "Makefile")));
		const fullTime = translated.receivedAt - client.progress(3)[0].receivedAt;

		// Cancelling a running translation: no response, and it stops
		client.call(5, "j2c_translate", { paths: ["src"], output: "big out" }, { progressToken: "t5" });
		// Queued behind it, then cancelled before it starts
		client.call(6, "j2c_check", { paths: ["src"] });
		await client.until(() => client.progress("t5").length >= 3, "translation progress");
		const cancelledAt = Date.now();
		client.send({ jsonrpc: "2.0", method: "notifications/cancelled", params: { requestId: 5, reason: "test" } });
		client.send({ jsonrpc: "2.0", method: "notifications/cancelled", params: { requestId: 6 } });
		// Unknown and finished requests are ignored
		client.send({ jsonrpc: "2.0", method: "notifications/cancelled", params: { requestId: 99 } });
		client.send({ jsonrpc: "2.0", method: "notifications/cancelled", params: { requestId: 3 } });
		assert.deepStrictEqual((await client.request(7, "ping")).result, {});
		// Tool calls run one at a time, so this answers once 5 stopped
		client.call(8, "j2c_capabilities", {});
		const capabilities = await client.response(8);
		const stopTime = capabilities.receivedAt - cancelledAt;
		assert(stopTime < Math.max(2000, fullTime / 2),
			`The cancelled translation took ${stopTime} ms to stop (a full one ${fullTime} ms)`);
		const cancelledProgress = client.progress("t5");
		assert(cancelledProgress.every((message) => message.receivedAt <= capabilities.receivedAt));
		// The output directory: J2C's marker and the partial output, without
		// the Makefile, which is written last
		assert(fs.existsSync(path.join(root, "big out", ".j2c-output")));
		assert(!fs.existsSync(path.join(root, "big out", "Makefile")));

		// The server is still usable, and the partial output is replaced
		// without force
		client.call(9, "j2c_translate", { paths: ["small"], output: "big out" });
		const replaced = await client.response(9);
		assert.strictEqual(replaced.result.isError, false, JSON.stringify(replaced.result));
		assert(fs.existsSync(path.join(root, "big out", "Makefile")));

		// j2c_report runs a check, with the same progress
		client.call(10, "j2c_report", { paths: ["src"] }, { progressToken: "report" });
		const report = await client.response(10);
		assert.strictEqual(report.result.isError, false, JSON.stringify(report.result).slice(0, 2000));
		assert(client.progress("report").some((message) => message.params.total > 100),
			"Counting the files of test/src");
		await client.close();

		// Exactly one response per answered request, none for cancelled ones
		for (const answered of [1, 2, 3, 4, 7, 8, 9, 10]) {
			assert.strictEqual(client.responses(answered).length, 1, `Responses to ${answered}`);
		}
		assert.strictEqual(client.responses(5).length, 0, "A cancelled call gets no response");
		assert.strictEqual(client.responses(6).length, 0, "A cancelled queued call gets no response");
		assert.strictEqual(client.messages.filter((message) => !message.method).length, 8);
		return { fullTime, stopTime };
	} catch (error) {
		client.process.kill();
		throw error;
	}
}

/**
 * Cancelling while the build tool resolves the class path stops it: a
 * Gradle wrapper that takes a minute is stopped at once
 */
async function checkBuildToolCancellation(cliJar, workspace, env) {
	const root = path.join(workspace, "slow gradle");
	fs.cpSync(path.join(repositoryRoot, "test", "projects", "gradle-simple"), root, { recursive: true });
	if (process.platform === "win32") {
		fs.writeFileSync(path.join(root, "gradlew.bat"),
			["@echo off", "ping -n 60 127.0.0.1 >nul", "exit /b 1", ""].join("\r\n"));
	} else {
		fs.writeFileSync(path.join(root, "gradlew"), ["#!/bin/sh", "sleep 60", "exit 1", ""].join("\n"));
		fs.chmodSync(path.join(root, "gradlew"), 0o755);
	}
	const client = new McpClient(cliJar, root, {
		env: { ...env, J2C_CACHE_DIR: path.join(workspace, "slow gradle cache") }
	});
	try {
		client.call(1, "j2c_check", { paths: ["."] }, { progressToken: "gradle" });
		await client.until(() => client.progress("gradle").some((message) =>
			/class path with Gradle/.test(message.params.message)), "the build tool phase");
		const cancelledAt = Date.now();
		client.send({ jsonrpc: "2.0", method: "notifications/cancelled", params: { requestId: 1 } });
		client.call(2, "j2c_capabilities", {});
		const capabilities = await client.response(2);
		const stopTime = capabilities.receivedAt - cancelledAt;
		assert(stopTime < 15000, `Gradle was stopped after ${stopTime} ms`);
		await client.close();
		assert.strictEqual(client.responses(1).length, 0, "A cancelled call gets no response");
		return stopTime;
	} catch (error) {
		client.process.kill();
		throw error;
	}
}

/**
 * j2c_build reports each compiled file as progress, and a cancel stops make
 * and what it started (a stub make that waits a minute)
 */
async function checkBuildCancellation(cliJar, workspace, env) {
	const { writeFakeMake } = require("./test-cli-build");
	const root = path.join(workspace, "slow make");
	const output = path.join(root, "out");
	fs.mkdirSync(output, { recursive: true });
	fs.writeFileSync(path.join(output, ".j2c-output"), "");
	fs.writeFileSync(path.join(output, "Makefile"), "");
	const stub = path.join(workspace, "slow make stub");
	fs.mkdirSync(stub);
	const make = writeFakeMake(stub);
	const canned = path.join(stub, "output.txt");
	fs.writeFileSync(canned, "Compile src/demo/A.cpp\nCompile src/demo/B.cpp\n");
	const pidFile = path.join(stub, "make.pid");
	const client = new McpClient(cliJar, root, {
		env: { ...env, J2C_MAKE: make, J2C_WSL: "never", J2C_FAKE_MAKE_OUTPUT: canned,
			J2C_FAKE_MAKE_ARGS: path.join(stub, "args.json"), J2C_FAKE_MAKE_PID: pidFile }
	});
	try {
		client.call(1, "j2c_build", { output: "out" }, { progressToken: "make" });
		await client.until(() => client.progress("make").some((message) =>
			/^Compiled src\/demo\/[AB]\.cpp$/.test(message.params.message)), "a compiled file");
		const messages = client.progress("make").map((message) => message.params.message);
		// Progress is sent at most every 100 ms, so B may replace A
		assert(/^Running .* in /.test(messages[0]), JSON.stringify(messages));
		const pid = Number(fs.readFileSync(pidFile, "utf8"));
		const cancelledAt = Date.now();
		client.send({ jsonrpc: "2.0", method: "notifications/cancelled", params: { requestId: 1 } });
		client.call(2, "j2c_capabilities", {});
		const capabilities = await client.response(2);
		const stopTime = capabilities.receivedAt - cancelledAt;
		assert(stopTime < 15000, `make was stopped after ${stopTime} ms`);
		await client.close();
		assert.strictEqual(client.responses(1).length, 0, "A cancelled call gets no response");
		let running = true;
		try {
			process.kill(pid, 0);
		} catch (error) {
			running = false;
		}
		assert(!running, "The stub make was stopped");
		return stopTime;
	} catch (error) {
		client.process.kill();
		throw error;
	}
}

/** Before 2025-03-26, progress notifications have no message */
async function checkOldProgress(cliJar, workspace, env) {
	const root = path.join(workspace, "progress");
	const client = new McpClient(cliJar, root, { env });
	try {
		await client.request(1, "initialize", { protocolVersion: "2024-11-05", capabilities: {} });
		client.call(2, "j2c_check", { paths: ["small"] }, { progressToken: "old" });
		await client.response(2);
		assert(client.progress("old").length > 0);
		assert(client.progress("old").every((message) => message.params.message === undefined));
		await client.close();
	} catch (error) {
		client.process.kill();
		throw error;
	}
}

module.exports = {
	checkBuildCancellation,
	checkBuildToolCancellation,
	checkIncremental,
	checkOldProgress,
	checkProgressAndCancellation
};
