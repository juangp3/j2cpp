// j2c run checks for test-cli.js.
//
// A stub make writes a stub program (a Node.js script) for the main class it
// is asked to build, which records its arguments, working directory and
// standard input, prints canned output and exits with a canned code; the
// JVM side is real (javac in J2C's JDK, then its java). Checked: the same
// output as the JVM, a difference and its first line, standard input and an
// uncaught exception (stack frames ignored), choosing among several mains,
// --no-compare, a crash, the timeout (the stub must be gone afterwards), the
// output limit, a failed build and usage errors. A real run (make and g++,
// in WSL on Windows) of test/runtime/stdin with its input.txt, against the
// bundled runtime, must match the JVM; it is skipped without make.
const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

const repositoryRoot = path.resolve(__dirname, "..", "..");
const runtimePrograms = path.join(repositoryRoot, "test", "runtime");
const helloDirectory = path.join(runtimePrograms, "hello");
const isWindows = process.platform === "win32";

/** A command running a Node.js script: a .cmd wrapper on Windows, else a shell script */
function wrapperText(script) {
	return isWindows ? `@"${process.execPath}" "${script}" %*\r\n@exit /b %ERRORLEVEL%\r\n`
		: `#!/bin/sh\nexec "${process.execPath}" "${script}" "$@"\n`;
}

/**
 * A make that, for a bin/<path> target, writes bin/<path> running a stub
 * program (with J2C_FAKE_MAKE_FAIL it prints that file and exits with 2
 * instead), recording its arguments in J2C_FAKE_MAKE_ARGS. The program
 * records {args, cwd, stdin} in J2C_FAKE_PROGRAM_RECORD, prints
 * J2C_FAKE_PROGRAM_STDOUT and J2C_FAKE_PROGRAM_STDERR (by default what
 * test/runtime/hello prints) and exits with J2C_FAKE_PROGRAM_EXIT; with
 * J2C_FAKE_PROGRAM_PID it writes its process id there and waits a minute.
 */
function writeFakeRunTools(directory) {
	const program = path.join(directory, "fake-program.js");
	fs.writeFileSync(program, [
		"const fs = require(\"fs\");",
		"const args = process.argv.slice(2);",
		"const chunks = [];",
		"process.stdin.on(\"data\", (chunk) => chunks.push(chunk));",
		"process.stdin.on(\"end\", () => {",
		"\tif (process.env.J2C_FAKE_PROGRAM_RECORD) {",
		"\t\tfs.writeFileSync(process.env.J2C_FAKE_PROGRAM_RECORD, JSON.stringify({ args, cwd: process.cwd(),",
		"\t\t\tstdin: Buffer.concat(chunks).toString(\"utf8\") }));",
		"\t}",
		"\tconst read = (name, fallback) => process.env[name] ? fs.readFileSync(process.env[name], \"utf8\") : fallback;",
		"\tprocess.stdout.write(read(\"J2C_FAKE_PROGRAM_STDOUT\", [\"hi from translated Java\", ...args, \"\"].join(\"\\n\")));",
		"\tprocess.stderr.write(read(\"J2C_FAKE_PROGRAM_STDERR\", \"done\\n\"));",
		"\tif (process.env.J2C_FAKE_PROGRAM_PID) {",
		"\t\tfs.writeFileSync(process.env.J2C_FAKE_PROGRAM_PID, String(process.pid));",
		"\t\tsetTimeout(() => process.exit(0), 60000);",
		"\t} else {",
		"\t\tprocess.exitCode = Number(process.env.J2C_FAKE_PROGRAM_EXIT || 0);",
		"\t}",
		"});",
		""
	].join("\n"));
	const make = path.join(directory, "fake-make.js");
	fs.writeFileSync(make, [
		"const fs = require(\"fs\");",
		"const path = require(\"path\");",
		"const args = process.argv.slice(2);",
		"if (args.includes(\"--version\")) { console.log(\"GNU Make 4.3\"); process.exit(0); }",
		"fs.writeFileSync(process.env.J2C_FAKE_MAKE_ARGS, JSON.stringify(args));",
		"if (process.env.J2C_FAKE_MAKE_FAIL) {",
		"\tprocess.stdout.write(fs.readFileSync(process.env.J2C_FAKE_MAKE_FAIL, \"utf8\"));",
		"\tprocess.exit(2);",
		"}",
		"const target = args[args.length - 1];",
		"fs.mkdirSync(path.dirname(target), { recursive: true });",
		`const binary = target + ${JSON.stringify(isWindows ? ".cmd" : "")};`,
		`fs.writeFileSync(binary, ${JSON.stringify(wrapperText(program))});`,
		"fs.chmodSync(binary, 0o755);",
		"console.log(`Compile src/${path.basename(target)}.cpp`);",
		"process.exit(0);",
		""
	].join("\n"));
	const wrapper = path.join(directory, isWindows ? "fake-make.cmd" : "fake-make");
	fs.writeFileSync(wrapper, wrapperText(make));
	fs.chmodSync(wrapper, 0o755);
	return wrapper;
}

/** Waits until the process is gone; false if it still runs after 5 s */
function gone(pid) {
	for (let i = 0; i < 50; i++) {
		try {
			process.kill(pid, 0);
			Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 100);
		} catch (error) {
			return true;
		}
	}
	return false;
}

/** A program reading standard input, with a second main class */
function writeEchoProgram(directory) {
	fs.mkdirSync(directory, { recursive: true });
	fs.writeFileSync(path.join(directory, "Echo.java"), [
		"import java.io.BufferedReader;",
		"import java.io.IOException;",
		"import java.io.InputStreamReader;",
		"",
		"public class Echo {",
		"\tpublic static void main(String[] args) throws IOException {",
		"\t\tBufferedReader in = new BufferedReader(new InputStreamReader(System.in));",
		"\t\tfor (String line = in.readLine(); line != null; line = in.readLine()) {",
		"\t\t\tSystem.out.println(line.toUpperCase());",
		"\t\t}",
		"\t\tthrow new IllegalStateException(\"end of input\");",
		"\t}",
		"}",
		""
	].join("\n"));
	fs.writeFileSync(path.join(directory, "Other.java"), [
		"public class Other {",
		"\tpublic static void main(String[] args) {",
		"\t\tSystem.out.println(\"other\");",
		"\t}",
		"}",
		""
	].join("\n"));
}

function checkRunParsing(runCli, tempDirectory) {
	const output = path.join(tempDirectory, "run output");
	fs.mkdirSync(output);
	const stubDirectory = fs.mkdtempSync(path.join(os.tmpdir(), "j2c-run-"));
	try {
		const make = writeFakeRunTools(stubDirectory);
		const file = (name) => path.join(stubDirectory, name);
		const environment = { ...process.env, J2C_FAKE_MAKE_ARGS: file("make-args.json"),
			J2C_FAKE_PROGRAM_RECORD: file("record.json") };
		const run = (args, inputs = [helloDirectory], env = {}) => runCli(["run", "--output", output, "--make", make,
			"--wsl", "never", ...inputs, ...args], { env: { ...environment, ...env } });
		const record = () => JSON.parse(fs.readFileSync(file("record.json"), "utf8"));

		// The stub prints what Hello prints: the same as the JVM
		const same = run(["--format", "json", "--jobs", "2", "--", "one", "two words"]);
		assert.strictEqual(same.status, 0, same.stdout + same.stderr);
		const result = JSON.parse(same.stdout);
		assert.strictEqual(result.schemaVersion, 5);
		assert.strictEqual(result.command, "run");
		assert.strictEqual(result.status, "ok", JSON.stringify(result.comparison));
		assert.strictEqual(result.main, "Hello");
		assert.deepStrictEqual(result.mains, ["Hello"]);
		assert.deepStrictEqual(result.args, ["one", "two words"]);
		assert.strictEqual(result.timeout, 60);
		assert.strictEqual(result.outputLimit, 20000);
		assert.deepStrictEqual(JSON.parse(fs.readFileSync(file("make-args.json"), "utf8")),
			["-k", "-j2", "--output-sync=target", "bin/Hello"]);
		assert.strictEqual(result.build.status, "ok");
		assert.strictEqual(result.build.log, undefined, "No build log when the build succeeded");
		// Translated against the bundled runtime by default, with directives
		assert(fs.existsSync(path.join(output, "runtime", "j2c_runtime.cpp")));
		assert(fs.readFileSync(path.join(output, "src", "Hello.cpp"), "utf8").includes("#line "));
		assert.strictEqual(result.cpp.exitCode, 0);
		assert.strictEqual(result.cpp.signal, null);
		assert.strictEqual(result.cpp.timedOut, false);
		assert.strictEqual(result.cpp.stdout, "hi from translated Java\none\ntwo words\n");
		assert.strictEqual(result.cpp.stderr, "done\n");
		assert.strictEqual(result.cpp.stdoutBytes, Buffer.byteLength(result.cpp.stdout));
		assert.strictEqual(result.cpp.stdoutTruncated, false);
		assert.strictEqual(result.cpp.workingDirectory, path.join(output, "run", "cpp"));
		assert.deepStrictEqual(record(), { args: ["one", "two words"], cwd: path.join(output, "run", "cpp"), stdin: "" });
		assert.strictEqual(result.jvm.status, "ran", JSON.stringify(result.jvm));
		assert.strictEqual(result.jvm.wsl, false);
		assert.strictEqual(result.jvm.mainClass, "Hello");
		assert.deepStrictEqual(result.jvm.diagnostics, []);
		assert.strictEqual(result.jvm.stdout, result.cpp.stdout);
		assert.strictEqual(result.jvm.workingDirectory, path.join(output, "run", "jvm"));
		assert(fs.existsSync(path.join(output, "run", "classes", "Hello.class")));
		assert(result.jvm.command.includes("-Duser.language=en"));
		assert.deepStrictEqual(result.jvm.command.slice(-3), ["Hello", "one", "two words"]);
		assert.deepStrictEqual(result.comparison, {
			status: "equal", reason: null, exitCode: { equal: true, cpp: 0, jvm: 0 },
			stdout: { equal: true, firstDifference: null },
			stderr: { equal: true, firstDifference: null, stackFramesIgnored: true }
		});
		assert(!same.stdout.includes("Selected stats"), "Only JSON on stdout");

		const text = run(["--", "one", "two words"]);
		assert.strictEqual(text.status, 0, text.stderr);
		assert(text.stdout.startsWith("--- stdout (C++)\nhi from translated Java\none\ntwo words\n--- stderr (C++)\ndone\n"),
			text.stdout);
		assert(text.stdout.includes("J2C run: the same output and exit code as the JVM\n"), text.stdout);
		assert(text.stdout.endsWith("J2C run: ok\n"), text.stdout);

		// A difference: the first differing line of each stream
		fs.writeFileSync(file("differs.txt"), "hi from translated Java\nONE\ntwo words\n");
		const differs = run(["--format", "json", "--", "one", "two words"], [helloDirectory],
			{ J2C_FAKE_PROGRAM_STDOUT: file("differs.txt") });
		assert.strictEqual(differs.status, 1);
		const difference = JSON.parse(differs.stdout);
		assert.strictEqual(difference.status, "differs");
		assert.strictEqual(difference.comparison.status, "differs");
		assert.deepStrictEqual(difference.comparison.stdout, { equal: false, firstDifference: { line: 2, cpp: "ONE", jvm: "one" } });
		assert.strictEqual(difference.comparison.stderr.equal, true);
		const differsText = run(["--", "one"], [helloDirectory], { J2C_FAKE_PROGRAM_STDOUT: file("differs.txt") });
		assert.strictEqual(differsText.status, 1);
		assert(differsText.stdout.includes("J2C run: stdout differs from the JVM's at line 2:\n  C++: \"ONE\"\n  JVM: \"one\"\n"),
			differsText.stdout);
		// A missing line, and a different exit code
		fs.writeFileSync(file("short.txt"), "hi from translated Java\n");
		const shorter = JSON.parse(run(["--format", "json", "--", "one"], [helloDirectory],
			{ J2C_FAKE_PROGRAM_STDOUT: file("short.txt"), J2C_FAKE_PROGRAM_EXIT: "3" }).stdout);
		assert.deepStrictEqual(shorter.comparison.stdout.firstDifference, { line: 2, cpp: "", jvm: "one" });
		assert.deepStrictEqual(shorter.comparison.exitCode, { equal: false, cpp: 3, jvm: 0 });

		// Standard input, an uncaught exception (the JVM's stack frames are
		// ignored), and --main among several classes with one
		const echoDirectory = path.join(tempDirectory, "run echo");
		writeEchoProgram(echoDirectory);
		fs.writeFileSync(file("input.txt"), "first line\nsecond é\n");
		fs.writeFileSync(file("echo-out.txt"), "FIRST LINE\nSECOND É\n");
		fs.writeFileSync(file("echo-err.txt"), "Exception in thread \"main\" java.lang.IllegalStateException: end of input\n");
		const echoEnv = { J2C_FAKE_PROGRAM_STDOUT: file("echo-out.txt"), J2C_FAKE_PROGRAM_STDERR: file("echo-err.txt"),
			J2C_FAKE_PROGRAM_EXIT: "1" };
		const echo = run(["--format", "json", "--main", "Echo", "--stdin", file("input.txt")], [echoDirectory], echoEnv);
		const echoed = JSON.parse(echo.stdout);
		assert.strictEqual(echoed.status, "ok", JSON.stringify(echoed));
		assert.strictEqual(echo.status, 0);
		assert.deepStrictEqual(echoed.mains, ["Echo", "Other"]);
		assert.strictEqual(echoed.main, "Echo");
		assert.strictEqual(echoed.stdinBytes, Buffer.byteLength("first line\nsecond é\n"));
		assert.strictEqual(record().stdin, "first line\nsecond é\n");
		assert.deepStrictEqual(JSON.parse(fs.readFileSync(file("make-args.json"), "utf8")).slice(-1), ["bin/Echo"]);
		assert.strictEqual(echoed.jvm.exitCode, 1);
		assert.strictEqual(echoed.jvm.stdout, "FIRST LINE\nSECOND É\n");
		assert.match(echoed.jvm.stderr, /^Exception in thread "main" java.lang.IllegalStateException: end of input\n\tat Echo.main\(/);
		assert.strictEqual(echoed.comparison.status, "equal");
		const several = run(["--format", "json"], [echoDirectory], echoEnv);
		assert.strictEqual(several.status, 2);
		assert(JSON.parse(several.stdout).message.includes("Several classes have a main method"), several.stdout);
		assert(JSON.parse(several.stdout).message.includes("[Echo, Other]"), several.stdout);
		const unknown = run(["--format", "json", "--main", "Nope"], [echoDirectory], echoEnv);
		assert.strictEqual(unknown.status, 2);
		assert(JSON.parse(unknown.stdout).message.includes("The main class Nope has no main method"), unknown.stdout);

		// --no-compare: only the C++ program, whose exit code decides
		const alone = JSON.parse(run(["--format", "json", "--no-compare"]).stdout);
		assert.strictEqual(alone.status, "ok");
		assert.strictEqual(alone.jvm, null);
		assert.strictEqual(alone.comparison.status, "skipped");
		const failing = run(["--format", "json", "--no-compare"], [helloDirectory], { J2C_FAKE_PROGRAM_EXIT: "3" });
		assert.strictEqual(failing.status, 1);
		assert.strictEqual(JSON.parse(failing.stdout).status, "failed");
		// A crash: 128 + the signal, where the program runs on Linux
		const crash = JSON.parse(run(["--format", "json"], [helloDirectory], { J2C_FAKE_PROGRAM_EXIT: "139" }).stdout);
		assert.strictEqual(crash.cpp.exitCode, 139);
		if (!isWindows) {
			assert.strictEqual(crash.status, "crashed");
			assert.strictEqual(crash.cpp.signal, "SIGSEGV");
		} else {
			assert.strictEqual(crash.status, "differs");
		}

		// The output limit truncates what is returned, not what is compared
		const limited = JSON.parse(run(["--format", "json", "--output-limit", "5", "--", "one"]).stdout);
		assert.strictEqual(limited.status, "ok");
		assert.strictEqual(limited.cpp.stdout, "hi fr");
		assert.strictEqual(limited.cpp.stdoutTruncated, true);
		assert.strictEqual(limited.cpp.stdoutBytes, 28);
		assert.strictEqual(limited.jvm.stdout, "hi fr");

		// The timeout stops the program and what it started
		const pidFile = file("program.pid");
		const started = Date.now();
		const hung = run(["--format", "json", "--timeout", "2", "--no-compare"], [helloDirectory],
			{ J2C_FAKE_PROGRAM_PID: pidFile });
		assert.strictEqual(hung.status, 1);
		const hungResult = JSON.parse(hung.stdout);
		assert.strictEqual(hungResult.status, "timeout");
		assert.strictEqual(hungResult.cpp.timedOut, true);
		assert.strictEqual(hungResult.cpp.stdout, "hi from translated Java\n", "Output before the timeout is kept");
		assert(Date.now() - started < 50000, "The program was not stopped");
		assert(gone(Number(fs.readFileSync(pidFile, "utf8"))), "The stub program still runs after the timeout");

		// A failed build: its diagnostics and log, and no run
		fs.writeFileSync(file("make-fail.txt"), [
			"Compile src/Hello.cpp",
			"src/Hello.cpp:3:10: error: 'x' was not declared in this scope",
			"make: *** [Makefile:90: obj/Hello.o] Error 1",
			""
		].join("\n"));
		const broken = run(["--format", "json"], [helloDirectory], { J2C_FAKE_MAKE_FAIL: file("make-fail.txt") });
		assert.strictEqual(broken.status, 1);
		const brokenResult = JSON.parse(broken.stdout);
		assert.strictEqual(brokenResult.status, "build-failed");
		assert.strictEqual(brokenResult.cpp, null);
		assert.strictEqual(brokenResult.jvm, null);
		assert.strictEqual(brokenResult.comparison, null);
		assert.strictEqual(brokenResult.build.exitCode, 2);
		assert.strictEqual(brokenResult.build.diagnostics[0].message, "'x' was not declared in this scope");
		assert(brokenResult.build.log.some((line) => line.includes("Error 1")));
		const brokenText = run([], [helloDirectory], { J2C_FAKE_MAKE_FAIL: file("make-fail.txt") });
		assert(brokenText.stdout.includes("J2C run: the build failed: make exited with 2, 1 error(s)"), brokenText.stdout);
	} finally {
		fs.rmSync(stubDirectory, { recursive: true, force: true });
	}

	// Usage errors
	assert.strictEqual(runCli(["run", helloDirectory]).status, 2, "--output is required");
	assert(runCli(["run", "--output", output, "--tests", helloDirectory]).stderr.includes("Unknown run option: --tests"));
	assert(runCli(["run", "--output", output, "--target", "all", helloDirectory]).stderr.includes("Unknown run option: --target"));
	assert(runCli(["run", "--output", output, "--timeout", "-1", helloDirectory]).stderr.includes("Invalid --timeout value: -1"));
	assert(runCli(["run", "--output", output, "--output-limit", "x", helloDirectory]).stderr.includes("Invalid --output-limit value: x"));
	assert(runCli(["run", "--output", output, "--stdin", path.join(output, "missing.txt"), helloDirectory]).stderr
		.includes("--stdin file does not exist"));
	const noMain = runCli(["run", "--format", "json", "--output", output, "--wsl", "never",
		path.join(repositoryRoot, "test", "build", "src")]);
	assert.strictEqual(noMain.status, 2);
	assert(JSON.parse(noMain.stdout).message.startsWith("No class with a main method"), noMain.stdout);
}

/**
 * A real build and run of test/runtime/stdin (with hello beside it, so the
 * main class must be chosen) against the bundled runtime, compared with the
 * JVM
 */
function checkRealRun(runCli, tempDirectory) {
	const output = path.join(tempDirectory, "real run output");
	fs.mkdirSync(output);
	const environment = { ...process.env };
	if (isWindows && !environment.J2C_WSL_DISTRO) {
		environment.J2C_WSL_DISTRO = "Ubuntu";
	}
	const stdinDirectory = path.join(runtimePrograms, "stdin");
	const run = runCli(["run", "--format", "json", "--output", output, "--name", "stdin", "--main", "Stdin",
		"--stdin", path.join(stdinDirectory, "input.txt"), stdinDirectory, helloDirectory], { env: environment, maxBuffer: 1 << 26 });
	const result = JSON.parse(run.stdout);
	if (result.status === "error" && /^Could not run make/.test(result.message)) {
		console.log(`j2c run with a real build skipped: ${result.message}`);
		return;
	}
	assert.strictEqual(result.status, "ok", JSON.stringify(result).slice(0, 4000));
	assert.strictEqual(run.status, 0);
	assert.deepStrictEqual(result.mains, ["Hello", "Stdin"]);
	assert.strictEqual(result.comparison.status, "equal", JSON.stringify(result.comparison));
	// The program ends with an uncaught InputMismatchException
	assert.strictEqual(result.cpp.exitCode, 1);
	assert.strictEqual(result.cpp.stderr, "Exception in thread \"main\" java.util.InputMismatchException\n");
	assert(result.cpp.stdout.startsWith("sum=60 average=20.00\n"), result.cpp.stdout.slice(0, 200));
	console.log(`j2c run built and ran test/runtime/stdin (${result.cpp.durationMillis} ms; the JVM ${result.jvm.durationMillis} ms`
		+ `${result.jvm.wsl ? ", in WSL" : ""}); the output and exit code match`);
}

function checkRun(runCli, tempDirectory) {
	checkRunParsing(runCli, tempDirectory);
	checkRealRun(runCli, tempDirectory);
}

module.exports = { checkRun, checkRunParsing, checkRealRun, writeFakeRunTools, gone };
