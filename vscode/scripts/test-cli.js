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

	const invalidPolicy = runCli([
		"--sealed-types", "invalid",
		sealedFixture
	]);
	assert.strictEqual(invalidPolicy.status, 2);
	assert(invalidPolicy.stderr.includes("Invalid --sealed-types value"));

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

	console.log("CLI integration checks passed");
} finally {
	fs.rmSync(tempDirectory, { recursive: true, force: true });
}
