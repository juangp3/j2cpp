// --line-directives and j2c build checks for test-cli.js.
//
// The directives must only add lines: removing them gives the output without
// the option, byte for byte. j2c build's parsing is checked with a stub make
// that prints canned GCC output; a real build (make and g++, in WSL on
// Windows) of test/build, with an error put into the generated C++, is
// checked when make can be run, and skipped with a message otherwise.
const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

const repositoryRoot = path.resolve(__dirname, "..", "..");
const sourceRoot = path.join(repositoryRoot, "test", "src");
const fixtureDirectory = path.join(sourceRoot, "se", "arnetheduck", "j2c", "test");
const buildSources = path.join(repositoryRoot, "test", "build", "src");
const counterJava = path.join(buildSources, "builddemo", "Counter.java");
const isWindows = process.platform === "win32";
const directivePattern = /^#line (\d+) "((?:[^"\\]|\\.)*)"\r?$/;

function filesUnder(directory) {
	return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
		const entryPath = path.join(directory, entry.name);
		return entry.isDirectory() ? filesUnder(entryPath) : [entryPath];
	});
}

/**
 * The file and line the compiler assumes for each physical line of a
 * generated file (index 0 is line 1), following its directives
 */
function presumedLines(output, file) {
	const self = path.relative(output, file).split(path.sep).join("/");
	const lines = fs.readFileSync(file, "utf8").split("\n");
	let current = self;
	let line = 1;
	return lines.map((text, index) => {
		const directive = directivePattern.exec(text);
		const presumed = { file: current, line, text, directive: Boolean(directive) };
		if (directive) {
			current = directive[2].replace(/\\(.)/g, "$1");
			line = Number(directive[1]);
			// A directive back to the file itself gives the real next line
			if (current === self) {
				assert.strictEqual(line, index + 2, `${file}:${index + 1}: ${text}`);
			} else {
				assert(current.endsWith(".java"), `${file}:${index + 1}: ${text}`);
				assert(fs.existsSync(path.resolve(output, current)), `${file}: ${current}`);
			}
		} else {
			line++;
		}
		return presumed;
	});
}

/** The Java line (1-based) of the first line containing text */
function javaLine(file, text) {
	const index = fs.readFileSync(file, "utf8").split(/\r?\n/).findIndex((line) => line.includes(text));
	assert(index >= 0, `${text} not in ${file}`);
	return index + 1;
}

/** Where the compiler puts the generated line containing text */
function presumedAt(output, file, text) {
	const presumed = presumedLines(output, file).find((line) => !line.directive && line.text.includes(text));
	assert(presumed, `${text} not in ${file}`);
	return { file: path.resolve(output, presumed.file), line: presumed.line };
}

function checkLineDirectives(runCli, tempDirectory) {
	// The whole test tree, with and without directives, under one name
	const plain = path.join(tempDirectory, "plain output");
	const lines = path.join(tempDirectory, "line output");
	fs.mkdirSync(plain);
	fs.mkdirSync(lines);
	const plainRun = runCli(["--name", "corpus", "--output", plain, "--sourcepath", sourceRoot, sourceRoot]);
	assert.strictEqual(plainRun.status, 0, plainRun.stderr);
	const linesRun = runCli(["--line-directives", "--format", "json", "--name", "corpus",
		"--output", lines, "--sourcepath", sourceRoot, sourceRoot]);
	assert.strictEqual(linesRun.status, 0, linesRun.stderr);
	const report = JSON.parse(linesRun.stdout);
	assert.strictEqual(report.schemaVersion, 4);
	assert.strictEqual(report.lineDirectives, true);

	const plainFiles = filesUnder(plain).map((file) => path.relative(plain, file)).sort();
	assert.deepStrictEqual(filesUnder(lines).map((file) => path.relative(lines, file)).sort(), plainFiles);
	let withDirectives = 0;
	for (const relative of plainFiles) {
		const plainText = fs.readFileSync(path.join(plain, relative), "utf8");
		const linesText = fs.readFileSync(path.join(lines, relative), "utf8");
		assert(!/^#line /m.test(plainText) && !plainText.includes("\u0001"), relative);
		assert(!linesText.includes("\u0001") && !linesText.includes("\u0002"), relative);
		// Directives are whole lines added to the same text
		const stripped = linesText.split("\n").filter((line) => !directivePattern.test(line)).join("\n");
		assert.strictEqual(stripped, plainText, `${relative} differs beyond its directives`);
		if (linesText !== plainText) {
			withDirectives++;
			presumedLines(lines, path.join(lines, relative));
		}
	}
	assert(withDirectives > 100, `Only ${withDirectives} files have directives`);
	// Generated helpers, stubs of classes without source, and the Makefile
	// have none
	for (const helper of ["src/Array.hpp", "src/ObjectArray.hpp", "src/j2c_array.cpp", "Makefile",
		"ext/src/java/lang/Object.hpp", "ext/stub/java/lang/Object-stub.cpp"]) {
		const file = path.join(lines, ...helper.split("/"));
		if (fs.existsSync(file)) {
			assert(!fs.readFileSync(file, "utf8").includes("#line "), helper);
		}
	}

	// Statements map to their Java lines, also in lambdas, which are
	// rewritten into anonymous classes
	const lambdasJava = path.join(fixtureDirectory, "Java17Lambdas.java");
	const generated = path.join(lines, "src", "se", "arnetheduck", "j2c", "test");
	const expectations = [
		["Java17Lambdas.cpp", "return npc(op)->applyAsInt(a, b);", "return op.applyAsInt(a, b);"],
		["Java17Lambdas.cpp", "base = int32_t(10);", "private int base = 10;"],
		["Java17Lambdas.cpp", "Java17Lambdas::apply(", "public static int apply(IntBinaryOperator op"],
		["Java17Lambdas.hpp", "static int32_t apply(", "public static int apply(IntBinaryOperator op"],
		["Java17Lambdas_1.cpp", "return a + b + Java17Lambdas_this->base;", "plusBase = (a, b) -> a + b + base;"],
		["Java17Lambdas_blockBody_5.cpp", "auto sum = a + b;", "int sum = a + b;"],
		["Java17Lambdas_blockBody_5.cpp", "return sum + offset;", "return sum + offset;"]
	];
	for (const [file, cpp, java] of expectations) {
		assert.deepStrictEqual(presumedAt(lines, path.join(generated, file), cpp),
			{ file: lambdasJava, line: javaLine(lambdasJava, java) }, `${file}: ${cpp}`);
	}
	// Boilerplate maps back to the C++ file itself
	const lambdaClass = path.join(generated, "Java17Lambdas_1.cpp");
	assert.strictEqual(presumedAt(lines, lambdaClass, "::class_()").file, lambdaClass);
}

/** A make that prints J2C_FAKE_MAKE_OUTPUT and exits with 2 */
function writeFakeMake(directory) {
	const script = path.join(directory, "fake-make.js");
	fs.writeFileSync(script, [
		"const fs = require(\"fs\");",
		"if (process.argv.includes(\"--version\")) { console.log(\"GNU Make 4.3\"); process.exit(0); }",
		"fs.writeFileSync(process.env.J2C_FAKE_MAKE_ARGS, JSON.stringify(process.argv.slice(2)));",
		"process.stdout.write(fs.readFileSync(process.env.J2C_FAKE_MAKE_OUTPUT, \"utf8\"));",
		"process.exit(2);",
		""
	].join("\n"));
	if (isWindows) {
		const wrapper = path.join(directory, "fake-make.cmd");
		fs.writeFileSync(wrapper, `@"${process.execPath}" "${script}" %*\r\n@exit /b %ERRORLEVEL%\r\n`);
		return wrapper;
	}
	const wrapper = path.join(directory, "fake-make");
	fs.writeFileSync(wrapper, `#!/bin/sh\nexec "${process.execPath}" "${script}" "$@"\n`);
	fs.chmodSync(wrapper, 0o755);
	return wrapper;
}

/** The generated line (1-based) containing text */
function physicalLine(file, text) {
	const index = fs.readFileSync(file, "utf8").split("\n").findIndex((line) => line.includes(text));
	assert(index >= 0, `${text} not in ${file}`);
	return index + 1;
}

function checkBuildParsing(runCli, tempDirectory) {
	const output = path.join(tempDirectory, "build parse output");
	fs.mkdirSync(output);
	const translated = runCli(["--line-directives", "--output", output, buildSources]);
	assert.strictEqual(translated.status, 0, translated.stderr);
	const counterCpp = path.join(output, "src", "builddemo", "Counter.cpp");
	const counterHpp = path.join(output, "src", "builddemo", "Counter.hpp");
	const javaPath = presumedLines(output, counterCpp).find((line) => line.file.endsWith(".java")).file;
	const statementLine = physicalLine(counterCpp, "count += step;");
	const fieldLine = physicalLine(counterHpp, "int32_t count");
	const classLine = physicalLine(counterCpp, "static ::java::lang::Class* c = ");
	assert.strictEqual(presumedAt(output, counterCpp, "count += step;").line, 12);

	// No directives reach the cpp file at the class literal, so an error
	// there is reported in C++ with the nearest Java line before it
	const canned = [
		"Building with \"g++ -Isrc -std=c++20\"",
		"Compile src/builddemo/Counter.cpp",
		`${javaPath}: In member function 'virtual int32_t builddemo::Counter::next()':`,
		`${javaPath}:12:15: error: 'j2c_undeclared' was not declared in this scope`,
		`${javaPath}:11:14: note: suggested alternative: 'step'`,
		"In file included from src/builddemo/Counter.cpp:2:",
		`${javaPath}:8:13: warning: unused member 'count' [-Wunused]`,
		`src/builddemo/Counter.cpp:${classLine}:5: error: 'c' was not declared in this scope`,
		"ext/src/java/lang/Object.hpp:3:10: fatal error: missing.hpp: No such file or directory",
		"/usr/bin/ld: obj/builddemo/Counter.o: in function `builddemo::Counter::next()':",
		`${javaPath}:13:(.text+0x1e): undefined reference to \`missing()'`,
		"/usr/bin/ld: cannot find -lmissing: No such file or directory",
		"collect2: error: ld returned 1 exit status",
		"make: *** [Makefile:130: obj/builddemo/Counter.o] Error 1",
		""
	];
	const stubDirectory = fs.mkdtempSync(path.join(os.tmpdir(), "j2c-make-"));
	try {
		const make = writeFakeMake(stubDirectory);
		const cannedFile = path.join(stubDirectory, "make-output.txt");
		const argsFile = path.join(stubDirectory, "make-args.json");
		fs.writeFileSync(cannedFile, canned.join("\n"));
		const environment = { ...process.env, J2C_FAKE_MAKE_OUTPUT: cannedFile, J2C_FAKE_MAKE_ARGS: argsFile };
		const built = runCli(["build", "--format", "json", "--output", output, "--make", make,
			"--wsl", "never", "--jobs", "3", "--target", "mains"], { env: environment });
		assert.strictEqual(built.status, 1, built.stderr);
		const report = JSON.parse(built.stdout);
		assert.strictEqual(report.schemaVersion, 4);
		assert.strictEqual(report.command, "build");
		assert.strictEqual(report.status, "failed");
		assert.strictEqual(report.exitCode, 2);
		assert.strictEqual(report.translated, false);
		assert.deepStrictEqual(report.makeCommand.slice(1), ["-k", "-j3", "--output-sync=target", "mains"]);
		assert.deepStrictEqual(JSON.parse(fs.readFileSync(argsFile, "utf8")), ["-k", "-j3", "--output-sync=target", "mains"]);
		assert.deepStrictEqual(report.summary, { errors: 5, warnings: 1 });
		assert.strictEqual(report.logTruncated, false);
		assert(report.log.includes("collect2: error: ld returned 1 exit status"));
		assert(!built.stdout.includes("Selected stats"), "Only JSON on stdout");
		assert(built.stderr.includes("Compile src/builddemo/Counter.cpp"), "make's output goes to stderr");

		const [statement, field, boilerplate, missing, undefinedReference, linker] = report.diagnostics;
		assert.deepStrictEqual(statement, {
			severity: "error",
			message: "'j2c_undeclared' was not declared in this scope",
			java: { file: counterJava, line: 12 },
			cpp: { file: counterCpp, line: statementLine, column: 15 },
			mapped: "line-directive",
			context: "In member function 'virtual int32_t builddemo::Counter::next()'",
			includedFrom: [],
			notes: [{
				severity: "note",
				message: "suggested alternative: 'step'",
				java: { file: counterJava, line: 11 },
				cpp: { file: counterCpp, line: physicalLine(counterCpp, "auto step"), column: 14 },
				mapped: "line-directive",
				context: null,
				includedFrom: [],
				notes: []
			}]
		});
		// In a header: the field's declaration, not its initializer in the unit
		assert.strictEqual(field.severity, "warning");
		assert.deepStrictEqual(field.java, { file: counterJava, line: 8 });
		assert.deepStrictEqual(field.cpp, { file: counterHpp, line: fieldLine, column: 13 });
		assert.deepStrictEqual(field.includedFrom, [{ file: counterCpp, line: 2 }]);
		assert.deepStrictEqual(boilerplate.cpp, { file: counterCpp, line: classLine, column: 5 });
		assert.strictEqual(boilerplate.mapped, "nearest");
		assert.deepStrictEqual(boilerplate.java, { file: counterJava, line: javaLine(counterJava, "return value * 2;") + 1 });
		// A file without directives has no Java line
		assert.strictEqual(missing.severity, "error");
		assert.strictEqual(missing.message, "missing.hpp: No such file or directory");
		assert.strictEqual(missing.java, null);
		assert.strictEqual(missing.mapped, "none");
		assert.deepStrictEqual(missing.cpp, { file: path.join(output, "ext", "src", "java", "lang", "Object.hpp"), line: 3, column: 10 });
		assert.strictEqual(undefinedReference.message, "undefined reference to `missing()'");
		assert.deepStrictEqual(undefinedReference.java, { file: counterJava, line: 13 });
		assert.strictEqual(linker.message, "cannot find -lmissing: No such file or directory");
		assert.strictEqual(linker.cpp, null);
		assert.strictEqual(report.diagnostics.length, 6);

		// Text output: Java locations first, then the C++ ones
		const text = runCli(["build", "--output", output, "--make", make, "--wsl", "never"], { env: environment });
		assert.strictEqual(text.status, 1, text.stderr);
		assert(text.stdout.includes(`${counterJava}:12: error: 'j2c_undeclared' was not declared in this scope [C++ ${counterCpp}:${statementLine}:15]`),
			text.stdout);
		assert(text.stdout.includes("J2C build failed: make exited with 2, 5 error(s)"), text.stdout);
		// One job: no --output-sync, which older makes lack
		const oneJob = runCli(["build", "--output", output, "--make", make, "--wsl", "never", "--jobs", "1"], { env: environment });
		assert.strictEqual(oneJob.status, 1);
		assert.deepStrictEqual(JSON.parse(fs.readFileSync(argsFile, "utf8")), ["-k", "-j1"]);
	} finally {
		fs.rmSync(stubDirectory, { recursive: true, force: true });
	}

	// Usage errors
	const notOutput = path.join(tempDirectory, "not j2c output");
	fs.mkdirSync(notOutput);
	const unmarked = runCli(["build", "--format", "json", "--output", notOutput]);
	assert.strictEqual(unmarked.status, 2);
	const unmarkedReport = JSON.parse(unmarked.stdout);
	assert.strictEqual(unmarkedReport.status, "usage-error");
	assert.strictEqual(unmarkedReport.command, "build");
	assert(unmarkedReport.message.includes("Not a J2C output directory"), unmarkedReport.message);
	assert.strictEqual(runCli(["build", buildSources]).status, 2, "--output is required");
	assert(runCli(["build", "--output", output, "--jobs", "0"]).stderr.includes("Invalid --jobs value: 0"));
	assert(runCli(["build", "--output", output, "--wsl", "sometimes"]).stderr.includes("Invalid --wsl value"));
	assert(runCli(["build", "--output", output, "--line-directives"]).stderr.includes("Unknown build option"));
	const noMake = runCli(["build", "--format", "json", "--output", output, "--make", "j2c-no-such-make", "--wsl", "never"]);
	assert.strictEqual(noMake.status, 1);
	assert.strictEqual(JSON.parse(noMake.stdout).status, "error");
	assert(JSON.parse(noMake.stdout).message.startsWith("Could not run j2c-no-such-make"));
}

/**
 * Translates and builds test/build with make, breaks a statement's C++, and
 * checks that the error is reported at its Java line
 */
function checkRealBuild(runCli, tempDirectory) {
	const output = path.join(tempDirectory, "build output");
	fs.mkdirSync(output);
	const environment = { ...process.env };
	if (isWindows && !environment.J2C_WSL_DISTRO) {
		environment.J2C_WSL_DISTRO = "Ubuntu";
	}
	const built = runCli(["build", "--format", "json", "--output", output, buildSources], { env: environment });
	const report = JSON.parse(built.stdout);
	if (report.status === "error" && /^Could not run make/.test(report.message)) {
		console.log(`j2c build with a real make skipped: ${report.message}`);
		return;
	}
	assert.strictEqual(built.status, 0, built.stderr);
	assert.strictEqual(report.status, "ok", JSON.stringify(report));
	assert.strictEqual(report.translated, true);
	assert(report.generatedFiles.includes("src/builddemo/Counter.cpp"));
	assert.deepStrictEqual(report.diagnostics, []);

	const counterCpp = path.join(output, "src", "builddemo", "Counter.cpp");
	const text = fs.readFileSync(counterCpp, "utf8");
	fs.writeFileSync(counterCpp, text.replace("count += step;", "count += j2c_undeclared;"));
	const broken = runCli(["build", "--format", "json", "--output", output], { env: environment });
	assert.strictEqual(broken.status, 1, broken.stderr);
	const brokenReport = JSON.parse(broken.stdout);
	assert.strictEqual(brokenReport.status, "failed");
	assert.strictEqual(brokenReport.translated, false);
	const error = brokenReport.diagnostics.find((diagnostic) => diagnostic.severity === "error");
	assert(error, JSON.stringify(brokenReport));
	assert.match(error.message, /'j2c_undeclared' was not declared in this scope|use of undeclared identifier 'j2c_undeclared'/);
	assert.deepStrictEqual(error.java, { file: counterJava, line: 12 });
	assert.strictEqual(error.mapped, "line-directive");
	assert.strictEqual(error.cpp.file, counterCpp);
	assert.strictEqual(error.cpp.line, physicalLine(counterCpp, "count += j2c_undeclared;"));
	console.log("j2c build compiled test/build and mapped an error to its Java line");
}

function checkBuild(runCli, tempDirectory) {
	checkLineDirectives(runCli, tempDirectory);
	checkBuildParsing(runCli, tempDirectory);
	checkRealBuild(runCli, tempDirectory);
}

module.exports = { checkBuild };
