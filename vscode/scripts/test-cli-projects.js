// Project auto-setup checks for test-cli.js: Maven and Gradle projects in
// test/projects are detected, their source roots and class paths are used,
// and a missing or failing build tool is a warning, not an error.
//
// The fixtures are copied into a temporary directory, so the build tools
// never write into the repository. Checks that run Maven need mvn (and
// network access the first time, for commons-cli and JUnit); they are
// skipped with a message when it is missing. Gradle is replaced by a stub
// gradlew that prints what J2C's init script would.
const assert = require("assert");
const childProcess = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const repositoryRoot = path.resolve(__dirname, "..", "..");
const fixturesDirectory = path.join(repositoryRoot, "test", "projects");
const isWindows = process.platform === "win32";

/** Copies test/projects into directory and returns the copies' paths */
function copyFixtures(directory) {
	fs.cpSync(fixturesDirectory, directory, { recursive: true });
	const copies = {};
	for (const name of fs.readdirSync(directory)) {
		copies[name] = path.join(directory, name);
	}
	return copies;
}

function mavenAvailable() {
	const result = childProcess.spawnSync(isWindows ? "mvn.cmd" : "mvn", ["-v"],
		{ encoding: "utf8", shell: isWindows });
	return result.status === 0;
}

/** An environment whose PATH holds only the Java executable's directory */
function javaOnlyEnvironment(extra = {}) {
	const result = childProcess.spawnSync("java", ["-XshowSettings:properties", "-version"],
		{ encoding: "utf8" });
	const match = /java\.home = (.+)/.exec(result.stderr);
	assert(match, "Could not find java.home");
	const environment = {};
	for (const [key, value] of Object.entries(process.env)) {
		if (key.toUpperCase() !== "PATH") {
			environment[key] = value;
		}
	}
	environment.PATH = path.join(match[1].trim(), "bin");
	return { ...environment, ...extra };
}

/**
 * A gradlew (gradlew.bat on Windows) in directory that logs its arguments to
 * gradlew-args.txt and prints lines in the init script's format. With
 * onlyOffline it fails unless --offline is given; with fail it always fails.
 */
function writeGradleStub(directory, lines, options = {}) {
	if (isWindows) {
		const script = ["@echo off", "echo %*>> \"%~dp0gradlew-args.txt\""];
		if (options.fail) {
			script.push("echo FAILURE: Build failed with an exception.", "exit /b 1");
		} else if (options.onlyOffline) {
			script.push("echo %* | findstr /C:\"--offline\" >nul || exit /b 1");
		}
		script.push(...lines.map((line) => `echo ${line}`), "exit /b 0", "");
		fs.writeFileSync(path.join(directory, "gradlew.bat"), script.join("\r\n"));
	} else {
		const script = ["#!/bin/sh", "echo \"$*\" >> \"$(dirname \"$0\")/gradlew-args.txt\""];
		if (options.fail) {
			script.push("echo 'FAILURE: Build failed with an exception.'", "exit 1");
		} else if (options.onlyOffline) {
			script.push("case \"$*\" in *--offline*) ;; *) exit 1 ;; esac");
		}
		script.push(...lines.map((line) => `echo '${line}'`), "exit 0", "");
		const file = path.join(directory, "gradlew");
		fs.writeFileSync(file, script.join("\n"));
		fs.chmodSync(file, 0o755);
	}
}

/** commons-cli 1.6.0 from the local Maven repository, if it is there */
function commonsCliJar() {
	const jar = path.join(os.homedir(), ".m2", "repository", "commons-cli", "commons-cli",
		"1.6.0", "commons-cli-1.6.0.jar");
	return fs.existsSync(jar) ? jar : null;
}

function report(result) {
	const lines = (result.stdout || "").trim().split(/\r?\n/);
	try {
		return JSON.parse(lines[lines.length - 1]);
	} catch (error) {
		throw new Error(`No JSON report (exit ${result.status}): ${result.stdout}\n${result.stderr}`);
	}
}

function samePath(a, b) {
	return path.resolve(a).toLowerCase() === path.resolve(b).toLowerCase();
}

function assertPaths(actual, expected, message) {
	assert.strictEqual(actual.length, expected.length, `${message}: ${JSON.stringify(actual)}`);
	expected.forEach((file, i) => assert(samePath(actual[i], file),
		`${message}: expected ${file}, got ${actual[i]}`));
}

function checkProjects(runCli, tempDirectory) {
	const fixtures = copyFixtures(path.join(tempDirectory, "projects"));
	const cache = path.join(tempDirectory, "project cache");
	const env = (extra = {}) => ({ env: { ...process.env, J2C_CACHE_DIR: cache, ...extra } });
	const isCommonsCli = (entry) => path.basename(entry) === "commons-cli-1.6.0.jar";
	const warningId = "J2C-PROJECT-CLASSPATH-UNRESOLVED";

	// Without a build, nothing is detected
	const plain = path.join(tempDirectory, "plain sources");
	fs.mkdirSync(path.join(plain, "p"), { recursive: true });
	fs.writeFileSync(path.join(plain, "p", "A.java"), "package p;\npublic class A {}\n");
	const plainReport = report(runCli(["--check", "--format", "json", plain], env()));
	assert.strictEqual(plainReport.status, "ok");
	assert.deepStrictEqual(plainReport.projects, []);
	assert.deepStrictEqual(plainReport.warnings, []);

	// Invalid options are usage errors
	const badMode = runCli(["--check", "--project", path.join(tempDirectory, "no such dir"), plain], env());
	assert.strictEqual(badMode.status, 2);
	assert(badMode.stderr.includes("Invalid --project value"), badMode.stderr);
	const noBuild = runCli(["--check", "--format", "json", "--project", plain, plain], env());
	assert.strictEqual(noBuild.status, 2);
	assert(report(noBuild).message.includes("No Maven or Gradle build"), noBuild.stdout);

	// Maven missing: the source roots are still used, with a warning, and the
	// dependency types do not resolve
	const missingTool = runCli(["--check", "--format", "json", fixtures["maven-simple"]],
		{ env: javaOnlyEnvironment({ J2C_CACHE_DIR: path.join(tempDirectory, "empty cache") }) });
	assert.strictEqual(missingTool.status, 1, missingTool.stderr);
	assert(missingTool.stderr.includes("J2C: Maven project in"), missingTool.stderr);
	const missingReport = report(missingTool);
	assert.strictEqual(missingReport.projects.length, 1);
	const missingProject = missingReport.projects[0];
	assert.strictEqual(missingProject.buildTool, "maven");
	assert.strictEqual(missingProject.classPathSource, "unresolved");
	assert.strictEqual(missingProject.command, null);
	assertPaths(missingProject.sourceRoots, [path.join(fixtures["maven-simple"], "src", "main", "java")],
		"Maven source roots");
	assertPaths(missingProject.testRoots, [path.join(fixtures["maven-simple"], "src", "test", "java")],
		"Maven test roots");
	assert.strictEqual(missingReport.warnings.length, 1);
	const warning = missingReport.warnings[0];
	assert.strictEqual(warning.id, warningId);
	assert.strictEqual(warning.severity, "warning");
	assert(samePath(warning.file, path.join(fixtures["maven-simple"], "pom.xml")));
	assert(warning.message.includes("Maven was not found"), warning.message);
	assert(warning.hint.includes("--classpath"));
	assert(missingReport.problems.length > 0);
	assert(missingReport.problems.every((problem) => problem.id === "J2C-COMPILE-ERROR"));
	assert(missingReport.problems.some((problem) => problem.message.includes("Options cannot be resolved")));

	// --no-build-tool never runs the tool
	const offReport = report(runCli(["--check", "--format", "json", "--no-build-tool",
		fixtures["maven-simple"]], env()));
	assert.strictEqual(offReport.warnings[0].id, warningId);
	assert(offReport.warnings[0].message.includes("--no-build-tool"));

	// --project none: the folder is an ordinary source folder again
	const noneReport = report(runCli(["--check", "--format", "json", "--project", "none",
		fixtures["maven-simple"]], env()));
	assert.deepStrictEqual(noneReport.projects, []);
	assert.deepStrictEqual(noneReport.warnings, []);

	if (mavenAvailable()) {
		// Main sources only, with the compile class path
		const simple = runCli(["--check", "--format", "json", fixtures["maven-simple"]], env());
		assert.strictEqual(simple.status, 0, `${simple.stdout}\n${simple.stderr}`);
		assert(simple.stderr.includes("resolving the class path:"), simple.stderr);
		const simpleReport = report(simple);
		assert.strictEqual(simpleReport.status, "ok");
		assert.strictEqual(simpleReport.files, 1);
		assert.deepStrictEqual(simpleReport.warnings, []);
		const simpleProject = simpleReport.projects[0];
		assert.deepStrictEqual(simpleProject.modules, ["."]);
		assert.strictEqual(simpleProject.classPathSource, "maven");
		assert(simpleProject.command.includes("build-classpath"), simpleProject.command);
		assert(simpleProject.command.includes("-DincludeScope=compile"), simpleProject.command);
		assert(simpleProject.classPath.some(isCommonsCli), JSON.stringify(simpleProject.classPath));
		assert(!simpleProject.classPath.some((entry) => entry.includes("junit")),
			"Test dependencies stay off the main class path");

		// --tests adds the test roots and the test class path, then the cache
		const testsReport = report(runCli(["--check", "--tests", "--format", "json",
			fixtures["maven-simple"]], env()));
		assert.strictEqual(testsReport.status, "ok", JSON.stringify(testsReport.problems));
		assert.strictEqual(testsReport.files, 2);
		assert(testsReport.projects[0].classPath.some((entry) => path.basename(entry).startsWith("junit-jupiter-api")));
		const cached = runCli(["--check", "--tests", "--format", "json", fixtures["maven-simple"]], env());
		assert.strictEqual(report(cached).projects[0].classPathSource, "cache");
		assert(!cached.stderr.includes("resolving the class path:"), cached.stderr);
		// A cached class path is used even with --no-build-tool
		const cachedOff = report(runCli(["--check", "--tests", "--no-build-tool", "--format", "json",
			fixtures["maven-simple"]], env()));
		assert.strictEqual(cachedOff.projects[0].classPathSource, "cache");
		assert.deepStrictEqual(cachedOff.warnings, []);
		// Changing a build file invalidates it
		const pom = path.join(fixtures["maven-simple"], "pom.xml");
		fs.appendFileSync(pom, "<!-- changed -->\n");
		const changed = report(runCli(["--check", "--tests", "--no-build-tool", "--format", "json",
			fixtures["maven-simple"]], env()));
		assert.strictEqual(changed.projects[0].classPathSource, "unresolved");

		// A file input with --project <dir> uses the build's roots and class path
		const appFile = path.join(fixtures["maven-simple"], "src", "main", "java", "simple", "App.java");
		const fileReport = report(runCli(["--check", "--format", "json", "--project",
			fixtures["maven-simple"], appFile], env()));
		assert.strictEqual(fileReport.status, "ok", JSON.stringify(fileReport.problems));
		assert.strictEqual(fileReport.files, 1);
		// and a package folder inside a source root is not made a root itself
		const packageReport = report(runCli(["--check", "--format", "json", "--project",
			fixtures["maven-simple"], path.dirname(appFile)], env()));
		assert.strictEqual(packageReport.status, "ok", JSON.stringify(packageReport.problems));

		// Modules, a sourceDirectory override and a build-helper source root;
		// the sibling module is translated from source, not from its classes
		const multiReport = report(runCli(["--check", "--format", "json", fixtures["maven-multi"]], env()));
		assert.strictEqual(multiReport.status, "ok", JSON.stringify(multiReport.problems));
		assert.strictEqual(multiReport.files, 3);
		const multiProject = multiReport.projects[0];
		assert.deepStrictEqual(multiProject.modules, [".", "core", "app"]);
		assertPaths(multiProject.sourceRoots, [
			path.join(fixtures["maven-multi"], "core", "src", "main", "java"),
			path.join(fixtures["maven-multi"], "core", "src", "extra", "java"),
			path.join(fixtures["maven-multi"], "app", "src", "java")
		], "Maven module source roots");
		assert.strictEqual(multiProject.classPath.length, 1, JSON.stringify(multiProject.classPath));
		assert(isCommonsCli(multiProject.classPath[0]));
		assert(!fs.existsSync(path.join(fixtures["maven-multi"], "core", "target", "classes")),
			"Resolving the class path does not compile");

		const multiOutput = path.join(tempDirectory, "maven multi output");
		fs.mkdirSync(multiOutput);
		const translated = runCli(["--format", "json", "--output", multiOutput, fixtures["maven-multi"]], env());
		assert.strictEqual(translated.status, 0, translated.stderr);
		const translatedReport = report(translated);
		assert(translatedReport.generatedFiles.includes("src/multi/app/Main.cpp"));
		assert(translatedReport.generatedFiles.includes("src/multi/extra/Names.cpp"));
		assert.strictEqual(translatedReport.projects[0].classPathSource, "cache");
	} else {
		console.log("Maven class path checks skipped (mvn not found on the PATH)");
	}

	// Gradle, through a stub wrapper: the init script's output is used for the
	// source roots and class path, minus the build's own output
	const gradle = fixtures["gradle-simple"];
	const jar = commonsCliJar();
	const libClasses = path.join(gradle, "lib", "build", "classes", "java", "main");
	fs.mkdirSync(libClasses, { recursive: true });
	const stubLines = [
		`j2c-main-source=${path.join(gradle, "src", "main", "java")}`,
		`j2c-main-source=${path.join(gradle, "lib", "src", "main", "java")}`,
		`j2c-main-classpath=${libClasses}`,
		...(jar ? [`j2c-main-classpath=${jar}`] : [])
	];
	writeGradleStub(gradle, stubLines);
	const gradleRun = runCli(["--check", "--format", "json", gradle], env());
	assert(gradleRun.stderr.includes("J2C: Gradle project in"), gradleRun.stderr);
	const gradleReport = report(gradleRun);
	const gradleProject = gradleReport.projects[0];
	assert.strictEqual(gradleProject.buildTool, "gradle");
	assert.deepStrictEqual(gradleProject.modules, [".", "lib"]);
	assertPaths(gradleProject.sourceRoots, [
		path.join(gradle, "src", "main", "java"),
		path.join(gradle, "lib", "src", "main", "java")
	], "Gradle source roots");
	assert.strictEqual(gradleProject.classPathSource, "gradle");
	assert(gradleProject.command.includes("--init-script"), gradleProject.command);
	assert(gradleProject.command.includes("j2cProjectInfo"), gradleProject.command);
	assert(!gradleProject.classPath.some((entry) => samePath(entry, libClasses)),
		"The build's own classes are not dependencies");
	const gradleArgs = fs.readFileSync(path.join(gradle, "gradlew-args.txt"), "utf8");
	assert(gradleArgs.includes("--init-script") && gradleArgs.includes("-q"), gradleArgs);
	if (jar) {
		assert.strictEqual(gradleReport.status, "ok", JSON.stringify(gradleReport.problems));
		assertPaths(gradleProject.classPath, [jar], "Gradle class path");
	} else {
		console.log("Gradle class path use not checked (commons-cli 1.6.0 not in ~/.m2)");
	}

	// Gradle failing online is retried offline
	const offline = path.join(tempDirectory, "gradle offline");
	fs.cpSync(fixturesDirectory + path.sep + "gradle-simple", offline, { recursive: true });
	writeGradleStub(offline, [`j2c-main-source=${path.join(offline, "src", "main", "java")}`],
		{ onlyOffline: true });
	const offlineReport = report(runCli(["--check", "--format", "json", "--project", offline,
		path.join(offline, "src", "main", "java", "gradle", "simple", "App.java")], env()));
	assert.strictEqual(offlineReport.projects[0].classPathSource, "gradle");
	assert(offlineReport.projects[0].command.includes("--offline"));
	assert.strictEqual(fs.readFileSync(path.join(offline, "gradlew-args.txt"), "utf8")
		.trim().split(/\r?\n/).length, 2);

	// Gradle failing both ways: a warning, and the default source roots
	const failing = path.join(tempDirectory, "gradle failing");
	fs.cpSync(fixturesDirectory + path.sep + "gradle-simple", failing, { recursive: true });
	writeGradleStub(failing, [], { fail: true });
	const failingRun = runCli(["--check", "--format", "json", failing], env());
	assert.strictEqual(failingRun.status, 1);
	const failingReport = report(failingRun);
	assert.strictEqual(failingReport.warnings[0].id, warningId);
	assert(failingReport.warnings[0].message.includes("exit status 1"), failingReport.warnings[0].message);
	assertPaths(failingReport.projects[0].sourceRoots, [
		path.join(failing, "src", "main", "java"),
		path.join(failing, "lib", "src", "main", "java")
	], "Gradle default source roots");

	// A failed translation still reports the detected build and its warning
	const failedOutput = path.join(tempDirectory, "failed project output");
	fs.mkdirSync(failedOutput);
	const failedTranslation = runCli(["--format", "json", "--output", failedOutput, failing], env());
	assert.strictEqual(failedTranslation.status, 1);
	const failedReport = report(failedTranslation);
	assert.strictEqual(failedReport.status, "problems");
	assert.strictEqual(failedReport.warnings[0].id, warningId);
	assert.strictEqual(failedReport.projects[0].buildTool, "gradle");

	const capabilities = report(runCli(["capabilities"]));
	assert.deepStrictEqual(capabilities.buildTools, ["maven", "gradle"]);
	assert(capabilities.problemIds.includes(warningId));
	console.log("Project auto-setup checks passed");
}

module.exports = {
	checkProjects,
	copyFixtures,
	javaOnlyEnvironment,
	mavenAvailable,
	writeGradleStub,
	commonsCliJar
};
