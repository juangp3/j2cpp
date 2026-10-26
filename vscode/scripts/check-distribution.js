// Checks the agent distribution files that the release publishes by hand:
// the MCP registry server.json, the npm package it names, the Claude Code
// plugin and marketplace, and the icons. They must parse, agree with each
// other, and carry the version of pom.xml and vscode/package.json (or
// --version <v>, the release tag). With the CLI JAR built, the npm launcher
// must start the MCP server. Run by prepare.js and the release workflow.
const assert = require("assert");
const childProcess = require("child_process");
const fs = require("fs");
const path = require("path");

const extensionDirectory = path.resolve(__dirname, "..");
const repositoryRoot = path.resolve(extensionDirectory, "..");
const cliJar = path.join(repositoryRoot, "target", "j2c-cli.jar");

function readJson(relative) {
	const file = path.join(repositoryRoot, relative);
	try {
		return JSON.parse(fs.readFileSync(file, "utf8"));
	} catch (error) {
		throw new Error(`${relative}: ${error.message}`);
	}
}

function pomVersion() {
	const pom = fs.readFileSync(path.join(repositoryRoot, "pom.xml"), "utf8");
	// The project's own version follows its artifactId, before any dependency
	const match = pom.match(/<artifactId>j2c-cli<\/artifactId>\s*<version>([^<]+)<\/version>/);
	assert(match, "pom.xml: project version not found");
	return match[1];
}

const versionArgument = process.argv.indexOf("--version");
const extensionVersion = readJson("vscode/package.json").version;
const version = versionArgument >= 0 ? process.argv[versionArgument + 1] : extensionVersion;
assert.strictEqual(extensionVersion, version, "vscode/package.json version");
assert.strictEqual(pomVersion(), version, "pom.xml version");

// MCP registry: server.json names the npm package, which claims the name
const server = readJson("server.json");
const npmPackage = readJson("agents/npm/package.json");
assert(/^https:\/\/static\.modelcontextprotocol\.io\/schemas\/\d{4}-\d{2}-\d{2}\/server\.schema\.json$/.test(server.$schema),
	`server.json: unexpected $schema ${server.$schema}`);
assert(/^io\.github\.[A-Za-z0-9-]+\/[A-Za-z0-9._-]+$/.test(server.name), `server.json: name ${server.name}`);
assert(server.description.length >= 1 && server.description.length <= 100, "server.json: description is 1 to 100 characters");
assert(!server.title || server.title.length <= 100, "server.json: title is at most 100 characters");
assert.strictEqual(server.version, version, "server.json version");
assert.deepStrictEqual(Object.keys(server.repository).sort(), ["source", "url"]);
assert.strictEqual(server.repository.source, "github");
assert.strictEqual(server.packages.length, 1, "server.json: one package");
const registryPackage = server.packages[0];
assert.strictEqual(registryPackage.registryType, "npm");
assert.strictEqual(registryPackage.identifier, npmPackage.name, "server.json package identifier");
assert.strictEqual(registryPackage.version, version, "server.json package version");
assert.deepStrictEqual(registryPackage.transport, { type: "stdio" });
for (const argument of registryPackage.packageArguments || []) {
	assert(["named", "positional"].includes(argument.type), JSON.stringify(argument));
	assert(argument.type !== "named" || argument.name, JSON.stringify(argument));
}
assert.strictEqual(npmPackage.mcpName, server.name, "agents/npm/package.json mcpName must match server.json name");
assert.strictEqual(npmPackage.version, version, "agents/npm/package.json version");
assert.strictEqual(npmPackage.license, readJson("vscode/package.json").license);
for (const bin of Object.values(npmPackage.bin)) {
	const file = path.join(repositoryRoot, "agents", "npm", bin);
	assert(fs.existsSync(file), `agents/npm: missing ${bin}`);
	assert(!fs.readFileSync(file, "utf8").includes("\r"), `agents/npm/${bin}: CR line ends break the shebang on Unix`);
	const syntax = childProcess.spawnSync(process.execPath, ["--check", file], { encoding: "utf8" });
	assert.strictEqual(syntax.status, 0, syntax.stderr);
}
// The repository URL is the one server.json declares
assert.strictEqual(npmPackage.repository.url.replace(/\.git$/, ""), server.repository.url);

// Claude Code plugin: the bundled skill is the maintained one, and the server
// configuration runs this version of the npm package
const plugin = readJson("agents/claude/plugin/.claude-plugin/plugin.json");
assert(/^[a-z0-9]+(-[a-z0-9]+)*$/.test(plugin.name), `plugin.json: name ${plugin.name} is not kebab-case`);
assert.strictEqual(plugin.version, version, "plugin.json version");
assert(plugin.description && plugin.author && plugin.author.name, "plugin.json: description and author");
const pluginMcp = readJson("agents/claude/plugin/.mcp.json");
const pluginServer = pluginMcp.mcpServers.j2c;
assert(pluginServer, "agents/claude/plugin/.mcp.json: a j2c server");
assert.strictEqual(pluginServer.command, "npx");
assert(pluginServer.args.includes(`${npmPackage.name}@${version}`),
	`agents/claude/plugin/.mcp.json must run ${npmPackage.name}@${version}: ${pluginServer.args.join(" ")}`);
const skill = "skills/j2c-translate/SKILL.md";
const normalize = (text) => text.replace(/\r\n/g, "\n");
assert.strictEqual(
	normalize(fs.readFileSync(path.join(repositoryRoot, "agents", "claude", "plugin", skill), "utf8")),
	normalize(fs.readFileSync(path.join(repositoryRoot, "agents", "claude", skill), "utf8")),
	`agents/claude/plugin/${skill} must be a copy of agents/claude/${skill}`);
const marketplace = readJson(".claude-plugin/marketplace.json");
assert(marketplace.name && marketplace.owner && marketplace.owner.name, "marketplace.json: name and owner");
const entry = marketplace.plugins.find((candidate) => candidate.name === plugin.name);
assert(entry, `marketplace.json: no entry for plugin ${plugin.name}`);
assert.strictEqual(entry.source, "./agents/claude/plugin");
assert(!entry.version, "marketplace.json: the version comes from plugin.json");

// Icons: square PNGs, 512 to 2048 px for the plugin directory, at least 128
// px for the VS Code Marketplace
function pngSize(relative) {
	const bytes = fs.readFileSync(path.join(repositoryRoot, relative));
	assert.strictEqual(bytes.toString("latin1", 1, 4), "PNG", `${relative} is not a PNG`);
	return [bytes.readUInt32BE(16), bytes.readUInt32BE(20)];
}
const [pluginIconWidth, pluginIconHeight] = pngSize("agents/claude/plugin/.claude-plugin/icon.png");
assert(pluginIconWidth === pluginIconHeight && pluginIconWidth >= 512 && pluginIconWidth <= 2048,
	`agents/claude/plugin/.claude-plugin/icon.png: ${pluginIconWidth}x${pluginIconHeight}`);
const extensionIcon = `vscode/${readJson("vscode/package.json").icon}`;
const [extensionIconWidth, extensionIconHeight] = pngSize(extensionIcon);
assert(extensionIconWidth === extensionIconHeight && extensionIconWidth >= 128,
	`${extensionIcon}: ${extensionIconWidth}x${extensionIconHeight}`);

// Copilot cloud coding agent: the sample configuration and setup steps run
// this version
const copilot = readJson("agents/copilot/mcp-config.json").mcpServers.j2c;
assert(copilot, "agents/copilot/mcp-config.json: a j2c server");
assert(["local", "stdio"].includes(copilot.type), "agents/copilot/mcp-config.json: a local server");
assert(copilot.args.includes(`${npmPackage.name}@${version}`),
	`agents/copilot/mcp-config.json must run ${npmPackage.name}@${version}`);
assert(Array.isArray(copilot.tools) && copilot.tools.length > 0, "agents/copilot/mcp-config.json: tools");
assert(fs.readFileSync(path.join(repositoryRoot, "agents", "copilot", "copilot-setup-steps.yml"), "utf8")
	.includes(`${npmPackage.name}@${version}`), `agents/copilot/copilot-setup-steps.yml must use ${version}`);

// The launcher starts the MCP server from the built JAR, which has the tools
// the Copilot configuration enables
if (fs.existsSync(cliJar)) {
	const launched = childProcess.spawnSync(process.execPath, [
		path.join(repositoryRoot, "agents", "npm", npmPackage.bin["j2c-mcp"]), "--root", repositoryRoot
	], {
		encoding: "utf8",
		input: [
			{ jsonrpc: "2.0", id: 1, method: "initialize", params: { protocolVersion: "2025-06-18", capabilities: {} } },
			{ jsonrpc: "2.0", id: 2, method: "tools/list" }
		].map((message) => JSON.stringify(message)).join("\n") + "\n",
		env: { ...process.env, J2C_JAR: cliJar }
	});
	assert.strictEqual(launched.status, 0, launched.stderr);
	const responses = launched.stdout.trim().split(/\r?\n/).map((line) => JSON.parse(line));
	const response = responses[0];
	assert.strictEqual(response.result.serverInfo.name, "j2c");
	assert.strictEqual(response.result.serverInfo.version, version);
	const tools = responses[1].result.tools.map((tool) => tool.name);
	for (const tool of copilot.tools) {
		assert(tool === "*" || tools.includes(tool), `agents/copilot/mcp-config.json: no tool ${tool}`);
	}
	const missingJava = childProcess.spawnSync(process.execPath, [
		path.join(repositoryRoot, "agents", "npm", npmPackage.bin.j2c), "--version"
	], { encoding: "utf8", env: { ...process.env, J2C_JAR: cliJar, J2C_JAVA: path.join(repositoryRoot, "no-such-java") } });
	assert.strictEqual(missingJava.status, 1);
	assert(missingJava.stderr.includes("J2C needs Java 11 or newer"), missingJava.stderr);
} else {
	console.log(`Skipping the launcher check: ${cliJar} not built`);
}

console.log(`Distribution files agree on version ${version}`);
