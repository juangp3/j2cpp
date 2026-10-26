// Runs the agent benchmark: each trial is a headless Claude Code session
// (claude -p) under the developer's own login, in a copy of the task's
// workspace outside the repository, with only the J2C MCP server and the
// file tools; the copy is then graded with lib/tasks.js.
//
//   node run.js [--task <ids>] [--tier <tiers>] [--trials <k>] [--model <model>]
//       [--config <mcp,skill,bash>] [--jobs <n>] [--claude <program>] [--keep]
//       [--dry-run] [--update]
//
// --update rewrites the scoreboard in docs/AGENT_BENCHMARK.md, only from a
// run of every task that the usage limit did not stop.
//
// An API key in the environment is removed, and a trial whose session
// reports any API key source stops the run: trials use the login's usage
// limits and are never billed to an API account.
const childProcess = require("child_process");
const fs = require("fs");
const os = require("os");
const path = require("path");
const bench = require("./lib/tasks");

const configs = {
	// The J2C tools and the file tools
	mcp: { tools: [], plugin: false },
	// The same, plus the Claude Code plugin's j2c-translate skill
	skill: { tools: [], plugin: true },
	// The same as mcp, plus Bash
	bash: { tools: ["Bash"], plugin: false }
};
const fileTools = ["Read", "Edit", "Write", "Glob", "Grep"];
const pluginDirectory = path.join(bench.repositoryRoot, "agents", "claude", "plugin");
const cliJar = path.join(bench.repositoryRoot, "target", "j2c-cli.jar");

function option(name, fallback) {
	const index = process.argv.indexOf(name);
	return index >= 0 ? process.argv[index + 1] : fallback;
}

/** The environment without API credentials or the calling session's variables */
function sessionEnvironment() {
	const environment = { ...process.env };
	for (const name of Object.keys(environment)) {
		if (name === "ANTHROPIC_API_KEY" || name === "ANTHROPIC_AUTH_TOKEN"
			|| name === "CLAUDECODE" || name.startsWith("CLAUDE_CODE_")
			|| name === "CLAUDE_PID" || name === "CLAUDE_EFFORT" || name === "CLAUDE_JOB_DIR") {
			delete environment[name];
		}
	}
	return environment;
}

function claudeArguments(task, config, model, mcpConfig) {
	const tools = fileTools.concat(configs[config].tools);
	const args = ["-p", task.prompt,
		"--output-format", "stream-json", "--verbose",
		// File tools stay in the working folder, settings files are ignored
		"--restricted",
		"--strict-mcp-config", "--mcp-config", mcpConfig,
		"--tools", tools.join(","),
		"--allowedTools", tools.concat("mcp__j2c").join(" "),
		"--permission-mode", "dontAsk",
		"--no-session-persistence"];
	if (model) {
		args.push("--model", model);
	}
	if (configs[config].plugin) {
		args.push("--plugin-dir", pluginDirectory);
	} else {
		args.push("--disable-slash-commands");
	}
	return args;
}

/** Why the session's init message rules the trial out, or null */
function initProblem(init) {
	if (!init) {
		return "no init message";
	}
	if (init.apiKeySource && init.apiKeySource !== "none") {
		return `the session uses an API key (${init.apiKeySource})`;
	}
	const servers = init.mcp_servers || [];
	if (servers.length !== 1 || servers[0].name !== "j2c" || servers[0].status !== "connected") {
		return `MCP servers: ${JSON.stringify(servers)}`;
	}
	if (!(init.tools || []).some((tool) => tool.startsWith("mcp__j2c__"))) {
		return "no j2c tools";
	}
	return null;
}

function git(workspace, args) {
	return childProcess.spawnSync("git", ["-c", "user.name=j2c-bench", "-c", "user.email=j2c-bench@localhost",
		"-c", "core.autocrlf=false", ...args], { cwd: workspace, encoding: "utf8", maxBuffer: 64 * 1024 * 1024 });
}

/** The agent's changes as a patch, and the lines it added and removed */
function workspaceDiff(workspace) {
	git(workspace, ["add", "-A"]);
	const patch = git(workspace, ["diff", "--cached", "HEAD"]).stdout;
	let added = 0;
	let removed = 0;
	for (const line of git(workspace, ["diff", "--cached", "--numstat", "HEAD"]).stdout.split("\n")) {
		const [plus, minus] = line.split("\t");
		added += Number(plus) || 0;
		removed += Number(minus) || 0;
	}
	return { patch, added, removed };
}

/** Runs claude, writing each stream-json line to the transcript */
function runSession(command, args, workspace, transcript, timeoutSeconds) {
	return new Promise((resolve) => {
		const child = childProcess.spawn(command, args, {
			cwd: workspace,
			env: sessionEnvironment(),
			stdio: ["ignore", "pipe", "pipe"],
			// Its own process group, so a timeout also stops the MCP server
			detached: process.platform !== "win32"
		});
		const messages = [];
		let buffer = "";
		let stderr = "";
		let timedOut = false;
		const out = fs.createWriteStream(transcript);
		child.stdout.setEncoding("utf8");
		child.stdout.on("data", (chunk) => {
			out.write(chunk);
			buffer += chunk;
			for (let end = buffer.indexOf("\n"); end >= 0; end = buffer.indexOf("\n")) {
				const line = buffer.slice(0, end).trim();
				buffer = buffer.slice(end + 1);
				if (line) {
					try {
						messages.push(JSON.parse(line));
					} catch (e) {
						stderr += `unparsed output: ${line}\n`;
					}
				}
			}
		});
		child.stderr.setEncoding("utf8");
		child.stderr.on("data", (chunk) => {
			stderr += chunk;
		});
		const kill = (signal) => {
			try {
				if (process.platform === "win32") {
					child.kill(signal);
				} else {
					process.kill(-child.pid, signal);
				}
			} catch (e) {
				// Already gone
			}
		};
		const timer = setTimeout(() => {
			timedOut = true;
			kill("SIGTERM");
			setTimeout(() => kill("SIGKILL"), 5000).unref();
		}, timeoutSeconds * 1000);
		child.on("error", (error) => {
			stderr += `${error.message}\n`;
		});
		child.on("close", (code) => {
			clearTimeout(timer);
			out.end();
			resolve({ code, messages, stderr, timedOut });
		});
	});
}

function toolCalls(messages) {
	const calls = {};
	for (const message of messages) {
		if (message.type !== "assistant" || !message.message || !Array.isArray(message.message.content)) {
			continue;
		}
		for (const block of message.message.content) {
			if (block.type === "tool_use") {
				calls[block.name] = (calls[block.name] || 0) + 1;
			}
		}
	}
	return calls;
}

function tokens(modelUsage) {
	const total = { input: 0, output: 0, cacheRead: 0, cacheCreation: 0 };
	for (const usage of Object.values(modelUsage || {})) {
		total.input += usage.inputTokens || 0;
		total.output += usage.outputTokens || 0;
		total.cacheRead += usage.cacheReadInputTokens || 0;
		total.cacheCreation += usage.cacheCreationInputTokens || 0;
	}
	return total;
}

async function runTrial(task, config, trial, settings) {
	const name = `${task.id}-${config}-${trial}`;
	const trialDirectory = path.join(settings.results, name);
	fs.mkdirSync(trialDirectory, { recursive: true });
	// Outside the repository, so no CLAUDE.md or AGENTS.md in a parent folder applies
	const scratch = fs.mkdtempSync(path.join(os.tmpdir(), "j2c-bench-"));
	const workspace = bench.prepareWorkspace(task, path.join(scratch, "work"));
	const mcpConfig = path.join(scratch, "mcp.json");
	fs.writeFileSync(mcpConfig, JSON.stringify({ mcpServers: { j2c: {
		command: "java", args: ["-jar", cliJar, "mcp", "--root", workspace] } } }, null, "\t"));
	git(workspace, ["init", "-q"]);
	git(workspace, ["add", "-A"]);
	git(workspace, ["commit", "-q", "-m", "start"]);
	const args = claudeArguments(task, config, settings.model, mcpConfig);
	const record = { task: task.id, tier: task.tier, config, trial, model: settings.model || null };
	if (settings.dryRun) {
		console.log(`${name}: (cd ${workspace} && ${settings.claude} ${args.map((arg) => JSON.stringify(arg)).join(" ")})`);
		fs.rmSync(scratch, { recursive: true, force: true });
		return null;
	}
	const started = Date.now();
	const session = await runSession(settings.claude, args, workspace,
		path.join(trialDirectory, "transcript.jsonl"), task.timeoutSeconds);
	const init = session.messages.find((message) => message.type === "system" && message.subtype === "init");
	const result = session.messages.find((message) => message.type === "result");
	record.claudeCodeVersion = init ? init.claude_code_version : null;
	record.sessionModel = init ? init.model : null;
	record.infrastructureError = initProblem(init)
		|| (!result && !session.timedOut ? `claude exited with ${session.code}: ${session.stderr.trim().slice(-2000)}` : null)
		// A refused API call, such as a reached usage limit (429), is not the agent's failure
		|| (result && result.api_error_status ? `API error ${result.api_error_status}: ${(result.result || "").slice(0, 300)}` : null);
	record.usageLimit = Boolean(result && result.api_error_status === 429);
	record.apiKeySource = init ? init.apiKeySource : null;
	record.timedOut = session.timedOut;
	record.wallMs = Date.now() - started;
	if (result) {
		record.subtype = result.subtype;
		record.numTurns = result.num_turns;
		record.durationMs = result.duration_ms;
		// What the session would cost on the API; nothing is billed
		record.apiEquivalentUsd = result.total_cost_usd;
		record.tokens = tokens(result.modelUsage);
		record.modelUsage = result.modelUsage;
		record.permissionDenials = (result.permission_denials || []).length;
		record.answer = result.result || null;
	}
	record.toolCalls = toolCalls(session.messages);
	const diff = workspaceDiff(workspace);
	fs.writeFileSync(path.join(trialDirectory, "changes.patch"), diff.patch);
	record.linesAdded = diff.added;
	record.linesRemoved = diff.removed;
	if (!record.infrastructureError) {
		const grade = bench.grade(task, workspace, { answer: record.answer || "" });
		record.passed = grade.passed && !session.timedOut;
		record.checks = grade.checks;
	}
	fs.writeFileSync(path.join(trialDirectory, "trial.json"), `${JSON.stringify(record, null, "\t")}\n`);
	if (settings.keep) {
		record.workspace = workspace;
	} else {
		fs.rmSync(scratch, { recursive: true, force: true });
	}
	const outcome = record.infrastructureError ? `infrastructure error: ${record.infrastructureError}`
		: record.passed ? "passed"
		: `failed: ${session.timedOut ? "timed out; " : ""}${record.checks.filter((check) => !check.passed)
			.map((check) => `${check.name} (${check.detail.split("\n")[0].slice(0, 200)})`).join(", ")}`;
	console.log(`${name}: ${outcome} [${record.numTurns || 0} turns, ${record.tokens ? record.tokens.output : 0} output tokens, `
		+ `${Math.round(record.wallMs / 1000)} s]`);
	return record;
}

function mean(values) {
	return values.length ? values.reduce((sum, value) => sum + value, 0) / values.length : null;
}

/** Per task and configuration: pass rate, means, and tokens per success */
function summarize(records) {
	const groups = new Map();
	for (const record of records) {
		const key = `${record.task}\t${record.config}`;
		if (!groups.has(key)) {
			groups.set(key, []);
		}
		groups.get(key).push(record);
	}
	return [...groups.values()].map((group) => {
		const graded = group.filter((record) => !record.infrastructureError);
		const passed = graded.filter((record) => record.passed);
		const totalTokens = (record) => record.tokens
			? record.tokens.input + record.tokens.output + record.tokens.cacheRead + record.tokens.cacheCreation : 0;
		const tokenSum = graded.reduce((sum, record) => sum + totalTokens(record), 0);
		return {
			task: group[0].task,
			tier: group[0].tier,
			config: group[0].config,
			trials: graded.length,
			infrastructureErrors: group.length - graded.length,
			passed: passed.length,
			passRate: graded.length ? passed.length / graded.length : null,
			allPassed: graded.length > 0 && passed.length === graded.length,
			meanTurns: mean(graded.map((record) => record.numTurns || 0)),
			meanOutputTokens: mean(graded.map((record) => record.tokens ? record.tokens.output : 0)),
			meanTotalTokens: mean(graded.map(totalTokens)),
			tokensPerSuccess: passed.length ? tokenSum / passed.length : null,
			meanApiEquivalentUsd: mean(graded.map((record) => record.apiEquivalentUsd || 0))
		};
	});
}

function printSummary(summary) {
	const rows = [["task", "config", "passed", "turns", "output tokens", "tokens/success", "API-equivalent $"]]
		.concat(summary.map((row) => [row.task, row.config,
			`${row.passed}/${row.trials}${row.infrastructureErrors ? ` (+${row.infrastructureErrors} infra)` : ""}`,
			row.meanTurns === null ? "-" : row.meanTurns.toFixed(1),
			row.meanOutputTokens === null ? "-" : Math.round(row.meanOutputTokens).toString(),
			row.tokensPerSuccess === null ? "-" : Math.round(row.tokensPerSuccess).toString(),
			row.meanApiEquivalentUsd === null ? "-" : row.meanApiEquivalentUsd.toFixed(2)]));
	const widths = rows[0].map((_, column) => Math.max(...rows.map((row) => row[column].length)));
	for (const row of rows) {
		console.log(row.map((cell, column) => cell.padEnd(widths[column])).join("  "));
	}
}

const scoreboardFile = path.join(bench.repositoryRoot, "docs", "AGENT_BENCHMARK.md");

function writeScoreboard(summary, records, settings, trials) {
	const begin = "<!-- agent-bench-scoreboard:begin -->";
	const end = "<!-- agent-bench-scoreboard:end -->";
	const text = fs.readFileSync(scoreboardFile, "utf8");
	const start = text.indexOf(begin);
	const finish = text.indexOf(end);
	if (start < 0 || finish < start) {
		throw new Error(`${scoreboardFile} has no scoreboard markers`);
	}
	const versions = [...new Set(records.map((record) => record.claudeCodeVersion).filter(Boolean))];
	const graded = summary.reduce((sum, row) => sum + row.trials, 0);
	const passed = summary.reduce((sum, row) => sum + row.passed, 0);
	const lines = [
		`${new Date().toISOString().slice(0, 10)}: \`${settings.model}\`, Claude Code ${versions.join(", ") || "unknown"}, `
			+ `${trials} trial(s) per task; ${passed} of ${graded} graded trials passed.`,
		"",
		"| Task | Tier | Config | Passed | Turns | Output tokens | Tokens per success | API-equivalent $ |",
		"| --- | --- | --- | --- | --- | --- | --- | --- |"
	].concat(summary.map((row) => `| ${row.task} | ${row.tier} | ${row.config} | ${row.passed}/${row.trials}`
		+ `${row.infrastructureErrors ? ` (+${row.infrastructureErrors} infra)` : ""}`
		+ ` | ${row.meanTurns === null ? "-" : row.meanTurns.toFixed(1)}`
		+ ` | ${row.meanOutputTokens === null ? "-" : Math.round(row.meanOutputTokens)}`
		+ ` | ${row.tokensPerSuccess === null ? "-" : Math.round(row.tokensPerSuccess)}`
		+ ` | ${row.meanApiEquivalentUsd === null ? "-" : row.meanApiEquivalentUsd.toFixed(2)} |`));
	fs.writeFileSync(scoreboardFile, `${text.slice(0, start + begin.length)}\n${lines.join("\n")}\n${text.slice(finish)}`);
}

async function main() {
	const configNames = option("--config", "mcp").split(",");
	for (const config of configNames) {
		if (!configs[config]) {
			throw new Error(`Unknown configuration: ${config} (${Object.keys(configs).join(", ")})`);
		}
	}
	const trials = Number(option("--trials", "1"));
	const jobs = Number(option("--jobs", "1"));
	if (!(trials >= 1) || !(jobs >= 1)) {
		throw new Error("--trials and --jobs must be at least 1");
	}
	if (!fs.existsSync(cliJar)) {
		throw new Error(`${cliJar} is missing: run mvn -q package -DskipTests`);
	}
	const tasks = bench.loadTasks(option("--task"), option("--tier"));
	const update = process.argv.includes("--update");
	if (update && (option("--task") || option("--tier"))) {
		throw new Error("--update needs every task: leave out --task and --tier");
	}
	const stamp = new Date().toISOString().replace(/[:.]/g, "-");
	const settings = {
		model: option("--model", "claude-opus-5-5"),
		claude: option("--claude", "claude"),
		keep: process.argv.includes("--keep"),
		dryRun: process.argv.includes("--dry-run"),
		results: path.join(bench.repositoryRoot, "target", "agent-bench", stamp)
	};
	const queue = [];
	for (const task of tasks) {
		for (const config of configNames) {
			for (let trial = 1; trial <= trials; ++trial) {
				queue.push({ task, config, trial });
			}
		}
	}
	console.log(`${queue.length} trial(s): ${tasks.length} task(s) x ${configNames.join(", ")} x ${trials}`
		+ `${settings.dryRun ? " (dry run)" : `, results in ${settings.results}`}`);
	const records = [];
	let stopped = null;
	const worker = async () => {
		while (queue.length && !stopped) {
			const { task, config, trial } = queue.shift();
			const record = await runTrial(task, config, trial, settings);
			if (record) {
				records.push(record);
				// An API key would bill an account; a usage limit fails every later trial too
				if ((record.apiKeySource && record.apiKeySource !== "none") || record.usageLimit) {
					stopped = record.infrastructureError;
				}
			}
		}
	};
	await Promise.all(Array.from({ length: Math.min(jobs, queue.length) }, worker));
	if (settings.dryRun) {
		return;
	}
	const summary = summarize(records);
	fs.writeFileSync(path.join(settings.results, "results.json"), `${JSON.stringify({
		date: new Date().toISOString(),
		model: settings.model,
		configs: configNames,
		trials,
		summary,
		records
	}, null, "\t")}\n`);
	console.log("");
	printSummary(summary);
	if (stopped) {
		console.log(`Stopped: ${stopped}`);
		process.exit(2);
	}
	if (update) {
		writeScoreboard(summary, records, settings, trials);
		console.log(`Updated ${path.relative(bench.repositoryRoot, scoreboardFile)}`);
	}
}

main().catch((error) => {
	console.error(error.message);
	process.exit(2);
});
