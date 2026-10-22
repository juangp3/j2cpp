package se.arnetheduck.j2c.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.runtime.OperationCanceledException;

import se.arnetheduck.j2c.transform.TranslationException;

/**
 * J2C as a Model Context Protocol server: JSON-RPC 2.0 messages, one per line,
 * on stdin and stdout. Every path is resolved against, and must stay inside,
 * the workspace root.
 *
 * Requests run one at a time on a worker thread, in the order they arrive,
 * while this thread keeps reading: ping, initialize and notifications are
 * handled at once, and notifications/cancelled stops a call at its next file or
 * phase, after which the call gets no response. A call with a progressToken
 * in its _meta gets notifications/progress while it runs. Every message is
 * written whole, one at a time.
 */
final class McpServer {
	/** Newest first; the client's version is used when it is one of these */
	private static final List<String> PROTOCOL_VERSIONS = Arrays.asList(
			"2025-06-18", "2025-03-26", "2024-11-05");
	/** Before 2025-03-26, progress notifications had no message */
	private static final String FIRST_PROGRESS_MESSAGE_VERSION = "2025-03-26";
	/**
	 * Requests answered as soon as they arrive; the others run in order on
	 * the worker, so a resources/read after a translation sees its files
	 */
	private static final List<String> IMMEDIATE = Arrays.asList("ping",
			"initialize");
	/** Progress notifications of one call are at least this far apart */
	static final long PROGRESS_INTERVAL_MILLIS = 100;

	private static final String INSTRUCTIONS = "J2C translates Java 17 source "
			+ "into C++20. Typical workflow: call j2c_check on the Java files or "
			+ "source folders, fix each reported problem in the Java source using "
			+ "its hint (j2c_explain gives details), check again until no problems "
			+ "remain (j2c_fix previews or applies the mechanical fixes some "
			+ "problems carry), then call j2c_translate with an output directory, and "
			+ "j2c_build to compile it and get C++ errors at their Java lines; j2c_test "
			+ "translates, builds and runs JUnit tests with GoogleTest and returns "
			+ "each result at its Java method; j2c_run translates, builds and runs "
			+ "a program and compares it with the JVM. Paths are "
			+ "relative to the workspace root. A Maven or Gradle project folder "
			+ "(such as \".\") can be passed directly: its source roots and "
			+ "dependency class path are detected. Translated code still needs a C++ "
			+ "implementation of the JDK classes it uses (see j2c_capabilities). "
			+ "j2c_report assesses how much translates and what blocks the rest; "
			+ "the resources hold the problem catalog (j2c://problems), the "
			+ "runtime's contract and API (j2c://runtime), and translated files "
			+ "(j2c://output/{path}).";

	/**
	 * A tool: its tools/list definition and what a call does. Calls get the
	 * call's progress, to report phases and counted items and to stop with
	 * OperationCanceledException once cancelled. Throwing
	 * IllegalArgumentException reports a usage error, TranslationException
	 * the problems; any other exception is a tool error.
	 */
	abstract static class Tool {
		final String name;
		final Map<String, Object> definition;

		Tool(Map<String, Object> definition) {
			this.name = (String) definition.get("name");
			this.definition = definition;
		}

		/** The structured result, shown as the tool result */
		abstract Map<String, Object> call(Map<?, ?> arguments, Progress progress)
				throws Exception;

		/**
		 * The tools/call result: the structured result as text and as
		 * structuredContent; overridden to add content
		 */
		Map<String, Object> result(Map<?, ?> arguments, Progress progress)
				throws Exception {
			return toolResult(call(arguments, progress), false);
		}
	}

	/** A tools/call in the queue or running */
	private final class Call extends Progress {
		final Object id;
		/** null when the client asked for no progress */
		final Object progressToken;
		/** Set once the response was written, or skipped after a cancel */
		boolean finished;
		private long lastReport;

		Call(Object id, Object progressToken) {
			this.id = id;
			this.progressToken = progressToken;
		}

		@Override
		protected void report(long progress, long total, String message) {
			if (progressToken == null) {
				return;
			}
			long now = System.nanoTime();
			if (lastReport != 0
					&& now - lastReport < TimeUnit.MILLISECONDS
							.toNanos(PROGRESS_INTERVAL_MILLIS)) {
				return;
			}
			lastReport = now;
			Map<String, Object> params = new LinkedHashMap<String, Object>();
			params.put("progressToken", progressToken);
			params.put("progress", progress);
			if (total >= 0) {
				params.put("total", total);
			}
			if (message != null && supportsProgressMessage()) {
				params.put("message", message);
			}
			Map<String, Object> notification = new LinkedHashMap<String, Object>();
			notification.put("jsonrpc", "2.0");
			notification.put("method", "notifications/progress");
			notification.put("params", params);
			synchronized (output) {
				// Progress stops with the response, and with a cancel
				if (!finished && !cancelled()) {
					send(notification);
				}
			}
		}
	}

	private final File root;
	private final J2cService service;
	/** Project auto-setup defaults, from j2c mcp --project and --no-build-tool */
	private final String project;
	private final boolean buildTool;
	private final Map<String, Tool> tools = new LinkedHashMap<String, Tool>();
	/** Tool calls not yet answered, by request id */
	private final Map<Object, Call> calls = new ConcurrentHashMap<Object, Call>();
	/** Guards out; every message is written while holding it */
	private final Object output = new Object();
	private PrintStream out;
	private volatile String protocolVersion = PROTOCOL_VERSIONS.get(0);

	McpServer(File root) {
		this(root, J2cService.PROJECT_AUTO, true, CheckCache.DEFAULT_MAX_FILES);
	}

	/**
	 * @param cachedFiles how many files' check results to keep between calls
	 *            (j2c mcp --check-cache); 0 checks everything every time
	 */
	McpServer(File root, String project, boolean buildTool, int cachedFiles) {
		this.root = root;
		this.service = new J2cService(root);
		this.project = project;
		this.buildTool = buildTool;
		this.content = new McpContent(root, service);
		if (cachedFiles > 0) {
			service.setCache(new CheckCache(cachedFiles));
		}
		registerTools();
	}

	/** Prompts and resources */
	private final McpContent content;

	/** Whether titles may be sent: 2025-06-18 added them */
	private boolean titles() {
		return "2025-06-18".compareTo(protocolVersion) <= 0;
	}

	void serve(InputStream in, PrintStream rawOut) throws IOException {
		out = new PrintStream(rawOut, true, StandardCharsets.UTF_8);
		// Only protocol messages may appear on stdout; the translator's
		// progress output goes to stderr
		System.setOut(System.err);
		ExecutorService worker = Executors
				.newSingleThreadExecutor(new ThreadFactory() {
					@Override
					public Thread newThread(Runnable runnable) {
						Thread thread = new Thread(runnable, "j2c-mcp-tools");
						thread.setDaemon(true);
						return thread;
					}
				});
		BufferedReader reader = new BufferedReader(new InputStreamReader(in,
				StandardCharsets.UTF_8));
		try {
			for (String line = reader.readLine(); line != null; line = reader
					.readLine()) {
				if (!line.trim().isEmpty()) {
					receive(line, worker);
				}
			}
		} finally {
			// The client closed stdin: answer the calls already received
			worker.shutdown();
			try {
				worker.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void send(Map<String, Object> message) {
		synchronized (output) {
			out.println(Json.write(message));
		}
	}

	/** Answers one message now, or queues a tool call */
	private void receive(String line, ExecutorService worker) {
		final Map<?, ?> request = request(line);
		Object method = request == null ? null : request.get("method");
		if (request == null || !request.containsKey("id")
				|| !"2.0".equals(request.get("jsonrpc"))
				|| IMMEDIATE.contains(method)) {
			// Notifications (such as cancellation), errors, ping and
			// initialize are answered at once
			Map<String, Object> response = handle(line);
			if (response != null) {
				send(response);
			}
			return;
		}
		final Map<?, ?> params = params(request);
		final boolean toolCall = "tools/call".equals(method);
		Object meta = params.get("_meta");
		Object token = meta instanceof Map ? ((Map<?, ?>) meta)
				.get("progressToken") : null;
		if (!(token instanceof String) && !(token instanceof Number)) {
			token = null;
		}
		final Call call = new Call(request.get("id"), token);
		if (call.id != null) {
			calls.put(call.id, call);
		}
		worker.execute(new Runnable() {
			@Override
			public void run() {
				Map<String, Object> response = null;
				try {
					if (!call.cancelled()) {
						response = toolCall ? callTool(call.id, params, call)
								: handle(line);
					}
				} catch (OperationCanceledException e) {
					response = null;
				} catch (Throwable e) {
					// Such as StackOverflowError: still exactly one response
					e.printStackTrace(System.err);
					response = error(call.id, -32603, "Internal error: " + e);
				} finally {
					synchronized (output) {
						// A cancelled request gets no response
						if (response != null && !call.cancelled()) {
							send(response);
						}
						call.finished = true;
					}
					if (call.id != null) {
						calls.remove(call.id, call);
					}
				}
			}
		});
	}

	/** The message as a request object, or null when it is not one */
	private static Map<?, ?> request(String line) {
		try {
			Object message = Json.parse(line);
			return message instanceof Map ? (Map<?, ?>) message : null;
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static Map<?, ?> params(Map<?, ?> request) {
		return request.get("params") instanceof Map ? (Map<?, ?>) request
				.get("params") : Collections.emptyMap();
	}

	/**
	 * The response to one message other than a queued tool call, or null for
	 * a notification. Tool calls are answered here too, on this thread, when
	 * called directly.
	 */
	Map<String, Object> handle(String line) {
		Object message;
		try {
			message = Json.parse(line);
		} catch (IllegalArgumentException e) {
			return error(null, -32700, "Parse error: " + e.getMessage());
		}
		if (!(message instanceof Map)) {
			return error(null, -32600, "Invalid request");
		}
		Map<?, ?> request = (Map<?, ?>) message;
		Object id = request.get("id");
		Object method = request.get("method");
		if (!"2.0".equals(request.get("jsonrpc")) || !(method instanceof String)) {
			return error(id, -32600, "Invalid request");
		}
		boolean notification = !request.containsKey("id");
		Map<?, ?> params = params(request);

		switch ((String) method) {
		case "initialize":
			return result(id, initialize(params));
		case "ping":
			return result(id, new LinkedHashMap<String, Object>());
		case "tools/list":
			Map<String, Object> list = new LinkedHashMap<String, Object>();
			List<Object> definitions = new ArrayList<Object>();
			for (Tool tool : tools.values()) {
				definitions.add(tool.definition);
			}
			list.put("tools", definitions);
			return result(id, list);
		case "tools/call":
			return callTool(id, params, new Progress());
		case "notifications/cancelled":
			cancel(params);
			return null;
		case "prompts/list":
			return result(id, McpContent.promptList(titles()));
		case "prompts/get":
			try {
				return result(id, McpContent.prompt(params));
			} catch (IllegalArgumentException e) {
				return error(id, -32602, e.getMessage());
			}
		case "resources/list":
			return result(id, content.resourceList(titles()));
		case "resources/templates/list":
			return result(id, McpContent.templateList(titles()));
		case "resources/read":
			try {
				return result(id, content.read(params));
			} catch (McpContent.NotFound e) {
				return error(id, -32002, e.getMessage());
			} catch (IllegalArgumentException e) {
				return error(id, -32602, e.getMessage());
			} catch (Exception e) {
				e.printStackTrace(System.err);
				return error(id, -32603, "Internal error: " + e.getMessage());
			}
		default:
			return notification ? null : error(id, -32601, "Method not found: "
					+ method);
		}
	}

	/** Stops the call named by notifications/cancelled, if still running */
	private void cancel(Map<?, ?> params) {
		Object requestId = params.get("requestId");
		Call call = requestId == null ? null : calls.get(requestId);
		if (call != null) {
			Object reason = params.get("reason");
			System.err.println("J2C: cancelling request " + Json.write(requestId)
					+ (reason instanceof String ? ": " + reason : ""));
			call.cancel();
		}
	}

	private boolean supportsProgressMessage() {
		return protocolVersion.compareTo(FIRST_PROGRESS_MESSAGE_VERSION) >= 0;
	}

	private Map<String, Object> initialize(Map<?, ?> params) {
		Object requested = params.get("protocolVersion");
		String version = PROTOCOL_VERSIONS.contains(requested) ? (String) requested
				: PROTOCOL_VERSIONS.get(0);
		protocolVersion = version;

		Map<String, Object> capabilities = new LinkedHashMap<String, Object>();
		Map<String, Object> toolCapabilities = new LinkedHashMap<String, Object>();
		toolCapabilities.put("listChanged", false);
		capabilities.put("tools", toolCapabilities);
		// Prompts and resources (with templates) are in every supported
		// version; the list never changes on its own, and nothing is
		// subscribable
		Map<String, Object> prompts = new LinkedHashMap<String, Object>();
		prompts.put("listChanged", false);
		capabilities.put("prompts", prompts);
		Map<String, Object> resources = new LinkedHashMap<String, Object>();
		resources.put("subscribe", false);
		resources.put("listChanged", false);
		capabilities.put("resources", resources);

		Map<String, Object> serverInfo = new LinkedHashMap<String, Object>();
		serverInfo.put("name", "j2c");
		if (titles()) {
			serverInfo.put("title", "J2C Java to C++");
		}
		serverInfo.put("version", J2cService.version());

		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("protocolVersion", version);
		result.put("capabilities", capabilities);
		result.put("serverInfo", serverInfo);
		result.put("instructions", INSTRUCTIONS);
		return result;
	}

	/**
	 * Runs a tool. OperationCanceledException is passed on when the call was
	 * cancelled.
	 */
	private Map<String, Object> callTool(Object id, Map<?, ?> params,
			Progress progress) {
		Object name = params.get("name");
		Map<?, ?> arguments = params.get("arguments") instanceof Map ? (Map<?, ?>) params
				.get("arguments") : Collections.emptyMap();
		String command = name instanceof String ? ((String) name).replaceFirst(
				"^j2c_", "") : "";
		Tool tool = tools.get(String.valueOf(name));
		if (tool == null) {
			return error(id, -32602, "Unknown tool: " + name);
		}

		try {
			return result(id, tool.result(arguments, progress));
		} catch (OperationCanceledException e) {
			if (progress.cancelled()) {
				throw e;
			}
			return result(id, toolResult(J2cService.errorJson(command, "error",
					"Stopped: " + e.getMessage()), true));
		} catch (IllegalArgumentException e) {
			return result(id, toolResult(J2cService.errorJson(command,
					"usage-error", e.getMessage()), true));
		} catch (TranslationException e) {
			return result(id, toolResult(J2cService.failedJson(command,
					e.problems(), service.projects()), true));
		} catch (Builder.BuildException e) {
			return result(id, toolResult(J2cService.errorJson(command, "error",
					e.getMessage()), true));
		} catch (Exception e) {
			e.printStackTrace(System.err);
			return result(id, toolResult(J2cService.errorJson(command, "error",
					String.valueOf(e.getMessage())), true));
		}
	}

	private void register(Tool tool) {
		tools.put(tool.name, tool);
	}

	/** The tools, in tools/list order */
	private void registerTools() {
		register(new Tool(tool("j2c_check", "Check Java for translation problems",
				"Reports every Java compile error and construct J2C cannot "
						+ "translate in the given Java files or source folders, "
						+ "with file, line, column, a stable problem id and a hint "
						+ "for fixing it. Writes nothing. Run before j2c_translate. "
						+ "Checking again is fast: only changed files and the files "
						+ "that use them are compiled again.",
				inputSchema(false), true, false, true)) {
			@Override
			Map<String, Object> call(Map<?, ?> arguments, Progress progress)
					throws Exception {
				return J2cService.checkJson(service.check(request(arguments,
						false), progress));
			}
		});
		register(new Tool(tool("j2c_translate", "Translate Java to C++",
				"Translates Java files or source folders into C++20 in the output "
						+ "directory and lists the generated files, including a "
						+ "Makefile. The output directory must exist and be empty or "
						+ "previous J2C output; its current contents are moved to a "
						+ "backup in the temporary folder. Fails with the same "
						+ "problems j2c_check reports.",
				inputSchema(true), false, true, true)) {
			@Override
			Map<String, Object> call(Map<?, ?> arguments, Progress progress)
					throws Exception {
				J2cService.TranslateResult result = service.translate(request(
						arguments, true), progress);
				content.translated(result);
				return J2cService.translateJson(result);
			}
		});
		Map<String, Object> fixSchema = inputSchema(false);
		@SuppressWarnings("unchecked")
		Map<String, Object> fixProperties = (Map<String, Object>) fixSchema
				.get("properties");
		fixProperties.put("apply", property("boolean",
				"Write the fixes into the Java files (default false: only preview "
						+ "them as a unified diff)"));
		register(new Tool(tool("j2c_fix", "Fix Java translation problems",
				"Previews, as a unified diff, the mechanical fixes of the problems "
						+ "j2c_check reports, such as this used in a lambda inside an "
						+ "anonymous class; with apply: true writes them into the Java "
						+ "files, checks again, and repeats until no fixable problem is "
						+ "left. Problems without a fix are returned with their hints.",
				// Like j2c_check, it may run the project's build tool
				fixSchema, false, true, true)) {
			@Override
			Map<String, Object> call(Map<?, ?> arguments, Progress progress)
					throws Exception {
				Fixer fixer = new Fixer(service, root);
				return fixer.json(fixer.fix(request(arguments, false),
						Boolean.TRUE.equals(arguments.get("apply")), progress));
			}
		});
		register(new Tool(tool("j2c_report", "Migration readiness report",
				"Reports how much of the Java code translates (files, types and "
						+ "methods with no problem from j2c_check), the blockers by "
						+ "problem id and package with top locations and quick-fix "
						+ "counts, test-feature blockers with tests, the JDK types and "
						+ "members the code uses compared with what the J2C runtime "
						+ "provides, and next steps. Writes nothing. Returns JSON and "
						+ "the same report as Markdown.",
				// Like j2c_check, it may run the project's build tool
				inputSchema(false), true, false, true)) {
			@Override
			Map<String, Object> call(Map<?, ?> arguments, Progress progress)
					throws Exception {
				Report report = new Report(service, root);
				return report.json(report.run(request(arguments, false), progress),
						strings(arguments, "paths", true));
			}

			@Override
			Map<String, Object> result(Map<?, ?> arguments, Progress progress)
					throws Exception {
				Report report = new Report(service, root);
				List<String> inputs = strings(arguments, "paths", true);
				Report.Result result = report.run(request(arguments, false),
						progress);
				Map<String, Object> toolResult = toolResult(report.json(result,
						inputs), false);
				// The same report as Markdown, for people reading along
				Map<String, Object> markdown = new LinkedHashMap<String, Object>();
				markdown.put("type", "text");
				markdown.put("text", report.markdown(result, inputs));
				List<Object> content = new ArrayList<Object>(
						(List<?>) toolResult.get("content"));
				content.add(markdown);
				toolResult.put("content", content);
				return toolResult;
			}
		});
		register(new Tool(tool("j2c_build", "Build translated C++",
				"Runs make in a J2C output directory (in WSL on Windows when make "
						+ "is missing) and returns the compiler's errors and warnings "
						+ "mapped to Java files and lines, with the C++ location of "
						+ "each, the exit code and the end of the log. With paths, "
						+ "translates them into the output directory first, with "
						+ "#line directives, like j2c_translate; otherwise the output "
						+ "directory must be earlier J2C output (translate it with "
						+ "lineDirectives: true for Java lines). Writes only inside "
						+ "the output directory.",
				buildSchema(), false, false, false)) {
			@Override
			Map<String, Object> call(Map<?, ?> arguments, Progress progress)
					throws Exception {
				J2cService.Request request = buildRequest(arguments);
				Builder.Options options = new Builder.Options();
				String target = optionalString(arguments, "target");
				if (target != null && !BUILD_TARGETS.contains(target)) {
					throw new IllegalArgumentException("target must be one of "
							+ BUILD_TARGETS);
				}
				options.target = target;
				Object jobs = arguments.get("jobs");
				if (jobs != null) {
					if (!(jobs instanceof Long)) {
						throw new IllegalArgumentException("jobs must be an integer");
					}
					options.jobs = Main.jobs(jobs.toString());
				}
				Builder.Result result = service.build(request, options,
						System.err, progress);
				if (result.translation != null) {
					content.translated(result.translation);
				}
				return Builder.json(result);
			}
		});
		register(new Tool(tool("j2c_test", "Run translated JUnit tests",
				"Translates Java files, source folders or a project's test roots "
						+ "with their JUnit tests into the output directory (like "
						+ "j2c_translate with tests and lineDirectives), builds the "
						+ "GoogleTest binary with make (in WSL on Windows when make is "
						+ "missing), runs it, and returns each test's result at its "
						+ "Java class, method, file and line: status (passed, failed, "
						+ "skipped, disabled, or notRun when the test process crashed "
						+ "or timed out first), the Java failure message, time and the "
						+ "C++ test name, with counts and the exit code. A failed build "
						+ "returns the compiler's errors at their Java lines, like "
						+ "j2c_build. Writes only inside the output directory.",
				testSchema(), false, false, false)) {
			@Override
			Map<String, Object> call(Map<?, ?> arguments, Progress progress)
					throws Exception {
				J2cService.Request request = request(arguments, true);
				// Closed-world like j2c_build
				request.buildTool = false;
				TestRunner.Options options = new TestRunner.Options();
				options.filter.addAll(strings(arguments, "filter", false));
				Object timeout = arguments.get("timeout");
				if (timeout != null) {
					if (!(timeout instanceof Long)) {
						throw new IllegalArgumentException(
								"timeout must be an integer");
					}
					options.timeout = Main.timeout(timeout.toString());
				}
				Object jobs = arguments.get("jobs");
				if (jobs != null) {
					if (!(jobs instanceof Long)) {
						throw new IllegalArgumentException("jobs must be an integer");
					}
					options.build.jobs = Main.jobs(jobs.toString());
				}
				TestRunner.Result result = TestRunner.run(service, request,
						options, System.err, progress);
				content.translated(result.translation);
				return TestRunner.json(result);
			}
		});
		register(new Tool(tool("j2c_run", "Run a translated program",
				"Translates a Java program into the output directory against the "
						+ "J2C runtime (bundled by default) with #line directives, "
						+ "builds its main class with make (in WSL on Windows when "
						+ "make is missing), runs it with args and stdin, runs the "
						+ "same main class on the JVM (compiled with javac), and "
						+ "compares them: status (ok, differs, failed, crashed, "
						+ "timeout or build-failed), each run's exit code, signal, "
						+ "duration, stdout and stderr (up to outputLimit "
						+ "characters each), and the first differing line of stdout "
						+ "and of stderr (stack frames ignored). A failed build "
						+ "returns the compiler's errors at their Java lines, like "
						+ "j2c_build. Writes only inside the output directory.",
				runSchema(), false, false, false)) {
			@Override
			Map<String, Object> call(Map<?, ?> arguments, Progress progress)
					throws Exception {
				J2cService.Request request = request(arguments, true);
				// Closed-world like j2c_build
				request.buildTool = false;
				ProgramRunner.Options options = new ProgramRunner.Options();
				options.main = optionalString(arguments, "main");
				options.args.addAll(strings(arguments, "args", false));
				String stdin = optionalString(arguments, "stdin");
				if (stdin != null) {
					options.stdin = stdin.getBytes(StandardCharsets.UTF_8);
				}
				Object compare = arguments.get("compare");
				if (compare != null && !(compare instanceof Boolean)) {
					throw new IllegalArgumentException("compare must be a boolean");
				}
				options.compare = !Boolean.FALSE.equals(compare);
				options.timeout = Main.timeout(integer(arguments, "timeout",
						options.timeout));
				options.outputLimit = Main.outputLimit(integer(arguments,
						"outputLimit", options.outputLimit));
				Object jobs = arguments.get("jobs");
				if (jobs != null) {
					options.build.jobs = Main.jobs(integer(arguments, "jobs", 0));
				}
				ProgramRunner.Result result = ProgramRunner.run(service, request,
						options, System.err, progress);
				content.translated(result.translation);
				return ProgramRunner.json(result);
			}
		});
		Map<String, Object> explainProperties = new LinkedHashMap<String, Object>();
		explainProperties.put("id", property("string",
				"A problem id such as J2C-LAMBDA-ANONYMOUS-THIS; omit to list all"));
		register(new Tool(tool("j2c_explain", "Explain a J2C problem id",
				"Explains what a problem id from j2c_check means and how to fix it.",
				schema(explainProperties, Collections.<String> emptyList()), true,
				false, false)) {
			@Override
			Map<String, Object> call(Map<?, ?> arguments, Progress progress) {
				return J2cService.explainJson(optionalString(arguments, "id"));
			}
		});
		register(new Tool(tool("j2c_capabilities", "J2C capabilities",
				"Lists the J2C version, supported Java features, known limitations "
						+ "and problem ids.",
				schema(new LinkedHashMap<String, Object>(),
						Collections.<String> emptyList()), true, false, false)) {
			@Override
			Map<String, Object> call(Map<?, ?> arguments, Progress progress) {
				return J2cService.capabilitiesJson();
			}
		});
	}

	/**
	 * j2c_build: the output directory, and the Java inputs translated into
	 * it first when paths is given. The build tool is never run, so the
	 * tool stays closed-world; j2c_translate resolves a project's class path.
	 */
	private J2cService.Request buildRequest(Map<?, ?> arguments)
			throws IOException {
		J2cService.Request request;
		if (arguments.get("paths") != null) {
			request = request(arguments, true);
		} else {
			request = new J2cService.Request();
			String output = optionalString(arguments, "output");
			if (output == null) {
				throw new IllegalArgumentException("Missing output directory");
			}
			request.output = resolve(output);
		}
		request.buildTool = false;
		request.lineDirectives = true;
		return request;
	}

	private J2cService.Request request(Map<?, ?> arguments, boolean writes)
			throws IOException {
		J2cService.Request request = new J2cService.Request();
		for (String path : strings(arguments, "paths", true)) {
			request.inputs.add(resolve(path));
		}
		for (String path : strings(arguments, "sourcePaths", false)) {
			request.sourcePaths.add(resolve(path));
		}
		for (String path : strings(arguments, "classPath", false)) {
			request.classPaths.add(resolve(path));
		}
		String sealedTypes = optionalString(arguments, "sealedTypes");
		if (sealedTypes != null) {
			request.sealedTypes = Main.sealedTypes(sealedTypes);
		}
		request.tests = Boolean.TRUE.equals(arguments.get("tests"));
		String runtime = optionalString(arguments, "runtime");
		if (runtime != null) {
			request.runtime = J2cService.runtime(runtime, root);
		}
		String projectMode = optionalString(arguments, "project");
		if (projectMode == null) {
			request.project = project;
		} else if (J2cService.PROJECT_AUTO.equals(projectMode)
				|| J2cService.PROJECT_NONE.equals(projectMode)) {
			request.project = projectMode;
		} else {
			request.project = resolve(projectMode).getPath();
		}
		Object runBuildTool = arguments.get("buildTool");
		if (runBuildTool != null && !(runBuildTool instanceof Boolean)) {
			throw new IllegalArgumentException("buildTool must be a boolean");
		}
		request.buildTool = runBuildTool == null ? buildTool
				: (Boolean) runBuildTool;
		String encoding = optionalString(arguments, "encoding");
		if (encoding != null) {
			request.encoding = Main.charset(encoding);
		}
		if (writes) {
			String output = optionalString(arguments, "output");
			if (output == null) {
				throw new IllegalArgumentException("Missing output directory");
			}
			request.output = resolve(output);
			request.name = optionalString(arguments, "name");
			request.force = Boolean.TRUE.equals(arguments.get("force"));
			request.lineDirectives = Boolean.TRUE.equals(arguments
					.get("lineDirectives"));
		}
		return request;
	}

	private File resolve(String path) {
		File file = new File(path);
		return file.isAbsolute() ? file : new File(root, path);
	}

	private static List<String> strings(Map<?, ?> arguments, String key,
			boolean required) {
		Object value = arguments.get(key);
		if (value == null) {
			if (required) {
				throw new IllegalArgumentException("Missing " + key);
			}
			return Collections.emptyList();
		}
		if (!(value instanceof List)) {
			throw new IllegalArgumentException(key + " must be an array of strings");
		}
		List<String> strings = new ArrayList<String>();
		for (Object item : (List<?>) value) {
			if (!(item instanceof String)) {
				throw new IllegalArgumentException(key
						+ " must be an array of strings");
			}
			strings.add((String) item);
		}
		return strings;
	}

	private static String optionalString(Map<?, ?> arguments, String key) {
		Object value = arguments.get(key);
		if (value != null && !(value instanceof String)) {
			throw new IllegalArgumentException(key + " must be a string");
		}
		return (String) value;
	}

	/** An integer argument as text for Main's parsers, or the default */
	private static String integer(Map<?, ?> arguments, String key,
			int fallback) {
		Object value = arguments.get(key);
		if (value == null) {
			return String.valueOf(fallback);
		}
		if (!(value instanceof Long)) {
			throw new IllegalArgumentException(key + " must be an integer");
		}
		return value.toString();
	}

	private static Map<String, Object> toolResult(Map<String, Object> structured,
			boolean isError) {
		Map<String, Object> text = new LinkedHashMap<String, Object>();
		text.put("type", "text");
		text.put("text", Json.write(structured));
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("content", Collections.singletonList(text));
		result.put("structuredContent", structured);
		result.put("isError", isError);
		return result;
	}


	/** make targets j2c_build may build; test would run the program */
	private static final List<String> BUILD_TARGETS = Arrays.asList("all",
			"mains", "tests");

	private static Map<String, Object> buildSchema() {
		Map<String, Object> schema = inputSchema(true);
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) schema
				.get("properties");
		properties.remove("buildTool");
		properties.remove("lineDirectives");
		properties.put("paths", array("Java files, source folders, or Maven or "
				+ "Gradle project folders to translate first, relative to the "
				+ "workspace root; omit to build earlier J2C output. Maven and "
				+ "Gradle are not run: a cached class path is used, so call "
				+ "j2c_translate first when dependencies must be resolved"));
		Map<String, Object> target = property("string",
				"make target: all (default, the libraries), mains (programs with a "
						+ "main method) or tests (GoogleTest binary, not run)");
		target.put("enum", BUILD_TARGETS);
		properties.put("target", target);
		properties.put("jobs", property("integer",
				"Parallel compile jobs (default: one per processor)"));
		schema.put("required", Collections.singletonList("output"));
		return schema;
	}

	private static Map<String, Object> testSchema() {
		Map<String, Object> schema = inputSchema(true);
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) schema
				.get("properties");
		properties.remove("tests");
		properties.remove("buildTool");
		properties.remove("lineDirectives");
		properties.put("paths", array("Java files, source folders, or Maven or "
				+ "Gradle project folders (their test roots too) with the JUnit "
				+ "tests and the code they test, relative to the workspace root. "
				+ "Maven and Gradle are not run: a cached class path is used, so "
				+ "call j2c_check with tests first when dependencies must be "
				+ "resolved"));
		properties.put("runtime", property("string",
				"bundled (the J2C runtime packaged in J2C) or a J2C runtime "
						+ "directory to translate against instead of the JDK's stubs; "
						+ "assertions on JDK objects and failure messages with Java "
						+ "values need it"));
		properties.put("filter", array("Tests to run: test classes (simple or "
				+ "qualified names), Class.method, Class#method or method names; "
				+ "omit to run every test"));
		properties.put("timeout", property("integer",
				"Seconds the tests may run before the test process and everything "
						+ "it started are stopped (default "
						+ TestRunner.DEFAULT_TIMEOUT + ", 0 for none)"));
		properties.put("jobs", property("integer",
				"Parallel compile jobs (default: one per processor)"));
		return schema;
	}

	private static Map<String, Object> runSchema() {
		Map<String, Object> schema = inputSchema(true);
		@SuppressWarnings("unchecked")
		Map<String, Object> properties = (Map<String, Object>) schema
				.get("properties");
		properties.remove("tests");
		properties.remove("buildTool");
		properties.remove("lineDirectives");
		properties.put("paths", array("Java files, source folders, or Maven or "
				+ "Gradle project folders with the program, relative to the "
				+ "workspace root. Maven and Gradle are not run: a cached class "
				+ "path is used, so call j2c_check first when dependencies must "
				+ "be resolved"));
		properties.put("runtime", property("string",
				"bundled (default: the J2C runtime packaged in J2C) or a J2C "
						+ "runtime directory to translate against"));
		properties.put("main", property("string",
				"The class whose main method runs, qualified or simple name; "
						+ "needed when several classes have one"));
		properties.put("args", array("Arguments of the program, for both runs"));
		properties.put("stdin", property("string",
				"Standard input of both runs, as UTF-8 text (default: none)"));
		properties.put("compare", property("boolean",
				"Also run the main class on the JVM and compare the output and "
						+ "exit code (default true)"));
		properties.put("timeout", property("integer",
				"Seconds each run may take before it and everything it started "
						+ "are stopped (default " + ProgramRunner.DEFAULT_TIMEOUT
						+ ", 0 for none)"));
		properties.put("outputLimit", property("integer",
				"Characters of each run's stdout and stderr returned (default "
						+ ProgramRunner.DEFAULT_OUTPUT_LIMIT
						+ "); the comparison uses all of it"));
		properties.put("jobs", property("integer",
				"Parallel compile jobs (default: one per processor)"));
		return schema;
	}

	private static Map<String, Object> inputSchema(boolean translate) {
		Map<String, Object> properties = new LinkedHashMap<String, Object>();
		properties.put("paths", array("Java files, source folders, or Maven or "
				+ "Gradle project folders (such as \".\"), relative to the "
				+ "workspace root"));
		properties.put("sourcePaths", array("Source roots used to resolve "
				+ "dependencies; inferred from the package declarations when omitted"));
		properties.put("classPath", array("Dependency JARs or class directories"));
		Map<String, Object> sealedTypes = property("string",
				"advisory (default) translates sealed types as ordinary "
						+ "inheritance; reject reports them as problems");
		sealedTypes.put("enum", Arrays.asList("advisory", "reject"));
		properties.put("sealedTypes", sealedTypes);
		properties.put("encoding", property("string",
				"Source file encoding (default UTF-8)"));
		properties.put("tests", property("boolean", translate
				? "Generate GoogleTest wrappers that run the JUnit tests (make test)"
				: "Also report JUnit test features that translated tests cannot run"));
		properties.put("runtime", property("string",
				"bundled translates against the J2C runtime packaged in J2C, so "
						+ "programs run without writing C++ (java.* comes from the "
						+ "runtime: JDK classes it lacks do not compile); or a runtime "
						+ "directory. Omit to translate against the JDK, with every "
						+ "JDK class a stub"));
		properties.put("project", property("string",
				"auto (default) detects a Maven or Gradle build in each folder in "
						+ "paths and uses its source roots (test roots with tests) and "
						+ "dependency class path; none turns detection off; a folder "
						+ "uses its build for the given paths"));
		properties.put("buildTool", property("boolean",
				"Whether Maven or Gradle (the project's wrapper, else mvn or gradle) "
						+ "may be run to resolve the dependency class path, which can "
						+ "download dependencies (default true; a cached class path is "
						+ "used either way)"));
		List<String> required = new ArrayList<String>();
		required.add("paths");
		if (translate) {
			properties.put("output", property("string",
					"Existing output directory, relative to the workspace root"));
			properties.put("name", property("string",
					"Project name used in the Makefile (default: output directory name)"));
			properties.put("force", property("boolean",
					"Translate into a non-empty directory that J2C did not create"));
			properties.put("lineDirectives", property("boolean",
					"Add #line directives, so compiler errors and debuggers name "
							+ "Java files and lines (see j2c_build)"));
			required.add("output");
		}
		return schema(properties, required);
	}

	private static Map<String, Object> tool(String name, String title,
			String description, Map<String, Object> inputSchema,
			boolean readOnly, boolean destructive, boolean openWorld) {
		Map<String, Object> annotations = new LinkedHashMap<String, Object>();
		annotations.put("title", title);
		annotations.put("readOnlyHint", readOnly);
		annotations.put("destructiveHint", destructive);
		annotations.put("idempotentHint", true);
		// Project auto-setup may run the build tool, which downloads
		annotations.put("openWorldHint", openWorld);

		Map<String, Object> tool = new LinkedHashMap<String, Object>();
		tool.put("name", name);
		tool.put("title", title);
		tool.put("description", description);
		tool.put("inputSchema", inputSchema);
		tool.put("annotations", annotations);
		return tool;
	}

	private static Map<String, Object> schema(Map<String, Object> properties,
			List<String> required) {
		Map<String, Object> schema = new LinkedHashMap<String, Object>();
		schema.put("type", "object");
		schema.put("properties", properties);
		if (!required.isEmpty()) {
			schema.put("required", required);
		}
		schema.put("additionalProperties", false);
		return schema;
	}

	private static Map<String, Object> array(String description) {
		Map<String, Object> items = new LinkedHashMap<String, Object>();
		items.put("type", "string");
		Map<String, Object> array = property("array", description);
		array.put("items", items);
		return array;
	}

	private static Map<String, Object> property(String type, String description) {
		Map<String, Object> property = new LinkedHashMap<String, Object>();
		property.put("type", type);
		property.put("description", description);
		return property;
	}

	private static Map<String, Object> result(Object id, Object result) {
		Map<String, Object> response = new LinkedHashMap<String, Object>();
		response.put("jsonrpc", "2.0");
		response.put("id", id);
		response.put("result", result);
		return response;
	}

	private static Map<String, Object> error(Object id, int code, String message) {
		Map<String, Object> error = new LinkedHashMap<String, Object>();
		error.put("code", code);
		error.put("message", message);
		Map<String, Object> response = new LinkedHashMap<String, Object>();
		response.put("jsonrpc", "2.0");
		response.put("id", id);
		response.put("error", error);
		return response;
	}
}
