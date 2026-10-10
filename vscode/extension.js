const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");
const vscode = require("vscode");

let translating = false;

function activate(context) {
	const output = vscode.window.createOutputChannel("J2C");
	context.subscriptions.push(output);
	context.subscriptions.push(vscode.commands.registerCommand(
		"j2c.translate",
		(target) => translate(context, output, target)
	));
}

async function translate(context, output, target) {
	if (translating) {
		vscode.window.showWarningMessage(
			"J2C: A translation is already running."
		);
		return;
	}

	const targets = getTargets(target);
	if (targets.length === 0) {
		vscode.window.showErrorMessage(
			"J2C: Select a Java file or source folder, or open a Java file first."
		);
		return;
	}

	for (const file of targets) {
		if (!fs.existsSync(file)) {
			vscode.window.showErrorMessage(`J2C: Input does not exist: ${file}`);
			return;
		}
		const stat = fs.statSync(file);
		if (!stat.isDirectory() && path.extname(file).toLowerCase() !== ".java") {
			vscode.window.showErrorMessage(
				`J2C: Expected a Java file or source folder: ${file}`
			);
			return;
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
		return;
	}
	const configuration = vscode.workspace.getConfiguration("j2c");
	const sourcePaths = getSourcePaths(configuration, workspaceFolder);
	const classPath = getClassPath(configuration, workspaceFolder);
	const outputSelection = await vscode.window.showOpenDialog({
		canSelectFiles: false,
		canSelectFolders: true,
		canSelectMany: false,
		openLabel: "Use as J2C output directory"
	});
	if (!outputSelection || outputSelection.length === 0) {
		return;
	}

	const outputDirectory = outputSelection[0].fsPath;
	const configuredJar = configuration.get("cliJar", "");
	const cliJar = configuredJar
		? path.resolve(configuredJar)
		: path.join(context.extensionPath, "lib", "j2c-cli.jar");
	if (!fs.existsSync(cliJar)) {
		vscode.window.showErrorMessage(
			`J2C CLI jar not found: ${cliJar}. Build/package the extension or set j2c.cliJar.`
		);
		return;
	}

	const args = ["-jar", cliJar, "--output", outputDirectory];
	args.push("--sealed-types", configuration.get("sealedTypes", "advisory"));
	if (sourcePaths.length > 0) {
		args.push("--sourcepath", sourcePaths.join(path.delimiter));
	}
	if (classPath.length > 0) {
		args.push("--classpath", classPath.join(path.delimiter));
	}
	if (workspaceFolder) {
		args.push("--name", path.basename(workspaceFolder.uri.fsPath));
	}
	args.push(...targets);

	output.appendLine(`Translating ${targets.join(", ")}`);
	output.appendLine(`Output: ${outputDirectory}`);
	translating = true;
	try {
		await vscode.window.withProgress({
			location: vscode.ProgressLocation.Notification,
			title: "J2C: Translating Java to C++",
			cancellable: true
		}, (_progress, token) => runAndReport(
			configuration.get("javaExecutable", "java"),
			args,
			workspaceFolder ? workspaceFolder.uri.fsPath : path.dirname(targets[0]),
			output,
			outputDirectory,
			token
		));
	} finally {
		translating = false;
	}
	output.show(true);
}

async function runAndReport(executable, args, cwd, output, outputDirectory, token) {
	try {
		const result = await runCli(executable, args, cwd, output, token);
		if (result.cancelled) {
			output.appendLine("Translation cancelled; the output directory may be incomplete.");
			vscode.window.showWarningMessage(
				"J2C: Translation cancelled. The output directory may be incomplete."
			);
		} else if (result.code === 0) {
			vscode.window.showInformationMessage(
				`J2C: Translation complete. Output: ${outputDirectory}`
			);
		} else {
			vscode.window.showErrorMessage(
				`J2C: Translation failed (exit code ${result.code}). See the J2C output channel.`
			);
		}
	} catch (error) {
		output.appendLine(String(error));
		vscode.window.showErrorMessage(
			`J2C: Could not start the translator. See the J2C output channel.`
		);
	}
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
		child.stdout.on("data", (data) => output.append(data));
		child.stderr.on("data", (data) => output.append(data));
		child.on("error", settle(reject));
		child.on("close", settle((code) => resolve({ code, cancelled })));
	});
}

function deactivate() {}

module.exports = { activate, deactivate };
