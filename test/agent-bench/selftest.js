// Checks the agent benchmark's graders without running an agent: each
// task's untouched workspace fails only j2c-check (so its drivers and
// expected output match the original code), its reference passes every
// check, and each tamper fails the check it names.
//
//   node selftest.js [--task <ids>] [--tier <tiers>] [--record]
//
// --record first rewrites each task's hidden/expected.txt from the driver's
// output on the untouched workspace.
const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const bench = require("./lib/tasks");

function option(name) {
	const index = process.argv.indexOf(name);
	return index >= 0 ? process.argv[index + 1] : undefined;
}

function failed(result) {
	return result.checks.filter((check) => !check.passed).map((check) => check.name);
}

function describe(result) {
	return result.checks.map((check) =>
		`${check.name}: ${check.passed ? "passed" : check.detail}`).join("\n  ");
}

function readAnswer(directory) {
	const file = path.join(directory, "ANSWER.txt");
	return fs.existsSync(file) ? fs.readFileSync(file, "utf8") : "";
}

/** Grades the workspace with an overlay laid over it, minus its ANSWER.txt */
function gradeWith(task, scratch, name, overlay) {
	const workspace = bench.prepareWorkspace(task, path.join(scratch, name), overlay);
	fs.rmSync(path.join(workspace, "ANSWER.txt"), { force: true });
	return bench.grade(task, workspace, { answer: overlay ? readAnswer(overlay) : "" });
}

// The glob matcher that "protected" relies on
assert(bench.globExpression("**/*.java").test("Main.java"));
assert(bench.globExpression("**/*.java").test("src/a/Main.java"));
assert(!bench.globExpression("src/*.java").test("src/a/Main.java"));
assert(bench.globExpression("build/**").test("build/cpp/Main.cpp"));
assert(!bench.globExpression("**/*.java").test("Main.javac"));

const tasks = bench.loadTasks(option("--task"), option("--tier"));
assert(tasks.length > 0, "No tasks selected");
const scratch = fs.mkdtempSync(path.join(os.tmpdir(), "j2c-bench-selftest-"));
let failures = 0;
try {
	for (const task of tasks) {
		if (process.argv.includes("--record")) {
			bench.recordExpected(task);
		}
		const problems = [];
		const untouched = gradeWith(task, scratch, `${task.id}-untouched`);
		const expectedUntouched = task.untouchedFails || ["j2c-check"];
		if (JSON.stringify(failed(untouched)) !== JSON.stringify(expectedUntouched)) {
			problems.push(`untouched workspace should fail only ${expectedUntouched.join(", ")}:\n  ${describe(untouched)}`);
		}
		const reference = gradeWith(task, scratch, `${task.id}-reference`,
			path.join(task.directory, "reference"));
		if (!reference.passed) {
			problems.push(`reference should pass:\n  ${describe(reference)}`);
		}
		// Every task rejects a file added outside the allowed paths
		const added = bench.prepareWorkspace(task, path.join(scratch, `${task.id}-added`),
			path.join(task.directory, "reference"));
		fs.writeFileSync(path.join(added, "j2c-bench-added.txt"), "not allowed\n");
		fs.rmSync(path.join(added, "ANSWER.txt"), { force: true });
		const addedResult = bench.grade(task, added, { only: ["protected"] });
		if (!failed(addedResult).includes("protected")) {
			problems.push("an added file should fail protected");
		}
		// ...but not J2C's output, marked by .j2c-output
		fs.rmSync(path.join(added, "j2c-bench-added.txt"));
		fs.mkdirSync(path.join(added, "out", "src"), { recursive: true });
		fs.writeFileSync(path.join(added, "out", ".j2c-output"), "");
		fs.writeFileSync(path.join(added, "out", "src", "Main.cpp"), "");
		if (failed(bench.grade(task, added, { only: ["protected"] })).includes("protected")) {
			problems.push("a J2C output folder should pass protected");
		}
		const tampers = path.join(task.directory, "tampers");
		const tamperNames = fs.existsSync(tampers) ? fs.readdirSync(tampers).sort() : [];
		for (const name of tamperNames) {
			const expected = fs.readFileSync(path.join(tampers, name, "FAILS.txt"), "utf8").trim().split(/\s+/);
			const overlay = path.join(scratch, `${task.id}-tamper-${name}-overlay`);
			fs.cpSync(path.join(tampers, name), overlay, { recursive: true });
			fs.rmSync(path.join(overlay, "FAILS.txt"));
			const result = gradeWith(task, scratch, `${task.id}-tamper-${name}`, overlay);
			for (const check of expected) {
				if (!failed(result).includes(check)) {
					problems.push(`tamper ${name} should fail ${check}:\n  ${describe(result)}`);
				}
			}
		}
		if (problems.length) {
			failures++;
			console.log(`FAIL ${task.id}\n${problems.join("\n")}`);
		} else {
			console.log(`ok   ${task.id} (${tamperNames.length} tamper(s))`);
		}
	}
} finally {
	fs.rmSync(scratch, { recursive: true, force: true });
}
if (failures) {
	console.log(`${failures} of ${tasks.length} task(s) failed the self-test`);
	process.exit(1);
}
console.log(`Agent benchmark self-test: ${tasks.length} task(s) passed`);
