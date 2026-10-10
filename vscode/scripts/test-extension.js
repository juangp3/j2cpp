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
	const outputs = [];
	const commands = new Map();
	const workspaceFolders = options.workspaceFolders || [];
	const config = options.config || {};
	const sourcePathConfig = options.javaSourcePaths || [];

	const vscode = {
		ProgressLocation: { Notification: 1 },
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
			showOpenDialog: async () => options.outputDirectory
				? [{ fsPath: options.outputDirectory }]
				: undefined,
			withProgress: (_progress, callback) => callback()
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
			child.stdout = new events.EventEmitter();
			child.stderr = new events.EventEmitter();
			const outcome = options.spawnOutcome || { code: 0 };
			process.nextTick(() => {
				if (outcome.error) {
					child.emit("error", outcome.error);
					return;
				}
				if (outcome.stdout) {
					child.stdout.emit("data", Buffer.from(outcome.stdout));
				}
				if (outcome.stderr) {
					child.stderr.emit("data", Buffer.from(outcome.stderr));
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
		commands,
		context,
		errors,
		information,
		outputs,
		spawned
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

async function testNoTargetIsReported() {
	const harness = createHarness();
	await harness.commands.get("j2c.translate")();

	assert.strictEqual(harness.spawned.length, 0);
	assert(harness.errors[0].includes("Select a Java file or source folder"));
}

async function main() {
	try {
		await testConfiguredTranslationArguments();
		await testCliFailureIsReported();
		await testSpawnFailureIsReported();
		await testMissingCliJarDoesNotSpawn();
		await testNoTargetIsReported();
		console.log("Extension configuration and failure checks passed");
	} finally {
		fs.rmSync(tempDirectory, { recursive: true, force: true });
	}
}

main().catch((error) => {
	console.error(error);
	process.exitCode = 1;
});
