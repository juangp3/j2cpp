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
	const codeActionProviders = [];
	const documentListeners = [];
	const workspaceFolders = options.workspaceFolders || [];
	const config = options.config || {};
	const sourcePathConfig = options.javaSourcePaths || [];

	const diagnosticCollection = () => ({
		entries: new Map(),
		clear() {
			this.entries.clear();
		},
		set(uri, fileDiagnostics) {
			this.entries.set(uri.fsPath, fileDiagnostics);
		},
		dispose() {}
	});
	const diagnostics = diagnosticCollection();
	const buildDiagnostics = diagnosticCollection();

	const vscode = {
		Diagnostic: class {
			constructor(range, message, severity) {
				Object.assign(this, { range, message, severity });
			}
		},
		DiagnosticSeverity: { Error: 0, Warning: 1, Information: 2 },
		DiagnosticRelatedInformation: class {
			constructor(location, message) {
				Object.assign(this, { location, message });
			}
		},
		Location: class {
			constructor(uri, range) {
				Object.assign(this, { uri, range });
			}
		},
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
				this.start = { line: startLine, character: startCharacter };
				this.end = { line: endLine, character: endCharacter };
			}
		},
		CodeAction: class {
			constructor(title, kind) {
				Object.assign(this, { title, kind });
			}
		},
		CodeActionKind: { QuickFix: "quickfix" },
		WorkspaceEdit: class {
			constructor() {
				this.replacements = [];
			}
			replace(uri, range, newText) {
				this.replacements.push({ uri, range, newText });
			}
		},
		languages: {
			createDiagnosticCollection: (name) => {
				assert(name === "j2c" || name === "j2c-build", name);
				return name === "j2c" ? diagnostics : buildDiagnostics;
			},
			registerCodeActionsProvider: (selector, provider, metadata) => {
				codeActionProviders.push({ selector, provider, metadata });
				return { dispose() {} };
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
			onDidChangeTextDocument: (listener) => {
				documentListeners.push(listener);
				return { dispose() {} };
			},
			textDocuments: options.textDocuments || [],
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
		changeDocument: (fsPath) => documentListeners.forEach((listener) =>
			listener({ document: { uri: { fsPath } }, contentChanges: [{ text: "x" }] })
		),
		codeActionProviders,
		commands,
		config,
		context,
		diagnostics,
		buildDiagnostics,
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
		schemaVersion: 3,
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

async function testQuickFixesFromJsonReport() {
	const root = path.join(tempDirectory, "quick fix workspace");
	const input = makeFile(path.join(root, "src", "Anonymous.java"));
	const dirty = makeFile(path.join(root, "src", "Dirty.java"));
	const bundledJar = makeFile(path.join(tempDirectory, "quick fix extension", "lib", "j2c-cli.jar"));
	const fix = {
		title: "Use a local variable for this (var self = this;)",
		edits: [
			{ file: input, range: { start: { line: 3, column: 3 }, end: { line: 3, column: 3 } }, newText: "var self = this;\n\t\t" },
			{ file: input, range: { start: { line: 4, column: 40 }, end: { line: 4, column: 44 } }, newText: "self" }
		]
	};
	const problem = (file, line, column, fixes) => ({
		id: "J2C-LAMBDA-ANONYMOUS-THIS",
		kind: "lambda expression",
		severity: "error",
		file,
		line,
		column,
		message: "Java 8 lambda expression cannot be translated",
		hint: "Assign this to a local variable",
		fixes
	});
	const report = {
		schemaVersion: 3,
		tool: "j2c",
		command: "check",
		status: "problems",
		problems: [
			problem(input, 4, 22, [fix]),
			problem(input, 9, 5, []),
			problem(dirty, 4, 22, [{ ...fix, edits: [{ ...fix.edits[1], file: dirty }] }])
		]
	};
	const harness = createHarness({
		extensionPath: path.dirname(path.dirname(bundledJar)),
		spawnOutcome: { code: 1, stdout: `${JSON.stringify(report)}\n` },
		textDocuments: [{ uri: { fsPath: dirty }, isDirty: true }],
		workspaceFolders: [createWorkspace(root)]
	});
	const [registration] = harness.codeActionProviders;
	assert.deepStrictEqual(registration.selector, { language: "java" });
	assert.deepStrictEqual(registration.metadata.providedCodeActionKinds, ["quickfix"]);
	await harness.commands.get("j2c.check")({ fsPath: input });

	const provide = (file, diagnostics) => registration.provider.provideCodeActions(
		{ uri: { fsPath: file } }, diagnostics[0].range, { diagnostics });
	const [withFix, withoutFix] = harness.diagnostics.entries.get(input);
	const other = { ...withFix, source: "java" };
	const actions = provide(input, [withFix, withoutFix, other]);
	assert.strictEqual(actions.length, 1, "Only J2C diagnostics with fixes get actions");
	const [action] = actions;
	assert.strictEqual(action.title, "J2C: Use a local variable for this (var self = this;)");
	assert.strictEqual(action.kind, "quickfix");
	assert.strictEqual(action.isPreferred, true);
	assert.deepStrictEqual(action.diagnostics, [withFix]);
	assert.deepStrictEqual(action.edit.replacements.map((replacement) => [
		replacement.uri.fsPath,
		replacement.range.startLine, replacement.range.startCharacter,
		replacement.range.endLine, replacement.range.endCharacter,
		replacement.newText
	]), [
		[input, 2, 2, 2, 2, "var self = this;\n\t\t"],
		[input, 3, 39, 3, 43, "self"]
	], "1-based JSON positions become 0-based editor positions");

	// The CLI read the saved file, so an unsaved one gets no fixes
	assert.deepStrictEqual(provide(dirty, harness.diagnostics.entries.get(dirty)), []);
	// Once the file changes, the fixes' ranges are stale
	harness.changeDocument(input);
	assert.deepStrictEqual(provide(input, [withFix]), []);
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

// j2c.project: a Maven or Gradle workspace folder is named with --project
// for selections inside it, and supplies the source roots; none turns it off
async function testProjectSetting() {
	const root = path.join(tempDirectory, "maven workspace");
	const pom = makeFile(path.join(root, "pom.xml"));
	const input = makeFile(path.join(root, "src", "main", "java", "demo", "App.java"));
	const bundledJar = makeFile(path.join(tempDirectory, "project extension", "lib", "j2c-cli.jar"));
	const extensionPath = path.dirname(path.dirname(bundledJar));
	const workspace = { name: "maven workspace", uri: { fsPath: root } };

	const auto = createHarness({ extensionPath, workspaceFolders: [workspace] });
	await auto.commands.get("j2c.check")({ fsPath: input });
	const autoArgs = auto.spawned[0][1];
	const projectIndex = autoArgs.indexOf("--project");
	assert(projectIndex > 0, JSON.stringify(autoArgs));
	assert.strictEqual(autoArgs[projectIndex + 1], root);
	assert(!autoArgs.includes("--sourcepath"), "The build supplies the source roots");

	// Selecting the project folder itself lets the CLI detect the build
	const folder = createHarness({ extensionPath, workspaceFolders: [workspace] });
	await folder.commands.get("j2c.check")({ fsPath: root });
	assert(!folder.spawned[0][1].includes("--project"));
	assert.strictEqual(folder.spawned[0][1][folder.spawned[0][1].length - 1], root);

	// Explicit source paths still win
	const configured = createHarness({ config: { sourcePaths: ["src/main/java"] }, extensionPath,
		workspaceFolders: [workspace] });
	await configured.commands.get("j2c.check")({ fsPath: input });
	assert(configured.spawned[0][1].includes("--sourcepath"));

	const none = createHarness({ config: { project: "none" }, extensionPath, mcpApi: true,
		workspaceFolders: [workspace] });
	await none.commands.get("j2c.check")({ fsPath: input });
	const noneArgs = none.spawned[0][1];
	assert.deepStrictEqual(noneArgs.slice(noneArgs.indexOf("--project"), noneArgs.indexOf("--project") + 2),
		["--project", "none"]);
	assert(noneArgs[noneArgs.indexOf("--sourcepath") + 1].startsWith(path.join(root, "src", "main", "java")),
		"Without detection, the default source roots are passed");
	const [server] = await none.mcpProviders.get("j2c").provideMcpServerDefinitions();
	assert.deepStrictEqual(server.args.slice(-2), ["--project", "none"]);

	// Without a build file nothing is added
	const plainRoot = path.join(tempDirectory, "plain workspace");
	const plainInput = makeFile(path.join(plainRoot, "src", "Plain.java"));
	const plain = createHarness({ extensionPath, workspaceFolders: [createWorkspace(plainRoot)] });
	await plain.commands.get("j2c.check")({ fsPath: plainInput });
	assert(!plain.spawned[0][1].includes("--project"));

	// A class path warning becomes a warning on the build file, which does
	// not count as a problem
	const report = {
		schemaVersion: 3,
		tool: "j2c",
		command: "check",
		status: "ok",
		files: 1,
		problems: [],
		projects: [{ buildTool: "maven", root, sourceRoots: [path.join(root, "src", "main", "java")],
			testRoots: [], classPath: [], classPathSource: "unresolved" }],
		warnings: [{
			id: "J2C-PROJECT-CLASSPATH-UNRESOLVED",
			kind: "project setup",
			severity: "warning",
			file: pom,
			line: 1,
			column: 0,
			message: "Maven was not found",
			hint: "Install Maven"
		}]
	};
	const warned = createHarness({
		extensionPath,
		spawnOutcome: { code: 0, stdout: `${JSON.stringify(report)}\n` },
		workspaceFolders: [workspace]
	});
	await warned.commands.get("j2c.check")({ fsPath: input });
	const [warning] = warned.diagnostics.entries.get(pom);
	assert.strictEqual(warning.severity, 1);
	assert.strictEqual(warning.code, "J2C-PROJECT-CLASSPATH-UNRESOLVED");
	assert(warning.message.includes("How to fix: Install Maven"));
	assert(warned.information[0].includes("No problems found"), JSON.stringify(warned.information));
	const log = warned.outputs[0].lines.join("");
	assert(log.includes("maven project"), log);
	assert(log.includes("warning: Maven was not found [J2C-PROJECT-CLASSPATH-UNRESOLVED]"), log);
}

// j2c.lineDirectives adds --line-directives to translations, not checks
async function testLineDirectivesSetting() {
	const root = path.join(tempDirectory, "line directives workspace");
	const input = makeFile(path.join(root, "src", "Sample.java"));
	const bundledJar = makeFile(path.join(tempDirectory, "line directives extension", "lib", "j2c-cli.jar"));
	const harness = createHarness({
		config: { lineDirectives: true },
		extensionPath: path.dirname(path.dirname(bundledJar)),
		outputDirectory: path.join(tempDirectory, "line directives output"),
		workspaceFolders: [createWorkspace(root)]
	});
	await harness.commands.get("j2c.translate")({ fsPath: input });
	await harness.commands.get("j2c.check")({ fsPath: input });
	assert(harness.spawned[0][1].includes("--line-directives"), JSON.stringify(harness.spawned[0][1]));
	assert(!harness.spawned[1][1].includes("--line-directives"), "Checks write no C++");
}

// J2C: Build Translated C++ runs j2c build in a J2C output directory and
// shows the compiler's errors at their Java lines, apart from check problems
async function testBuildPublishesMappedDiagnostics() {
	const root = path.join(tempDirectory, "build workspace");
	const java = makeFile(path.join(root, "src", "demo", "Counter.java"));
	const output = path.join(root, "cpp");
	makeFile(path.join(output, ".j2c-output"));
	const cpp = makeFile(path.join(output, "src", "demo", "Counter.cpp"));
	const header = makeFile(path.join(output, "ext", "src", "java", "lang", "Object.hpp"));
	const bundledJar = makeFile(path.join(tempDirectory, "build extension", "lib", "j2c-cli.jar"));
	const extensionPath = path.dirname(path.dirname(bundledJar));
	const location = (file, line, column) => column ? { file, line, column } : { file, line };
	const diagnostic = (severity, message, javaLocation, cppLocation, mapped, notes = []) => ({
		severity, message, java: javaLocation, cpp: cppLocation, mapped, context: null, includedFrom: [], notes
	});
	const report = {
		schemaVersion: 3,
		tool: "j2c",
		command: "build",
		status: "failed",
		exitCode: 2,
		output,
		translated: false,
		diagnostics: [
			diagnostic("error", "'x' was not declared in this scope", location(java, 12), location(cpp, 34, 15),
				"line-directive", [diagnostic("note", "suggested alternative: 'step'", location(java, 11), location(cpp, 33, 10), "line-directive")]),
			diagnostic("warning", "unused variable 'c'", location(java, 18), location(cpp, 51, 5), "nearest"),
			diagnostic("error", "missing.hpp: No such file or directory", null, location(header, 3, 10), "none"),
			diagnostic("error", "cannot find -lmissing", null, null, "none")
		],
		summary: { errors: 3, warnings: 1 },
		log: [],
		logTruncated: false
	};
	const harness = createHarness({
		config: { "build.make": "gmake", "build.wslDistribution": "Ubuntu" },
		extensionPath,
		spawnOutcome: { code: 1, stdout: `${JSON.stringify(report)}\n`, stderr: "Compile src/demo/Counter.cpp\n" },
		workspaceFolders: [createWorkspace(root)]
	});
	await harness.commands.get("j2c.build")({ fsPath: output });

	assert.deepStrictEqual(harness.spawned[0][1], ["-jar", bundledJar, "build", "--output", output,
		"--format", "json", "--make", "gmake", "--wsl", "auto", "--wsl-distro", "Ubuntu"]);
	assert.deepStrictEqual(harness.spawned[0][2], { cwd: root, windowsHide: true });
	const [mapped] = harness.buildDiagnostics.entries.get(java);
	assert.strictEqual(mapped.range.startLine, 11);
	assert.strictEqual(mapped.severity, 0);
	assert.strictEqual(mapped.source, "j2c build");
	assert(mapped.message.startsWith("'x' was not declared in this scope"), mapped.message);
	assert(mapped.message.includes(`C++: ${cpp}:34:15`), mapped.message);
	assert.strictEqual(mapped.relatedInformation[0].location.uri.fsPath, java);
	assert.strictEqual(mapped.relatedInformation[0].location.range.startLine, 10);
	// Generated code without a directive stays in C++, naming the nearest
	// Java line; files without directives are C++ only
	const cppDiagnostics = harness.buildDiagnostics.entries.get(cpp);
	assert.strictEqual(cppDiagnostics[0].severity, 1);
	assert.strictEqual(cppDiagnostics[0].range.startLine, 50);
	assert.strictEqual(cppDiagnostics[0].range.startCharacter, 4);
	assert(cppDiagnostics[0].message.includes(`Nearest Java line: ${java}:18`));
	assert.strictEqual(harness.buildDiagnostics.entries.get(header)[0].range.startLine, 2);
	assert.strictEqual(harness.buildDiagnostics.entries.size, 3, "A diagnostic without a location is only logged");
	assert.strictEqual(harness.diagnostics.entries.size, 0, "Check problems are kept apart");
	assert(harness.errors[0].includes("Build failed with 3 error(s)"), JSON.stringify(harness.errors));
	const log = harness.outputs[0].lines.join("");
	assert(log.includes(`${java}:12: error: 'x' was not declared in this scope`), log);
	assert(log.includes("error: cannot find -lmissing"), log);
	assert(log.includes("Compile src/demo/Counter.cpp"), log);

	// A clean build replaces them
	const clean = { ...report, status: "ok", exitCode: 0, diagnostics: [], summary: { errors: 0, warnings: 0 } };
	const succeeded = createHarness({
		extensionPath,
		spawnOutcome: { code: 0, stdout: `${JSON.stringify(clean)}\n` },
		workspaceFolders: [createWorkspace(root)]
	});
	await succeeded.commands.get("j2c.build")({ fsPath: output });
	assert.deepStrictEqual(succeeded.spawned[0][1].slice(-2), ["--wsl", "auto"]);
	assert(succeeded.information[0].includes("Build succeeded"), JSON.stringify(succeeded.information));

	// A folder that is not J2C output asks for one, and is refused
	const notOutput = path.join(root, "src");
	const refused = createHarness({
		extensionPath,
		outputDirectory: notOutput,
		workspaceFolders: [createWorkspace(root)]
	});
	await refused.commands.get("j2c.build")({ fsPath: notOutput });
	assert.strictEqual(refused.spawned.length, 0);
	assert(refused.errors[0].includes("is not J2C output"), JSON.stringify(refused.errors));

	// make could not be run: the CLI's message is shown
	const unavailable = createHarness({
		extensionPath,
		spawnOutcome: { code: 1, stdout: `${JSON.stringify({ schemaVersion: 3, tool: "j2c", command: "build", status: "error", message: "Could not run make" })}\n` },
		workspaceFolders: [createWorkspace(root)]
	});
	await unavailable.commands.get("j2c.build")({ fsPath: output });
	assert(unavailable.errors[0].includes("Build could not run (exit code 1): Could not run make"), JSON.stringify(unavailable.errors));
}

// Menu "when" clauses may only use context keys VS Code defines: an
// unknown key is silently false and hides the menu item
function testMenuContextKeys() {
	const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, "..", "package.json"), "utf8"));
	const known = new Set(["editorLangId", "resourceLangId", "explorerResourceIsFolder"]);
	const explorer = manifest.contributes.menus["explorer/context"];
	for (const menus of Object.values(manifest.contributes.menus)) {
		for (const item of menus) {
			for (const key of item.when.split(/\|\||&&/).map((term) => term.split("==")[0].trim())) {
				assert.ok(known.has(key), `${item.command} uses unknown context key ${key}`);
			}
		}
	}
	for (const command of ["j2c.translate", "j2c.check", "j2c.build"]) {
		const item = explorer.find((candidate) => candidate.command === command);
		assert.ok(item.when.includes("explorerResourceIsFolder"), `${command} is not offered on folders`);
	}
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
		await testQuickFixesFromJsonReport();
		await testOutputDirectoryGuard();
		await testMcpServerDefinitions();
		await testTestsSettingPassesFlag();
		await testProjectSetting();
		await testNoTargetIsReported();
		await testLineDirectivesSetting();
		await testBuildPublishesMappedDiagnostics();
		testMenuContextKeys();
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
