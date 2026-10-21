// --runtime bundled checks for test-cli.js.
//
// The runtime packaged in the CLI JAR is extracted into J2C_CACHE_DIR as
// runtime-<version>-<hash>. Translating with it must give the same output as
// --runtime runtime; the extracted copy is reused while its files are
// intact, replaced when any file is missing, changed or added, and
// concurrent processes extracting at once must all succeed with one copy.
//
// Run directly with --concurrent <jar> <cache> <count> <input>, it starts
// that many checks at once and prints their exit statuses as JSON.
const assert = require("assert");
const childProcess = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const repositoryRoot = path.resolve(__dirname, "..", "..");
const runtimeDirectory = path.join(repositoryRoot, "runtime");
const hello = path.join(repositoryRoot, "test", "runtime", "hello");

/** Paths below a directory with forward slashes, sorted */
function relativeFiles(directory, prefix = "") {
	return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
		const relative = prefix + entry.name;
		return entry.isDirectory()
			? relativeFiles(path.join(directory, entry.name), relative + "/")
			: [relative];
	}).sort();
}

/** The files Maven packages from runtime/ (see pom.xml) */
function packagedFiles() {
	return relativeFiles(runtimeDirectory).filter((file) =>
		["api.txt", "README.md", "required-classes.txt"].includes(file)
		|| file.startsWith("java/") || file.startsWith("cpp/"));
}

function cacheEntries(cache) {
	return fs.existsSync(cache) ? fs.readdirSync(cache).filter((name) => name.startsWith("runtime-")).sort() : [];
}

/** Asserts that the cache holds one intact copy of the runtime, and returns it */
function assertExtracted(cache) {
	const entries = cacheEntries(cache);
	assert.strictEqual(entries.length, 1, `Expected one runtime in the cache: ${entries}`);
	assert.match(entries[0], /^runtime-[A-Za-z0-9._-]+-[0-9a-f]{16}$/);
	const extracted = path.join(cache, entries[0]);
	const packaged = packagedFiles();
	assert.deepStrictEqual(relativeFiles(extracted).filter((file) => file !== ".j2c-runtime"), packaged);
	for (const file of packaged) {
		assert(fs.readFileSync(path.join(extracted, file)).equals(fs.readFileSync(path.join(runtimeDirectory, file))),
			`${file} differs from runtime/${file}`);
	}
	return extracted;
}

function checkBundledRuntime(runCli, tempDirectory, cliJar) {
	const cache = path.join(tempDirectory, "runtime cache");
	const env = { ...process.env, J2C_CACHE_DIR: cache };
	const withCache = { env };

	// The same output as the runtime directory: the generated code never
	// names the runtime's location
	const bundledOutput = path.join(tempDirectory, "bundled runtime output");
	const directoryOutput = path.join(tempDirectory, "directory runtime output");
	fs.mkdirSync(bundledOutput);
	fs.mkdirSync(directoryOutput);
	const bundled = runCli(["--runtime", "bundled", "--name", "hello", "--format", "json",
		"--output", bundledOutput, hello], withCache);
	assert.strictEqual(bundled.status, 0, bundled.stderr || bundled.stdout);
	const direct = runCli(["--runtime", runtimeDirectory, "--name", "hello", "--format", "json",
		"--output", directoryOutput, hello]);
	assert.strictEqual(direct.status, 0, direct.stderr || direct.stdout);
	assert.deepStrictEqual(JSON.parse(bundled.stdout).generatedFiles, JSON.parse(direct.stdout).generatedFiles);
	const outputFiles = relativeFiles(bundledOutput);
	assert.deepStrictEqual(outputFiles, relativeFiles(directoryOutput));
	assert(outputFiles.includes("runtime/j2c_runtime.cpp"), outputFiles.join("\n"));
	for (const file of outputFiles) {
		assert(fs.readFileSync(path.join(bundledOutput, file)).equals(fs.readFileSync(path.join(directoryOutput, file))),
			`${file} differs between --runtime bundled and --runtime runtime`);
	}
	const extracted = assertExtracted(cache);

	// Nor where the runtime is: JDT keys of generic source types hold their
	// file's path, which once ordered includes differently for runtimes in
	// the temporary folder before and after LambdaDesugarer's j2c-desugar
	// directory there
	for (const prefix of ["j2c-a runtime ", "j2c-z runtime "]) {
		const copy = fs.mkdtempSync(path.join(os.tmpdir(), prefix));
		try {
			fs.cpSync(runtimeDirectory, copy, { recursive: true });
			const copyOutput = fs.mkdtempSync(path.join(tempDirectory, "copied runtime output "));
			const copied = runCli(["--runtime", copy, "--name", "hello", "--output", copyOutput, hello]);
			assert.strictEqual(copied.status, 0, copied.stderr || copied.stdout);
			assert.deepStrictEqual(relativeFiles(copyOutput), outputFiles);
			for (const file of outputFiles) {
				assert(fs.readFileSync(path.join(copyOutput, file)).equals(fs.readFileSync(path.join(bundledOutput, file))),
					`${file} depends on the runtime's location (${copy})`);
			}
		} finally {
			fs.rmSync(copy, { recursive: true, force: true });
		}
	}

	// Reused as it is: nothing is written again
	const marker = path.join(extracted, ".j2c-runtime");
	const sentinel = path.join(extracted, "java", "java", "lang", "Object.java");
	const past = new Date(Date.now() - 3600 * 1000);
	fs.utimesSync(marker, past, past);
	fs.utimesSync(sentinel, past, past);
	const reused = runCli(["--runtime", "bundled", "--check", hello], withCache);
	assert.strictEqual(reused.status, 0, reused.stderr || reused.stdout);
	assert.strictEqual(Math.round(fs.statSync(marker).mtimeMs), past.getTime());
	assert.strictEqual(Math.round(fs.statSync(sentinel).mtimeMs), past.getTime());
	assert.deepStrictEqual(cacheEntries(cache), [path.basename(extracted)]);

	// A damaged copy is replaced: a changed, a missing and an extra file
	// (an extra hook would be compiled into every program), and a missing
	// marker (an interrupted extraction)
	const damages = [
		() => fs.appendFileSync(path.join(extracted, "java", "java", "lang", "String.java"), "\n// changed\n"),
		() => fs.rmSync(path.join(extracted, "cpp", "src", "j2c_runtime.cpp")),
		() => fs.writeFileSync(path.join(extracted, "cpp", "src", "extra.cpp"), "#error extra\n"),
		() => fs.rmSync(marker)
	];
	for (const damage of damages) {
		damage();
		const repaired = runCli(["--runtime", "bundled", "--check", hello], withCache);
		assert.strictEqual(repaired.status, 0, repaired.stderr || repaired.stdout);
		assert.strictEqual(assertExtracted(cache), extracted);
	}

	// j2c report compares with the bundled API under its name
	const report = runCli(["report", "--runtime", "bundled", hello], withCache);
	assert.strictEqual(report.status, 0, report.stderr);
	assert(report.stdout.includes("The J2C runtime (`--runtime bundled`) provides"), report.stdout);
	const reportJson = JSON.parse(runCli(["report", "--format", "json", "--runtime", "bundled", hello], withCache).stdout);
	assert.strictEqual(reportJson.jdk.runtime, "bundled");

	// Concurrent processes: on an empty cache, and on a damaged copy
	const concurrentCache = path.join(tempDirectory, "concurrent runtime cache");
	for (const prepare of [() => {}, () => {
		fs.rmSync(path.join(assertExtracted(concurrentCache), "cpp", "src", "j2c_runtime.hpp"));
	}]) {
		prepare();
		const run = childProcess.spawnSync(process.execPath, [__filename, "--concurrent", cliJar,
			concurrentCache, "6", hello], { encoding: "utf8" });
		assert.strictEqual(run.status, 0, run.stderr);
		const results = JSON.parse(run.stdout);
		assert.strictEqual(results.length, 6);
		for (const result of results) {
			assert.strictEqual(result.status, 0, result.stderr);
		}
		assertExtracted(concurrentCache);
	}
	// Each process wrote a temporary copy; the losers deleted theirs
	assert.deepStrictEqual(fs.readdirSync(concurrentCache).filter((name) => name.includes(".tmp-")), []);

	// A directory named bundled is reached with a path
	const notRuntime = runCli(["--runtime", "./bundled", "--check", hello], withCache);
	assert.strictEqual(notRuntime.status, 2);
	assert(notRuntime.stderr.includes("Not a J2C runtime"), notRuntime.stderr);
}

function concurrent(jar, cache, count, input) {
	const results = [];
	let running = 0;
	for (let index = 0; index < count; ++index) {
		running++;
		const child = childProcess.spawn("java", ["-jar", jar, "--runtime", "bundled", "--check", input], {
			env: { ...process.env, J2C_CACHE_DIR: cache }
		});
		const result = { status: null, stderr: "" };
		results.push(result);
		child.stdout.resume();
		child.stderr.on("data", (chunk) => {
			result.stderr += chunk;
		});
		child.on("close", (status) => {
			result.status = status;
			if (--running === 0) {
				process.stdout.write(JSON.stringify(results));
			}
		});
	}
}

if (require.main === module) {
	const [option, jar, cache, count, input] = process.argv.slice(2);
	assert.strictEqual(option, "--concurrent");
	concurrent(jar, cache, Number(count), input);
}

module.exports = { checkBundledRuntime };
