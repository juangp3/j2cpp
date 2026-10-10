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
const featuresFixture = path.join(fixtureDirectory, "Java17Features.java");
const extensionVersion = require(path.join(extensionDirectory, "package.json")).version;
const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), "j2c-cli test "));
const outputDirectory = path.join(tempDirectory, "generated output");

function runCli(args, options = {}) {
	return childProcess.spawnSync("java", ["-jar", cliJar, ...args], {
		encoding: "utf8",
		...options
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
	assert(help.stdout.includes("--check"));

	const version = runCli(["--version"]);
	assert.strictEqual(version.status, 0, version.stderr);
	assert.strictEqual(version.stdout.trim(), `j2c ${extensionVersion}`);

	const checkWithOutput = runCli(["--check", "--output", tempDirectory, sealedFixture]);
	assert.strictEqual(checkWithOutput.status, 2);
	assert(checkWithOutput.stderr.includes("--check does not write output"));

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
		"\tstatic int run() {",
		"\t\tInteger k = 1;",
		"\t\tRunnable r = (Runnable & java.io.Serializable) () -> {};",
		"\t\treturn switch (k) { case 1 -> 2; default -> 3; };",
		"\t}",
		"}",
		""
	].join("\n"));
	const intersectionProblem = "Unsupported.java:5: Java 8 lambda expression cannot be translated: its target is an intersection type";
	const boxedSwitchProblem = "Unsupported.java:6: switch expressions on Integer are not supported";
	const unsupportedOutput = path.join(tempDirectory, "unsupported output");
	fs.mkdirSync(unsupportedOutput);
	const unsupported = runCli(["--output", unsupportedOutput, unsupportedSource]);
	assert.strictEqual(unsupported.status, 1, unsupported.stdout);
	assert(unsupported.stderr.includes(intersectionProblem), unsupported.stderr);
	assert(unsupported.stderr.includes(boxedSwitchProblem), unsupported.stderr);
	assert(!unsupported.stderr.includes("\tat "), "User errors must not print a stack trace");

	// --check reports every problem and writes nothing, not even into cwd
	const checkDirectory = path.join(tempDirectory, "check cwd");
	fs.mkdirSync(checkDirectory);
	const checked = runCli(["--check", unsupportedSource], { cwd: checkDirectory });
	assert.strictEqual(checked.status, 1, checked.stderr);
	assert(checked.stdout.includes(intersectionProblem));
	assert(checked.stdout.includes(boxedSwitchProblem));
	assert(checked.stdout.includes("J2C check: 2 problem(s) in 1 of 1 file(s)"));
	assert(checked.stdout.includes("1 x lambda expression"));
	assert(checked.stdout.includes("1 x switch expression selector"));
	assert.deepStrictEqual(fs.readdirSync(checkDirectory), []);

	// Lambdas and method references are rewritten into anonymous classes
	const lambdaSource = path.join(scratchPackage, "Lambdas.java");
	fs.writeFileSync(lambdaSource, [
		"package scratch;",
		"public class Lambdas {",
		"\tinterface F { int apply(int x); }",
		"\tstatic int run(int offset) {",
		"\t\tF shifted = x -> x + offset;",
		"\t\tF boxed = Integer::valueOf;",
		"\t\treturn shifted.apply(1) + boxed.apply(2);",
		"\t}",
		"}",
		""
	].join("\n"));
	const lambdaOutput = path.join(tempDirectory, "lambda output");
	fs.mkdirSync(lambdaOutput);
	const lambdas = runCli(["--output", lambdaOutput, lambdaSource]);
	assert.strictEqual(lambdas.status, 0, lambdas.stderr);
	const lambdaClass = fs.readFileSync(
		path.join(lambdaOutput, "src", "scratch", "Lambdas_run_1.cpp"), "utf8"
	);
	assert.match(lambdaClass, /int32_t scratch::Lambdas_run_1::apply\(int32_t x\)\s*\{\s*return x \+ offset;/);
	assert(
		fs.readFileSync(path.join(lambdaOutput, "src", "scratch", "Lambdas.cpp"), "utf8")
			.includes("new Lambdas_run_1(offset)"),
		"The captured local must be passed to the generated class"
	);

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

		const corpusCheck = runCli(["--check", "--sourcepath", sourceRoot, sourceRoot]);
		assert.strictEqual(corpusCheck.status, 0, corpusCheck.stdout);
		assert.match(corpusCheck.stdout, /J2C check: no problems found in \d+ file\(s\)/);

		// Text blocks, enum/String switches, and try-with-resources
		const featuresOutput = path.join(tempDirectory, "features output");
		fs.mkdirSync(featuresOutput);
		const features = runCli([
			"--output", featuresOutput,
			"--sourcepath", sourceRoot,
			featuresFixture
		]);
		assert.strictEqual(features.status, 0, features.stderr);
		const featureSource = fs.readFileSync(path.join(
			featuresOutput, "src", "se", "arnetheduck", "j2c", "test", "Java17Features.cpp"
		), "utf8");
		assert(featureSource.includes(String.raw`u"J2C\n  \"text block\"\n"_j`));
		assert.match(featureSource, /\(v == Java17Features_Shape::CIRCLE\)/);
		assert(featureSource.includes(String.raw`(npc(v)->equals(u"sat"_j)) || (npc(v)->equals(u"sun"_j))`));
		assert(featureSource.includes(String.raw`(npc(v)->equals(u"go"_j)) || (npc(v)->equals(u"run"_j))`));
		assert(featureSource.includes("finally([&] { if (reader != nullptr) { reader->close(); } })"));
		assert(!featureSource.includes("try ("), "try-with-resources must not be emitted verbatim");

		const lambdasOutput = path.join(tempDirectory, "lambdas output");
		fs.mkdirSync(lambdasOutput);
		const lambdaFixture = runCli([
			"--output", lambdasOutput,
			"--sourcepath", sourceRoot,
			path.join(fixtureDirectory, "Java17Lambdas.java")
		]);
		assert.strictEqual(lambdaFixture.status, 0, lambdaFixture.stderr);
		const lambdaSources = filesUnder(path.join(lambdasOutput, "src"))
			.filter((file) => file.endsWith(".cpp"))
			.map((file) => fs.readFileSync(file, "utf8"))
			.join("\n");
		// toString() in a lambda means the enclosing object's toString()
		assert(lambdaSources.includes("append(Java17Lambdas_this->toString())"));
		// Default and static interface methods get stub definitions
		assert(fs.existsSync(path.join(lambdasOutput, "ext", "stub", "java", "util", "Comparator-stub.cpp")));

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
