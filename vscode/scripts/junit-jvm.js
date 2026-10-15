// Runs the JUnit fixtures in test/junit and test/junit-runtime with real
// JUnit on the JVM and compares every test's outcome, and the failure
// messages their expected-results.json files record, with those files: the
// expectations the translated GoogleTest runs are checked against
// (junit-gtest.js) are what JUnit itself does.
//
// The JUnit JARs are downloaded with Maven into target/junit-jvm/lib; they
// are test tools only and never part of the shipped CLI. The fixtures are
// compiled against them, not against the test/junit-api stand-ins.
//
// Usage: node scripts/junit-jvm.js
const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");
const { compare, expectedOutcome, readExpected } = require("./check-gtest");

const extensionDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..");
const fixtureDirectories = ["junit", "junit-runtime"].map((name) => path.join(repositoryRoot, "test", name));
const workRoot = path.join(repositoryRoot, "target", "junit-jvm");
const lib = path.join(workRoot, "lib");
const classes = path.join(workRoot, "classes");
const reports = path.join(workRoot, "reports");

const PLATFORM = "1.10.5";
const ARTIFACTS = [
	// Jupiter and Vintage engines and the launcher, in one JAR
	`org.junit.platform:junit-platform-console-standalone:${PLATFORM}`,
	"junit:junit:4.13.2",
	"org.hamcrest:hamcrest-core:1.3",
];
const MAVEN_DEPENDENCY_PLUGIN = "org.apache.maven.plugins:maven-dependency-plugin:3.6.1";

function run(command, args, options = {}) {
	const result = childProcess.spawnSync(command, args, { encoding: "utf8", maxBuffer: 1 << 28, ...options });
	if (result.error) {
		throw result.error;
	}
	return result;
}

function jarName(artifact) {
	const [, name, version] = artifact.split(":");
	return `${name}-${version}.jar`;
}

function download() {
	const missing = ARTIFACTS.filter((artifact) => !fs.existsSync(path.join(lib, jarName(artifact))));
	if (missing.length === 0) {
		return;
	}
	const maven = process.platform === "win32" ? "mvn.cmd" : "mvn";
	for (const artifact of missing) {
		const result = run(maven, ["-q", `${MAVEN_DEPENDENCY_PLUGIN}:copy`, `-Dartifact=${artifact}`, `-DoutputDirectory=${lib}`],
			{ cwd: repositoryRoot, stdio: "inherit", shell: process.platform === "win32" });
		if (result.status !== 0) {
			throw new Error(`downloading ${artifact} failed`);
		}
	}
}

function javaSources(directory) {
	const sources = [];
	for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
		const file = path.join(directory, entry.name);
		if (entry.isDirectory()) {
			sources.push(...javaSources(file));
		} else if (entry.name.endsWith(".java")) {
			sources.push(file);
		}
	}
	return sources.sort();
}

function decode(text) {
	return text.replace(/&(#x[0-9a-fA-F]+|#[0-9]+|lt|gt|amp|quot|apos);/g, (match, entity) => {
		switch (entity) {
		case "lt": return "<";
		case "gt": return ">";
		case "amp": return "&";
		case "quot": return "\"";
		case "apos": return "'";
		default:
			return String.fromCodePoint(entity[1] === "x" ? parseInt(entity.slice(2), 16) : parseInt(entity.slice(1), 10));
		}
	});
}

function attribute(tag, name) {
	const match = tag.match(new RegExp(`\\s${name}="([^"]*)"`));
	return match ? decode(match[1]) : null;
}

// The legacy XML reports: one <testcase> per test, with <skipped>, <failure>
// or <error> inside when it did not pass
function results(reportDirectory) {
	const actual = {};
	for (const file of fs.readdirSync(reportDirectory).filter((name) => /^TEST-.*\.xml$/.test(name))) {
		const xml = fs.readFileSync(path.join(reportDirectory, file), "utf8");
		const testcase = /<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
		for (let match; (match = testcase.exec(xml)) !== null;) {
			const head = match[1];
			const body = match[3] || "";
			const suite = attribute(head, "classname").replace(/\./g, "_");
			// Jupiter names methods with their parameters: "test()"
			const name = attribute(head, "name").replace(/\(\)$/, "");
			const failure = body.match(/<(failure|error)\b([^>]*)/);
			const result = /<skipped\b/.test(body)
				? { outcome: "skip" }
				: failure
					? { outcome: "fail", message: attribute(failure[2], "message") || "" }
					: { outcome: "pass" };
			actual[suite] = actual[suite] || {};
			actual[suite][name] = result;
		}
	}
	return actual;
}

function main() {
	download();
	fs.rmSync(classes, { recursive: true, force: true });
	fs.rmSync(reports, { recursive: true, force: true });
	fs.mkdirSync(classes, { recursive: true });

	const classPath = ARTIFACTS.map((artifact) => path.join(lib, jarName(artifact))).join(path.delimiter);
	const javac = run("javac", ["--release", "17", "-nowarn", "-d", classes, "-cp", classPath, ...fixtureDirectories.flatMap(javaSources)]);
	if (javac.status !== 0) {
		throw new Error(`javac failed:\n${javac.stdout}${javac.stderr}`);
	}

	const launcher = run("java", [
		"-jar", path.join(lib, jarName(ARTIFACTS[0])),
		"execute",
		"--disable-banner",
		"--details=none",
		"--class-path", [classes, ...ARTIFACTS.slice(1).map((artifact) => path.join(lib, jarName(artifact)))].join(path.delimiter),
		"--select-package", "se.arnetheduck.j2c.test.junit",
		"--reports-dir", reports,
	]);
	// The launcher exits with 1 when tests fail, which some do on purpose
	if (launcher.status !== 0 && launcher.status !== 1) {
		throw new Error(`the JUnit launcher failed (${launcher.status}):\n${launcher.stdout}${launcher.stderr}`);
	}

	const expected = readExpected(fixtureDirectories);
	const problems = compare(results(reports), expected);
	if (problems.length > 0) {
		console.error(problems.join("\n"));
		console.error("Real JUnit disagrees with the expected results in test/junit and test/junit-runtime");
		process.exitCode = 1;
		return;
	}
	const count = Object.values(expected).reduce((sum, tests) => sum + Object.keys(tests).length, 0);
	const messages = Object.values(expected).reduce((sum, tests) =>
		sum + Object.values(tests).filter((test) => expectedOutcome(test).message !== undefined).length, 0);
	console.log(`JUnit ${PLATFORM} on the JVM matches the expected outcomes of ${count} test(s) and ${messages} failure message(s)`);
}

main();
