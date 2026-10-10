const assert = require("assert");
const childProcess = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const extensionDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..");
const cliJar = path.join(repositoryRoot, "target", "j2c-cli.jar");
const sourceRoot = path.join(repositoryRoot, "test", "src");
const fixtureDirectory = path.join(sourceRoot, "se", "arnetheduck", "j2c", "test");
const sealedFixture = path.join(fixtureDirectory, "Java17Unsupported.java");
const recordsFixture = path.join(fixtureDirectory, "Java17Records.java");
const controlFlowFixture = path.join(fixtureDirectory, "Java17ControlFlow.java");
const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), "j2c-cli test "));
const outputDirectory = path.join(tempDirectory, "generated output");

function runCli(args) {
	return childProcess.spawnSync("java", ["-jar", cliJar, ...args], {
		encoding: "utf8"
	});
}

function javaMajorVersion() {
	const version = childProcess.spawnSync("java", ["-version"], {
		encoding: "utf8"
	});
	assert.strictEqual(version.status, 0, version.stderr);
	const match = `${version.stdout}\n${version.stderr}`.match(/version "(?:1\.)?(\d+)/);
	assert(match, `Unable to determine Java version: ${version.stderr}`);
	return Number(match[1]);
}

function filesUnder(directory) {
	return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
		const entryPath = path.join(directory, entry.name);
		return entry.isDirectory() ? filesUnder(entryPath) : [entryPath];
	});
}

try {
	assert(fs.existsSync(cliJar), `CLI JAR not found: ${cliJar}`);
	fs.mkdirSync(outputDirectory);

	const help = runCli(["--help"]);
	assert.strictEqual(help.status, 0, help.stderr);
	assert(help.stdout.includes("--sealed-types <advisory|reject>"));

	const missingInput = runCli(["--output", outputDirectory]);
	assert.strictEqual(missingInput.status, 2);
	assert(missingInput.stderr.includes("No Java input was provided"));

	const missingOptionValue = runCli([
		"--output", outputDirectory,
		"--sealed-types"
	]);
	assert.strictEqual(missingOptionValue.status, 2);
	assert(missingOptionValue.stderr.includes("Missing value for --sealed-types"));

	const invalidPolicy = runCli([
		"--sealed-types", "invalid",
		sealedFixture
	]);
	assert.strictEqual(invalidPolicy.status, 2);
	assert(invalidPolicy.stderr.includes("Invalid --sealed-types value"));

	const invalidEncoding = runCli([
		"--output", outputDirectory,
		"--encoding", "no-such-charset",
		sealedFixture
	]);
	assert.strictEqual(invalidEncoding.status, 2);
	assert(invalidEncoding.stderr.includes("Unsupported --encoding value"));

	// Syntax the writers cannot translate must fail loudly instead of
	// producing plausible-looking but invalid C++
	const scratchRoot = path.join(tempDirectory, "scratch src");
	const scratchPackage = path.join(scratchRoot, "scratch");
	fs.mkdirSync(scratchPackage, { recursive: true });
	const unsupportedSource = path.join(scratchPackage, "Unsupported.java");
	fs.writeFileSync(unsupportedSource, [
		"package scratch;",
		"public class Unsupported {",
		"\tinterface F { int apply(int x); }",
		"\tstatic int run() {",
		"\t\tF twice = x -> x * 2;",
		"\t\tF boxed = Integer::valueOf;",
		"\t\treturn twice.apply(1) + boxed.apply(2);",
		"\t}",
		"}",
		""
	].join("\n"));
	const unsupportedOutput = path.join(tempDirectory, "unsupported output");
	fs.mkdirSync(unsupportedOutput);
	const unsupported = runCli(["--output", unsupportedOutput, unsupportedSource]);
	assert.strictEqual(unsupported.status, 1, unsupported.stdout);
	assert(unsupported.stderr.includes("Unsupported.java:5: Java 8 lambda expressions are not supported"));
	assert(unsupported.stderr.includes("Unsupported.java:6: Java 8 method references are not supported"));
	assert(!unsupported.stderr.includes("\tat "), "User errors must not print a stack trace");

	// Sources are read as UTF-8 by default and C++ is written as UTF-8,
	// independent of the platform charset
	const utf8Source = path.join(scratchPackage, "Utf8.java");
	fs.writeFileSync(utf8Source, [
		"package scratch;",
		"public class Utf8 {",
		"\tpublic static String text() { return \"ação\"; }",
		"}",
		""
	].join("\n"), "utf8");
	const utf8Output = path.join(tempDirectory, "utf8 output");
	fs.mkdirSync(utf8Output);
	const utf8 = runCli(["--output", utf8Output, utf8Source]);
	assert.strictEqual(utf8.status, 0, utf8.stderr);
	assert(
		fs.readFileSync(path.join(utf8Output, "src", "scratch", "Utf8.cpp"), "utf8")
			.includes("u\"ação\""),
		"Non-ASCII string literal was not preserved as UTF-8"
	);

	if (javaMajorVersion() >= 17) {
		// Every legacy fixture exercises translator paths that the Java 17
		// AST level can break; the whole tree must keep translating.
		const corpusOutput = path.join(tempDirectory, "corpus output");
		fs.mkdirSync(corpusOutput);
		const corpus = runCli(["--output", corpusOutput, "--sourcepath", sourceRoot, sourceRoot]);
		assert.strictEqual(corpus.status, 0, corpus.stderr);
		const corpusSources = filesUnder(path.join(corpusOutput, "src"))
			.filter((file) => file.endsWith(".cpp"));
		assert(corpusSources.length > 100, `Only ${corpusSources.length} sources generated`);
		for (const file of corpusSources) {
			assert(
				!fs.readFileSync(file, "utf8").includes("MISSING"),
				`Unresolved AST placeholder in ${file}`
			);
		}

		const advisory = runCli([
			"--output", outputDirectory,
			"--sourcepath", sourceRoot,
			sealedFixture
		]);
		assert.strictEqual(advisory.status, 0, advisory.stderr);

		let generatedHeaders = filesUnder(outputDirectory)
			.filter((file) => file.endsWith(".hpp"))
			.map((file) => fs.readFileSync(file, "utf8"))
			.join("\n");
		assert(generatedHeaders.includes("Java permitted direct subtypes:"));
		assert(generatedHeaders.includes("Java17SealedImpl"));
		assert(generatedHeaders.includes("not enforced by C++"));
		assert(generatedHeaders.includes(
			"Java non-sealed type: this inheritance branch remains open"
		));

		const rejected = runCli([
			"--output", outputDirectory,
			"--sourcepath", sourceRoot,
			"--sealed-types", "reject",
			sealedFixture
		]);
		assert.notStrictEqual(rejected.status, 0);
		assert(rejected.stderr.includes("sealed type rejected by --sealed-types reject"));
		assert(rejected.stderr.includes("non-sealed type rejected by --sealed-types reject"));

		const records = runCli([
			"--output", outputDirectory,
			"--sourcepath", sourceRoot,
			recordsFixture
		]);
		assert.strictEqual(records.status, 0, records.stderr);

		generatedHeaders = filesUnder(outputDirectory)
			.filter((file) => file.endsWith(".hpp"))
			.map((file) => fs.readFileSync(file, "utf8"))
			.join("\n");
		assert(generatedHeaders.includes("Java17User"));
		assert(generatedHeaders.includes("operator=="));

		const controlFlow = runCli([
			"--output", outputDirectory,
			"--sourcepath", sourceRoot,
			controlFlowFixture
		]);
		assert.strictEqual(controlFlow.status, 0, controlFlow.stderr);
		const generatedSources = filesUnder(outputDirectory)
			.filter((file) => file.endsWith(".cpp"))
			.map((file) => fs.readFileSync(file, "utf8"))
			.join("\n");
		assert(generatedSources.includes("patternLength"));
		assert(generatedSources.includes("switchValue"));
		assert.match(
			generatedSources,
			/case int32_t\(0\):\s*return int32_t\(1\);/
		);
		assert.match(
			generatedSources,
			/case int32_t\(0\):\s*result = 10;\s*break;/,
			"Arrow rules in switch statements must not return or fall through"
		);
		assert.match(
			generatedSources,
			/\(v == Java17ControlFlow_Light::AMBER\) \|\| \(v == Java17ControlFlow_Light::GREEN\)/
		);
	} else {
		console.log("Java 17 translation checks skipped (requires Java 17+)");
	}

	console.log("CLI integration checks passed");
} finally {
	fs.rmSync(tempDirectory, { recursive: true, force: true });
}
