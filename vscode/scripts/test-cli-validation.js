// Validation checks for test-cli.js: every selected file is checked before
// anything is written, so a translation with one bad file leaves the output
// directory as it was, and a dependency (a file that is only on the source
// path) with Java errors gets a header and stubs and a J2C-DEPENDENCY-ERRORS
// warning instead of failing the translation.
const assert = require("assert");
const fs = require("fs");
const path = require("path");

function write(root, relative, lines) {
	const file = path.join(root, relative);
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, `${lines.join("\n")}\n`, "utf8");
	return file;
}

/** Relative paths and contents of every file below directory */
function snapshot(directory) {
	const files = {};
	const visit = (current) => {
		for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
			const entryPath = path.join(current, entry.name);
			if (entry.isDirectory()) {
				visit(entryPath);
			} else {
				files[path.relative(directory, entryPath).split(path.sep).join("/")] =
					fs.readFileSync(entryPath, "utf8");
			}
		}
	};
	visit(directory);
	return files;
}

function checkSelectedProblemsWriteNothing(runCli, tempDirectory) {
	const root = path.join(tempDirectory, "validation");
	const good = write(root, "src/demo/Good.java", [
		"package demo;",
		"public class Good {",
		"\tpublic int value() { return 1; }",
		"}"
	]);
	const bad = path.join(root, "src", "demo", "Bad.java");
	const badLines = [
		"package demo;",
		"public class Bad {",
		"\tpublic int value() { return missing(); }",
		"}"
	];
	const unsupported = path.join(root, "src", "demo", "Pattern.java");
	const unsupportedLines = [
		"package demo;",
		"public class Pattern {",
		"\tstatic Object o = \"\";",
		"\tstatic boolean b = o instanceof String s && s.isEmpty();",
		"}"
	];

	// One good and one bad file: nothing is written, not even the marker
	write(root, "src/demo/Bad.java", badLines);
	const output = path.join(root, "out");
	fs.mkdirSync(output);
	const failed = runCli(["--format", "json", "--output", output, path.join(root, "src")]);
	assert.strictEqual(failed.status, 1, failed.stderr);
	const report = JSON.parse(failed.stdout);
	assert.strictEqual(report.status, "problems");
	assert.deepStrictEqual(report.problems.map((problem) => [problem.id, path.basename(problem.file), problem.line]),
		[["J2C-COMPILE-ERROR", "Bad.java", 3]]);
	assert.deepStrictEqual(fs.readdirSync(output), [], "A failed translation writes nothing");

	// Every selected file's problems are reported at once, in text mode too
	write(root, "src/demo/Pattern.java", unsupportedLines);
	const both = runCli(["--output", output, path.join(root, "src")]);
	assert.strictEqual(both.status, 1, both.stderr);
	assert(both.stderr.includes("Bad.java:3:"), both.stderr);
	assert(both.stderr.includes("Pattern.java:4:"), both.stderr);
	assert.deepStrictEqual(fs.readdirSync(output), []);
	fs.rmSync(unsupported);
	fs.rmSync(bad);

	// Earlier output is left as it was, rather than moved to the backup
	const translated = runCli(["--output", output, path.join(root, "src")]);
	assert.strictEqual(translated.status, 0, translated.stderr);
	assert(fs.existsSync(path.join(output, "src", "demo", "Good.cpp")));
	const before = snapshot(output);
	write(root, "src/demo/Bad.java", badLines);
	const again = runCli(["--format", "json", "--output", output, path.join(root, "src")]);
	assert.strictEqual(again.status, 1, again.stderr);
	assert.deepStrictEqual(snapshot(output), before, "A failed translation leaves earlier output as it was");

	// Selecting just the good file translates it, with the bad one unused
	const goodOnly = runCli(["--format", "json", "--output", output, "--sourcepath",
		path.join(root, "src"), good]);
	assert.strictEqual(goodOnly.status, 0, goodOnly.stderr);
	assert.deepStrictEqual(JSON.parse(goodOnly.stdout).warnings, []);
}

function checkDependencyErrors(runCli, tempDirectory) {
	const root = path.join(tempDirectory, "dependency errors");
	const main = write(root, "src/demo/Main.java", [
		"package demo;",
		"",
		"import lib.Dep;",
		"",
		"public class Main {",
		"\tpublic static void main(String[] args) {",
		"\t\tSystem.out.println(Dep.twice(2) + new Dep.Inner().value);",
		"\t}",
		"}"
	]);
	const dep = write(root, "src/lib/Dep.java", [
		"package lib;",
		"",
		"public class Dep {",
		"\tpublic static class Inner {",
		"\t\tpublic int value = 1;",
		"\t}",
		"",
		"\tpublic static int twice(int x) {",
		"\t\treturn x * 2 + missing();",
		"\t}",
		"}"
	]);
	const output = path.join(root, "out");
	fs.mkdirSync(output);

	// The dependency gets a header and stubs, and the translation a warning
	const translated = runCli(["--format", "json", "--output", output, "--sourcepath",
		path.join(root, "src"), main]);
	assert.strictEqual(translated.status, 0, translated.stderr);
	const report = JSON.parse(translated.stdout);
	assert.strictEqual(report.status, "ok");
	assert.deepStrictEqual(report.problems, []);
	assert.strictEqual(report.warnings.length, 1, translated.stdout);
	const [warning] = report.warnings;
	assert.strictEqual(warning.id, "J2C-DEPENDENCY-ERRORS");
	assert.strictEqual(warning.severity, "warning");
	assert.strictEqual(path.resolve(warning.file), path.resolve(dep));
	assert.strictEqual(warning.line, 9);
	assert(warning.message.includes("1 Java error(s)"), warning.message);
	assert(warning.message.includes("missing"), warning.message);
	assert(warning.hint.includes("--check"), warning.hint);
	assert(translated.stderr.includes("J2C: warning: "), translated.stderr);
	assert(translated.stderr.includes("[J2C-DEPENDENCY-ERRORS]"), translated.stderr);

	const files = report.generatedFiles;
	assert(files.includes("src/demo/Main.cpp"), files.join("\n"));
	assert(files.includes("ext/src/lib/Dep.hpp"), files.join("\n"));
	assert(files.includes("ext/src/lib/Dep_Inner.hpp"), files.join("\n"));
	// From the bindings, like a library class: stubs, no implementation
	assert(!files.includes("ext/src/lib/Dep.cpp"), files.join("\n"));
	assert(files.some((file) => /^ext\/stub\/lib\/Dep[-_]stub\.cpp$/.test(file)), files.join("\n"));
	const header = fs.readFileSync(path.join(output, "ext", "src", "lib", "Dep.hpp"), "utf8");
	assert.match(header, /static int32_t twice\(int32_t x\);/);
	assert(fs.existsSync(path.join(output, "Makefile")));

	// Selected, the same file is an error
	const selected = runCli(["--format", "json", "--output", output, path.join(root, "src")]);
	assert.strictEqual(selected.status, 1, selected.stderr);
	assert.deepStrictEqual(JSON.parse(selected.stdout).problems.map((problem) =>
		[problem.id, path.basename(problem.file), problem.line]), [["J2C-COMPILE-ERROR", "Dep.java", 9]]);

	// explain knows the warning
	const explained = runCli(["explain", "J2C-DEPENDENCY-ERRORS"]);
	assert.strictEqual(explained.status, 0, explained.stderr);
	assert(explained.stdout.includes("header"), explained.stdout);
}

function checkValidation(runCli, tempDirectory) {
	checkSelectedProblemsWriteNothing(runCli, tempDirectory);
	checkDependencyErrors(runCli, tempDirectory);
}

module.exports = { checkValidation };
