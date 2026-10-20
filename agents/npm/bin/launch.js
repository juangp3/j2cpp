// Runs the bundled J2C CLI JAR with Java 11 or newer, passing stdin, stdout,
// stderr and the exit status through. Java is J2C_JAVA, else
// $JAVA_HOME/bin/java, else java on the PATH; J2C_JAR overrides the JAR.
const childProcess = require("child_process");
const path = require("path");

function javaExecutable() {
	if (process.env.J2C_JAVA) {
		return process.env.J2C_JAVA;
	}
	if (process.env.JAVA_HOME) {
		return path.join(process.env.JAVA_HOME, "bin", process.platform === "win32" ? "java.exe" : "java");
	}
	return "java";
}

function launch(args) {
	const jar = process.env.J2C_JAR || path.join(__dirname, "..", "lib", "j2c-cli.jar");
	const java = javaExecutable();
	const child = childProcess.spawn(java, ["-jar", jar, ...args], { stdio: "inherit" });
	child.on("error", (error) => {
		process.stderr.write(`j2c: could not run ${java} (${error.message}). J2C needs Java 11 or `
			+ "newer: install it, or set JAVA_HOME or J2C_JAVA.\n");
		process.exit(1);
	});
	for (const signal of ["SIGINT", "SIGTERM"]) {
		process.on(signal, () => child.kill(signal));
	}
	child.on("exit", (code, signal) => {
		process.exit(code === null ? (signal ? 1 : 0) : code);
	});
}

module.exports = { launch };
