// Translates (and, where make is available, compiles) every probe in
// test/probes, then prints a status matrix. Each probe declares the outcome it
// expects on its first line ("// expect: compiles" or "// expect: rejected").
// The run fails when an outcome differs from its expectation, which flags
// both new gaps and gaps that were fixed without updating the probe.
const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const extensionDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..");
const cliJar = path.join(repositoryRoot, "target", "j2c-cli.jar");
const probeRoot = path.join(repositoryRoot, "test", "probes");
const outputRoot = path.join(repositoryRoot, "target", "probes");

function run(command, args, options = {}) {
	return childProcess.spawnSync(command, args, { encoding: "utf8", ...options });
}

function firstLine(text, pattern) {
	const line = text.split(/\r?\n/).find((candidate) => pattern.test(candidate));
	return line ? line.trim() : "";
}

const canCompile = run("make", ["--version"]).status === 0;
const probes = fs.readdirSync(path.join(probeRoot, "probes"))
	.filter((name) => name.endsWith(".java"))
	.sort();
fs.rmSync(outputRoot, { recursive: true, force: true });

const results = probes.map((file) => {
	const source = path.join(probeRoot, "probes", file);
	const name = path.basename(file, ".java");
	const expected = (fs.readFileSync(source, "utf8").match(/^\/\/ expect: (\w+)/) || [])[1];

	const check = run("java", ["-jar", cliJar, "--check", "--sourcepath", probeRoot, source]);
	if (check.status !== 0) {
		return { name, expected, outcome: "rejected", detail: firstLine(check.stdout, /\.java:\d+:/) };
	}

	const output = path.join(outputRoot, name);
	fs.mkdirSync(output, { recursive: true });
	const translate = run("java", ["-jar", cliJar, "--output", output, "--sourcepath", probeRoot, source]);
	if (translate.status !== 0) {
		return { name, expected, outcome: "crashed", detail: firstLine(translate.stderr, /\S/) };
	}
	if (!canCompile) {
		return { name, expected, outcome: "translates", detail: "make not available" };
	}

	const make = run("make", ["-k", "-j8", "-C", output]);
	if (make.status !== 0) {
		const errors = `${make.stdout}\n${make.stderr}`.split(/\r?\n/).filter((line) => / error/.test(line));
		return { name, expected, outcome: "fails to compile", detail: `${errors.length} error(s); ${errors[0] || ""}`.trim() };
	}
	return { name, expected, outcome: "compiles", detail: "" };
});

let unexpected = 0;
for (const result of results) {
	// Without make, a probe expected to compile can only be shown to translate
	const matches = result.outcome === result.expected
		|| (!canCompile && result.expected === "compiles" && result.outcome === "translates");
	if (!matches) {
		unexpected++;
	}
	const status = matches ? "ok  " : "DIFF";
	console.log(`${status} ${result.name.padEnd(12)} expected ${String(result.expected).padEnd(9)} got ${result.outcome}${result.detail ? ` - ${result.detail}` : ""}`);
}
console.log(`${results.length} probe(s), ${unexpected} unexpected outcome(s)${canCompile ? "" : " (compilation skipped: make not found)"}`);
process.exitCode = unexpected === 0 ? 0 : 1;
