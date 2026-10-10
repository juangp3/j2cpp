const assert = require("assert");
const events = require("events");
const fs = require("fs");
const Module = require("module");
const os = require("os");
const path = require("path");

const extensionModule = path.resolve(__dirname, "..", "extension.js");
const tempDirectory = fs.mkdtempSync(path.join(os.tmpdir(), "j2c extension test "));

function createWorkspace(root) {
	return {
		uri: { fsPath: root }
	};
}

function createHarness(options = {}) {
	const spawned = [];
	const errors = [];
	const information = [];
	const warnings = [];
	const killed = [];
	const cancellationListeners = [];
	const outputs = [];
	const commands = new Map();
	const workspaceFolders = options.workspaceFolders || [];
	const config = options.config || {};
	const sourcePathConfig = options.javaSourcePaths || [];

	const diagnostics = {
		entries: new Map(),
		clear() {
			this.entries.clear();
		},
		set(uri, fileDiagnostics) {
			this.entries.set(uri.fsPath, fileDiagnostics);
		},
		dispose() {}
	};

	const vscode = {
		Diagnostic: class {
			constructor(range, message, severity) {
				Object.assign(this, { range, message, severity });
			}
		},
		DiagnosticSeverity: { Error: 0 },
		ProgressLocation: { Notification: 1 },
		Range: class {
			constructor(startLine, startCharacter, endLine, endCharacter) {
				Object.assign(this, { startLine, startCharacter, endLine, endCharacter });
			}
		},
		languages: {
			createDiagnosticCollection: (name) => {
				assert.strictEqual(name, "j2c");
				return diagnostics;
			}
		},
		Uri: {
			file: (fsPath) => ({ fsPath })
		},
		commands: {
			registerCommand: (name, callback) => {
				commands.set(name, callback);
				return { dispose() {} };
			}
		},
		window: {
			activeTextEditor: options.activeTextEditor,
			createOutputChannel: () => {
				const lines = [];
				const output = {
					lines,
					append: (line) => lines.push(line),
					appendLine: (line) => lines.push(`${line}\n`),
					show() {}
				};
				outputs.push(output);
				return output;
			},
			showErrorMessage: (message) => errors.push(message),
			showInformationMessage: (message) => information.push(message),
			showWarningMessage: (message) => warnings.push(message),
			showOpenDialog: async () => options.outputDirectory
				? [{ fsPath: options.outputDirectory }]
				: undefined,
			withProgress: (progressOptions, callback) => {
				assert.strictEqual(progressOptions.cancellable, true);
				return callback({ report() {} }, {
					onCancellationRequested: (listener) => {
						cancellationListeners.push(listener);
						return { dispose() {} };
					}
				});
			}
		},
		workspace: {
			getConfiguration: (section) => ({
				get: (key, defaultValue) => {
					if (section === "j2c") {
						return config[key] === undefined ? defaultValue : config[key];
					}
					if (section === "java.project" && key === "sourcePaths") {
						return sourcePathConfig;
					}
					return defaultValue;
				}
			}),
			getWorkspaceFolder: (uri) => workspaceFolders.find((folder) => {
				const relative = path.relative(folder.uri.fsPath, uri.fsPath);
				return relative === "" || (
					relative !== ".." &&
					!relative.startsWith(`..${path.sep}`) &&
					!path.isAbsolute(relative)
				);
			})
		}
	};

	const childProcessMock = {
		spawn: (...args) => {
			spawned.push(args);
			const child = new events.EventEmitter();
			const stream = () => {
				const emitter = new events.EventEmitter();
				emitter.setEncoding = (encoding) => {
					emitter.encoding = encoding;
				};
				return emitter;
			};
			child.stdout = stream();
			child.stderr = stream();
			child.kill = () => {
				killed.push(child);
				process.nextTick(() => child.emit("close", null));
			};
			const outcome = (options.spawnOutcomes && options.spawnOutcomes.shift())
				|| options.spawnOutcome
				|| { code: 0 };
			process.nextTick(() => {
				assert.strictEqual(child.stdout.encoding, "utf8");
				assert.strictEqual(child.stderr.encoding, "utf8");
				if (outcome.hang) {
					return;
				}
				if (outcome.error) {
					child.emit("error", outcome.error);
					return;
				}
				if (outcome.stdout) {
					child.stdout.emit("data", outcome.stdout);
				}
				if (outcome.stderr) {
					child.stderr.emit("data", outcome.stderr);
				}
				child.emit("close", outcome.code);
			});
			return child;
		}
	};

	delete require.cache[extensionModule];
	const originalLoad = Module._load;
	Module._load = function (request, parent, isMain) {
		if (request === "vscode") {
			return vscode;
		}
		if (request === "child_process") {
			return childProcessMock;
		}
		return originalLoad.call(this, request, parent, isMain);
	};
	let extension;
	try {
		extension = require(extensionModule);
	} finally {
		Module._load = originalLoad;
	}

	const context = {
		extensionPath: options.extensionPath || tempDirectory,
		subscriptions: []
	};
	extension.activate(context);

	return {
		cancel: () => cancellationListeners.forEach((listener) => listener()),
		commands,
		context,
		diagnostics,
		errors,
		information,
		killed,
		outputs,
		spawned,
		warnings
	};
}

function makeFile(filePath) {
	fs.mkdirSync(path.dirname(filePath), { recursive: true });
	fs.writeFileSync(filePath, "");
	return filePath;
}

async function testConfiguredTranslationArguments() {
	const root = path.join(tempDirectory, "configured workspace");
	const workspace = createWorkspace(root);
	const sourceRoot = path.join(root, "configured src");
	const fallbackRoot = path.join(root, "java src");
	const dependency = path.join(root, "deps", "library one.jar");
	const absoluteDependency = path.join(tempDirectory, "absolute dependency");
	const cliJar = makeFile(path.join(tempDirectory, "custom cli.jar"));
	const input = makeFile(path.join(sourceRoot, "Sample.java"));
	const outputDirectory = path.join(tempDirectory, "generated output");
	fs.mkdirSync(fallbackRoot, { recursive: true });
	makeFile(dependency);
	fs.mkdirSync(absoluteDependency, { recursive: true });

	const harness = createHarness({
		config: {
			sourcePaths: ["configured src", "missing src"],
			classPath: [path.relative(root, dependency), absoluteDependency],
			cliJar,
			javaExecutable: "java executable with spaces",
			sealedTypes: "reject"
		},
		javaSourcePaths: ["java src"],
		outputDirectory,
		workspaceFolders: [workspace]
	});
	await harness.commands.get("j2c.translate")({ fsPath: input });

	assert.strictEqual(harness.spawned.length, 1);
	assert.deepStrictEqual(harness.spawned[0], [
		"java executable with spaces",
		[
			"-jar", cliJar,
			"--output", outputDirectory,
			"--sealed-types", "reject",
			"--sourcepath", sourceRoot,
			"--classpath", [dependency, absoluteDependency].join(path.delimiter),
			"--name", path.basename(root),
			input
		],
		{ cwd: root, windowsHide: true }
	]);
	assert.deepStrictEqual(harness.errors, []);
	assert.strictEqual(harness.information.length, 1);
	assert(harness.information[0].includes("Translation complete"));
	assert(harness.outputs[0].lines.some((line) => line.includes(input)));
	assert(harness.context.subscriptions.length > 0);
}

async function testCliFailureIsReported() {
	const root = path.join(tempDirectory, "failed workspace");
	const input = makeFile(path.join(root, "src", "Failure.java"));
	const bundledJar = makeFile(path.join(tempDirectory, "bundled", "lib", "j2c-cli.jar"));
	const harness = createHarness({
		extensionPath: path.dirname(path.dirname(bundledJar)),
		outputDirectory: path.join(tempDirectory, "failed output"),
		spawnOutcome: {
			code: 2,
			stdout: "translator output\n",
			stderr: "translation diagnostic\n"
		},
		workspaceFolders: [createWorkspace(root)]
	});
	await harness.commands.get("j2c.translate")({ fsPath: input });

	assert.strictEqual(harness.spawned.length, 1);
	assert.strictEqual(harness.errors.length, 1);
	assert(harness.errors[0].includes("exit code 2"));
	assert(harness.outputs[0].lines.some((line) => line.includes("translator output")));
	assert(harness.outputs[0].lines.some((line) => line.includes("translation diagnostic")));
	assert.deepStrictEqual(harness.information, []);
}

async function testSpawnFailureIsReported() {
	const root = path.join(tempDirectory, "spawn failure workspace");
	const input = makeFile(path.join(root, "Failure.java"));
	const bundledJar = makeFile(path.join(tempDirectory, "spawn failure extension", "lib", "j2c-cli.jar"));
	const harness = createHarness({
		extensionPath: path.dirname(path.dirname(bundledJar)),
		outputDirectory: path.join(tempDirectory, "spawn failure output"),
		spawnOutcome: { error: new Error("missing Java runtime") },
		workspaceFolders: [createWorkspace(root)]
	});
	await harness.commands.get("j2c.translate")({ fsPath: input });

	assert.strictEqual(harness.spawned.length, 1);
	assert(harness.errors[0].includes("Could not start the translator"));
	assert(harness.outputs[0].lines.some((line) =>
		line.includes("missing Java runtime")
	));
	assert.deepStrictEqual(harness.information, []);
}

async function testMissingCliJarDoesNotSpawn() {
	const root = path.join(tempDirectory, "missing jar workspace");
	const input = makeFile(path.join(root, "MissingJar.java"));
	const harness = createHarness({
		config: { cliJar: path.join(tempDirectory, "missing-cli.jar") },
		outputDirectory: path.join(tempDirectory, "missing jar output"),
		workspaceFolders: [createWorkspace(root)]
	});
	await harness.commands.get("j2c.translate")({ fsPath: input });

	assert.strictEqual(harness.spawned.length, 0);
	assert(harness.errors[0].includes("J2C CLI jar not found"));
}

async function testCancellationStopsTranslator() {
	const root = path.join(tempDirectory, "cancelled workspace");
	const input = makeFile(path.join(root, "Slow.java"));
	const bundledJar = makeFile(path.join(tempDirectory, "cancel extension", "lib", "j2c-cli.jar"));
	const harness = createHarness({
		extensionPath: path.dirname(path.dirname(bundledJar)),
		outputDirectory: path.join(tempDirectory, "cancelled output"),
		spawnOutcome: { hang: true },
		workspaceFolders: [createWorkspace(root)]
	});
	const translate = harness.commands.get("j2c.translate");
	const running = translate({ fsPath: input });
	await new Promise((resolve) => setImmediate(resolve));

	await translate({ fsPath: input });
	assert.strictEqual(harness.spawned.length, 1, "A second run must not start");
	assert(harness.warnings[0].includes("already running"));

	harness.cancel();
	await running;
	assert.strictEqual(harness.killed.length, 1);
	assert(harness.warnings[1].includes("Cancelled"));
	assert.deepStrictEqual(harness.errors, []);
	assert.deepStrictEqual(harness.information, []);

	const rerun = translate({ fsPath: input });
	await new Promise((resolve) => setImmediate(resolve));
	assert.strictEqual(harness.spawned.length, 2, "A run must be possible after cancelling");
	harness.cancel();
	await rerun;
}

async function testCheckPublishesProblems() {
	const root = path.join(tempDirectory, "check workspace");
	const input = makeFile(path.join(root, "src", "Lambdas.java"));
	const other = path.join(root, "src", "Other.java");
	const bundledJar = makeFile(path.join(tempDirectory, "check extension", "lib", "j2c-cli.jar"));
	const harness = createHarness({
		extensionPath: path.dirname(path.dirname(bundledJar)),
		spawnOutcomes: [
			{
				code: 1,
				stdout: [
					`${input}:5: Java 8 lambda expressions are not supported`,
					`${input}:9: Java 8 method references are not supported`,
					`${other}:2: Syntax error on token "}"`,
					"J2C check: 3 problem(s) in 2 of 2 file(s)",
					"  1 x lambda expression",
					""
				].join("\r\n")
			},
			{ code: 0, stdout: "J2C check: no problems found in 2 file(s)\n" }
		],
		workspaceFolders: [createWorkspace(root)]
	});
	const check = harness.commands.get("j2c.check");
	await check({ fsPath: path.join(root, "src") });

	assert.strictEqual(harness.spawned.length, 1);
	const args = harness.spawned[0][1];
	assert(args.includes("--check"));
	assert(!args.includes("--output"), "A check must not write output");
	assert(!args.includes("--name"));
	assert.strictEqual(args[args.length - 1], path.join(root, "src"));

	const inputProblems = harness.diagnostics.entries.get(input);
	assert.strictEqual(inputProblems.length, 2);
	assert.strictEqual(inputProblems[0].range.startLine, 4);
	assert.strictEqual(inputProblems[0].message, "Java 8 lambda expressions are not supported");
	assert.strictEqual(inputProblems[0].source, "j2c");
	assert.strictEqual(harness.diagnostics.entries.get(other)[0].range.startLine, 1);
	assert(harness.warnings[0].includes("3 problem(s) would stop translation"));
	assert.deepStrictEqual(harness.errors, []);

	await check({ fsPath: path.join(root, "src") });
	assert.strictEqual(harness.diagnostics.entries.size, 0, "A clean check clears old problems");
	assert(harness.information[0].includes("No problems found"));
}

async function testTranslationProblemsArePublished() {
	const root = path.join(tempDirectory, "problem workspace");
	const input = makeFile(path.join(root, "Broken.java"));
	const bundledJar = makeFile(path.join(tempDirectory, "problem extension", "lib", "j2c-cli.jar"));
	const harness = createHarness({
		extensionPath: path.dirname(path.dirname(bundledJar)),
		outputDirectory: path.join(tempDirectory, "problem output"),
		spawnOutcome: {
			code: 1,
			stderr: `J2C translation failed:\n${input}:3: Java 8 lambda expressions are not supported\n`
		},
		workspaceFolders: [createWorkspace(root)]
	});
	await harness.commands.get("j2c.translate")({ fsPath: input });

	assert.strictEqual(harness.diagnostics.entries.get(input).length, 1);
	assert(harness.errors[0].includes("1 problem(s) stop translation"));
}

async function testNoTargetIsReported() {
	const harness = createHarness();
	await harness.commands.get("j2c.translate")();

	assert.strictEqual(harness.spawned.length, 0);
	assert(harness.errors[0].includes("Select a Java file or source folder"));
}

async function main() {
	// A test whose promise never settles lets Node exit with an empty event
	// loop; fail unless every test actually completed.
	process.exitCode = 1;
	try {
		await testConfiguredTranslationArguments();
		await testCliFailureIsReported();
		await testSpawnFailureIsReported();
		await testMissingCliJarDoesNotSpawn();
		await testCancellationStopsTranslator();
		await testCheckPublishesProblems();
		await testTranslationProblemsArePublished();
		await testNoTargetIsReported();
		console.log("Extension configuration and failure checks passed");
		process.exitCode = 0;
	} finally {
		fs.rmSync(tempDirectory, { recursive: true, force: true });
	}
}

main().catch((error) => {
	console.error(error);
	process.exitCode = 1;
});
