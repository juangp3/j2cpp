// Checks (and with --build, translates and compiles) the open-source projects
// in test/corpus/projects.json, pinned to a commit each. The expected results
// in that file are a ratchet: the run fails when a project's problem count
// rises or it drops a ladder step (checks, then builds). See
// docs/VALIDATION_ROADMAP.md, milestone M1.
//
// Usage: node scripts/corpus.js [--project a,b] [--tier small,medium]
//                               [--build [--build-tier small,medium]] [--update]
//                               [--jobs N]
//
// --update writes the observed results back as the new expectations and
// refreshes the scoreboard in docs/VALIDATION_PROJECTS.md.
//
// Cloned sources and downloaded JARs are only read: J2C parses them, and
// --build compiles the C++ that J2C generated, with J2C's own Makefile.
const childProcess = require("child_process");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const extensionDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..");
// J2C_CLI_JAR runs a copy, so rebuilding target/ does not disturb a long run
const cliJar = process.env.J2C_CLI_JAR || path.join(repositoryRoot, "target", "j2c-cli.jar");
const corpusFile = path.join(repositoryRoot, "test", "corpus", "projects.json");
const scoreboardFile = path.join(repositoryRoot, "docs", "VALIDATION_PROJECTS.md");
const workRoot = path.join(repositoryRoot, "target", "corpus");
const MAVEN_CENTRAL = "https://repo1.maven.org/maven2";
const STEPS = ["none", "checks", "builds"];

function run(command, args, options = {}) {
	return childProcess.spawnSync(command, args, {
		encoding: "utf8",
		maxBuffer: 1 << 30,
		...options
	});
}

function parseArguments(argv) {
	const options = { projects: null, tiers: null, build: false, buildTiers: null, update: false,
		jobs: 1, worker: null };
	for (let i = 0; i < argv.length; ++i) {
		const argument = argv[i];
		if (argument === "--project") {
			options.projects = argv[++i].split(",");
		} else if (argument === "--tier") {
			options.tiers = argv[++i].split(",");
		} else if (argument === "--build") {
			options.build = true;
		} else if (argument === "--build-tier") {
			options.buildTiers = argv[++i].split(",");
		} else if (argument === "--update") {
			options.update = true;
		} else if (argument === "--jobs") {
			options.jobs = Math.max(1, Number(argv[++i]) || 1);
		} else if (argument === "--worker") {
			// Internal: run one project and print its result as JSON
			options.worker = argv[++i];
		} else {
			throw new Error(`Unknown argument: ${argument}`);
		}
	}
	return options;
}

function git(directory, args) {
	const result = run("git", ["-C", directory, ...args]);
	if (result.status !== 0) {
		throw new Error(`git ${args.join(" ")} failed: ${result.stderr.trim()}`);
	}
	return result.stdout.trim();
}

/** A sparse, shallow clone of the project's source roots at its commit */
function checkout(project) {
	const directory = path.join(workRoot, "src", project.name);
	const marker = path.join(directory, ".corpus-commit");
	if (fs.existsSync(marker) && fs.readFileSync(marker, "utf8").trim() === project.commit) {
		return directory;
	}
	fs.rmSync(directory, { recursive: true, force: true });
	fs.mkdirSync(directory, { recursive: true });
	git(directory, ["init", "-q"]);
	git(directory, ["config", "core.longpaths", "true"]);
	git(directory, ["config", "core.sparseCheckout", "true"]);
	git(directory, ["remote", "add", "origin", project.repository]);
	const patterns = project.roots.map((root) => `/${root}/**/*.java`);
	fs.writeFileSync(path.join(directory, ".git", "info", "sparse-checkout"), patterns.join("\n") + "\n");
	git(directory, ["fetch", "-q", "--depth", "1", "--filter=blob:none", "origin", project.commit]);
	const checkoutResult = run("git", ["-C", directory, "checkout", "-q", "FETCH_HEAD"]);
	if (checkoutResult.status !== 0) {
		// Windows rejects some file names (such as 'ts"') anywhere in the
		// tree, even outside the sparse roots; write only the roots' files
		// A fresh repository without the blob filter, so no blob is fetched lazily
		fs.rmSync(path.join(directory, ".git"), { recursive: true, force: true });
		git(directory, ["init", "-q"]);
		git(directory, ["remote", "add", "origin", project.repository]);
		git(directory, ["fetch", "-q", "--depth", "1", "origin", project.commit]);
		const listing = run("git", ["-C", directory, "ls-tree", "-r", "-z", "FETCH_HEAD", "--", ...project.roots]);
		const entries = listing.stdout.split("\0").filter(Boolean).map((line) => {
			const [meta, file] = line.split("\t");
			return { sha: meta.split(" ")[2], file };
		}).filter((entry) => entry.file.endsWith(".java"));
		// One process for all blobs: each is "<sha> blob <size>\n<bytes>\n"
		const batch = childProcess.spawnSync("git", ["-C", directory, "cat-file", "--batch"],
			{ input: entries.map((entry) => entry.sha).join("\n") + "\n", maxBuffer: 1 << 30 });
		let offset = 0;
		for (const entry of entries) {
			const header = batch.stdout.indexOf(0x0a, offset);
			const size = Number(batch.stdout.toString("latin1", offset, header).split(" ")[2]);
			const target = path.join(directory, ...entry.file.split("/"));
			fs.mkdirSync(path.dirname(target), { recursive: true });
			fs.writeFileSync(target, batch.stdout.subarray(header + 1, header + 1 + size));
			offset = header + 1 + size + 1;
		}
	}
	fs.writeFileSync(marker, project.commit + "\n");
	return directory;
}

/** Downloads a group:artifact:version JAR from Maven Central, checking its SHA-1 */
async function dependency(coordinates) {
	const [group, artifact, version] = coordinates.split(":");
	const file = path.join(workRoot, "deps", `${artifact}-${version}.jar`);
	if (fs.existsSync(file)) {
		return file;
	}
	const url = `${MAVEN_CENTRAL}/${group.replace(/\./g, "/")}/${artifact}/${version}/${artifact}-${version}.jar`;
	const [jar, sha1] = await Promise.all([fetch(url), fetch(`${url}.sha1`)]);
	if (!jar.ok || !sha1.ok) {
		throw new Error(`Could not download ${coordinates}: HTTP ${jar.status}`);
	}
	const bytes = Buffer.from(await jar.arrayBuffer());
	const expected = (await sha1.text()).trim().split(/\s+/)[0];
	const actual = crypto.createHash("sha1").update(bytes).digest("hex");
	if (actual !== expected) {
		throw new Error(`Checksum mismatch for ${coordinates}`);
	}
	fs.mkdirSync(path.dirname(file), { recursive: true });
	fs.writeFileSync(file, bytes);
	return file;
}

function cliArguments(project, directory, jars) {
	const roots = project.roots.map((root) => path.join(directory, root));
	const args = ["--sourcepath", roots.join(path.delimiter)];
	if (jars.length > 0) {
		args.push("--classpath", jars.join(path.delimiter));
	}
	return { roots, args };
}

function check(project, directory, jars) {
	const { roots, args } = cliArguments(project, directory, jars);
	const result = run("java", ["-Xmx4g", "-jar", cliJar, "--check", "--format", "json", ...args, ...roots],
		{ timeout: 30 * 60 * 1000 });
	const lines = (result.stdout || "").trim().split(/\r?\n/);
	let report;
	try {
		report = JSON.parse(lines[lines.length - 1]);
	} catch (error) {
		return { error: `no JSON report (exit ${result.status}): ${(result.stderr || "").trim().slice(-300)}` };
	}
	const byId = {};
	for (const problem of report.problems || []) {
		byId[problem.id] = (byId[problem.id] || 0) + 1;
	}
	return { problems: (report.problems || []).length, files: report.files, byId };
}

/** make in the output directory, or in WSL on Windows when make is missing */
function make(output) {
	// J2C_MAKE_JOBS lowers make's parallelism when projects run in parallel
	const args = ["-k", `-j${process.env.J2C_MAKE_JOBS || 8}`, "-C"];
	if (run("make", ["--version"]).status === 0) {
		return run("make", [...args, output], { timeout: 4 * 60 * 60 * 1000 });
	}
	if (process.platform === "win32") {
		const distribution = process.env.J2C_WSL_DISTRO || "Ubuntu";
		const wslPath = output.replace(/^([A-Za-z]):/, (match, drive) => `/mnt/${drive.toLowerCase()}`)
			.replace(/\\/g, "/");
		return run("wsl", ["-d", distribution, "make", ...args, wslPath], { timeout: 4 * 60 * 60 * 1000 });
	}
	return null;
}

function build(project, directory, jars) {
	const output = path.join(workRoot, "out", project.name);
	fs.rmSync(output, { recursive: true, force: true });
	fs.mkdirSync(output, { recursive: true });
	const { roots, args } = cliArguments(project, directory, jars);
	const translate = run("java", ["-Xmx6g", "-jar", cliJar, "--output", output, ...args, ...roots],
		{ timeout: 30 * 60 * 1000 });
	if (translate.status !== 0) {
		const line = `${translate.stdout}\n${translate.stderr}`.split(/\r?\n/).find((text) => /failed|Exception/.test(text));
		return { built: false, detail: `translation failed: ${(line || "").trim()}` };
	}
	const result = make(output);
	if (result === null) {
		return { built: null, detail: "make not available" };
	}
	if (result.status === 0) {
		return { built: true, detail: "" };
	}
	// Distinct compiler errors, without their locations
	const errors = new Map();
	const lines = `${result.stdout}\n${result.stderr}`.split(/\r?\n/);
	for (const line of lines) {
		const match = /^(.*?):\d+:\d+: (?:fatal )?error: (.*)$/.exec(line);
		if (match && !errors.has(match[2])) {
			errors.set(match[2], `${path.basename(match[1])}: ${match[2]}`);
		}
	}
	if (errors.size === 0) {
		// No located compiler error: a killed compiler (out of memory), the linker, or make itself
		const line = lines.find((text) => /error|Killed|\*\*\*/.test(text));
		return {
			built: false,
			errors: 0,
			firstErrors: [],
			detail: `make failed (exit ${result.status}): ${(line || "").trim().slice(0, 300)}`
		};
	}
	return {
		built: false,
		errors: errors.size,
		firstErrors: [...errors.values()].slice(0, 5),
		detail: `${errors.size} distinct compile error(s)`
	};
}

function stepOf(result) {
	if (result.error || result.problems > 0) {
		return "none";
	}
	return result.built ? "builds" : "checks";
}

function compare(project, result, built) {
	const expected = project.expected;
	if (!expected) {
		return { status: "new", note: "no expectation yet; run with --update" };
	}
	if (result.error) {
		return { status: "FAIL", note: result.error };
	}
	const step = stepOf(result);
	// Without --build, a project expected to build can only be shown to check
	const expectedStep = !built && expected.step === "builds" ? "checks" : expected.step;
	if (result.problems > expected.problems) {
		return { status: "FAIL", note: `problems rose from ${expected.problems} to ${result.problems}` };
	}
	if (STEPS.indexOf(step) < STEPS.indexOf(expectedStep)) {
		return { status: "FAIL", note: `dropped from "${expectedStep}" to "${step}"` };
	}
	if (result.problems < expected.problems || STEPS.indexOf(step) > STEPS.indexOf(expectedStep)) {
		return { status: "better", note: "improved; run with --update to raise the expectation" };
	}
	return { status: "ok", note: "" };
}

function scoreboard(projects) {
	const rows = projects.map((project) => {
		const expected = project.expected || {};
		const problems = expected.problems === undefined ? "–" : String(expected.problems);
		return `| ${project.name} | ${project.tier} | ${problems} | ${expected.step || "–"} |`;
	});
	return [
		"| Project | Tier | Problems | Ladder step |",
		"| --- | --- | ---: | --- |",
		...rows
	].join("\n");
}

function writeScoreboard(projects) {
	const begin = "<!-- corpus-scoreboard:begin -->";
	const end = "<!-- corpus-scoreboard:end -->";
	const text = fs.readFileSync(scoreboardFile, "utf8");
	const start = text.indexOf(begin);
	const finish = text.indexOf(end);
	if (start < 0 || finish < start) {
		throw new Error(`${scoreboardFile} has no scoreboard markers`);
	}
	const table = `${begin}\n${scoreboard(projects)}\n${end}`;
	fs.writeFileSync(scoreboardFile, text.slice(0, start) + table + text.slice(finish + end.length));
}

async function runProject(project, building) {
	const started = Date.now();
	let result;
	try {
		const directory = checkout(project);
		const jars = [];
		for (const coordinates of project.dependencies) {
			jars.push(await dependency(coordinates));
		}
		result = check(project, directory, jars);
		if (building && !result.error && result.problems === 0) {
			Object.assign(result, build(project, directory, jars));
		}
	} catch (error) {
		result = { error: error.message };
	}
	result.name = project.name;
	result.step = stepOf(result);
	result.seconds = Math.round((Date.now() - started) / 1000);
	return result;
}

/** Runs one project in a child process, so several run at once */
function runWorker(project, building, jobs) {
	return new Promise((resolve) => {
		const args = [__filename, "--worker", project.name];
		if (building) {
			args.push("--build");
		}
		const makeJobs = String(Math.max(1, Math.floor(8 / jobs)));
		const child = childProcess.spawn(process.execPath, args,
			{ env: { ...process.env, J2C_MAKE_JOBS: process.env.J2C_MAKE_JOBS || makeJobs } });
		let stdout = "";
		let stderr = "";
		child.stdout.on("data", (chunk) => { stdout += chunk; });
		child.stderr.on("data", (chunk) => { stderr += chunk; });
		child.on("close", () => {
			try {
				const lines = stdout.trim().split(/\r?\n/);
				resolve(JSON.parse(lines[lines.length - 1]));
			} catch (error) {
				resolve({ name: project.name, step: "none", seconds: 0,
					error: `worker failed: ${stderr.trim().slice(-300)}` });
			}
		});
	});
}

async function main() {
	const options = parseArguments(process.argv.slice(2));
	if (!fs.existsSync(cliJar)) {
		throw new Error(`${cliJar} is missing; run "mvn -q package -DskipTests" first`);
	}
	const corpus = JSON.parse(fs.readFileSync(corpusFile, "utf8"));
	const selected = corpus.projects.filter((project) =>
		(!options.projects || options.projects.includes(project.name))
		&& (!options.tiers || options.tiers.includes(project.tier)));
	if (selected.length === 0) {
		throw new Error("No project matches the selection");
	}
	if (options.worker) {
		const project = corpus.projects.find((candidate) => candidate.name === options.worker);
		console.log(JSON.stringify(await runProject(project, options.build)));
		return;
	}

	const isBuilding = (project) => options.build
		&& (!options.buildTiers || options.buildTiers.includes(project.tier));
	const results = [];
	let failures = 0;
	const report = (project, result) => {
		const verdict = compare(project, result, isBuilding(project));
		result.status = verdict.status;
		if (verdict.status === "FAIL") {
			failures++;
		}
		results.push(result);
		const problems = result.error ? "error" : `${result.problems} problem(s)`;
		const detail = [verdict.note, result.detail].filter(Boolean).join("; ");
		console.log(`${verdict.status.padEnd(6)} ${project.name.padEnd(24)} ${problems.padEnd(14)} ${result.step.padEnd(7)} ${String(result.seconds).padStart(5)}s${detail ? `  ${detail}` : ""}`);
		for (const line of result.firstErrors || []) {
			console.log(`         ${line}`);
		}
	};

	if (options.jobs <= 1) {
		for (const project of selected) {
			report(project, await runProject(project, isBuilding(project)));
		}
	} else {
		// Downloads are shared, so fetch them once before the workers start
		for (const project of selected) {
			for (const coordinates of project.dependencies) {
				await dependency(coordinates);
			}
		}
		const queue = [...selected];
		const worker = async () => {
			for (let project = queue.shift(); project; project = queue.shift()) {
				report(project, await runWorker(project, isBuilding(project), options.jobs));
			}
		};
		await Promise.all(Array.from({ length: Math.min(options.jobs, selected.length) }, worker));
		// Keep results in corpus order
		results.sort((a, b) => selected.findIndex((p) => p.name === a.name)
			- selected.findIndex((p) => p.name === b.name));
	}

	fs.mkdirSync(workRoot, { recursive: true });
	fs.writeFileSync(path.join(workRoot, "results.json"), JSON.stringify(results, null, 1) + "\n");

	if (options.update) {
		for (const result of results) {
			if (result.error) {
				continue;
			}
			const project = corpus.projects.find((candidate) => candidate.name === result.name);
			const previous = project.expected;
			if (!previous) {
				project.expected = { problems: result.problems, step: result.step };
				continue;
			}
			// A ratchet: --update only records improvements, so a regression
			// keeps failing until the expectation is lowered by hand
			const step = STEPS.indexOf(result.step) > STEPS.indexOf(previous.step)
				? result.step : previous.step;
			project.expected = { problems: Math.min(result.problems, previous.problems), step };
		}
		fs.writeFileSync(corpusFile, JSON.stringify(corpus, null, "\t") + "\n");
		writeScoreboard(corpus.projects);
		console.log(`Updated ${path.relative(repositoryRoot, corpusFile)} and the scoreboard`);
	}

	console.log(`${results.length} project(s), ${failures} failure(s); details in ${path.relative(repositoryRoot, path.join(workRoot, "results.json"))}`);
	process.exitCode = failures === 0 ? 0 : 1;
}

main().catch((error) => {
	console.error(error.message);
	process.exitCode = 2;
});
