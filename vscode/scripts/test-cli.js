const assert = require("assert");
const childProcess = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");

const extensionDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..");
const cliJar = path.join(repositoryRoot, "target", "j2c-cli.jar");
const sourceRoot = path.join(repositoryRoot, "test", "src");
const fixtureDirectory = path.join(sourceRoot, "se", "arnetheduck", "j2c", "test");
const sealedFixture = path.join(fixtureDirectory, "Java17Unsupported.java");
const recordsFixture = path.join(fixtureDirectory, "Java17Records.java");
const controlFlowFixture = path.join(fixtureDirectory, "Java17ControlFlow.java");
const featuresFixture = path.join(fixtureDirectory, "Java17Features.java");
const extensionVersion = require(path.join(extensionDirectory, "package.json")).version;
const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), "j2c-cli test "));
const outputDirectory = path.join(tempDirectory, "generated output");

function runCli(args, options = {}) {
	return childProcess.spawnSync("java", ["-jar", cliJar, ...args], {
		encoding: "utf8",
		...options
	});
}

function javaMajorVersion() {
	const version = childProcess.spawnSync("java", ["-version"], {
		encoding: "utf8"
	});
	assert.strictEqual(version.status, 0, version.stderr);
	const match = `${version.stdout}\n${version.stderr}`.match(/version "(?:1\.)?(\d+)/);
	assert(match, `Unable to determine Java version: ${version.stderr}`);
	return Number(match[1]);
}

function filesUnder(directory) {
	return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
		const entryPath = path.join(directory, entry.name);
		return entry.isDirectory() ? filesUnder(entryPath) : [entryPath];
	});
}

try {
	assert(fs.existsSync(cliJar), `CLI JAR not found: ${cliJar}`);
	fs.mkdirSync(outputDirectory);

	const help = runCli(["--help"]);
	assert.strictEqual(help.status, 0, help.stderr);
	assert(help.stdout.includes("--sealed-types <advisory|reject>"));
	assert(help.stdout.includes("--check"));

	const version = runCli(["--version"]);
	assert.strictEqual(version.status, 0, version.stderr);
	assert.strictEqual(version.stdout.trim(), `j2c ${extensionVersion}`);

	const checkWithOutput = runCli(["--check", "--output", tempDirectory, sealedFixture]);
	assert.strictEqual(checkWithOutput.status, 2);
	assert(checkWithOutput.stderr.includes("--check does not write output"));

	const missingInput = runCli(["--output", outputDirectory]);
	assert.strictEqual(missingInput.status, 2);
	assert(missingInput.stderr.includes("No Java input was provided"));

	const missingOptionValue = runCli([
		"--output", outputDirectory,
		"--sealed-types"
	]);
	assert.strictEqual(missingOptionValue.status, 2);
	assert(missingOptionValue.stderr.includes("Missing value for --sealed-types"));

	const invalidPolicy = runCli([
		"--sealed-types", "invalid",
		sealedFixture
	]);
	assert.strictEqual(invalidPolicy.status, 2);
	assert(invalidPolicy.stderr.includes("Invalid --sealed-types value"));

	const invalidEncoding = runCli([
		"--output", outputDirectory,
		"--encoding", "no-such-charset",
		sealedFixture
	]);
	assert.strictEqual(invalidEncoding.status, 2);
	assert(invalidEncoding.stderr.includes("Unsupported --encoding value"));

	// Syntax the writers cannot translate must fail loudly instead of
	// producing plausible-looking but invalid C++
	const scratchRoot = path.join(tempDirectory, "scratch src");
	const scratchPackage = path.join(scratchRoot, "scratch");
	fs.mkdirSync(scratchPackage, { recursive: true });
	const unsupportedSource = path.join(scratchPackage, "Unsupported.java");
	fs.writeFileSync(unsupportedSource, [
		"package scratch;",
		"public class Unsupported {",
		"\tstatic final boolean EMPTY = ((Object) \"\") instanceof String s && s.isEmpty();",
		"\tstatic int run() {",
		"\t\tint x = \"text\";",
		"\t\treturn x;",
		"\t}",
		"}",
		""
	].join("\n"));
	const patternProblem = "Unsupported.java:3: instanceof patterns outside method bodies, such as in field initializers, are not supported";
	const compileProblem = "Unsupported.java:5: Type mismatch: cannot convert from String to int";
	const unsupportedOutput = path.join(tempDirectory, "unsupported output");
	fs.mkdirSync(unsupportedOutput);
	const unsupported = runCli(["--output", unsupportedOutput, unsupportedSource]);
	assert.strictEqual(unsupported.status, 1, unsupported.stdout);
	assert(unsupported.stderr.includes(patternProblem), unsupported.stderr);
	assert(unsupported.stderr.includes(compileProblem), unsupported.stderr);
	assert(!unsupported.stderr.includes("\tat "), "User errors must not print a stack trace");

	// --check reports every problem and writes nothing, not even into cwd
	const checkDirectory = path.join(tempDirectory, "check cwd");
	fs.mkdirSync(checkDirectory);
	const checked = runCli(["--check", unsupportedSource], { cwd: checkDirectory });
	assert.strictEqual(checked.status, 1, checked.stderr);
	assert(checked.stdout.includes(patternProblem));
	assert(checked.stdout.includes(compileProblem));
	assert(checked.stdout.includes("J2C check: 2 problem(s) in 1 of 1 file(s)"));
	assert(checked.stdout.includes("1 x instanceof pattern"));
	assert(checked.stdout.includes("1 x compile error"));
	assert.deepStrictEqual(fs.readdirSync(checkDirectory), []);


	// Lambdas and method references are rewritten into anonymous classes
	const lambdaSource = path.join(scratchPackage, "Lambdas.java");
	fs.writeFileSync(lambdaSource, [
		"package scratch;",
		"public class Lambdas {",
		"\tinterface F { int apply(int x); }",
		"\tstatic int run(int offset) {",
		"\t\tF shifted = x -> x + offset;",
		"\t\tF boxed = Integer::valueOf;",
		"\t\treturn shifted.apply(1) + boxed.apply(2);",
		"\t}",
		"}",
		""
	].join("\n"));
	// A captured wildcard as an invariant type argument cannot be implemented
	// by any class; it is reported (found in commons-collections)
	const captureSource = path.join(scratchPackage, "Capture.java");
	fs.writeFileSync(captureSource, [
		"package scratch;",
		"import java.util.Collection;",
		"import java.util.function.ToIntFunction;",
		"public class Capture {",
		"\tstatic <C> int sum(Iterable<C> items, ToIntFunction<C> f) { return 0; }",
		"\tstatic <E> int sizes(Iterable<? extends Collection<E>> items) {",
		"\t\treturn sum(items, Collection::size);",
		"\t}",
		"}",
		""
	].join("\n"));
	const capture = JSON.parse(runCli(["--check", "--format", "json", captureSource]).stdout);
	assert.deepStrictEqual(capture.problems.map((problem) => `${problem.id}@${problem.line}`),
		["J2C-LAMBDA-NON-DENOTABLE-TYPE@7"], JSON.stringify(capture.problems));
	assert(capture.problems[0].message.includes("captured wildcard"));

	// A lambda becomes a separate class, where Iface.super.m() cannot be
	// named; it is reported (G33)
	const interfaceSuperSource = path.join(scratchPackage, "InterfaceSuper.java");
	fs.writeFileSync(interfaceSuperSource, [
		"package scratch;",
		"import java.util.function.Supplier;",
		"interface Greeter { default String greet() { return \"hi\"; } }",
		"public class InterfaceSuper implements Greeter {",
		"\tpublic String greet() {",
		"\t\tSupplier<String> lambda = () -> Greeter.super.greet();",
		"\t\tSupplier<String> reference = Greeter.super::greet;",
		"\t\treturn lambda.get() + reference.get();",
		"\t}",
		"}",
		""
	].join("\n"));
	const interfaceSuper = JSON.parse(runCli(["--check", "--format", "json", interfaceSuperSource]).stdout);
	assert.deepStrictEqual(interfaceSuper.problems.map((problem) => `${problem.id}@${problem.line}`),
		["J2C-LAMBDA-INTERFACE-SUPER@6", "J2C-LAMBDA-INTERFACE-SUPER@7"], JSON.stringify(interfaceSuper.problems));

	// A module declaration is skipped: it has nothing to translate, and
	// checking it against packages compiled as sources fails
	const moduleRoot = path.join(tempDirectory, "module source");
	fs.mkdirSync(path.join(moduleRoot, "mod", "api"), { recursive: true });
	fs.writeFileSync(path.join(moduleRoot, "module-info.java"), "module mod { exports mod.api; }\n");
	fs.writeFileSync(path.join(moduleRoot, "mod", "api", "package-info.java"), "package mod.api;\n");
	fs.writeFileSync(path.join(moduleRoot, "mod", "api", "Api.java"), "package mod.api;\npublic class Api {}\n");
	const moduleCheck = JSON.parse(runCli(["--check", "--format", "json", moduleRoot]).stdout);
	assert.deepStrictEqual(moduleCheck.problems, [], JSON.stringify(moduleCheck.problems));
	assert.strictEqual(moduleCheck.files, 1);

	// --runtime translates against runtime/ instead of the running JDK
	const runtimeDirectory = path.join(repositoryRoot, "runtime");
	const runtimeOutput = path.join(tempDirectory, "runtime output");
	fs.mkdirSync(runtimeOutput);
	const runtimeRun = runCli(["--runtime", runtimeDirectory, "--output", runtimeOutput,
		path.join(repositoryRoot, "test", "runtime", "hello")]);
	assert.strictEqual(runtimeRun.status, 0, runtimeRun.stderr || runtimeRun.stdout);
	const runtimeObject = fs.readFileSync(path.join(runtimeOutput, "ext", "src", "java", "lang", "Object.hpp"), "utf8");
	// The runtime's Object has no wait(), unlike the JDK's
	assert(!runtimeObject.includes("wait("), runtimeObject);
	assert.strictEqual(
		fs.readFileSync(path.join(runtimeOutput, "ext", "native", "java", "lang", "Object-native.cpp"), "utf8"),
		fs.readFileSync(path.join(runtimeDirectory, "cpp", "native", "java", "lang", "Object-native.cpp"), "utf8"));
	const runtimeMakefile = fs.readFileSync(path.join(runtimeOutput, "Makefile"), "utf8");
	assert(runtimeMakefile.includes("\truntime/j2c_runtime.cpp \\"), runtimeMakefile);
	assert(fs.existsSync(path.join(runtimeOutput, "runtime", "j2c_runtime.cpp")));
	// make J2C_GC=boehm links the Boehm collector, which then serves every
	// allocation through the main file's operator new (G8)
	assert(runtimeMakefile.includes("ifeq ($(J2C_GC),boehm)"), runtimeMakefile);
	assert(runtimeMakefile.includes("-DJ2C_GC_BOEHM"), runtimeMakefile);
	assert(runtimeMakefile.includes("GC_LDLIBS = -Wl,--wrap=__cxa_throw $(GC_LIBS)"), runtimeMakefile);
	assert(runtimeMakefile.includes("$(LINK_LIBS) $(GC_LDLIBS)"), runtimeMakefile);
	const runtimeMain = fs.readFileSync(path.join(runtimeOutput, "src", "Hello-main.cpp"), "utf8");
	assert(runtimeMain.includes("#ifdef J2C_GC_BOEHM"), runtimeMain);
	assert(runtimeMain.includes("void* operator new(std::size_t n) { return gc_allocate(n, 0, false); }"), runtimeMain);
	assert(runtimeMain.includes("__wrap___cxa_throw"), runtimeMain);
	assert(runtimeMain.includes("GC_INIT();"), runtimeMain);
	assert(runtimeMain.includes("% (sizeof(j2c_gc_thrown)"), "the template's %% is formatted");
	const notRuntime = runCli(["--runtime", tempDirectory, "--check", path.join(repositoryRoot, "test", "runtime", "hello")]);
	assert.strictEqual(notRuntime.status, 2);
	assert(notRuntime.stderr.includes("Not a J2C runtime"), notRuntime.stderr);

	// A surrogate escape followed by a hex digit splits the C++ literal (G44)
	const textSource = fs.readFileSync(path.join(repositoryRoot, "test", "runtime", "text", "Text.java"), "utf8");
	assert(textSource.includes("\"a\\ud83d\\ude00b\""), "Text.java keeps the G44 case");
	const textOutput = path.join(tempDirectory, "text output");
	fs.mkdirSync(textOutput);
	const textRun = runCli(["--runtime", runtimeDirectory, "--output", textOutput,
		path.join(repositoryRoot, "test", "runtime", "text")]);
	assert.strictEqual(textRun.status, 0, textRun.stderr || textRun.stdout);
	const textCpp = fs.readFileSync(path.join(textOutput, "src", "Text.cpp"), "utf8");
	assert(textCpp.includes("u\"a\\xd83d\\xde00\" u\"b\"_j"), "surrogate escape must not absorb the following hex digit");

	// JDT's isEqualTo matches TypeDescriptor.OfField<F> with AbstractList<E>,
	// which dropped the base header that Class.hpp includes (hashids-java)
	const depsRoot = path.join(tempDirectory, "base deps");
	fs.mkdirSync(depsRoot);
	fs.writeFileSync(path.join(depsRoot, "Deps.java"),
		"import java.util.ArrayList;\npublic class Deps {\n\tpublic static int size() {\n\t\treturn new ArrayList<String>().size();\n\t}\n}\n");
	const depsOutput = path.join(tempDirectory, "base deps output");
	fs.mkdirSync(depsOutput);
	const depsRun = runCli(["--output", depsOutput, depsRoot]);
	assert.strictEqual(depsRun.status, 0, depsRun.stderr || depsRun.stdout);
	// Class's bases depend on the JDK (TypeDescriptor.OfField is JDK 12+)
	const classHeader = fs.readFileSync(path.join(depsOutput, "ext", "src", "java", "lang", "Class.hpp"), "utf8");
	for (const [, include] of classHeader.matchAll(/#include <([^>]+\.hpp)>/g)) {
		assert(["src", path.join("ext", "src")].some((root) => fs.existsSync(path.join(depsOutput, root, include))),
			`Class.hpp includes ${include}, which was not generated`);
	}

	// A String switch alone must define the npc helper it calls (jsoup)
	const switchRoot = path.join(tempDirectory, "string switch");
	fs.mkdirSync(path.join(switchRoot, "sw"), { recursive: true });
	fs.writeFileSync(path.join(switchRoot, "sw", "Kind.java"), [
		"package sw;",
		"final class Kind {",
		"\tstatic int of(String name) {",
		"\t\tswitch (name) { case \"a\": return 1; default: return 0; }",
		"\t}",
		"\t// C++20 keywords (ModuleDescriptor has a requires() method)",
		"\tstatic int requires(int concept) { return concept; }",
		"}",
		""
	].join("\n"));
	// An enum switch expression on this also needs the helper (Forge)
	fs.writeFileSync(path.join(switchRoot, "sw", "Mode.java"), [
		"package sw;",
		"enum Mode {",
		"\tA, B;",
		"\tboolean first() { return switch (this) { case A -> true; default -> false; }; }",
		"}",
		""
	].join("\n"));
	// Hoisted switch locals and boxed array elements (Forge)
	fs.writeFileSync(path.join(switchRoot, "sw", "Boxes.java"), [
		"package sw;",
		"import java.util.ArrayList;",
		"import java.util.List;",
		"class Boxes {",
		"\tstatic List<String> pick(int k) {",
		"\t\tswitch (k) { case 1: List<String> items = new ArrayList<>(); return items; default: return null; }",
		"\t}",
		"\tstatic int bump(Integer[] counts, int i) { counts[i]++; return counts[i]; }",
		"}",
		""
	].join("\n"));
	const switchOutput = path.join(tempDirectory, "string switch output");
	fs.mkdirSync(switchOutput);
	const switchRun = runCli(["--output", switchOutput, switchRoot]);
	assert.strictEqual(switchRun.status, 0, switchRun.stderr);
	const switchSource = fs.readFileSync(path.join(switchOutput, "src", "sw", "Kind.cpp"), "utf8");
	assert(switchSource.includes("npc(v)->equals("), switchSource);
	assert(switchSource.includes("static T* npc(T* t)"), switchSource);
	assert(switchSource.includes("Kind::requires_(int32_t concept_)"), switchSource);
	const modeSource = fs.readFileSync(path.join(switchOutput, "src", "sw", "Mode.cpp"), "utf8");
	assert(modeSource.includes("auto v = npc(this);") && modeSource.includes("static T* npc(T* t)"), modeSource);
	const boxesSource = fs.readFileSync(path.join(switchOutput, "src", "sw", "Boxes.cpp"), "utf8");
	assert(boxesSource.includes("#include <java/util/ArrayList.hpp>"), boxesSource);
	assert(boxesSource.includes("counts->set(i, ::java::lang::Integer::valueOf(static_cast<int32_t>((npc((*counts)[i]))->intValue() + 1)));"), boxesSource);

	// Stubs for class path types guess setter and getter bodies only when the
	// types fit: commons-cli's Option has setType(Object) beside a Class field
	const libraryRoot = path.join(tempDirectory, "library");
	fs.mkdirSync(path.join(libraryRoot, "lib"), { recursive: true });
	fs.writeFileSync(path.join(libraryRoot, "lib", "Option.java"), [
		"package lib;",
		"public class Option {",
		"\tprivate Class<?> type;",
		"\tprivate String name;",
		"\tpublic void setType(Class<?> type) { this.type = type; }",
		"\tpublic void setType(Object type) { this.type = (Class<?>) type; }",
		"\tpublic Object getName() { return name; }",
		"\tprivate boolean flag;",
		"\tpublic Boolean getFlag() { return flag; }",
		"\tpublic void setFlag(Boolean flag) { this.flag = flag; }",
		"}",
		""
	].join("\n"));
	const libraryClasses = path.join(tempDirectory, "library classes");
	const javac = childProcess.spawnSync("javac", ["-d", libraryClasses, path.join(libraryRoot, "lib", "Option.java")],
		{ encoding: "utf8" });
	assert.strictEqual(javac.status, 0, javac.stderr);
	const userRoot = path.join(tempDirectory, "library user");
	fs.mkdirSync(path.join(userRoot, "app"), { recursive: true });
	fs.writeFileSync(path.join(userRoot, "app", "Use.java"),
		"package app;\npublic class Use { Object name(lib.Option o) { o.setType(String.class); return o.getName(); } }\n");
	const libraryOutput = path.join(tempDirectory, "library output");
	fs.mkdirSync(libraryOutput);
	const libraryRun = runCli(["--output", libraryOutput, "--classpath", libraryClasses, userRoot]);
	assert.strictEqual(libraryRun.status, 0, libraryRun.stderr);
	const optionStub = fs.readFileSync(path.join(libraryOutput, "ext", "stub", "lib", "Option-stub.cpp"), "utf8");
	assert.strictEqual(optionStub.split("/* setter */").length - 1, 1, optionStub);
	// A boolean field is not returned as a Boolean box (MutableBoolean in Forge)
	assert(!optionStub.includes("return flag ; /* getter */"), optionStub);
	assert(optionStub.includes("return name_ ; /* getter */") || optionStub.includes("return name ; /* getter */"), optionStub);

	// A forwarder to a base method with an erased parameter upcasts the
	// argument, so it needs the argument's full declaration: EmptySpliterator
	// has tryAdvance(C), which OfRef inherits for tryAdvance(Consumer)
	const forwarderRoot = path.join(tempDirectory, "erased forwarder");
	fs.mkdirSync(path.join(forwarderRoot, "fw"), { recursive: true });
	fs.writeFileSync(path.join(forwarderRoot, "fw", "Empty.java"),
		"package fw;\npublic class Empty { static java.util.Spliterator<String> of() { return java.util.Spliterators.emptySpliterator(); } }\n");
	const forwarderOutput = path.join(tempDirectory, "erased forwarder output");
	fs.mkdirSync(forwarderOutput);
	const forwarderRun = runCli(["--output", forwarderOutput, forwarderRoot]);
	assert.strictEqual(forwarderRun.status, 0, forwarderRun.stderr);
	const ofRefStub = fs.readFileSync(path.join(forwarderOutput, "ext", "stub", "java", "util",
		"Spliterators_EmptySpliterator_OfRef-stub.cpp"), "utf8");
	assert(ofRefStub.includes("Spliterators_EmptySpliterator::tryAdvance(a0);"), ofRefStub);
	assert(ofRefStub.includes("#include <java/util/function/Consumer.hpp>"), ofRefStub);

	const lambdaOutput = path.join(tempDirectory, "lambda output");
	fs.mkdirSync(lambdaOutput);
	const lambdas = runCli(["--output", lambdaOutput, lambdaSource]);
	assert.strictEqual(lambdas.status, 0, lambdas.stderr);
	const lambdaClass = fs.readFileSync(
		path.join(lambdaOutput, "src", "scratch", "Lambdas_run_1.cpp"), "utf8"
	);
	assert.match(lambdaClass, /int32_t scratch::Lambdas_run_1::apply\(int32_t x\)\s*\{\s*return x \+ offset;/);
	assert(
		fs.readFileSync(path.join(lambdaOutput, "src", "scratch", "Lambdas.cpp"), "utf8")
			.includes("new Lambdas_run_1(offset)"),
		"The captured local must be passed to the generated class"
	);

	// Sources are read as UTF-8 by default and C++ is written as UTF-8,
	// independent of the platform charset
	const utf8Source = path.join(scratchPackage, "Utf8.java");
	fs.writeFileSync(utf8Source, [
		"package scratch;",
		"public class Utf8 {",
		"\tpublic static String text() { return \"ação\"; }",
		"}",
		""
	].join("\n"), "utf8");
	const utf8Output = path.join(tempDirectory, "utf8 output");
	fs.mkdirSync(utf8Output);
	const utf8 = runCli(["--output", utf8Output, utf8Source]);
	assert.strictEqual(utf8.status, 0, utf8.stderr);
	assert(
		fs.readFileSync(path.join(utf8Output, "src", "scratch", "Utf8.cpp"), "utf8")
			.includes("u\"ação\""),
		"Non-ASCII string literal was not preserved as UTF-8"
	);

	// Machine-readable output for scripts and AI agents
	const jsonCheck = runCli(["--check", "--format", "json", unsupportedSource]);
	assert.strictEqual(jsonCheck.status, 1, jsonCheck.stderr);
	const checkReport = JSON.parse(jsonCheck.stdout);
	assert.strictEqual(checkReport.schemaVersion, 1);
	assert.strictEqual(checkReport.command, "check");
	assert.strictEqual(checkReport.status, "problems");
	assert.strictEqual(checkReport.summary.problemCount, 2);
	const patternEntry = checkReport.problems.find((problem) => problem.id === "J2C-PATTERN-OUTSIDE-METHOD");
	assert(patternEntry, jsonCheck.stdout);
	assert.strictEqual(patternEntry.line, 3);
	assert(patternEntry.column > 1);
	assert(patternEntry.hint.includes("constructor"));
	assert(checkReport.problems.some((problem) => problem.id === "J2C-COMPILE-ERROR"));

	const badFormat = runCli(["--check", "--format", "xml", unsupportedSource]);
	assert.strictEqual(badFormat.status, 2);
	const jsonUsage = runCli(["--check", "--format", "json"]);
	assert.strictEqual(jsonUsage.status, 2);
	assert.strictEqual(JSON.parse(jsonUsage.stdout).status, "usage-error");

	const explained = runCli(["explain", "J2C-PATTERN-OUTSIDE-METHOD"]);
	assert.strictEqual(explained.status, 0, explained.stderr);
	assert(explained.stdout.includes("How to fix:"));
	const allExplained = JSON.parse(runCli(["explain", "--format", "json"]).stdout);
	assert(allExplained.problems.length >= 8);
	for (const entry of allExplained.problems) {
		assert(entry.id.startsWith("J2C-") && entry.title && entry.explanation && entry.hint, entry.id);
	}
	assert.strictEqual(runCli(["explain", "J2C-NO-SUCH-ID"]).status, 2);

	const capabilities = JSON.parse(runCli(["capabilities"]).stdout);
	assert.strictEqual(capabilities.version, extensionVersion);
	assert.strictEqual(capabilities.javaSourceLevel, "17");
	assert.deepStrictEqual(
		capabilities.problemIds,
		allExplained.problems.map((entry) => entry.id)
	);

	// Translation refuses to move aside a directory J2C did not create
	const guarded = path.join(tempDirectory, "guarded output");
	fs.mkdirSync(guarded);
	fs.writeFileSync(path.join(guarded, "notes.txt"), "user data");
	const refused = runCli(["--output", guarded, utf8Source]);
	assert.strictEqual(refused.status, 2);
	assert(refused.stderr.includes("was not created by J2C"));
	assert.deepStrictEqual(fs.readdirSync(guarded), ["notes.txt"]);

	const jsonTranslate = runCli(["--output", guarded, "--force", "--format", "json", utf8Source]);
	assert.strictEqual(jsonTranslate.status, 0, jsonTranslate.stderr);
	const translateReport = JSON.parse(jsonTranslate.stdout);
	assert.strictEqual(translateReport.status, "ok");
	assert.strictEqual(translateReport.makefile, "Makefile");
	assert(translateReport.generatedFiles.includes("src/scratch/Utf8.cpp"));
	assert(!translateReport.generatedFiles.includes(".j2c-output"));
	assert(fs.existsSync(path.join(guarded, ".j2c-output")));
	// Once marked as J2C output, translating again needs no --force
	assert.strictEqual(runCli(["--output", guarded, utf8Source]).status, 0);

	// JUnit tests (--tests): runnable test classes get GoogleTest wrappers;
	// test/junit-api declares the JUnit annotations the fixtures use
	const junitRoot = path.join(repositoryRoot, "test", "junit");
	const junitApi = path.join(repositoryRoot, "test", "junit-api");
	const junitSourcePath = [junitRoot, junitApi].join(path.delimiter);
	const junitCheck = runCli(["--check", "--tests", "--format", "json", "--sourcepath", junitSourcePath, junitRoot]);
	assert.strictEqual(junitCheck.status, 0, junitCheck.stdout);

	const junitOutput = path.join(tempDirectory, "junit output");
	fs.mkdirSync(junitOutput);
	const junit = runCli(["--tests", "--format", "json", "--output", junitOutput, "--sourcepath", junitSourcePath, junitRoot]);
	assert.strictEqual(junit.status, 0, junit.stderr);
	const junitReport = JSON.parse(junit.stdout);
	const wrapperDirectory = "test/se/arnetheduck/j2c/test/junit";
	assert.deepStrictEqual(junitReport.testFiles, [
		`${wrapperDirectory}/DerivedTest_test.cpp`,
		`${wrapperDirectory}/Java17Assert4Test_test.cpp`,
		`${wrapperDirectory}/Java17AssertionsTest_test.cpp`,
		`${wrapperDirectory}/Java17JUnit4Test_test.cpp`,
		`${wrapperDirectory}/Java17JUnit5Test_test.cpp`
	], "Abstract test classes get no wrapper of their own");

	// JUnit assertions become j2c_junit.hpp calls with Java's overload types;
	// JUnit 4's leading message is passed after the values
	assert(junitReport.generatedFiles.includes("src/j2c_junit.hpp"));
	const assertionSource = fs.readFileSync(path.join(junitOutput, "src", "se", "arnetheduck", "j2c", "test", "junit", "Java17AssertionsTest.cpp"), "utf8");
	assert(assertionSource.includes("::j2c::junit::assertEquals< int64_t >(int64_t(5LL), int32_t(5))"));
	assert(assertionSource.includes("::j2c::junit::assertThrows< ::java::lang::IllegalArgumentException >(\"java.lang.IllegalArgumentException\""));
	const assert4Source = fs.readFileSync(path.join(junitOutput, "src", "se", "arnetheduck", "j2c", "test", "junit", "Java17Assert4Test.cpp"), "utf8");
	assert(assert4Source.includes("::j2c::junit::assertEquals< int64_t >(int64_t(3LL), int64_t(3LL), u\"lengths\"_j)"));
	assert(junitReport.generatedFiles.includes("test/j2c_gtest.hpp"));
	const junitMakefile = fs.readFileSync(path.join(junitOutput, "Makefile"), "utf8");
	assert(junitMakefile.includes(`${wrapperDirectory}/Java17JUnit5Test_test.cpp`));
	assert(junitMakefile.includes("$(TESTS_BIN)"));
	const derivedWrapper = fs.readFileSync(path.join(junitOutput, wrapperDirectory, "DerivedTest_test.cpp"), "utf8");
	assert.match(derivedWrapper, /test->baseSetUp\(\);\s*test->derivedSetUp\(\);/,
		"Superclass @BeforeEach methods run first");
	const junit4Wrapper = fs.readFileSync(path.join(junitOutput, wrapperDirectory, "Java17JUnit4Test_test.cpp"), "utf8");
	assert(junit4Wrapper.includes("dynamic_cast< ::java::lang::IllegalArgumentException* >(outcome.thrown)"));
	assert(junit4Wrapper.includes("{ \"ignored\", \"\" }"));

	const plainOutput = path.join(tempDirectory, "junit without tests");
	fs.mkdirSync(plainOutput);
	assert.strictEqual(runCli(["--output", plainOutput, "--sourcepath", junitSourcePath, junitRoot]).status, 0);
	assert(!fs.existsSync(path.join(plainOutput, "test")), "Without --tests no wrappers are generated");
	assert(!fs.readFileSync(path.join(plainOutput, "src", "se", "arnetheduck", "j2c", "test", "junit", "Java17AssertionsTest.cpp"), "utf8")
		.includes("::j2c::junit::"), "Without --tests assertions are ordinary calls");

	// Assertion forms that need the Java runtime are reported, not stubbed
	const assertionScratch = path.join(tempDirectory, "assertion scratch");
	fs.mkdirSync(path.join(assertionScratch, "scratch"), { recursive: true });
	const unsupportedAssertions = path.join(assertionScratch, "scratch", "UnsupportedAssertions.java");
	fs.writeFileSync(unsupportedAssertions, [
		"package scratch;",
		"import static org.junit.jupiter.api.Assertions.*;",
		"import org.junit.jupiter.api.Test;",
		"class UnsupportedAssertions {",
		"\t@Test void lazy() { assertTrue(() -> true); }",
		"\t@Test void grouped() { assertAll(() -> {}); }",
		"\t@Test void classVariable() { Class<IllegalStateException> type = IllegalStateException.class; assertThrows(type, () -> {}); }",
		"\t@Test void supported() { assertEquals(1, 1); }",
		"}",
		""
	].join("\n"));
	const assertionProblems = JSON.parse(runCli(["--check", "--tests", "--format", "json",
		"--sourcepath", [assertionScratch, junitApi].join(path.delimiter), unsupportedAssertions]).stdout);
	assert.deepStrictEqual(
		assertionProblems.problems.map((problem) => `${problem.id}@${problem.line}`),
		["J2C-TEST-UNSUPPORTED-ASSERTION@5", "J2C-TEST-UNSUPPORTED-ASSERTION@6", "J2C-TEST-UNSUPPORTED-ASSERTION@7"],
		JSON.stringify(assertionProblems.problems, null, 1)
	);

	// Test features the wrappers cannot run are reported in tests mode
	const testScratch = path.join(tempDirectory, "test scratch");
	fs.mkdirSync(path.join(testScratch, "scratch"), { recursive: true });
	const unsupportedTests = path.join(testScratch, "scratch", "UnsupportedTests.java");
	fs.writeFileSync(unsupportedTests, [
		"package scratch;",
		"import org.junit.jupiter.api.Nested;",
		"import org.junit.jupiter.api.Test;",
		"import org.junit.jupiter.api.TestInstance;",
		"import org.junit.jupiter.api.Timeout;",
		"import org.junit.jupiter.api.extension.ExtendWith;",
		"import org.junit.jupiter.params.ParameterizedTest;",
		"import org.mockito.Mockito;",
		"@ExtendWith(Object.class)",
		"@TestInstance(TestInstance.Lifecycle.PER_CLASS)",
		"class UnsupportedTests {",
		"\t@ParameterizedTest void parameterized() {}",
		"\t@Test @Timeout(5) void slow() {}",
		"\t@Test private void hidden() {}",
		"\t@Test void injected(Object info) {}",
		"\t@Nested class Inner { @Test void inner() {} }",
		"}",
		""
	].join("\n"));
	const testSourcePath = [testScratch, junitApi].join(path.delimiter);
	const testProblems = JSON.parse(runCli(["--check", "--tests", "--format", "json", "--sourcepath", testSourcePath, unsupportedTests]).stdout);
	const testIds = testProblems.problems.map((problem) => `${problem.id}@${problem.line}`).sort();
	assert.deepStrictEqual(testIds, [
		"J2C-COMPILE-ERROR@8",
		"J2C-TEST-EXTENSION@11",
		"J2C-TEST-INSTANCE-LIFECYCLE@11",
		"J2C-TEST-MOCKING@8",
		"J2C-TEST-NESTED@16",
		"J2C-TEST-PARAMETERS@15",
		"J2C-TEST-PRIVATE-METHOD@14",
		"J2C-TEST-TIMEOUT@13",
		"J2C-TEST-UNSUPPORTED-KIND@12"
	], JSON.stringify(testProblems.problems, null, 1));
	assert.strictEqual(testProblems.summary.byKind["unit test"], 8);
	const withoutTestsMode = JSON.parse(runCli(["--check", "--format", "json", "--sourcepath", testSourcePath, unsupportedTests]).stdout);
	assert.deepStrictEqual(withoutTestsMode.problems.map((problem) => problem.id), ["J2C-COMPILE-ERROR"],
		"Test problems are only reported with --tests");

	if (javaMajorVersion() >= 17) {
		// Every legacy fixture exercises translator paths that the Java 17
		// AST level can break; the whole tree must keep translating.
		const corpusOutput = path.join(tempDirectory, "corpus output");
		fs.mkdirSync(corpusOutput);
		const corpus = runCli(["--output", corpusOutput, "--sourcepath", sourceRoot, sourceRoot]);
		assert.strictEqual(corpus.status, 0, corpus.stderr);
		const corpusSources = filesUnder(path.join(corpusOutput, "src"))
			.filter((file) => file.endsWith(".cpp"));
		assert(corpusSources.length > 100, `Only ${corpusSources.length} sources generated`);
		for (const file of corpusSources) {
			assert(
				!fs.readFileSync(file, "utf8").includes("MISSING"),
				`Unresolved AST placeholder in ${file}`
			);
		}

		// Anonymous and local classes are named Outer$1 and Outer$1Local (G47)
		const corpusTest = path.join(corpusOutput, "src", "se", "arnetheduck", "j2c", "test");
		assert(fs.readFileSync(path.join(corpusTest, "LocalClassTest_1.cpp"), "utf8")
			.includes('::class_(u"se.arnetheduck.j2c.test.LocalClassTest$1", 40);'));
		assert(fs.readFileSync(path.join(corpusTest, "LocalClassTest_localVar_C_1.cpp"), "utf8")
			.includes('::class_(u"se.arnetheduck.j2c.test.LocalClassTest$1C", 41);'));
		assert(!corpusSources.some((file) => fs.readFileSync(file, "utf8").includes('::class_(u"", 0)')),
			"every class has a name");

		const corpusCheck = runCli(["--check", "--sourcepath", sourceRoot, sourceRoot]);
		assert.strictEqual(corpusCheck.status, 0, corpusCheck.stdout);
		assert.match(corpusCheck.stdout, /J2C check: no problems found in \d+ file\(s\)/);

		// Text blocks, enum/String switches, and try-with-resources
		const featuresOutput = path.join(tempDirectory, "features output");
		fs.mkdirSync(featuresOutput);
		const features = runCli([
			"--output", featuresOutput,
			"--sourcepath", sourceRoot,
			featuresFixture
		]);
		assert.strictEqual(features.status, 0, features.stderr);
		const featureSource = fs.readFileSync(path.join(
			featuresOutput, "src", "se", "arnetheduck", "j2c", "test", "Java17Features.cpp"
		), "utf8");
		assert(featureSource.includes(String.raw`u"J2C\n  \"text block\"\n"_j`));
		assert.match(featureSource, /\(v == Java17Features_Shape::CIRCLE\)/);
		assert(featureSource.includes(String.raw`(npc(v)->equals(u"sat"_j)) || (npc(v)->equals(u"sun"_j))`));
		assert(featureSource.includes(String.raw`(npc(v)->equals(u"go"_j)) || (npc(v)->equals(u"run"_j))`));
		// Resources close on a normal exit, or close and suppress, then rethrow (G45)
		assert(featureSource.includes("auto finally0 = on_normal_exit([&] { if (reader != nullptr) { reader->close(); } });"));
		assert(featureSource.includes("primary0->addSuppressed(suppressed0);"));
		assert(featureSource.includes("throw;"));
		// throw uses the dynamic type unless the operand is a new object (G46)
		assert(featureSource.includes("npc(e)->j2c_throw();"));
		assert(featureSource.includes("throw new ::java::lang::IllegalArgumentException(static_cast< ::java::lang::Throwable* >(s));"));
		assert(fs.readFileSync(path.join(featuresOutput, "ext", "src", "java", "lang", "Throwable.hpp"), "utf8")
			.includes("[[noreturn]] virtual void j2c_throw() { throw this; }"));
		assert(!featureSource.includes("try ("), "try-with-resources must not be emitted verbatim");
		// Boxed compound assignments, increments and decrements make new boxes
		assert(featureSource.includes("a = ::java::lang::Integer::valueOf(static_cast<int32_t>((npc(a))->intValue() + 2))"));
		assert(featureSource.includes("b = ::java::lang::Long::valueOf(static_cast<int64_t>((npc(b))->longValue() * 3))"));
		assert(featureSource.includes("auto _j2c_old = a;"));
		assert(featureSource.includes("jdiv<int32_t>((npc(a))->intValue(), 2)"));
		assert(!featureSource.includes("->intValue() +="), "a box must not be updated in place");
		// (int) object checks the box, then unboxes
		assert(featureSource.includes("(npc(java_cast< ::java::lang::Integer* >(value)))->intValue()"));
		// A conditional with an intersection type casts to a nameable type
		assert(featureSource.includes("static_cast< ::java::lang::Object* >(u\"none\"_j)"));
		assert(!/#include <Object\.hpp>/.test(featureSource), "no header exists for an intersection type");
		// Implicit enum constructor parameters cannot clash with Java ones
		const namedHeader = fs.readFileSync(path.join(
			featuresOutput, "src", "se", "arnetheduck", "j2c", "test", "Java17Features_Named.hpp"
		), "utf8");
		assert(namedHeader.includes("::java::lang::String* j2c_name, int j2c_ordinal, ::java::lang::String* name"));
		// JDK headers narrow covariant returns that would need circular
		// includes, as source headers do, and callers cast back
		const localDateHeader = fs.readFileSync(path.join(
			featuresOutput, "ext", "src", "java", "time", "LocalDate.hpp"
		), "utf8");
		assert(localDateHeader.includes("::java::time::chrono::ChronoLocalDateTime* atTime(LocalTime* a0) override;"), localDateHeader);
		assert(!localDateHeader.includes(" LocalDateTime* atTime(LocalTime* a0) override;"));
		const localDateTimeHeader = fs.readFileSync(path.join(
			featuresOutput, "ext", "src", "java", "time", "LocalDateTime.hpp"
		), "utf8");
		assert(localDateTimeHeader.includes("::java::time::chrono::ChronoLocalDate* toLocalDate() override;"));
		assert(featureSource.includes("java_cast< ::java::time::LocalDateTime* >(npc(::java::time::LocalDate::now())->atTime("));
		// Iface.super.m() calls that interface's implementation (G33)
		const greetingSource = fs.readFileSync(path.join(
			featuresOutput, "src", "se", "arnetheduck", "j2c", "test", "Java17Features_Greeting.cpp"
		), "utf8");
		assert(greetingSource.includes("append(Java17Features_Polite::greet(name))"), greetingSource);
		assert(greetingSource.includes("java_cast< ::java::lang::String* >(Java17Features_Echo::echo(value))"));
		assert(greetingSource.includes("::java::util::Comparator::reversed()"));
		assert(!greetingSource.includes("super::"));

		const lambdasOutput = path.join(tempDirectory, "lambdas output");
		fs.mkdirSync(lambdasOutput);
		const lambdaFixture = runCli([
			"--output", lambdasOutput,
			"--sourcepath", sourceRoot,
			path.join(fixtureDirectory, "Java17Lambdas.java")
		]);
		assert.strictEqual(lambdaFixture.status, 0, lambdaFixture.stderr);
		const lambdaSources = filesUnder(path.join(lambdasOutput, "src"))
			.filter((file) => file.endsWith(".cpp"))
			.map((file) => fs.readFileSync(file, "utf8"))
			.join("\n");
		// A local class created from a lambda's or an anonymous class gets the
		// variables it captured from that class; a local subclass passes
		// them to its local superclass (G34)
		assert(lambdaSources.includes("return new Java17Lambdas_localFromOtherClasses_Offset_1(base);"));
		assert(lambdaSources.includes("(new Java17Lambdas_localFromOtherClasses_Twice_1(base))->twice(1)"));
		assert(lambdaSources.includes(": super(base, ::default_init_tag())"));
		// A default overriding another default is declared, not skipped (G50)
		const upperHeader = fs.readFileSync(path.join(
			lambdasOutput, "src", "se", "arnetheduck", "j2c", "test", "Java17Lambdas_Upper.hpp"
		), "utf8");
		assert(upperHeader.includes("int32_t level() override;"), upperHeader);
		assert(!upperHeader.includes("already declared"));
		// Each erasure of advance() is forwarded to the generic superclass,
		// and a superclass's interface method once its type arguments are
		// known, with an upcast past the pure overload (G35)
		const tickerHeader = fs.readFileSync(path.join(
			lambdasOutput, "src", "se", "arnetheduck", "j2c", "test", "Java17Lambdas_IntTicker.hpp"
		), "utf8");
		assert(tickerHeader.includes("virtual bool advance(::java::lang::Object* action);"), tickerHeader);
		assert(tickerHeader.includes("virtual bool advance(Java17Lambdas_IntScale* action);"), tickerHeader);
		assert(lambdaSources.includes("return Java17Lambdas_Ticker::advance(action);"));
		assert(lambdaSources.includes(
			"return Java17Lambdas_EmptyStore::fill(static_cast< ::java::lang::Object* >(array));"
		));
		// toString() in a lambda means the enclosing object's toString()
		assert(lambdaSources.includes("append(Java17Lambdas_this->toString())"));
		// A reassigned bound receiver is passed in, not captured
		assert(lambdaSources.includes("->bind(tally)"));
		// A field-initializer lambda receives the enclosing instance
		assert(lambdaSources.includes("new Java17Lambdas_1(this)"));
		// An enhanced for over a new expression parenthesizes it before ->
		assert(lambdaSources.includes("auto _i = (new Java17Lambdas_Countdown(from))->iterator();"));
		// An inherited covariant return is downcast from the base's C++ type
		assert(lambdaSources.includes("return java_cast< Java17Lambdas_LimitDerived* >(Java17Lambdas_LimitBase::limit(n));"));
		// An interface bridges its default method to the generic superinterface
		const positiveHeader = fs.readFileSync(path.join(
			lambdasOutput, "src", "se", "arnetheduck", "j2c", "test", "Java17Lambdas_PositiveTally.hpp"
		), "utf8");
		assert(positiveHeader.includes("virtual bool test(::java::lang::Object* value) override;"), positiveHeader);
		// A default method overriding an abstract one is declared, not skipped
		const holderHeader = fs.readFileSync(path.join(
			lambdasOutput, "src", "se", "arnetheduck", "j2c", "test", "Java17Lambdas_Holder.hpp"
		), "utf8");
		assert(holderHeader.includes("::java::lang::Object* get() override;"), holderHeader);
		// A record accessor implementing an interface method overrides it
		const boxHeader = fs.readFileSync(path.join(
			lambdasOutput, "src", "se", "arnetheduck", "j2c", "test", "Java17Lambdas_Box.hpp"
		), "utf8");
		assert(boxHeader.includes("int32_t size() override;"), boxHeader);
		// Default and static interface methods get stub definitions
		assert(fs.existsSync(path.join(lambdasOutput, "ext", "stub", "java", "util", "Comparator-stub.cpp")));

		const advisory = runCli([
			"--output", outputDirectory,
			"--sourcepath", sourceRoot,
			sealedFixture
		]);
		assert.strictEqual(advisory.status, 0, advisory.stderr);

		let generatedHeaders = filesUnder(outputDirectory)
			.filter((file) => file.endsWith(".hpp"))
			.map((file) => fs.readFileSync(file, "utf8"))
			.join("\n");
		assert(generatedHeaders.includes("Java permitted direct subtypes:"));
		assert(generatedHeaders.includes("Java17SealedImpl"));
		assert(generatedHeaders.includes("not enforced by C++"));
		assert(generatedHeaders.includes(
			"Java non-sealed type: this inheritance branch remains open"
		));

		const rejected = runCli([
			"--output", outputDirectory,
			"--sourcepath", sourceRoot,
			"--sealed-types", "reject",
			sealedFixture
		]);
		assert.notStrictEqual(rejected.status, 0);
		assert(rejected.stderr.includes("sealed type rejected by --sealed-types reject"));
		assert(rejected.stderr.includes("non-sealed type rejected by --sealed-types reject"));

		const records = runCli([
			"--output", outputDirectory,
			"--sourcepath", sourceRoot,
			recordsFixture
		]);
		assert.strictEqual(records.status, 0, records.stderr);

		generatedHeaders = filesUnder(outputDirectory)
			.filter((file) => file.endsWith(".hpp"))
			.map((file) => fs.readFileSync(file, "utf8"))
			.join("\n");
		assert(generatedHeaders.includes("Java17User"));
		assert(generatedHeaders.includes("operator=="));
		// Enum constants with bodies take the enum constructors' arguments
		assert.match(
			generatedHeaders,
			/Java17Records_Tier_1\(::java::lang::String\* j2c_name, int j2c_ordinal, Java17Records_Limits\* main0, Java17Records_Limits\* side0, int32_t copies0\);/,
			"Enum constant bodies must declare the enum constructors"
		);
		assert.match(
			generatedHeaders,
			/Java17Records_Tier_2\(::java::lang::String\* j2c_name, int j2c_ordinal, Java17Records_Limits\* main0, Java17Records_Limits\* side0, int32_t copies0, int32_t extra0\);/
		);

		const controlFlow = runCli([
			"--output", outputDirectory,
			"--sourcepath", sourceRoot,
			controlFlowFixture
		]);
		assert.strictEqual(controlFlow.status, 0, controlFlow.stderr);
		const generatedSources = filesUnder(outputDirectory)
			.filter((file) => file.endsWith(".cpp"))
			.map((file) => fs.readFileSync(file, "utf8"))
			.join("\n");
		assert(generatedSources.includes("patternLength"));
		assert(generatedSources.includes("switchValue"));
		assert.match(
			generatedSources,
			/case int32_t\(0\):\s*return int32_t\(1\);/
		);
		assert.match(
			generatedSources,
			/case int32_t\(0\):\s*result = 10;\s*break;/,
			"Arrow rules in switch statements must not return or fall through"
		);
		assert.match(
			generatedSources,
			/\(v == Java17ControlFlow_Light::AMBER\) \|\| \(v == Java17ControlFlow_Light::GREEN\)/
		);

		const arithmeticOutput = path.join(tempDirectory, "arithmetic output");
		fs.mkdirSync(arithmeticOutput);
		const arithmetic = runCli([
			"--output", arithmeticOutput,
			"--sourcepath", sourceRoot,
			path.join(fixtureDirectory, "Java17Arithmetic.java")
		]);
		assert.strictEqual(arithmetic.status, 0, arithmetic.stderr);
		const arithmeticSource = fs.readFileSync(path.join(
			arithmeticOutput, "src", "se", "arnetheduck", "j2c", "test", "Java17Arithmetic.cpp"
		), "utf8");
		// Arrays are covariant in every dimension: Sub[][] derives from Base[][]
		assert(arithmeticSource.includes(
			"typedef ::SubArray< u\"[[Lse.arnetheduck.j2c.test.Java17Arithmetic$VarargSub;\", ::se::arnetheduck::j2c::test::Java17Arithmetic_VarargSubArray, Java17Arithmetic_VarargBaseArrayArray > Java17Arithmetic_VarargSubArrayArray;"
		));
		// Class names are binary names, as Class.getName() returns them (G47)
		assert(fs.readFileSync(path.join(
			arithmeticOutput, "src", "se", "arnetheduck", "j2c", "test", "Java17Arithmetic_VarargSub.cpp"
		), "utf8").includes('::class_(u"se.arnetheduck.j2c.test.Java17Arithmetic$VarargSub", 50);'));
		const arithmeticArrays = fs.readFileSync(path.join(arithmeticOutput, "src", "Array.hpp"), "utf8");
		assert(arithmeticArrays.includes('::class_(u"[I", 2)'));
		assert(fs.readFileSync(path.join(arithmeticOutput, "src", "ObjectArray.hpp"), "utf8")
			.includes('::class_(u"[Ljava.lang.Object;", 19)'));
		// Array accesses are checked unless J2C_NO_BOUNDS_CHECKS is defined,
		// and j2c_array.cpp throws the exceptions with the JVM's messages (G43)
		assert(arithmeticArrays.includes("value_type &operator[](size_type i) { return p[j2c_check_index(i, length)]; }"));
		assert(arithmeticArrays.includes("#ifndef J2C_NO_BOUNDS_CHECKS"));
		assert(arithmeticArrays.includes("Array(int n) : length(j2c_check_size(n))"));
		const objectArrays = fs.readFileSync(path.join(arithmeticOutput, "src", "ObjectArray.hpp"), "utf8");
		assert(objectArrays.includes("T set(size_type i, T x) { set0(j2c_check_index(i, length), x); return x; }"));
		assert(fs.readFileSync(path.join(arithmeticOutput, "src", "SubArray.hpp"), "utf8")
			.includes("this->p[j2c_check_index(i, this->length)]"));
		const arrayChecks = fs.readFileSync(path.join(arithmeticOutput, "src", "j2c_array.cpp"), "utf8");
		assert(arrayChecks.includes('u" out of bounds for length "'));
		assert(arrayChecks.includes("#include <java/lang/ArrayIndexOutOfBoundsException.hpp>"));
		assert(arrayChecks.includes("#include <java/lang/NegativeArraySizeException.hpp>"));
		assert(fs.existsSync(path.join(arithmeticOutput, "ext", "src", "java", "lang", "ArrayIndexOutOfBoundsException.hpp")));
		assert(fs.existsSync(path.join(arithmeticOutput, "ext", "src", "java", "lang", "NegativeArraySizeException.hpp")));
		assert.match(fs.readFileSync(path.join(arithmeticOutput, "Makefile"), "utf8"), /^\tsrc\/j2c_array\.cpp \\$/m);
		assert(arithmeticSource.includes("return (*values)[i];"));
		// Operands before the first String are added as numbers (G49)
		const concatSource = arithmeticSource.replace(/\s*\r?\n\s*/g, "");
		assert(concatSource.includes("return ::java::lang::StringBuilder().append((i + j))->append(u\" ints \"_j)->append(c)->append(i)"),
			arithmeticSource);
		assert(concatSource.includes("(::java::lang::StringBuilder().append((c + i))->append(u\"char\"_j)"));
		// A varargs constructor called with no arguments gets an empty array
		assert(arithmeticSource.includes("(new Java17Arithmetic_VarargCount(new ::int32_tArray()))->count"));
	} else {
		console.log("Java 17 translation checks skipped (requires Java 17+)");
	}

	console.log("CLI integration checks passed");
} finally {
	fs.rmSync(tempDirectory, { recursive: true, force: true });
}
