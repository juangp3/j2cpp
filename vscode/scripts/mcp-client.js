// A long-running `j2c mcp` server for tests that need to talk to it while a
// call runs: progress notifications, cancellation, ping during a call, and
// checks repeated after edits.
const assert = require("assert");
const childProcess = require("child_process");

class McpClient {
	constructor(cliJar, root, options = {}) {
		this.messages = [];
		this.stderr = "";
		this.waiters = [];
		this.process = childProcess.spawn("java", ["-jar", cliJar, "mcp", "--root", root,
			...(options.args || [])], { env: { ...process.env, ...(options.env || {}) } });
		let buffer = "";
		this.process.stdout.setEncoding("utf8");
		this.process.stdout.on("data", (chunk) => {
			buffer += chunk;
			for (let end = buffer.indexOf("\n"); end >= 0; end = buffer.indexOf("\n")) {
				const line = buffer.slice(0, end).trim();
				buffer = buffer.slice(end + 1);
				if (line === "") {
					continue;
				}
				// Nothing but protocol messages may reach stdout
				const message = JSON.parse(line);
				assert.strictEqual(message.jsonrpc, "2.0", line);
				message.receivedAt = Date.now();
				this.messages.push(message);
				this.notify();
			}
		});
		this.process.stderr.setEncoding("utf8");
		this.process.stderr.on("data", (chunk) => {
			this.stderr += chunk;
			this.notify();
		});
		this.exited = new Promise((resolve) => this.process.on("exit", resolve));
	}

	notify() {
		for (const waiter of [...this.waiters]) {
			if (waiter.predicate()) {
				this.waiters.splice(this.waiters.indexOf(waiter), 1);
				clearTimeout(waiter.timer);
				waiter.resolve();
			}
		}
	}

	/**
	 * Resolves once predicate() is true: checked on every message, and every
	 * 50 ms for conditions outside the protocol, such as a stub's pid file
	 */
	until(predicate, what, timeout = 180000) {
		if (predicate()) {
			return Promise.resolve();
		}
		return new Promise((resolve, reject) => {
			const waiter = { predicate, resolve: () => {
				clearInterval(waiter.poll);
				resolve();
			} };
			waiter.timer = setTimeout(() => {
				clearInterval(waiter.poll);
				this.waiters.splice(this.waiters.indexOf(waiter), 1);
				reject(new Error(`Timed out waiting for ${what}; stderr:\n${this.stderr.slice(-2000)}`));
			}, timeout);
			waiter.poll = setInterval(() => this.notify(), 50);
			this.waiters.push(waiter);
		});
	}

	send(message) {
		this.process.stdin.write(JSON.stringify(message) + "\n");
	}

	/** The responses (not notifications) with this id */
	responses(id) {
		return this.messages.filter((message) => message.id === id && !message.method);
	}

	async response(id) {
		await this.until(() => this.responses(id).length > 0, `the response to ${id}`);
		return this.responses(id)[0];
	}

	async request(id, method, params) {
		this.send({ jsonrpc: "2.0", id, method, ...(params ? { params } : {}) });
		return this.response(id);
	}

	call(id, name, args, meta) {
		this.send({
			jsonrpc: "2.0", id, method: "tools/call",
			params: { name, arguments: args, ...(meta ? { _meta: meta } : {}) }
		});
	}

	progress(token) {
		return this.messages.filter((message) => message.method === "notifications/progress"
			&& message.params.progressToken === token);
	}

	/** Closes stdin, which stops the server once it answered what it got */
	async close() {
		this.process.stdin.end();
		const status = await this.exited;
		assert.strictEqual(status, 0, this.stderr);
	}
}

module.exports = { McpClient };
