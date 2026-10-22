// j2c test checks for test-cli.js.
//
// Parsing is checked with a stub make, which writes a stub GoogleTest binary
// that prints canned console output and writes a canned JSON report: results
// mapped to Java methods, files and lines, --filter, a crash without a
// report, the timeout (the stub's process must be gone afterwards), a failed
// build and usage errors. A real run (make, g++ and GoogleTest, in WSL on
// Windows) of test/junit and test/junit-runtime against the J2C runtime
// compares every outcome and message with expected-results.json; it is
// skipped with a message without make or GoogleTest.
const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { compare, readExpected } = require("./check-gtest");

const repositoryRoot = path.resolve(__dirname, "..", "..");
const junitDirectory = path.join(repositoryRoot, "test", "junit");
const junitRuntimeDirectory = path.join(repositoryRoot, "test", "junit-runtime");
const junitApi = path.join(repositoryRoot, "test", "junit-api");
const fixturePackage = path.join(junitDirectory, "se", "arnetheduck", "j2c", "test", "junit");
const isWindows = process.platform === "win32";
const suitePrefix = "se_arnetheduck_j2c_test_junit_";
const outcomes = { passed: "pass", failed: "fail", skipped: "skip", disabled: "skip" };

/** The line (1-based) of the first line of file containing text */
function lineOf(file, text) {
	const index = fs.readFileSync(file, "utf8").split(/\r?\n/).findIndex((line) => line.includes(text));
	assert(index >= 0, `${text} not in ${file}`);
	return index + 1;
}

/** A command running a Node.js script: a .cmd wrapper on Windows, else a shell script */
function writeWrapper(file, script) {
	if (isWindows) {
		fs.writeFileSync(`${file}.cmd`, `@"${process.execPath}" "${script}" %*\r\n@exit /b %ERRORLEVEL%\r\n`);
		return `${file}.cmd`;
	}
	fs.writeFileSync(file, `#!/bin/sh\nexec "${process.execPath}" "${script}" "$@"\n`);
	fs.chmodSync(file, 0o755);
	return file;
}

/**
 * A make that, for the tests target, writes bin/<NAME>-tests running a stub
 * GoogleTest binary (with J2C_FAKE_MAKE_FAIL it prints that file and exits
 * with 2 instead), and a stub binary that records its arguments in
 * J2C_FAKE_GTEST_ARGS, prints J2C_FAKE_GTEST_CONSOLE, writes
 * J2C_FAKE_GTEST_REPORT to its --gtest_output (unless J2C_FAKE_GTEST_NO_REPORT)
 * and exits with J2C_FAKE_GTEST_EXIT; with J2C_FAKE_GTEST_PID it writes its
 * process id there and waits a minute
 */
function writeFakeTools(directory) {
	const gtest = path.join(directory, "fake-gtest.js");
	fs.writeFileSync(gtest, [
		"const fs = require(\"fs\");",
		"const args = process.argv.slice(2);",
		"fs.writeFileSync(process.env.J2C_FAKE_GTEST_ARGS, JSON.stringify(args));",
		"const text = fs.readFileSync(process.env.J2C_FAKE_GTEST_CONSOLE, \"utf8\");",
		"const output = args.find((arg) => arg.startsWith(\"--gtest_output=json:\"));",
		"if (!process.env.J2C_FAKE_GTEST_NO_REPORT) {",
		"\tfs.writeFileSync(output.slice(\"--gtest_output=json:\".length), fs.readFileSync(process.env.J2C_FAKE_GTEST_REPORT));",
		"}",
		"if (process.env.J2C_FAKE_GTEST_PID) {",
		"\tfs.writeFileSync(process.env.J2C_FAKE_GTEST_PID, String(process.pid));",
		"\t// Later than the progress sent when the tests start",
		"\tsetTimeout(() => process.stdout.write(text), 500);",
		"\tsetTimeout(() => process.exit(0), 60000);",
		"} else {",
		"\tprocess.stdout.write(text);",
		"\tprocess.exit(Number(process.env.J2C_FAKE_GTEST_EXIT || 1));",
		"}",
		""
	].join("\n"));
	const make = path.join(directory, "fake-make.js");
	fs.writeFileSync(make, [
		"const fs = require(\"fs\");",
		"const path = require(\"path\");",
		`const isWindows = ${isWindows};`,
		"const args = process.argv.slice(2);",
		"if (args.includes(\"--version\")) { console.log(\"GNU Make 4.3\"); process.exit(0); }",
		"fs.writeFileSync(process.env.J2C_FAKE_MAKE_ARGS, JSON.stringify(args));",
		"if (process.env.J2C_FAKE_MAKE_FAIL) {",
		"\tprocess.stdout.write(fs.readFileSync(process.env.J2C_FAKE_MAKE_FAIL, \"utf8\"));",
		"\tprocess.exit(2);",
		"}",
		"const name = fs.readFileSync(\"Makefile\", \"utf8\").match(/^NAME := (.+)$/m)[1].trim();",
		"fs.mkdirSync(\"bin\", { recursive: true });",
		"const binary = path.join(\"bin\", `${name}-tests`);",
		"if (isWindows) {",
		`\tfs.writeFileSync(\`\${binary}.cmd\`, ${JSON.stringify(`@"${process.execPath}" "${gtest}" %*\r\n@exit /b %ERRORLEVEL%\r\n`)});`,
		"} else {",
		`\tfs.writeFileSync(binary, ${JSON.stringify(`#!/bin/sh\nexec "${process.execPath}" "${gtest}" "$@"\n`)});`,
		"\tfs.chmodSync(binary, 0o755);",
		"}",
		"console.log(\"Compile test/se/arnetheduck/j2c/test/junit/DerivedTest_test.cpp\");",
		"process.exit(0);",
		""
	].join("\n"));
	return writeWrapper(path.join(directory, "fake-make"), make);
}

/** A GoogleTest JSON report's test entry */
function gtestEntry(name, result, failures) {
	const entry = { name, file: "test/x_test.cpp", line: 1, status: result === "NOTRUN" ? "NOTRUN" : "RUN",
		result: result === "NOTRUN" ? "SUPPRESSED" : result, time: "0.012s", classname: "x" };
	if (failures) {
		entry.failures = failures.map((text) => ({ failure: `test/x_test.cpp:9\nFailed\n${text}\n`, type: "" }));
	}
	return entry;
}

/** The canned report, and the console output that goes with it */
function cannedRun() {
	const report = {
		tests: 7,
		testsuites: [
			{ name: `${suitePrefix}DerivedTest`, testsuite: [gtestEntry("inheritedTest", "COMPLETED")] },
			{ name: `${suitePrefix}Java17AssertionsTest`, testsuite: [
				gtestEntry("primitiveMismatchFails", "COMPLETED", ["Test threw java.lang.AssertionError: expected: <4> but was: <5>"]),
				gtestEntry("failFails", "COMPLETED", ["Test threw java.lang.AssertionError: line one\nline two",
					"@AfterEach threw java.lang.IllegalStateException"]),
				gtestEntry("DISABLED_native", "NOTRUN")
			] },
			{ name: `${suitePrefix}Java17JUnit4Test`, testsuite: [
				gtestEntry("expectedMissing", "COMPLETED", ["Expected exception: java.lang.IllegalArgumentException"])
			] },
			{ name: `${suitePrefix}Java17JUnit5Test`, testsuite: [
				gtestEntry("disabledWithReason", "SKIPPED"),
				gtestEntry("unexpectedException", "COMPLETED", ["Test threw java.lang.UnsupportedOperationException"])
			] },
			{ name: `${suitePrefix}Java17Assert4Test`, testsuite: [gtestEntry("assumptionSkips", "SKIPPED")] }
		]
	};
	const consoleText = [
		"[==========] Running 7 tests from 5 test suites.",
		`[ RUN      ] ${suitePrefix}DerivedTest.inheritedTest`,
		`[       OK ] ${suitePrefix}DerivedTest.inheritedTest (12 ms)`,
		`[ RUN      ] ${suitePrefix}Java17JUnit5Test.disabledWithReason`,
		"test/x_test.cpp:20: Skipped",
		"Disabled in Java: not ready",
		"",
		`[  SKIPPED ] ${suitePrefix}Java17JUnit5Test.disabledWithReason (0 ms)`,
		`[ RUN      ] ${suitePrefix}Java17Assert4Test.assumptionSkips`,
		"test/x_test.cpp:30: Skipped",
		"Assumption failed",
		"",
		`[  SKIPPED ] ${suitePrefix}Java17Assert4Test.assumptionSkips (0 ms)`,
		"[==========] 7 tests from 5 test suites ran. (12 ms total)",
		""
	].join("\n");
	return { report, consoleText };
}

function checkTestParsing(runCli, tempDirectory) {
	const output = path.join(tempDirectory, "test parse output");
	fs.mkdirSync(output);
	const stubDirectory = fs.mkdtempSync(path.join(os.tmpdir(), "j2c-test-"));
	try {
		const make = writeFakeTools(stubDirectory);
		const file = (name) => path.join(stubDirectory, name);
		const { report, consoleText } = cannedRun();
		fs.writeFileSync(file("report.json"), JSON.stringify(report));
		fs.writeFileSync(file("console.txt"), consoleText);
		const environment = { ...process.env, J2C_FAKE_MAKE_ARGS: file("make-args.json"),
			J2C_FAKE_GTEST_ARGS: file("gtest-args.json"), J2C_FAKE_GTEST_CONSOLE: file("console.txt"),
			J2C_FAKE_GTEST_REPORT: file("report.json") };
		const inputs = ["--sourcepath", [junitDirectory, junitApi].join(path.delimiter), junitDirectory];
		const test = (args, env = environment) => runCli(["test", "--output", output, "--make", make,
			"--wsl", "never", "--name", "stubbed", ...args, ...inputs], { env });

		const run = test(["--format", "json", "--jobs", "2"]);
		assert.strictEqual(run.status, 1, run.stderr);
		const result = JSON.parse(run.stdout);
		assert.strictEqual(result.schemaVersion, 5);
		assert.strictEqual(result.command, "test");
		assert.strictEqual(result.status, "failed");
		assert.strictEqual(result.exitCode, 1);
		assert.strictEqual(result.reportRead, true);
		assert.strictEqual(result.timedOut, false);
		assert.strictEqual(result.timeout, 300);
		assert.strictEqual(result.gtestFilter, null);
		assert(!run.stdout.includes("Selected stats"), "Only JSON on stdout");
		assert(run.stderr.includes("[ RUN      ]"), "The tests' output goes to stderr");
		assert.deepStrictEqual(JSON.parse(fs.readFileSync(file("make-args.json"), "utf8")),
			["-k", "-j2", "--output-sync=target", "tests"]);
		const gtestArgs = JSON.parse(fs.readFileSync(file("gtest-args.json"), "utf8"));
		assert.deepStrictEqual(gtestArgs.slice(1), ["--gtest_color=no"]);
		assert(gtestArgs[0].startsWith("--gtest_output=json:") && gtestArgs[0].endsWith(path.join("bin", "stubbed-tests.json")), gtestArgs[0]);
		assert(result.testFiles.includes("test/se/arnetheduck/j2c/test/junit/DerivedTest_test.cpp"));
		assert.deepStrictEqual(result.build.diagnostics, []);
		assert.strictEqual(result.build.status, "ok");
		assert.strictEqual(result.build.log, undefined, "No build log when the build succeeded");
		assert.deepStrictEqual(result.summary, { total: 7, passed: 1, failed: 4, skipped: 1, disabled: 1, notRun: 0,
			timeMillis: 84, exitCode: 1 });

		const byName = new Map(result.tests.map((entry) => [entry.cppTest.slice(suitePrefix.length), entry]));
		const derivedJava = path.join(fixturePackage, "DerivedTest.java");
		const baseJava = path.join(fixturePackage, "AbstractBaseTest.java");
		// An inherited test: run in the subclass, declared in the base class
		assert.deepStrictEqual(byName.get("DerivedTest.inheritedTest"), {
			javaClass: "se.arnetheduck.j2c.test.junit.DerivedTest",
			javaMethod: "inheritedTest",
			declaringClass: "se.arnetheduck.j2c.test.junit.AbstractBaseTest",
			file: baseJava,
			line: lineOf(baseJava, "void inheritedTest()"),
			status: "passed",
			message: null,
			exception: null,
			failure: null,
			disabledReason: null,
			timeMillis: 12,
			cppTest: `${suitePrefix}DerivedTest.inheritedTest`,
			cppFile: "test/se/arnetheduck/j2c/test/junit/DerivedTest_test.cpp"
		});
		assert(fs.existsSync(derivedJava));
		const assertionsJava = path.join(fixturePackage, "Java17AssertionsTest.java");
		const mismatch = byName.get("Java17AssertionsTest.primitiveMismatchFails");
		assert.strictEqual(mismatch.status, "failed");
		assert.strictEqual(mismatch.message, "expected: <4> but was: <5>");
		assert.strictEqual(mismatch.exception, "java.lang.AssertionError");
		assert.strictEqual(mismatch.failure, "Test threw java.lang.AssertionError: expected: <4> but was: <5>");
		assert.strictEqual(mismatch.file, assertionsJava);
		assert.strictEqual(mismatch.line, lineOf(assertionsJava, "void primitiveMismatchFails()"));
		// Several failures: the message is the first one's, the failure all of them
		const twice = byName.get("Java17AssertionsTest.failFails");
		assert.strictEqual(twice.message, "line one\nline two");
		assert.strictEqual(twice.failure, "Test threw java.lang.AssertionError: line one\nline two\n\n"
			+ "@AfterEach threw java.lang.IllegalStateException");
		// JUnit 4's expected exceptions have JUnit's message and no exception
		const missing = byName.get("Java17JUnit4Test.expectedMissing");
		assert.strictEqual(missing.message, "Expected exception: java.lang.IllegalArgumentException");
		assert.strictEqual(missing.exception, null);
		const unexpected = byName.get("Java17JUnit5Test.unexpectedException");
		assert.strictEqual(unexpected.message, "");
		assert.strictEqual(unexpected.exception, "java.lang.UnsupportedOperationException");
		const disabled = byName.get("Java17JUnit5Test.disabledWithReason");
		assert.strictEqual(disabled.status, "disabled");
		assert.strictEqual(disabled.message, "not ready");
		assert.strictEqual(disabled.disabledReason, "not ready");
		const assumption = byName.get("Java17Assert4Test.assumptionSkips");
		assert.strictEqual(assumption.status, "skipped");
		assert.strictEqual(assumption.message, "Assumption failed", "Skip messages come from the console");
		assert(!byName.has("Java17AssertionsTest.DISABLED_native"), "GoogleTest's own disabled tests are left out");

		// Text: the tests that did not pass, at their Java lines
		const text = test([]);
		assert.strictEqual(text.status, 1, text.stderr);
		assert(text.stdout.includes(`${assertionsJava}:${mismatch.line}: failed: se.arnetheduck.j2c.test.junit.Java17AssertionsTest.primitiveMismatchFails: expected: <4> but was: <5>`),
			text.stdout);
		assert(text.stdout.includes(": disabled: se.arnetheduck.j2c.test.junit.Java17JUnit5Test.disabledWithReason: not ready"), text.stdout);
		assert(text.stdout.includes("J2C test: 1 passed, 4 failed, 1 skipped, 1 disabled (exit code 1)"), text.stdout);
		assert(!text.stdout.includes("inheritedTest"), "Passed tests are not listed");

		// Every test passed: exit code 0
		const passing = { tests: 1, testsuites: [report.testsuites[0]] };
		fs.writeFileSync(file("passing.json"), JSON.stringify(passing));
		const ok = test(["--format", "json"], { ...environment, J2C_FAKE_GTEST_REPORT: file("passing.json"), J2C_FAKE_GTEST_EXIT: "0" });
		assert.strictEqual(ok.status, 0, ok.stderr);
		assert.strictEqual(JSON.parse(ok.stdout).status, "ok");

		// --filter: Java names to --gtest_filter, whole classes as Suite.*
		const filtered = test(["--format", "json", "--filter", "DerivedTest,se.arnetheduck.j2c.test.junit.Java17AssertionsTest.primitiveMismatchFails",
			"--filter", "Java17JUnit4Test#ignored, lifecycleRan"]);
		const filteredResult = JSON.parse(filtered.stdout);
		const expectedFilter = [`${suitePrefix}DerivedTest.*`, `${suitePrefix}Java17AssertionsTest.primitiveMismatchFails`,
			`${suitePrefix}Java17JUnit4Test.ignored`, `${suitePrefix}Java17JUnit4Test.lifecycleRan`];
		assert.deepStrictEqual(filteredResult.gtestFilter.split(":").sort(), expectedFilter.sort());
		assert(JSON.parse(fs.readFileSync(file("gtest-args.json"), "utf8")).includes(`--gtest_filter=${filteredResult.gtestFilter}`));
		const unknown = test(["--format", "json", "--filter", "NoSuchTest"]);
		assert.strictEqual(unknown.status, 2);
		assert.strictEqual(JSON.parse(unknown.stdout).status, "usage-error");
		assert(JSON.parse(unknown.stdout).message.includes("NoSuchTest matches no JUnit test"));

		// A crash: no report, the console's results, the rest not run
		const crashConsole = [
			`[ RUN      ] ${suitePrefix}DerivedTest.inheritedTest`,
			`[       OK ] ${suitePrefix}DerivedTest.inheritedTest (3 ms)`,
			`[ RUN      ] ${suitePrefix}DerivedTest.overriddenTest`,
			""
		].join("\n");
		fs.writeFileSync(file("crash.txt"), crashConsole);
		const crashed = test(["--format", "json", "--filter", "DerivedTest,Java17JUnit5Test"], { ...environment,
			J2C_FAKE_GTEST_CONSOLE: file("crash.txt"), J2C_FAKE_GTEST_NO_REPORT: "1", J2C_FAKE_GTEST_EXIT: "134" });
		assert.strictEqual(crashed.status, 1);
		const crash = JSON.parse(crashed.stdout);
		assert.strictEqual(crash.status, "failed");
		assert.strictEqual(crash.reportRead, false);
		assert.strictEqual(crash.exitCode, 134);
		const crashTests = new Map(crash.tests.map((entry) => [entry.cppTest.slice(suitePrefix.length), entry]));
		assert.strictEqual(crashTests.get("DerivedTest.inheritedTest").status, "passed");
		assert.strictEqual(crashTests.get("DerivedTest.inheritedTest").timeMillis, 3);
		assert.strictEqual(crashTests.get("DerivedTest.overriddenTest").status, "failed");
		assert.strictEqual(crashTests.get("DerivedTest.overriddenTest").message,
			"The test process ended (exit code 134) before this test finished");
		assert.strictEqual(crashTests.get("Java17JUnit5Test.beforeEachRan").status, "notRun");
		assert.strictEqual(crash.summary.notRun, crash.tests.length - 2);

		// The timeout stops the test process and what it started
		const pidFile = file("gtest.pid");
		const started = Date.now();
		const hung = test(["--format", "json", "--timeout", "2", "--filter", "DerivedTest"], { ...environment,
			J2C_FAKE_GTEST_CONSOLE: file("crash.txt"), J2C_FAKE_GTEST_NO_REPORT: "1", J2C_FAKE_GTEST_PID: pidFile });
		const hungResult = JSON.parse(hung.stdout);
		assert.strictEqual(hung.status, 1);
		assert.strictEqual(hungResult.status, "timeout");
		assert.strictEqual(hungResult.timedOut, true);
		assert.strictEqual(hungResult.timeout, 2);
		assert.match(hungResult.tests.find((entry) => entry.javaMethod === "overriddenTest").message,
			/^Did not finish within the timeout of 2 s/);
		assert(Date.now() - started < 50000, "The tests were not stopped");
		const pid = Number(fs.readFileSync(pidFile, "utf8"));
		let alive = true;
		for (let i = 0; i < 50 && alive; i++) {
			try {
				process.kill(pid, 0);
				Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 100);
			} catch (error) {
				alive = false;
			}
		}
		assert(!alive, `The stub test binary ${pid} still runs after the timeout`);

		// A failed build: its diagnostics, its log, and no tests
		fs.writeFileSync(file("make-fail.txt"), [
			"Compile test/se/arnetheduck/j2c/test/junit/DerivedTest_test.cpp",
			"test/se/arnetheduck/j2c/test/junit/DerivedTest_test.cpp:2:10: fatal error: gtest/gtest.h: No such file or directory",
			"make: *** [Makefile:134: obj/test/x.o] Error 1",
			""
		].join("\n"));
		const broken = test(["--format", "json"], { ...environment, J2C_FAKE_MAKE_FAIL: file("make-fail.txt") });
		assert.strictEqual(broken.status, 1);
		const brokenResult = JSON.parse(broken.stdout);
		assert.strictEqual(brokenResult.status, "build-failed");
		assert.strictEqual(brokenResult.exitCode, null);
		assert.deepStrictEqual(brokenResult.tests, []);
		assert.strictEqual(brokenResult.build.exitCode, 2);
		assert.strictEqual(brokenResult.build.diagnostics.length, 1);
		assert.strictEqual(brokenResult.build.diagnostics[0].message, "gtest/gtest.h: No such file or directory");
		assert(brokenResult.build.log.some((line) => line.includes("Error 1")));
		const brokenText = test([], { ...environment, J2C_FAKE_MAKE_FAIL: file("make-fail.txt") });
		assert(brokenText.stdout.includes("J2C test: the build failed: make exited with 2, 1 error(s)"), brokenText.stdout);
	} finally {
		fs.rmSync(stubDirectory, { recursive: true, force: true });
	}

	// Usage errors
	assert.strictEqual(runCli(["test", junitDirectory]).status, 2, "--output is required");
	assert(runCli(["test", "--output", output, "--target", "all", junitDirectory]).stderr.includes("Unknown test option: --target"));
	assert(runCli(["test", "--output", output, "--tests", junitDirectory]).stderr.includes("Unknown test option: --tests"));
	assert(runCli(["test", "--output", output, "--timeout", "-1", junitDirectory]).stderr.includes("Invalid --timeout value: -1"));
	const noTests = runCli(["test", "--format", "json", "--output", output, "--wsl", "never",
		path.join(repositoryRoot, "test", "build", "src")]);
	assert.strictEqual(noTests.status, 2);
	assert(JSON.parse(noTests.stdout).message.startsWith("No runnable JUnit test"), noTests.stdout);
}

/** The outcomes of a j2c test result, as check-gtest.js compares them */
function outcomesOf(result) {
	const actual = {};
	for (const entry of result.tests) {
		const [suite, name] = entry.cppTest.split(".");
		actual[suite] = actual[suite] || {};
		actual[suite][name] = { outcome: outcomes[entry.status], message: entry.message };
	}
	return actual;
}

/** Whether a result says make, a compiler or GoogleTest is missing */
function missingToolchain(result) {
	if (result.status === "error" && /^Could not run make/.test(result.message)) {
		return result.message;
	}
	const lines = result.status === "build-failed"
		? (result.build.log || []).concat((result.build.diagnostics || []).map((diagnostic) => diagnostic.message)) : [];
	if (lines.some((line) => /gtest\/gtest\.h: No such file|cannot find -lgtest/.test(line))) {
		return "GoogleTest is not installed";
	}
	return null;
}

/**
 * Translates both JUnit fixture directories against the runtime, builds and
 * runs them, and compares every outcome and message with the expected ones
 */
function checkRealTests(runCli, tempDirectory) {
	const output = path.join(tempDirectory, "junit test output");
	fs.mkdirSync(output);
	const environment = { ...process.env };
	if (isWindows && !environment.J2C_WSL_DISTRO) {
		environment.J2C_WSL_DISTRO = "Ubuntu";
	}
	const run = runCli(["test", "--format", "json", "--runtime", path.join(repositoryRoot, "runtime"),
		"--name", "junit", "--output", output, "--sourcepath", [junitDirectory, junitRuntimeDirectory, junitApi].join(path.delimiter),
		junitDirectory, junitRuntimeDirectory], { env: environment, maxBuffer: 1 << 26 });
	const result = JSON.parse(run.stdout);
	const missing = missingToolchain(result);
	if (missing) {
		console.log(`j2c test with a real GoogleTest run skipped: ${missing}`);
		return;
	}
	assert.strictEqual(result.status, "failed", JSON.stringify(result).slice(0, 4000));
	assert.strictEqual(run.status, 1);
	const problems = compare(outcomesOf(result), readExpected([junitDirectory, junitRuntimeDirectory]), true);
	assert.deepStrictEqual(problems, []);
	for (const entry of result.tests) {
		assert(entry.file && fs.existsSync(entry.file) && entry.line > 0, `${entry.cppTest} has no Java location`);
		assert.match(fs.readFileSync(entry.file, "utf8").split(/\r?\n/)[entry.line - 1], new RegExp(`\\b${entry.javaMethod}\\(`));
	}
	assert.strictEqual(result.summary.total, result.tests.length);
	assert.strictEqual(result.summary.exitCode, 1);
	console.log(`j2c test ran ${result.tests.length} translated JUnit tests with GoogleTest; every outcome and message matches`);
}

function checkTest(runCli, tempDirectory) {
	checkTestParsing(runCli, tempDirectory);
	checkRealTests(runCli, tempDirectory);
}

module.exports = { checkTest, writeFakeTools, cannedRun, outcomesOf, missingToolchain };
