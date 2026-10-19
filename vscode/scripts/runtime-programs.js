// Translates each program in test/runtime/<name>/ against the J2C runtime
// (runtime/), builds and runs it, runs the same program with java, and
// compares the results: stdout exactly, stderr without stack frames (J2C
// records none), and the exit status. Arguments come from args.txt, one per
// line, and standard input from input.txt (its bytes as they are). Each run
// starts in a new, empty working directory (run-java and run-cpp under the
// output), so programs may create files with relative paths. CFLAGS is
// passed to make, so CI can build with sanitizers.
//
// On Windows without make, the C++ program builds and runs in WSL. java runs
// there too when WSL has one (J2C_WSL_JAVA names it, otherwise `java` on
// WSL's PATH), so file paths and error messages match Linux; otherwise the
// Windows java runs, and programs that print OS messages may differ.
//
// --gc boehm builds with make J2C_GC=boehm, optimized (-O2, where the
// compiler keeps fewer stale pointers on the stack), and collects every
// J2C_GC_STRESS allocations (default 1000), so an object the collector
// cannot see is freed while still in use and the output differs. A program
// with max-rss-mb.txt has its peak resident memory measured with
// /usr/bin/time: it must stay under that many MB with the collector and
// exceed it without, which shows the bound means something.
//
// Usage: node scripts/runtime-programs.js [--program a,b] [--gc boehm]
const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const extensionDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..");
const cliJar = process.env.J2C_CLI_JAR || path.join(repositoryRoot, "target", "j2c-cli.jar");
const programsRoot = path.join(repositoryRoot, "test", "runtime");
const runtimeDirectory = path.join(repositoryRoot, "runtime");

function option(name) {
	const index = process.argv.indexOf(name);
	return index >= 0 ? process.argv[index + 1] : null;
}

const gc = option("--gc");
if (gc !== null && gc !== "boehm") {
	console.error(`--gc must be boehm, not ${gc}`);
	process.exit(2);
}
const gcStress = process.env.J2C_GC_STRESS || "1000";
const cflags = [process.env.CFLAGS, gc ? `-O2 -DJ2C_GC_STRESS=${gcStress}` : null].filter(Boolean).join(" ");
const workRoot = path.join(repositoryRoot, "target", gc ? "runtime-programs-gc" : "runtime-programs");

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

function native(command, args, cwd, input) {
	if (!useWsl) {
		return run(command, args, { cwd, input, env: { ...process.env, CFLAGS: cflags } });
	}
	const env = cflags ? `CFLAGS=${shellQuote(cflags)} ` : "";
	const line = `cd ${shellQuote(wslPath(cwd))} && ${env}${[command, ...args].map(shellQuote).join(" ")}`;
	return run("wsl", ["-d", wslDistribution, "bash", "-c", line], { input });
}

// The java that runs next to the C++ program: WSL's when the program runs there
function findWslJava() {
	if (!useWsl) {
		return null;
	}
	if (process.env.J2C_WSL_JAVA) {
		return process.env.J2C_WSL_JAVA;
	}
	const which = run("wsl", ["-d", wslDistribution, "bash", "-lc", "command -v java"]);
	return which.status === 0 && which.stdout.trim() ? which.stdout.trim() : null;
}
const wslJava = findWslJava();
if (useWsl && !wslJava) {
	// Paths and error messages then differ from the Linux C++ program's
	console.error("warning: WSL has no java, so programs run with Windows java; "
		+ "file and input programs will differ (set J2C_WSL_JAVA to a JDK 17 in WSL)");
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
	const make = native("make", ["-s", "-j8", ...(gc ? [`J2C_GC=${gc}`] : []), "all", "mains"], output);
	if (make.status !== 0) {
		const errors = `${make.stdout}\n${make.stderr}`.split("\n").filter((line) => /error/.test(line));
		return { name, ok: false, detail: `build failed: ${errors.slice(0, 3).join(" | ")}` };
	}
	const rssFile = path.join(directory, "max-rss-mb.txt");
	const maxRss = fs.existsSync(rssFile) ? Number(fs.readFileSync(rssFile, "utf8").trim()) : null;
	const inputFile = path.join(directory, "input.txt");
	const input = fs.existsSync(inputFile) ? fs.readFileSync(inputFile) : undefined;
	// Fresh working directories, so files one run writes cannot affect the other
	const cppRun = path.join(output, "run-cpp");
	const javaRun = path.join(output, "run-java");
	fs.mkdirSync(cppRun);
	fs.mkdirSync(javaRun);
	const binary = useWsl ? wslPath(path.join(output, "bin", className)) : path.join(output, "bin", className);
	// GNU time writes the peak resident set size in KB to rss.txt
	const cpp = maxRss === null
		? native(binary, args, cppRun, input)
		: native("/usr/bin/time", ["-f", "%M", "-o", "rss.txt", binary, ...args], cppRun, input);
	// The runtime formats numbers like Locale.US (String.format, printf), so
	// the JVM gets that locale whatever the machine's settings are
	const locale = ["-Duser.language=en", "-Duser.country=US", "-Duser.language.format=en", "-Duser.country.format=US"];
	const source = path.join(directory, main);
	const java = wslJava
		? native(wslJava, [...locale, wslPath(source), ...args], javaRun, input)
		: run("java", [...locale, source, ...args], { cwd: javaRun, input });

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
	let note = "";
	if (maxRss !== null) {
		const lines = normalize(fs.readFileSync(path.join(cppRun, "rss.txt"), "utf8")).trim().split("\n");
		const rss = Math.round(Number(lines[lines.length - 1]) / 1024);
		note = ` (peak RSS ${rss} MB, bound ${maxRss} MB)`;
		if (gc && !(rss < maxRss)) {
			problems.push(`peak RSS ${rss} MB with the collector, bound ${maxRss} MB`);
		} else if (!gc && !(rss >= maxRss)) {
			problems.push(`peak RSS ${rss} MB without the collector is under the bound of ${maxRss} MB`);
		}
	}
	return { name, ok: problems.length === 0, note, detail: problems.join("\n") };
}

const selection = option("--program") ? option("--program").split(",") : null;
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
	console.log(`${result.ok ? "ok  " : "FAIL"} ${name}${result.note || ""}${result.detail ? `\n${result.detail}` : ""}`);
}
console.log(`${programs.length} program(s)${gc ? ` with J2C_GC=${gc}` : ""}, ${failures} failure(s)${wslJava ? `, java in WSL (${wslJava})` : ""}`);
process.exitCode = failures === 0 ? 0 : 1;
