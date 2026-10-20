// Copies the CLI JAR (build it first with mvn package) and the license files
// into the package before npm pack or npm publish.
const fs = require("fs");
const path = require("path");

const packageDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(packageDirectory, "..", "..");
const jar = path.join(repositoryRoot, "target", "j2c-cli.jar");
if (!fs.existsSync(jar)) {
	console.error(`${jar} not found: run mvn -q package -DskipTests in ${repositoryRoot} first`);
	process.exit(1);
}
fs.mkdirSync(path.join(packageDirectory, "lib"), { recursive: true });
fs.copyFileSync(jar, path.join(packageDirectory, "lib", "j2c-cli.jar"));
for (const name of ["LICENSE", "THIRD-PARTY-NOTICES.txt"]) {
	fs.copyFileSync(path.join(repositoryRoot, name), path.join(packageDirectory, name));
}
