// Translates the JUnit fixtures with --tests, builds the GoogleTest
// wrappers, runs them and compares every outcome with the fixtures'
// expected-results.json (check-gtest.js).
//
// Without --runtime the JDK classes are J2C's stubs: only test/junit is
// translated, test/cpp/j2c_test_hooks.cpp supplies the hooks a runtime
// would, and failure messages are not compared, because the stubbed String
// cannot show Java values or user messages. With --runtime test/junit and
// test/junit-runtime (assertions on JDK objects) are translated against the
// J2C runtime (runtime/), so assertions use its equals and toString, and the
// failure messages the expected results record are compared too; real JUnit
// produces the same ones (junit-jvm.js). CFLAGS is passed to the
// compiler, so CI can build with sanitizers. On Windows without make, the
// build runs in WSL (J2C_WSL_DISTRO, default Ubuntu).
//
// Usage: node scripts/junit-gtest.js [--runtime]
const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");
const { compare, gtestResults, readExpected } = require("./check-gtest");

const extensionDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..");
const cliJar = process.env.J2C_CLI_JAR || path.join(repositoryRoot, "target", "j2c-cli.jar");
const withRuntime = process.argv.includes("--runtime");
const fixtureDirectories = (withRuntime ? ["junit", "junit-runtime"] : ["junit"])
	.map((name) => path.join(repositoryRoot, "test", name));
const junitApi = path.join(repositoryRoot, "test", "junit-api");
const output = path.join(repositoryRoot, "target", withRuntime ? "cpp-junit-runtime" : "cpp-junit");
const name = path.basename(output);
const cflags = process.env.CFLAGS || "";

function run(command, args, options = {}) {
	const result = childProcess.spawnSync(command, args, { encoding: "utf8", maxBuffer: 1 << 28, ...options });
	if (result.error) {
		throw result.error;
	}
	return result;
}

const useWsl = process.platform === "win32" && childProcess.spawnSync("make", ["--version"]).status !== 0;
const wslDistribution = process.env.J2C_WSL_DISTRO || "Ubuntu";

function wslPath(file) {
	return file.replace(/^([A-Za-z]):/, (match, drive) => `/mnt/${drive.toLowerCase()}`).replace(/\\/g, "/");
}

function shellQuote(text) {
	return `'${text.replace(/'/g, "'\\''")}'`;
}

// Runs a shell command line in the output directory, natively or in WSL
function shell(line) {
	const full = `cd ${shellQuote(useWsl ? wslPath(output) : output)} && ${line}`;
	return useWsl
		? run("wsl", ["-d", wslDistribution, "bash", "-c", full])
		: run("bash", ["-c", full]);
}

function check(result, what) {
	if (result.status !== 0) {
		throw new Error(`${what} failed (${result.status}):\n${result.stdout}\n${result.stderr}`.trim());
	}
}

function main() {
	fs.rmSync(output, { recursive: true, force: true });
	fs.mkdirSync(output, { recursive: true });
	const translate = run("java", ["-jar", cliJar, "--tests",
		...(withRuntime ? ["--runtime", path.join(repositoryRoot, "runtime")] : []),
		"--output", output, "--sourcepath", [...fixtureDirectories, junitApi].join(path.delimiter), ...fixtureDirectories]);
	check(translate, "translation");

	const flags = cflags ? `CFLAGS=${shellQuote(cflags)} ` : "";
	if (withRuntime) {
		check(shell(`${flags}make -s -j8 tests`), "make tests");
	} else {
		// The hooks a runtime would provide (class_, unimplemented_, _j)
		const hooks = useWsl ? wslPath(path.join(repositoryRoot, "test", "cpp", "j2c_test_hooks.cpp"))
			: path.join(repositoryRoot, "test", "cpp", "j2c_test_hooks.cpp");
		check(shell(`mkdir -p obj && g++ -std=c++20 -fwrapv ${cflags} -Isrc -Iext/src -c ${shellQuote(hooks)} -o obj/hooks.o`), "compiling the test hooks");
		check(shell(`${flags}make -s -j8 tests TEST_LDLIBS=obj/hooks.o`), "make tests");
	}
	// Some fixture tests fail on purpose, so the exit status is not checked
	const tests = shell(`./bin/${name}-tests --gtest_output=json:results.json`);
	const reportFile = path.join(output, "results.json");
	if (!fs.existsSync(reportFile)) {
		throw new Error(`the tests did not write a report (${tests.status}):\n${tests.stdout}\n${tests.stderr}`);
	}

	const expected = readExpected(fixtureDirectories);
	const problems = compare(gtestResults(JSON.parse(fs.readFileSync(reportFile, "utf8"))), expected, withRuntime);
	if (problems.length > 0) {
		console.error(problems.join("\n"));
		console.error(`GoogleTest results ${withRuntime ? "with the runtime " : ""}differ from the expected results`);
		process.exitCode = 1;
		return;
	}
	const count = Object.values(expected).reduce((sum, entries) => sum + Object.keys(entries).length, 0);
	const messages = Object.values(expected).reduce((sum, entries) =>
		sum + Object.values(entries).filter((entry) => typeof entry === "object" && entry.message !== undefined).length, 0);
	console.log(withRuntime
		? `GoogleTest with the J2C runtime matches the expected outcomes of ${count} test(s) and ${messages} failure message(s)`
		: `GoogleTest with stubbed JDK classes matches the expected outcomes of ${count} test(s)`);
}

main();
