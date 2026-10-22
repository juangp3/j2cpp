// Translates test/src/.../Java17Mocks.java with --mocks, then builds and
// runs test/cpp/Java17MocksTest.cpp, a hand-written GoogleTest and gMock
// test that passes the generated mocks to the translated code, through the
// generated Makefile (make tests USER_TEST_SRCS=...). The JDK classes are
// J2C's stubs, so test/cpp/j2c_test_hooks.cpp supplies the runtime hooks.
// It also translates all of test/src with --mocks and compiles every mock
// generated from its interfaces, checking that none is abstract.
//
// gMock comes from pkg-config or the default include and library paths
// (libgmock-dev), from GMOCK_CFLAGS and GMOCK_LIBS when set, or else is
// built from the GoogleTest sources in /usr/src/googletest (the googletest
// package) into target/gmock. CFLAGS is passed to the compiler, so CI can
// build with sanitizers. On Windows without make, the build runs in WSL
// (J2C_WSL_DISTRO, default Ubuntu).
//
// Usage: node scripts/mocks-gtest.js
const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const extensionDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..");
const cliJar = process.env.J2C_CLI_JAR || path.join(repositoryRoot, "target", "j2c-cli.jar");
const sourcePath = path.join(repositoryRoot, "test", "src");
const fixture = path.join(sourcePath, "se", "arnetheduck", "j2c", "test", "Java17Mocks.java");
const output = path.join(repositoryRoot, "target", "cpp-mocks");
const treeOutput = path.join(repositoryRoot, "target", "cpp-mocks-tree");
const gmockBuild = path.join(repositoryRoot, "target", "gmock");
const name = path.basename(output);
const cflags = process.env.CFLAGS || "";
const tests = 10;

function run(command, args, options = {}) {
	const result = childProcess.spawnSync(command, args, { encoding: "utf8", maxBuffer: 1 << 28, ...options });
	if (result.error) {
		throw result.error;
	}
	return result;
}

const useWsl = process.platform === "win32" && childProcess.spawnSync("make", ["--version"]).status !== 0;
const wslDistribution = process.env.J2C_WSL_DISTRO || "Ubuntu";

function nativePath(file) {
	return useWsl ? file.replace(/^([A-Za-z]):/, (match, drive) => `/mnt/${drive.toLowerCase()}`).replace(/\\/g, "/") : file;
}

function shellQuote(text) {
	return `'${text.replace(/'/g, "'\\''")}'`;
}

// Runs a shell command line in a directory, natively or in WSL
function shell(directory, line) {
	const full = `cd ${shellQuote(nativePath(directory))} && ${line}`;
	return useWsl
		? run("wsl", ["-d", wslDistribution, "bash", "-c", full])
		: run("bash", ["-c", full]);
}

function check(result, what) {
	if (result.status !== 0) {
		throw new Error(`${what} failed (${result.status}):\n${result.stdout}\n${result.stderr}`.trim());
	}
}

// gMock's compiler flags and libraries, or null for the Makefile's defaults
function findGmock() {
	if (process.env.GMOCK_CFLAGS !== undefined || process.env.GMOCK_LIBS !== undefined) {
		return { cflags: process.env.GMOCK_CFLAGS || "", libs: process.env.GMOCK_LIBS || "" };
	}
	const probe = shell(repositoryRoot, "echo '#include <gmock/gmock.h>' | g++ -std=c++20 -x c++ -fsyntax-only - "
		+ "&& { pkg-config --exists gmock 2>/dev/null || ls /usr/lib/*/libgmock.* /usr/lib/libgmock.* >/dev/null 2>&1; }");
	if (probe.status === 0) {
		return null;
	}
	const sources = "/usr/src/googletest/googlemock";
	check(shell(repositoryRoot, `test -f ${sources}/src/gmock-all.cc`),
		"finding gMock (install libgmock-dev, or googletest for its sources, or set GMOCK_CFLAGS and GMOCK_LIBS)");
	const library = path.join(gmockBuild, "libgmock.a");
	if (!fs.existsSync(library)) {
		fs.mkdirSync(gmockBuild, { recursive: true });
		console.log(`Building gMock from ${sources} into ${gmockBuild}`);
		check(shell(gmockBuild, `g++ -std=c++20 -O1 -I${sources}/include -I${sources} -c ${sources}/src/gmock-all.cc -o gmock-all.o && ar rcs libgmock.a gmock-all.o`),
			"building gMock");
	}
	return { cflags: `-I${sources}/include`, libs: nativePath(library) };
}

function filesUnder(directory) {
	return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
		const entryPath = path.join(directory, entry.name);
		return entry.isDirectory() ? filesUnder(entryPath) : [entryPath];
	});
}

// Every mock of the test tree's interfaces compiles and is concrete
function checkTree(gmock) {
	fs.rmSync(treeOutput, { recursive: true, force: true });
	fs.mkdirSync(treeOutput, { recursive: true });
	check(run("java", ["-jar", cliJar, "--mocks", "--output", treeOutput, "--sourcepath", sourcePath, sourcePath]), "translating test/src");
	const mockRoot = path.join(treeOutput, "mock");
	const lines = ["#include <type_traits>"];
	const headers = filesUnder(mockRoot).map((file) => path.relative(mockRoot, file).split(path.sep).join("/")).sort();
	for (const header of headers) {
		const directory = path.posix.dirname(header);
		const cppClass = `${directory === "." ? "" : `::${directory.split("/").join("::")}`}::${path.posix.basename(header, ".hpp")}`;
		lines.push(`#include <${header}>`, `static_assert(!std::is_abstract_v<${cppClass}>);`);
	}
	fs.writeFileSync(path.join(treeOutput, "mocks.cpp"), `${lines.join("\n")}\n`);
	check(shell(treeOutput, `g++ -std=c++20 -fsyntax-only -Isrc -Iext/src -Imock ${gmock ? gmock.cflags : ""} mocks.cpp`),
		"compiling the mocks of test/src");
	return headers.length;
}

function main() {
	fs.rmSync(output, { recursive: true, force: true });
	fs.mkdirSync(output, { recursive: true });
	check(run("java", ["-jar", cliJar, "--mocks", "--output", output, "--sourcepath", sourcePath, fixture]), "translation");

	// The hand-written test goes in test/, like any user test
	fs.mkdirSync(path.join(output, "test"), { recursive: true });
	fs.copyFileSync(path.join(repositoryRoot, "test", "cpp", "Java17MocksTest.cpp"), path.join(output, "test", "Java17MocksTest.cpp"));
	const hooks = nativePath(path.join(repositoryRoot, "test", "cpp", "j2c_test_hooks.cpp"));
	check(shell(output, `mkdir -p obj && g++ -std=c++20 -fwrapv ${cflags} -Isrc -Iext/src -c ${shellQuote(hooks)} -o obj/hooks.o`), "compiling the test hooks");

	const flags = cflags ? `CFLAGS=${shellQuote(cflags)} ` : "";
	const gmock = findGmock();
	const gmockVariables = gmock ? `GMOCK_CFLAGS=${shellQuote(gmock.cflags)} GMOCK_LIBS=${shellQuote(gmock.libs)} ` : "";
	check(shell(output, `${flags}${gmockVariables}make -s -j8 tests USER_TEST_SRCS=test/Java17MocksTest.cpp TEST_LDLIBS=obj/hooks.o`), "make tests");
	const result = shell(output, `./bin/${name}-tests --gtest_brief=1`);
	check(result, "the gMock test");
	const passed = /\[  PASSED  \] (\d+) tests?/.exec(result.stdout);
	if (!passed || Number(passed[1]) !== tests) {
		throw new Error(`expected ${tests} passing tests:\n${result.stdout}\n${result.stderr}`);
	}
	console.log(`gMock classes generated with --mocks pass ${tests} hand-written GoogleTest test(s)`);
	console.log(`The ${checkTree(gmock)} gMock classes of test/src compile and are concrete`);
}

try {
	main();
} catch (error) {
	console.error(error.message);
	process.exitCode = 1;
}
