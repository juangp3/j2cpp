const assert = require("assert");
const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const extensionDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..");
const cliJar = path.join(repositoryRoot, "target", "j2c-cli.jar");
const noticesFile = path.join(repositoryRoot, "THIRD-PARTY-NOTICES.txt");
const licensesDirectory = path.join(repositoryRoot, "licenses");
const dependencyNoticesDirectory = path.join(licensesDirectory, "dependencies");
const dependencyListFile = path.join(repositoryRoot, "target", "runtime-dependencies.txt");
const jarLicensePrefix = "META-INF/j2c-licenses/";

function filesUnder(directory) {
	return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
		const entryPath = path.join(directory, entry.name);
		return entry.isDirectory() ? filesUnder(entryPath) : [entryPath];
	});
}

// Reads entry names from the ZIP central directory; the CLI JAR is not ZIP64.
function jarEntries(jarPath) {
	const data = fs.readFileSync(jarPath);
	let end = data.length - 22;
	while (end >= 0 && data.readUInt32LE(end) !== 0x06054b50) {
		end--;
	}
	assert(end >= 0, `Not a ZIP file: ${jarPath}`);
	const count = data.readUInt16LE(end + 10);
	let offset = data.readUInt32LE(end + 16);
	const names = [];
	for (let index = 0; index < count; index++) {
		assert.strictEqual(data.readUInt32LE(offset), 0x02014b50, "Corrupt central directory");
		const nameLength = data.readUInt16LE(offset + 28);
		const extraLength = data.readUInt16LE(offset + 30);
		const commentLength = data.readUInt16LE(offset + 32);
		names.push(data.toString("utf8", offset + 46, offset + 46 + nameLength));
		offset += 46 + nameLength + extraLength + commentLength;
	}
	return new Set(names);
}

function resolvedDependencies() {
	const maven = process.platform === "win32" ? "mvn.cmd" : "mvn";
	const list = childProcess.spawnSync(
		maven,
		["-q", "dependency:list", "-DincludeScope=runtime", `-DoutputFile=${dependencyListFile}`],
		{ cwd: repositoryRoot, stdio: "inherit", shell: process.platform === "win32" }
	);
	if (list.error) {
		throw list.error;
	}
	assert.strictEqual(list.status, 0, "mvn dependency:list failed");
	return fs.readFileSync(dependencyListFile, "utf8")
		.split(/\r?\n/)
		.map((line) => line.match(/^\s*([\w.-]+):([\w.-]+):jar:([\w.-]+):(?:compile|runtime)/))
		.filter(Boolean)
		.map(([, group, artifact, version]) => `${group}:${artifact}:${version}`)
		.sort();
}

function inventoriedDependencies() {
	return fs.readFileSync(noticesFile, "utf8")
		.split(/\r?\n/)
		.map((line) => line.match(/^- ([\w.-]+:[\w.-]+:[\w.-]+)/))
		.filter(Boolean)
		.map(([, coordinates]) => coordinates)
		.sort();
}

const resolved = resolvedDependencies();
assert(resolved.length > 0, "No runtime dependencies resolved");
assert.deepStrictEqual(
	inventoriedDependencies(),
	resolved,
	"THIRD-PARTY-NOTICES.txt does not match the resolved runtime dependencies"
);

const expectedNoticeDirectories = resolved
	.map((coordinates) => {
		const [, artifact, version] = coordinates.split(":");
		return `${artifact}-${version}`;
	})
	.sort();
assert.deepStrictEqual(
	fs.readdirSync(dependencyNoticesDirectory).sort(),
	expectedNoticeDirectories,
	"licenses/dependencies does not match the resolved runtime dependencies"
);
for (const directory of expectedNoticeDirectories) {
	assert(
		filesUnder(path.join(dependencyNoticesDirectory, directory)).length > 0,
		`No notice files for ${directory}`
	);
}

const entries = jarEntries(cliJar);
const expectedJarEntries = [
	"LICENSE",
	"THIRD-PARTY-NOTICES.txt",
	...filesUnder(licensesDirectory).map((file) =>
		path.posix.join("licenses", ...path.relative(licensesDirectory, file).split(path.sep))
	)
].map((name) => jarLicensePrefix + name);
for (const name of expectedJarEntries) {
	assert(entries.has(name), `CLI JAR is missing ${name}`);
}
for (const name of ["about.html", "META-INF/LICENSE", "META-INF/NOTICE"]) {
	assert(!entries.has(name), `CLI JAR contains an unattributed shaded ${name}`);
}

console.log(
	`License inventory matches ${resolved.length} runtime dependencies; ` +
	`${expectedJarEntries.length} license files are bundled in the CLI JAR.`
);
