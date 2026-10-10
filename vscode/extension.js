const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");
const vscode = require("vscode");

// "path:line: message", as printed by the CLI for problems in Java sources
const PROBLEM_PATTERN = /^(.+\.java):(\d+): (.+?)\r?$/;

let running = false;

function activate(context) {
	const output = vscode.window.createOutputChannel("J2C");
	const diagnostics = vscode.languages.createDiagnosticCollection("j2c");
	const session = { context, output, diagnostics };
	context.subscriptions.push(output, diagnostics);
	context.subscriptions.push(vscode.commands.registerCommand(
		"j2c.translate",
		(target) => translate(session, target)
	));
	context.subscriptions.push(vscode.commands.registerCommand(
		"j2c.check",
		(target) => check(session, target)
	));
}

async function translate(session, target) {
	const invocation = await prepare(session, target, true);
	if (!invocation) {
		return;
	}
	await execute(session, invocation, "Translating Java to C++", (result, problems) => {
		if (result.code === 0) {
			vscode.window.showInformationMessage(
				`J2C: Translation complete. Output: ${invocation.outputDirectory}`
			);
		} else if (problems > 0) {
			vscode.window.showErrorMessage(
				`J2C: ${problems} problem(s) stop translation. See the Problems panel.`
			);
		} else {
			vscode.window.showErrorMessage(
				`J2C: Translation failed (exit code ${result.code}). See the J2C output channel.`
			);
		}
	});
}

async function check(session, target) {
	const invocation = await prepare(session, target, false);
	if (!invocation) {
		return;
	}
	await execute(session, invocation, "Checking translatability", (result, problems) => {
		if (result.code === 0) {
			vscode.window.showInformationMessage(
				"J2C: No problems found; the selection can be translated."
			);
		} else if (result.code === 1 && problems > 0) {
			vscode.window.showWarningMessage(
				`J2C: ${problems} problem(s) would stop translation. See the Problems panel.`
			);
		} else {
			vscode.window.showErrorMessage(
				`J2C: Check failed (exit code ${result.code}). See the J2C output channel.`
			);
		}
	});
}

/**
 * Validates the selection and settings and builds the CLI invocation, or
 * shows an error and returns undefined.
 */
async function prepare(session, target, writesOutput) {
	if (running) {
		vscode.window.showWarningMessage(
			"J2C: A translation or check is already running."
		);
		return undefined;
	}

	const targets = getTargets(target);
	if (targets.length === 0) {
		vscode.window.showErrorMessage(
			"J2C: Select a Java file or source folder, or open a Java file first."
		);
		return undefined;
	}

	for (const file of targets) {
		if (!fs.existsSync(file)) {
			vscode.window.showErrorMessage(`J2C: Input does not exist: ${file}`);
			return undefined;
		}
		const stat = fs.statSync(file);
		if (!stat.isDirectory() && path.extname(file).toLowerCase() !== ".java") {
			vscode.window.showErrorMessage(
				`J2C: Expected a Java file or source folder: ${file}`
			);
			return undefined;
		}
	}

	const workspaceFolder = vscode.workspace.getWorkspaceFolder(
		vscode.Uri.file(targets[0])
	);
	if (workspaceFolder && targets.some((file) => {
		const folder = vscode.workspace.getWorkspaceFolder(
			vscode.Uri.file(file)
		);
		return !folder || folder.uri.fsPath !== workspaceFolder.uri.fsPath;
	})) {
		vscode.window.showErrorMessage(
			"J2C: Translate files from one workspace folder at a time."
		);
		return undefined;
	}
	const configuration = vscode.workspace.getConfiguration("j2c");
	const sourcePaths = getSourcePaths(configuration, workspaceFolder);
	const classPath = getClassPath(configuration, workspaceFolder);

	let outputDirectory;
	if (writesOutput) {
		const outputSelection = await vscode.window.showOpenDialog({
			canSelectFiles: false,
			canSelectFolders: true,
			canSelectMany: false,
			openLabel: "Use as J2C output directory"
		});
		if (!outputSelection || outputSelection.length === 0) {
			return undefined;
		}
		outputDirectory = outputSelection[0].fsPath;
	}

	const configuredJar = configuration.get("cliJar", "");
	const cliJar = configuredJar
		? path.resolve(configuredJar)
		: path.join(session.context.extensionPath, "lib", "j2c-cli.jar");
	if (!fs.existsSync(cliJar)) {
		vscode.window.showErrorMessage(
			`J2C CLI jar not found: ${cliJar}. Build/package the extension or set j2c.cliJar.`
		);
		return undefined;
	}

	const args = ["-jar", cliJar];
	args.push(...(writesOutput ? ["--output", outputDirectory] : ["--check"]));
	args.push("--sealed-types", configuration.get("sealedTypes", "advisory"));
	if (sourcePaths.length > 0) {
		args.push("--sourcepath", sourcePaths.join(path.delimiter));
	}
	if (classPath.length > 0) {
		args.push("--classpath", classPath.join(path.delimiter));
	}
	if (workspaceFolder && writesOutput) {
		args.push("--name", path.basename(workspaceFolder.uri.fsPath));
	}
	args.push(...targets);

	return {
		args,
		cwd: workspaceFolder ? workspaceFolder.uri.fsPath : path.dirname(targets[0]),
		executable: configuration.get("javaExecutable", "java"),
		outputDirectory,
		targets
	};
}

async function execute(session, invocation, title, onComplete) {
	const { context, output, diagnostics } = session;
	const version = context.extension && context.extension.packageJSON
		? context.extension.packageJSON.version
		: "unknown";
	output.appendLine(`J2C ${version}: ${title} ${invocation.targets.join(", ")}`);
	if (invocation.outputDirectory) {
		output.appendLine(`Output: ${invocation.outputDirectory}`);
	}

	running = true;
	try {
		await vscode.window.withProgress({
			location: vscode.ProgressLocation.Notification,
			title: `J2C: ${title}`,
			cancellable: true
		}, async (_progress, token) => {
			try {
				const result = await runCli(
					invocation.executable, invocation.args, invocation.cwd, output, token
				);
				if (result.cancelled) {
					output.appendLine("Cancelled; any output directory may be incomplete.");
					vscode.window.showWarningMessage(
						"J2C: Cancelled. Any output directory may be incomplete."
					);
					return;
				}
				onComplete(result, publishProblems(diagnostics, result.text));
			} catch (error) {
				output.appendLine(String(error));
				vscode.window.showErrorMessage(
					"J2C: Could not start the translator. See the J2C output channel."
				);
			}
		});
	} finally {
		running = false;
	}
	output.show(true);
}

/** Replaces the J2C diagnostics with the problems in the CLI output */
function publishProblems(diagnostics, text) {
	const byFile = new Map();
	for (const line of text.split("\n")) {
		const match = line.match(PROBLEM_PATTERN);
		if (!match) {
			continue;
		}
		const lineIndex = Math.max(Number(match[2]) - 1, 0);
		const diagnostic = new vscode.Diagnostic(
			new vscode.Range(lineIndex, 0, lineIndex, Number.MAX_SAFE_INTEGER),
			match[3],
			vscode.DiagnosticSeverity.Error
		);
		diagnostic.source = "j2c";
		if (!byFile.has(match[1])) {
			byFile.set(match[1], []);
		}
		byFile.get(match[1]).push(diagnostic);
	}

	diagnostics.clear();
	let count = 0;
	for (const [file, fileDiagnostics] of byFile) {
		diagnostics.set(vscode.Uri.file(file), fileDiagnostics);
		count += fileDiagnostics.length;
	}
	return count;
}

function getTargets(target) {
	const values = Array.isArray(target) ? target : [target];
	const files = values.map((value) => {
		if (typeof value === "string") {
			return value;
		}
		if (value && value.fsPath) {
			return value.fsPath;
		}
		if (value && value.resourceUri && value.resourceUri.fsPath) {
			return value.resourceUri.fsPath;
		}
		return undefined;
	}).filter(Boolean);
	if (files.length > 0) {
		return files;
	}

	const editor = vscode.window.activeTextEditor;
	return editor && editor.document.languageId === "java"
		? [editor.document.uri.fsPath]
		: [];
}

function getSourcePaths(configuration, workspaceFolder) {
	const configured = configuration.get("sourcePaths", []);
	let relativePaths = configured;
	if (relativePaths.length === 0) {
		relativePaths = vscode.workspace.getConfiguration("java.project")
			.get("sourcePaths", []);
	}
	if (relativePaths.length === 0) {
		relativePaths = ["src/main/java", "src/test/java", "src"];
	}
	if (!workspaceFolder) {
		return [];
	}

	return relativePaths.map((sourcePath) =>
		path.resolve(workspaceFolder.uri.fsPath, sourcePath)
	).filter((sourcePath) => fs.existsSync(sourcePath));
}

function getClassPath(configuration, workspaceFolder) {
	const paths = configuration.get("classPath", []);
	if (!workspaceFolder) {
		return paths.map((classpath) => path.resolve(classpath));
	}
	return paths.map((classpath) => path.isAbsolute(classpath)
		? classpath
		: path.resolve(workspaceFolder.uri.fsPath, classpath)
	);
}

function runCli(executable, args, cwd, output, token) {
	return new Promise((resolve, reject) => {
		const child = childProcess.spawn(executable, args, {
			cwd,
			windowsHide: true
		});
		let cancelled = false;
		const cancellation = token && token.onCancellationRequested(() => {
			cancelled = true;
			child.kill();
		});
		const settle = (callback) => (value) => {
			if (cancellation) {
				cancellation.dispose();
			}
			callback(value);
		};
		// Decode as streams so multi-byte characters split across chunks
		// are not corrupted
		child.stdout.setEncoding("utf8");
		child.stderr.setEncoding("utf8");
		let text = "";
		const capture = (data) => {
			text += data;
			output.append(data);
		};
		child.stdout.on("data", capture);
		child.stderr.on("data", capture);
		child.on("error", settle(reject));
		child.on("close", settle((code) => resolve({ code, cancelled, text })));
	});
}

function deactivate() {}

module.exports = { activate, deactivate };
