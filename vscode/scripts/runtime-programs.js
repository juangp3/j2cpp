// Translates each program in test/runtime/<name>/ against the J2C runtime
// (runtime/), builds and runs it, runs the same program with java, and
// compares the results: stdout exactly, stderr without stack frames (J2C
// records none), and the exit status. Arguments come from args.txt, one per
// line. CFLAGS is passed to make, so CI can build with sanitizers.
//
// Usage: node scripts/runtime-programs.js [--program a,b]
const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const extensionDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..");
const cliJar = process.env.J2C_CLI_JAR || path.join(repositoryRoot, "target", "j2c-cli.jar");
const programsRoot = path.join(repositoryRoot, "test", "runtime");
const runtimeDirectory = path.join(repositoryRoot, "runtime");
const workRoot = path.join(repositoryRoot, "target", "runtime-programs");

function run(command, args, options = {}) {
	return childProcess.spawnSync(command, args, { encoding: "utf8", maxBuffer: 1 << 28, ...options });
}

// On Windows without make, build and run in WSL, like corpus.js
const useWsl = run("make", ["--version"]).status !== 0 && process.platform === "win32";
const wslDistribution = process.env.J2C_WSL_DISTRO || "Ubuntu";

function wslPath(file) {
	return file.replace(/^([A-Za-z]):/, (match, drive) => `/mnt/${drive.toLowerCase()}`).replace(/\\/g, "/");
}

function shellQuote(text) {
	return `'${text.replace(/'/g, "'\\''")}'`;
}

function native(command, args, cwd) {
	if (!useWsl) {
		return run(command, args, { cwd });
	}
	const env = process.env.CFLAGS ? `CFLAGS=${shellQuote(process.env.CFLAGS)} ` : "";
	const line = `cd ${shellQuote(wslPath(cwd))} && ${env}${[command, ...args].map(shellQuote).join(" ")}`;
	return run("wsl", ["-d", wslDistribution, "bash", "-c", line]);
}

function normalize(text) {
	return text.replace(/\r\n/g, "\n");
}

// The JVM prints "\tat ..." frames and "\t... n more", indented further under
// suppressed exceptions; J2C records none
function withoutFrames(text) {
	return normalize(text).split("\n").filter((line) => !/^\t+(at |\.\.\. )/.test(line)).join("\n");
}

function check(name) {
	const directory = path.join(programsRoot, name);
	const sources = fs.readdirSync(directory).filter((file) => file.endsWith(".java"));
	const main = sources.find((file) => /static\s+void\s+main\s*\(/.test(fs.readFileSync(path.join(directory, file), "utf8")));
	if (!main) {
		return { name, ok: false, detail: "no class with main" };
	}
	const className = path.basename(main, ".java");
	const argsFile = path.join(directory, "args.txt");
	const args = fs.existsSync(argsFile)
		? normalize(fs.readFileSync(argsFile, "utf8")).split("\n").filter((line) => line.length > 0)
		: [];

	const output = path.join(workRoot, name);
	fs.rmSync(output, { recursive: true, force: true });
	fs.mkdirSync(output, { recursive: true });
	const translate = run("java", ["-jar", cliJar, "--runtime", runtimeDirectory, "--output", output, directory]);
	if (translate.status !== 0) {
		return { name, ok: false, detail: `translation failed: ${(translate.stdout + translate.stderr).trim().split("\n").slice(-3).join(" | ")}` };
	}
	const make = native("make", ["-s", "-j8", "all", "mains"], output);
	if (make.status !== 0) {
		const errors = `${make.stdout}\n${make.stderr}`.split("\n").filter((line) => /error/.test(line));
		return { name, ok: false, detail: `build failed: ${errors.slice(0, 3).join(" | ")}` };
	}
	const cpp = native(`./bin/${className}`, args, output);
	const java = run("java", [path.join(directory, main), ...args], { cwd: directory });

	const problems = [];
	if (normalize(cpp.stdout) !== normalize(java.stdout)) {
		problems.push(`stdout differs\n--- java\n${normalize(java.stdout)}--- c++\n${normalize(cpp.stdout)}`);
	}
	if (withoutFrames(cpp.stderr) !== withoutFrames(java.stderr)) {
		problems.push(`stderr differs\n--- java\n${withoutFrames(java.stderr)}\n--- c++\n${withoutFrames(cpp.stderr)}`);
	}
	if (cpp.status !== java.status) {
		problems.push(`exit status ${cpp.status}, java ${java.status}`);
	}
	return { name, ok: problems.length === 0, detail: problems.join("\n") };
}

const selection = process.argv.indexOf("--program") >= 0
	? process.argv[process.argv.indexOf("--program") + 1].split(",")
	: null;
const programs = fs.readdirSync(programsRoot)
	.filter((name) => fs.statSync(path.join(programsRoot, name)).isDirectory())
	.filter((name) => !selection || selection.includes(name))
	.sort();

let failures = 0;
for (const name of programs) {
	const result = check(name);
	if (!result.ok) {
		failures++;
	}
	console.log(`${result.ok ? "ok  " : "FAIL"} ${name}${result.detail ? `\n${result.detail}` : ""}`);
}
console.log(`${programs.length} program(s), ${failures} failure(s)`);
process.exitCode = failures === 0 ? 0 : 1;
