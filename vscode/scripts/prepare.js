const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const extensionDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..");
const maven = process.platform === "win32" ? "mvn.cmd" : "mvn";
const build = childProcess.spawnSync(maven, ["-DskipTests", "package"], {
	cwd: repositoryRoot,
	stdio: "inherit",
	shell: process.platform === "win32"
});
if (build.error) {
	throw build.error;
}
if (build.status !== 0) {
	process.exit(build.status || 1);
}

const licenses = childProcess.spawnSync(
	process.execPath,
	[path.join(extensionDirectory, "scripts", "check-licenses.js")],
	{ stdio: "inherit" }
);
if (licenses.error) {
	throw licenses.error;
}
if (licenses.status !== 0) {
	process.exit(licenses.status || 1);
}

const tests = childProcess.spawnSync(
	process.execPath,
	[path.join(extensionDirectory, "scripts", "test-cli.js")],
	{ stdio: "inherit" }
);
if (tests.error) {
	throw tests.error;
}
if (tests.status !== 0) {
	process.exit(tests.status || 1);
}

const extensionTests = childProcess.spawnSync(
	process.execPath,
	[path.join(extensionDirectory, "scripts", "test-extension.js")],
	{ stdio: "inherit" }
);
if (extensionTests.error) {
	throw extensionTests.error;
}
if (extensionTests.status !== 0) {
	process.exit(extensionTests.status || 1);
}

const cliJar = path.join(repositoryRoot, "target", "j2c-cli.jar");
const extensionLib = path.join(extensionDirectory, "lib");
fs.mkdirSync(extensionLib, { recursive: true });
fs.copyFileSync(cliJar, path.join(extensionLib, "j2c-cli.jar"));
fs.copyFileSync(
	path.join(repositoryRoot, "LICENSE"),
	path.join(extensionDirectory, "LICENSE")
);
fs.copyFileSync(
	path.join(repositoryRoot, "THIRD-PARTY-NOTICES.txt"),
	path.join(extensionDirectory, "THIRD-PARTY-NOTICES.txt")
);
fs.copyFileSync(
	path.join(repositoryRoot, "CHANGELOG.md"),
	path.join(extensionDirectory, "CHANGELOG.md")
);
fs.rmSync(path.join(extensionDirectory, "licenses"), { recursive: true, force: true });
fs.cpSync(
	path.join(repositoryRoot, "licenses"),
	path.join(extensionDirectory, "licenses"),
	{ recursive: true }
);
