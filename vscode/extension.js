const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");
const vscode = require("vscode");

// "path:line: message", as printed by CLIs without --format json
const PROBLEM_PATTERN = /^(.+\.java):(\d+): (.+?)\r?$/;
// Written by the CLI into every output directory it creates
const OUTPUT_MARKER = ".j2c-output";
// A workspace folder with one of these is a Maven or Gradle project
const BUILD_FILES = ["pom.xml", "build.gradle", "build.gradle.kts", "settings.gradle", "settings.gradle.kts"];

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
	registerMcpServer(context);
}

/**
 * Offers the bundled CLI to agents (Copilot agent mode) as an MCP server per
 * workspace folder. Editors without the MCP extension API skip this.
 */
function registerMcpServer(context) {
	if (!vscode.lm || typeof vscode.lm.registerMcpServerDefinitionProvider !== "function"
		|| typeof vscode.McpStdioServerDefinition !== "function") {
		return;
	}
	const changed = new vscode.EventEmitter();
	context.subscriptions.push(changed);
	context.subscriptions.push(vscode.workspace.onDidChangeConfiguration((event) => {
		if (event.affectsConfiguration("j2c")) {
			changed.fire();
		}
	}));
	context.subscriptions.push(vscode.workspace.onDidChangeWorkspaceFolders(() => changed.fire()));
	context.subscriptions.push(vscode.lm.registerMcpServerDefinitionProvider("j2c", {
		onDidChangeMcpServerDefinitions: changed.event,
		provideMcpServerDefinitions: () => mcpServers(context)
	}));
}

function mcpServers(context) {
	const configuration = vscode.workspace.getConfiguration("j2c");
	if (!configuration.get("mcp.enabled", true)) {
		return [];
	}
	const cliJar = cliJarPath(configuration, context);
	if (!fs.existsSync(cliJar)) {
		return [];
	}
	return (vscode.workspace.workspaceFolders || []).map((folder) => {
		const server = new vscode.McpStdioServerDefinition(
			`J2C (${folder.name})`,
			configuration.get("javaExecutable", "java"),
			["-jar", cliJar, "mcp", "--root", folder.uri.fsPath,
				...(configuration.get("project", "auto") === "none" ? ["--project", "none"] : [])],
			{},
			extensionVersion(context)
		);
		server.cwd = folder.uri;
		return server;
	});
}

async function translate(session, target) {
	const invocation = await prepare(session, target, true);
	if (!invocation) {
		return;
	}
	await execute(session, invocation, "Translating Java to C++", (result, problems, report) => {
		if (result.code === 0) {
			const files = report && report.generatedFiles
				? ` (${report.generatedFiles.length} files)`
				: "";
			vscode.window.showInformationMessage(
				`J2C: Translation complete${files}. Output: ${invocation.outputDirectory}`
			);
		} else if (problems > 0) {
			vscode.window.showErrorMessage(
				`J2C: ${problems} problem(s) stop translation. See the Problems panel.`
			);
		} else {
			vscode.window.showErrorMessage(
				`J2C: Translation failed (exit code ${result.code})${errorDetail(report)}. See the J2C output channel.`
			);
		}
	});
}

async function check(session, target) {
	const invocation = await prepare(session, target, false);
	if (!invocation) {
		return;
	}
	await execute(session, invocation, "Checking translatability", (result, problems, report) => {
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
				`J2C: Check failed (exit code ${result.code})${errorDetail(report)}. See the J2C output channel.`
			);
		}
	});
}

function errorDetail(report) {
	return report && report.message ? `: ${report.message}` : "";
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
	const project = projectSetup(configuration, workspaceFolder, targets);
	const sourcePaths = getSourcePaths(configuration, workspaceFolder, project.detected);
	const classPath = getClassPath(configuration, workspaceFolder);

	let outputDirectory;
	let force = false;
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

		// Translation moves an output directory's contents aside first
		if (!isReplaceable(outputDirectory)) {
			const choice = await vscode.window.showWarningMessage(
				`J2C: ${outputDirectory} is not empty and was not created by J2C. ` +
				"Translating moves its contents to a backup in the temporary folder.",
				{ modal: true },
				"Translate Anyway"
			);
			if (choice !== "Translate Anyway") {
				return undefined;
			}
			force = true;
		}
	}

	const cliJar = cliJarPath(configuration, session.context);
	if (!fs.existsSync(cliJar)) {
		vscode.window.showErrorMessage(
			`J2C CLI jar not found: ${cliJar}. Build/package the extension or set j2c.cliJar.`
		);
		return undefined;
	}

	const args = ["-jar", cliJar];
	args.push(...(writesOutput ? ["--output", outputDirectory] : ["--check"]));
	args.push("--format", "json");
	if (force) {
		args.push("--force");
	}
	args.push("--sealed-types", configuration.get("sealedTypes", "advisory"));
	if (configuration.get("tests", false)) {
		args.push("--tests");
	}
	args.push(...project.args);
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

function isReplaceable(directory) {
	try {
		const entries = fs.readdirSync(directory);
		return entries.length === 0 || entries.includes(OUTPUT_MARKER);
	} catch (error) {
		// A missing directory is reported by the CLI
		return true;
	}
}

function cliJarPath(configuration, context) {
	const configuredJar = configuration.get("cliJar", "");
	return configuredJar
		? path.resolve(configuredJar)
		: path.join(context.extensionPath, "lib", "j2c-cli.jar");
}

function extensionVersion(context) {
	return context.extension && context.extension.packageJSON
		? context.extension.packageJSON.version
		: "unknown";
}

async function execute(session, invocation, title, onComplete) {
	const { context, output, diagnostics } = session;
	output.appendLine(`J2C ${extensionVersion(context)}: ${title} ${invocation.targets.join(", ")}`);
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
				const report = parseReport(result.stdout);
				if (report) {
					logReport(output, report);
				} else {
					// A custom j2c.cliJar without --format json support
					output.append(result.stdout);
				}
				const problems = report
					? publishReport(diagnostics, report)
					: publishText(diagnostics, result.text);
				onComplete(result, problems, report);
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

/** The CLI's --format json report, or undefined if stdout is not one */
function parseReport(stdout) {
	const lines = stdout.split(/\r?\n/).filter((line) => line.trim() !== "");
	for (let i = lines.length - 1; i >= 0; i--) {
		try {
			const report = JSON.parse(lines[i]);
			if (report && report.tool === "j2c" && report.schemaVersion) {
				return report;
			}
		} catch (error) {
			// Not JSON
		}
	}
	return undefined;
}

function logReport(output, report) {
	for (const project of report.projects || []) {
		output.appendLine(`${project.buildTool} project ${project.root}: ${project.sourceRoots.length} source root(s), `
			+ `class path from ${project.classPathSource} (${project.classPath.length} entries)`);
	}
	for (const warning of report.warnings || []) {
		output.appendLine(`${warning.file}: warning: ${warning.message} [${warning.id}]`);
	}
	for (const problem of report.problems || []) {
		output.appendLine(`${problem.file}:${problem.line}:${problem.column}: ${problem.message} [${problem.id}]`);
	}
	if (report.generatedFiles) {
		output.appendLine(`Generated ${report.generatedFiles.length} files in ${report.output}`);
	}
	if (report.message) {
		output.appendLine(report.message);
	}
}

/** Replaces the J2C diagnostics with the report's problems */
function publishReport(diagnostics, report) {
	const byFile = new Map();
	for (const problem of report.problems || []) {
		const line = Math.max(problem.line - 1, 0);
		const column = Math.max((problem.column || 1) - 1, 0);
		const diagnostic = new vscode.Diagnostic(
			new vscode.Range(line, column, line, Number.MAX_SAFE_INTEGER),
			problem.hint ? `${problem.message}\nHow to fix: ${problem.hint}` : problem.message,
			vscode.DiagnosticSeverity.Error
		);
		diagnostic.source = "j2c";
		diagnostic.code = problem.id;
		addDiagnostic(byFile, problem.file, diagnostic);
	}
	// Warnings, such as an unresolved class path, go on the build file but
	// are not counted as problems that stop translation
	const warnings = report.warnings || [];
	for (const warning of warnings) {
		const diagnostic = new vscode.Diagnostic(
			new vscode.Range(0, 0, 0, Number.MAX_SAFE_INTEGER),
			warning.hint ? `${warning.message}\nHow to fix: ${warning.hint}` : warning.message,
			vscode.DiagnosticSeverity.Warning
		);
		diagnostic.source = "j2c";
		diagnostic.code = warning.id;
		addDiagnostic(byFile, warning.file, diagnostic);
	}
	return replaceDiagnostics(diagnostics, byFile) - warnings.length;
}

/** The same for "path:line: message" text from older CLIs */
function publishText(diagnostics, text) {
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
		addDiagnostic(byFile, match[1], diagnostic);
	}
	return replaceDiagnostics(diagnostics, byFile);
}

function addDiagnostic(byFile, file, diagnostic) {
	if (!byFile.has(file)) {
		byFile.set(file, []);
	}
	byFile.get(file).push(diagnostic);
}

function replaceDiagnostics(diagnostics, byFile) {
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

/**
 * Project auto-setup (j2c.project): the CLI detects a Maven or Gradle build
 * in a selected folder by itself; for a selection inside a workspace folder
 * with a build, the folder is named with --project, so the build's source
 * roots and class path are used.
 */
function projectSetup(configuration, workspaceFolder, targets) {
	if (configuration.get("project", "auto") === "none") {
		return { args: ["--project", "none"], detected: false };
	}
	if (!workspaceFolder) {
		return { args: [], detected: false };
	}
	const root = workspaceFolder.uri.fsPath;
	if (!BUILD_FILES.some((name) => fs.existsSync(path.join(root, name)))) {
		return { args: [], detected: false };
	}
	const selectsRoot = targets.some((target) => path.resolve(target) === path.resolve(root));
	return { args: selectsRoot ? [] : ["--project", root], detected: true };
}

function getSourcePaths(configuration, workspaceFolder, projectDetected) {
	const configured = configuration.get("sourcePaths", []);
	let relativePaths = configured;
	// A detected build supplies its own source roots
	if (relativePaths.length === 0 && projectDetected) {
		return [];
	}
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
		let stdout = "";
		let stderr = "";
		// stdout carries the JSON report; stderr is progress, shown live
		child.stdout.on("data", (data) => {
			stdout += data;
		});
		child.stderr.on("data", (data) => {
			stderr += data;
			output.append(data);
		});
		child.on("error", settle(reject));
		child.on("close", settle((code) => resolve({
			code,
			cancelled,
			stdout,
			text: stdout + stderr
		})));
	});
}

function deactivate() {}

module.exports = { activate, deactivate };
