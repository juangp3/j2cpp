// Agent benchmark tasks: loading, workspace copies and grading. A task is
// tasks/<id>/ with task.json, workspace/ (what the agent starts with),
// reference/ (a correct solution, laid over workspace/), hidden/ (drivers
// and expected output the agent never sees) and, optionally, tampers/<name>/
// (wrong solutions that the self-test expects to fail a named check).
const childProcess = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");

const benchDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(benchDirectory, "..", "..");
const tasksDirectory = path.join(benchDirectory, "tasks");
const defaultCliJar = path.join(repositoryRoot, "target", "j2c-cli.jar");

// The checks, in the order they run
const checkNames = ["protected", "javac", "driver", "j2c-check", "answer"];

// Paths an agent may create in any task without failing "protected"
const defaultIgnored = [".git/**", "build/**", "answer.json"];

const defaultPrompt = "Make the Java code in this folder translate to C++ with J2C; "
	+ "the j2c MCP tools are available. Change only the Java sources, and keep what "
	+ "the code does and its classes and members unchanged. You are done when j2c_check "
	+ "reports no problems; there is no need to translate or build.";

function readJson(file) {
	return JSON.parse(fs.readFileSync(file, "utf8"));
}

function loadTask(id) {
	const directory = path.join(tasksDirectory, id);
	const task = readJson(path.join(directory, "task.json"));
	for (const check of task.checks) {
		if (!checkNames.includes(check)) {
			throw new Error(`${id}: unknown check ${check}`);
		}
	}
	return {
		...task,
		id,
		directory,
		prompt: task.prompt || defaultPrompt,
		sourceRoots: task.sourceRoots || ["src"],
		allowed: task.allowed || ["**/*.java"],
		ignored: defaultIgnored.concat(task.ignored || []),
		timeoutSeconds: task.timeoutSeconds || 900
	};
}

/** Tasks whose id or tier is in the comma-separated filters (all when empty) */
function loadTasks(ids, tiers) {
	const idList = ids ? ids.split(",") : null;
	const tierList = tiers ? tiers.split(",") : null;
	const tasks = fs.readdirSync(tasksDirectory).sort()
		.filter((id) => fs.existsSync(path.join(tasksDirectory, id, "task.json")))
		.map(loadTask)
		.filter((task) => (!idList || idList.includes(task.id))
			&& (!tierList || tierList.includes(task.tier)));
	if (idList) {
		for (const id of idList) {
			if (!tasks.some((task) => task.id === id)) {
				throw new Error(`Unknown task: ${id}`);
			}
		}
	}
	return tasks;
}

function filesUnder(directory, base = directory) {
	if (!fs.existsSync(directory)) {
		return [];
	}
	return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
		const entryPath = path.join(directory, entry.name);
		return entry.isDirectory() ? filesUnder(entryPath, base)
			: [path.relative(base, entryPath).split(path.sep).join("/")];
	});
}

/** A glob with *, ** and ? as a regular expression over "/"-separated paths */
function globExpression(glob) {
	let expression = "";
	for (let i = 0; i < glob.length; ++i) {
		const c = glob[i];
		if (c === "*" && glob[i + 1] === "*") {
			// "**/" matches no folder too
			expression += glob[i + 2] === "/" ? "(?:.*/)?" : ".*";
			i += glob[i + 2] === "/" ? 2 : 1;
		} else if (c === "*") {
			expression += "[^/]*";
		} else if (c === "?") {
			expression += "[^/]";
		} else {
			expression += c.replace(/[.+^${}()|[\]\\]/g, "\\$&");
		}
	}
	return new RegExp(`^${expression}$`);
}

function matchesAny(file, globs) {
	return globs.some((glob) => globExpression(glob).test(file));
}

/** Copies the task's workspace, and with a reference or tamper, that on top */
function prepareWorkspace(task, destination, overlay) {
	fs.mkdirSync(destination, { recursive: true });
	fs.cpSync(path.join(task.directory, "workspace"), destination, { recursive: true });
	if (overlay) {
		fs.cpSync(overlay, destination, { recursive: true });
	}
	return destination;
}

function hash(file) {
	return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function run(command, args, options = {}) {
	return childProcess.spawnSync(command, args, {
		encoding: "utf8",
		maxBuffer: 64 * 1024 * 1024,
		timeout: 300000,
		...options
	});
}

function output(result) {
	return `${result.error ? `${result.error.message}\n` : ""}${result.stdout || ""}${result.stderr || ""}`.trim();
}

// Each check returns null when it passes, or what went wrong

/** Files outside "allowed" are unchanged, and none were added there */
function checkProtected(task, workspace) {
	const original = path.join(task.directory, "workspace");
	const problems = [];
	for (const file of filesUnder(original)) {
		if (matchesAny(file, task.allowed)) {
			continue;
		}
		const copy = path.join(workspace, file);
		if (!fs.existsSync(copy)) {
			problems.push(`deleted ${file}`);
		} else if (hash(copy) !== hash(path.join(original, file))) {
			problems.push(`changed ${file}`);
		}
	}
	const originals = new Set(filesUnder(original));
	for (const file of filesUnder(workspace)) {
		if (!originals.has(file) && !matchesAny(file, task.allowed)
			&& !matchesAny(file, task.ignored)) {
			problems.push(`added ${file}`);
		}
	}
	return problems.length ? problems.join(", ") : null;
}

function javaSources(directory) {
	return filesUnder(directory).filter((file) => file.endsWith(".java"))
		.map((file) => path.join(directory, file));
}

/** Compiles the sources and the hidden drivers; returns the class folder */
function compile(task, workspace, classes) {
	const sources = task.sourceRoots.flatMap((root) => javaSources(path.join(workspace, root)))
		.concat(javaSources(path.join(task.directory, "hidden")));
	const classPath = (task.compileSourcepath || [])
		.map((entry) => path.join(repositoryRoot, entry)).join(path.delimiter);
	const result = run("javac", ["--release", "17", "-nowarn", "-encoding", "UTF-8",
		"-d", classes, ...(classPath ? ["-sourcepath", classPath] : []), ...sources]);
	return result.status === 0 ? null : output(result) || "javac failed";
}

function runDriver(task, classes) {
	return run("java", ["-cp", classes, "-Duser.language=en", "-Duser.country=US",
		"-Dfile.encoding=UTF-8", task.driver], { timeout: 60000 });
}

function normalize(text) {
	return text.replace(/\r\n/g, "\n");
}

function checkDriver(task, classes) {
	const result = runDriver(task, classes);
	const expected = normalize(fs.readFileSync(path.join(task.directory, "hidden", "expected.txt"), "utf8"));
	const actual = normalize(result.stdout || "");
	if (result.status !== 0) {
		return `${task.driver} exited with ${result.status}: ${output(result)}`;
	}
	if (actual !== expected) {
		const expectedLines = expected.split("\n");
		const actualLines = actual.split("\n");
		const line = expectedLines.findIndex((text, i) => text !== actualLines[i]);
		return `output differs at line ${line + 1}: expected ${JSON.stringify(expectedLines[line])}, `
			+ `got ${JSON.stringify(actualLines[line])}`;
	}
	return null;
}

function checkJ2c(task, workspace, cliJar) {
	const args = ["-jar", cliJar, "--check", "--format", "json", "--project", "none"];
	if (task.tests) {
		args.push("--tests");
	}
	if (task.checkSourcepath) {
		args.push("--sourcepath", task.checkSourcepath
			.map((entry) => path.join(repositoryRoot, entry)).join(path.delimiter));
	}
	const result = run("java", [...args, ...task.sourceRoots], { cwd: workspace });
	let report;
	try {
		report = JSON.parse(result.stdout);
	} catch (e) {
		return `j2c --check gave no report: ${output(result)}`;
	}
	if (report.problems.length) {
		return report.problems.map((problem) =>
			`${problem.id} at ${path.relative(workspace, problem.file)}:${problem.line}`).join(", ");
	}
	return report.status === "ok" ? null : `j2c --check status ${report.status}`;
}

/** The agent's final answer names every expected text */
function checkAnswer(task, answer) {
	const missing = (task.answerIncludes || []).filter((text) => !(answer || "").includes(text));
	return missing.length ? `answer does not mention ${missing.join(", ")}` : null;
}

/**
 * Grades a workspace: { passed, checks: [{ name, passed, detail }] }. A
 * check after a failed javac is skipped and counts as failed.
 */
function grade(task, workspace, options = {}) {
	const cliJar = options.cliJar || defaultCliJar;
	const classes = fs.mkdtempSync(path.join(os.tmpdir(), "j2c-bench-classes-"));
	const checks = [];
	let compiled = false;
	try {
		for (const name of checkNames.filter((check) => task.checks.includes(check))) {
			let detail;
			if (name === "protected") {
				detail = checkProtected(task, workspace);
			} else if (name === "javac") {
				detail = compile(task, workspace, classes);
				compiled = detail === null;
			} else if (name === "driver") {
				detail = compiled ? checkDriver(task, classes) : "not run: javac failed";
			} else if (name === "j2c-check") {
				detail = checkJ2c(task, workspace, cliJar);
			} else {
				detail = checkAnswer(task, options.answer);
			}
			checks.push({ name, passed: detail === null, detail });
		}
	} finally {
		fs.rmSync(classes, { recursive: true, force: true });
	}
	return { passed: checks.every((check) => check.passed), checks };
}

/** Writes hidden/expected.txt from the driver's output on the untouched workspace */
function recordExpected(task) {
	const workspace = fs.mkdtempSync(path.join(os.tmpdir(), "j2c-bench-record-"));
	const classes = path.join(workspace, ".classes");
	try {
		prepareWorkspace(task, path.join(workspace, "w"));
		fs.mkdirSync(classes);
		const failure = compile(task, path.join(workspace, "w"), classes);
		if (failure) {
			throw new Error(`${task.id}: ${failure}`);
		}
		const result = runDriver(task, classes);
		if (result.status !== 0) {
			throw new Error(`${task.id}: ${output(result)}`);
		}
		fs.writeFileSync(path.join(task.directory, "hidden", "expected.txt"), normalize(result.stdout));
	} finally {
		fs.rmSync(workspace, { recursive: true, force: true });
	}
}

module.exports = {
	benchDirectory,
	repositoryRoot,
	checkNames,
	loadTasks,
	prepareWorkspace,
	grade,
	recordExpected,
	globExpression
};
