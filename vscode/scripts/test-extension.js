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
	const mcpProviders = new Map();
	const configurationListeners = [];
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
		EventEmitter: class {
			constructor() {
				this.listeners = [];
				this.event = (listener) => {
					this.listeners.push(listener);
					return { dispose() {} };
				};
			}
			fire() {
				this.listeners.forEach((listener) => listener());
			}
			dispose() {}
		},
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
			showWarningMessage: (message) => {
				warnings.push(message);
				return Promise.resolve(options.warningChoice);
			},
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
			workspaceFolders,
			onDidChangeConfiguration: (listener) => {
				configurationListeners.push(listener);
				return { dispose() {} };
			},
			onDidChangeWorkspaceFolders: () => ({ dispose() {} }),
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
	if (options.mcpApi) {
		vscode.McpStdioServerDefinition = class {
			constructor(label, command, args, env, version) {
				Object.assign(this, { label, command, args, env, version });
			}
		};
		vscode.lm = {
			registerMcpServerDefinitionProvider: (id, provider) => {
				mcpProviders.set(id, provider);
				return { dispose() {} };
			}
		};
	}

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
		extension: { packageJSON: { version: "9.9.9" } },
		extensionPath: options.extensionPath || tempDirectory,
		subscriptions: []
	};
	extension.activate(context);

	return {
		cancel: () => cancellationListeners.forEach((listener) => listener()),
		changeConfiguration: (section) => configurationListeners.forEach((listener) =>
			listener({ affectsConfiguration: (name) => name === section })
		),
		commands,
		config,
		context,
		diagnostics,
		mcpProviders,
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
			"--format", "json",
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

async function testJsonReportPublishesDiagnostics() {
	const root = path.join(tempDirectory, "json workspace");
	const input = makeFile(path.join(root, "src", "Anonymous.java"));
	const bundledJar = makeFile(path.join(tempDirectory, "json extension", "lib", "j2c-cli.jar"));
	const report = {
		schemaVersion: 1,
		tool: "j2c",
		command: "check",
		status: "problems",
		problems: [{
			id: "J2C-LAMBDA-ANONYMOUS-THIS",
			kind: "lambda expression",
			severity: "error",
			file: input,
			line: 4,
			column: 22,
			message: "Java 8 lambda expression cannot be translated",
			hint: "Assign this to a local variable before the lambda"
		}]
	};
	const harness = createHarness({
		extensionPath: path.dirname(path.dirname(bundledJar)),
		spawnOutcome: {
			code: 1,
			stdout: `${JSON.stringify(report)}\n`,
			stderr: "progress on stderr\n"
		},
		workspaceFolders: [createWorkspace(root)]
	});
	await harness.commands.get("j2c.check")({ fsPath: input });

	const args = harness.spawned[0][1];
	assert.deepStrictEqual(args.slice(2, 5), ["--check", "--format", "json"]);
	const [diagnostic] = harness.diagnostics.entries.get(input);
	assert.strictEqual(diagnostic.range.startLine, 3);
	assert.strictEqual(diagnostic.range.startCharacter, 21);
	assert.strictEqual(diagnostic.code, "J2C-LAMBDA-ANONYMOUS-THIS");
	assert(diagnostic.message.includes("How to fix: Assign this"));
	const log = harness.outputs[0].lines.join("");
	assert(log.includes(`${input}:4:22: Java 8 lambda expression cannot be translated [J2C-LAMBDA-ANONYMOUS-THIS]`));
	assert(log.includes("progress on stderr"));
	assert(!log.includes("schemaVersion"), "The raw JSON report is not shown to people");
	assert(harness.warnings[0].includes("1 problem(s) would stop translation"));
}

async function testOutputDirectoryGuard() {
	const root = path.join(tempDirectory, "guard workspace");
	const input = makeFile(path.join(root, "Guard.java"));
	const bundledJar = makeFile(path.join(tempDirectory, "guard extension", "lib", "j2c-cli.jar"));
	const occupied = path.join(tempDirectory, "occupied output");
	makeFile(path.join(occupied, "notes.txt"));
	const base = {
		extensionPath: path.dirname(path.dirname(bundledJar)),
		outputDirectory: occupied,
		workspaceFolders: [createWorkspace(root)]
	};

	const declined = createHarness(base);
	await declined.commands.get("j2c.translate")({ fsPath: input });
	assert(declined.warnings[0].includes("was not created by J2C"));
	assert.strictEqual(declined.spawned.length, 0, "Declining must not translate");

	const confirmed = createHarness({ ...base, warningChoice: "Translate Anyway" });
	await confirmed.commands.get("j2c.translate")({ fsPath: input });
	assert(confirmed.spawned[0][1].includes("--force"));

	// Previous J2C output is replaced without asking
	makeFile(path.join(occupied, ".j2c-output"));
	const marked = createHarness(base);
	await marked.commands.get("j2c.translate")({ fsPath: input });
	assert.deepStrictEqual(marked.warnings, []);
	assert(!marked.spawned[0][1].includes("--force"));
}

async function testMcpServerDefinitions() {
	const root = path.join(tempDirectory, "mcp workspace");
	fs.mkdirSync(root, { recursive: true });
	const bundledJar = makeFile(path.join(tempDirectory, "mcp extension", "lib", "j2c-cli.jar"));
	const harness = createHarness({
		config: { javaExecutable: "java 17" },
		extensionPath: path.dirname(path.dirname(bundledJar)),
		mcpApi: true,
		workspaceFolders: [{ name: "mcp workspace", uri: { fsPath: root } }]
	});

	const provider = harness.mcpProviders.get("j2c");
	assert(provider, "The MCP server definition provider is registered");
	const [server] = await provider.provideMcpServerDefinitions();
	assert.strictEqual(server.label, "J2C (mcp workspace)");
	assert.strictEqual(server.command, "java 17");
	assert.deepStrictEqual(server.args, ["-jar", bundledJar, "mcp", "--root", root]);
	assert.strictEqual(server.cwd.fsPath, root);
	assert.strictEqual(server.version, "9.9.9");

	let changes = 0;
	provider.onDidChangeMcpServerDefinitions(() => changes++);
	harness.changeConfiguration("j2c");
	assert.strictEqual(changes, 1, "Changing J2C settings refreshes the server list");

	harness.config["mcp.enabled"] = false;
	assert.deepStrictEqual(await provider.provideMcpServerDefinitions(), []);

	// Editors without the MCP API still activate normally
	const withoutApi = createHarness({ workspaceFolders: [] });
	assert.strictEqual(withoutApi.mcpProviders.size, 0);
	assert(withoutApi.commands.has("j2c.translate"));
}

async function testTestsSettingPassesFlag() {
	const root = path.join(tempDirectory, "tests workspace");
	const input = makeFile(path.join(root, "src", "CalculatorTest.java"));
	const bundledJar = makeFile(path.join(tempDirectory, "tests extension", "lib", "j2c-cli.jar"));
	const harness = createHarness({
		config: { tests: true },
		extensionPath: path.dirname(path.dirname(bundledJar)),
		workspaceFolders: [createWorkspace(root)]
	});
	await harness.commands.get("j2c.check")({ fsPath: input });
	assert(harness.spawned[0][1].includes("--tests"), "j2c.tests checks JUnit tests");
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
		await testJsonReportPublishesDiagnostics();
		await testOutputDirectoryGuard();
		await testMcpServerDefinitions();
		await testTestsSettingPassesFlag();
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
