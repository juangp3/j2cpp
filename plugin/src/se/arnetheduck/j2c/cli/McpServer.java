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

import se.arnetheduck.j2c.transform.TranslationException;

/**
 * J2C as a Model Context Protocol server: JSON-RPC 2.0 messages, one per line,
 * on stdin and stdout. Every path is resolved against, and must stay inside,
 * the workspace root.
 */
final class McpServer {
	/** Newest first; the client's version is used when it is one of these */
	private static final List<String> PROTOCOL_VERSIONS = Arrays.asList(
			"2025-06-18", "2025-03-26", "2024-11-05");

	private static final String INSTRUCTIONS = "J2C translates Java 17 source "
			+ "into C++20. Typical workflow: call j2c_check on the Java files or "
			+ "source folders, fix each reported problem in the Java source using "
			+ "its hint (j2c_explain gives details), check again until no problems "
			+ "remain (j2c_fix previews or applies the mechanical fixes some "
			+ "problems carry), then call j2c_translate with an output directory. Paths are "
			+ "relative to the workspace root. A Maven or Gradle project folder "
			+ "(such as \".\") can be passed directly: its source roots and "
			+ "dependency class path are detected. Translated code still needs a C++ "
			+ "implementation of the JDK classes it uses (see j2c_capabilities). "
			+ "j2c_report assesses how much translates and what blocks the rest; "
			+ "the resources hold the problem catalog (j2c://problems), the "
			+ "runtime's contract and API (j2c://runtime), and translated files "
			+ "(j2c://output/{path}).";

	private final File root;
	private final J2cService service;
	/** Project auto-setup defaults, from j2c mcp --project and --no-build-tool */
	private final String project;
	private final boolean buildTool;

	McpServer(File root) {
		this(root, J2cService.PROJECT_AUTO, true);
	}

	McpServer(File root, String project, boolean buildTool) {
		this.root = root;
		this.service = new J2cService(root);
		this.project = project;
		this.buildTool = buildTool;
		this.content = new McpContent(root, service);
	}

	/** Prompts and resources */
	private final McpContent content;
	/** The version agreed in initialize */
	private String protocolVersion = PROTOCOL_VERSIONS.get(0);

	/** Whether titles may be sent: 2025-06-18 added them */
	private boolean titles() {
		return "2025-06-18".compareTo(protocolVersion) <= 0;
	}

	void serve(InputStream in, PrintStream rawOut) throws IOException {
		PrintStream out = new PrintStream(rawOut, true, StandardCharsets.UTF_8);
		// Only protocol messages may appear on stdout; the translator's
		// progress output goes to stderr
		System.setOut(System.err);
		BufferedReader reader = new BufferedReader(new InputStreamReader(in,
				StandardCharsets.UTF_8));
		for (String line = reader.readLine(); line != null; line = reader
				.readLine()) {
			if (line.trim().isEmpty()) {
				continue;
			}
			Map<String, Object> response = handle(line);
			if (response != null) {
				out.println(Json.write(response));
			}
		}
	}

	/** The response to one message, or null for a notification */
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
		Map<?, ?> params = request.get("params") instanceof Map ? (Map<?, ?>) request
				.get("params") : Collections.emptyMap();

		switch ((String) method) {
		case "initialize":
			return result(id, initialize(params));
		case "ping":
			return result(id, new LinkedHashMap<String, Object>());
		case "tools/list":
			Map<String, Object> tools = new LinkedHashMap<String, Object>();
			tools.put("tools", toolDefinitions());
			return result(id, tools);
		case "tools/call":
			return callTool(id, params);
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

	private Map<String, Object> initialize(Map<?, ?> params) {
		Object requested = params.get("protocolVersion");
		String version = PROTOCOL_VERSIONS.contains(requested) ? (String) requested
				: PROTOCOL_VERSIONS.get(0);
		protocolVersion = version;

		Map<String, Object> capabilities = new LinkedHashMap<String, Object>();
		Map<String, Object> tools = new LinkedHashMap<String, Object>();
		tools.put("listChanged", false);
		capabilities.put("tools", tools);
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

	private Map<String, Object> callTool(Object id, Map<?, ?> params) {
		Object name = params.get("name");
		Map<?, ?> arguments = params.get("arguments") instanceof Map ? (Map<?, ?>) params
				.get("arguments") : Collections.emptyMap();
		String command = name instanceof String ? ((String) name).replaceFirst(
				"^j2c_", "") : "";

		try {
			switch (String.valueOf(name)) {
			case "j2c_check": {
				J2cService.CheckResult result = service.check(request(arguments,
						false));
				return result(id, toolResult(J2cService.checkJson(result), false));
			}
			case "j2c_translate": {
				J2cService.TranslateResult result = service.translate(request(
						arguments, true));
				content.translated(result);
				return result(id, toolResult(J2cService.translateJson(result),
						false));
			}
			case "j2c_fix": {
				Fixer fixer = new Fixer(service, root);
				Fixer.Result result = fixer.fix(request(arguments, false),
						Boolean.TRUE.equals(arguments.get("apply")));
				return result(id, toolResult(fixer.json(result), false));
			}
			case "j2c_report": {
				Report report = new Report(service, root);
				List<String> inputs = strings(arguments, "paths", true);
				Report.Result result = report.run(request(arguments, false));
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
				return result(id, toolResult);
			}
			case "j2c_explain":
				return result(id, toolResult(J2cService.explainJson(optionalString(
						arguments, "id")), false));
			case "j2c_capabilities":
				return result(id, toolResult(J2cService.capabilitiesJson(), false));
			default:
				return error(id, -32602, "Unknown tool: " + name);
			}
		} catch (IllegalArgumentException e) {
			return result(id, toolResult(J2cService.errorJson(command,
					"usage-error", e.getMessage()), true));
		} catch (TranslationException e) {
			return result(id, toolResult(J2cService.failedJson(command,
					e.problems(), service.projects()), true));
		} catch (Exception e) {
			e.printStackTrace(System.err);
			return result(id, toolResult(J2cService.errorJson(command, "error",
					String.valueOf(e.getMessage())), true));
		}
	}

	private J2cService.Request request(Map<?, ?> arguments, boolean writes) {
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

	private static List<Object> toolDefinitions() {
		List<Object> tools = new ArrayList<Object>();
		tools.add(tool("j2c_check", "Check Java for translation problems",
				"Reports every Java compile error and construct J2C cannot "
						+ "translate in the given Java files or source folders, "
						+ "with file, line, column, a stable problem id and a hint "
						+ "for fixing it. Writes nothing. Run before j2c_translate.",
				inputSchema(false), true, false, true));
		tools.add(tool("j2c_translate", "Translate Java to C++",
				"Translates Java files or source folders into C++20 in the output "
						+ "directory and lists the generated files, including a "
						+ "Makefile. The output directory must exist and be empty or "
						+ "previous J2C output; its current contents are moved to a "
						+ "backup in the temporary folder. Fails with the same "
						+ "problems j2c_check reports.",
				inputSchema(true), false, true, true));
		Map<String, Object> fixSchema = inputSchema(false);
		@SuppressWarnings("unchecked")
		Map<String, Object> fixProperties = (Map<String, Object>) fixSchema
				.get("properties");
		fixProperties.put("apply", property("boolean",
				"Write the fixes into the Java files (default false: only preview "
						+ "them as a unified diff)"));
		tools.add(tool("j2c_fix", "Fix Java translation problems",
				"Previews, as a unified diff, the mechanical fixes of the problems "
						+ "j2c_check reports, such as this used in a lambda inside an "
						+ "anonymous class; with apply: true writes them into the Java "
						+ "files, checks again, and repeats until no fixable problem is "
						+ "left. Problems without a fix are returned with their hints.",
				// Like j2c_check, it may run the project's build tool
				fixSchema, false, true, true));
		tools.add(tool("j2c_report", "Migration readiness report",
				"Reports how much of the Java code translates (files, types and "
						+ "methods with no problem from j2c_check), the blockers by "
						+ "problem id and package with top locations and quick-fix "
						+ "counts, test-feature blockers with tests, the JDK types and "
						+ "members the code uses compared with what the J2C runtime "
						+ "provides, and next steps. Writes nothing. Returns JSON and "
						+ "the same report as Markdown.",
				// Like j2c_check, it may run the project's build tool
				inputSchema(false), true, false, true));
		Map<String, Object> explainProperties = new LinkedHashMap<String, Object>();
		explainProperties.put("id", property("string",
				"A problem id such as J2C-LAMBDA-ANONYMOUS-THIS; omit to list all"));
		tools.add(tool("j2c_explain", "Explain a J2C problem id",
				"Explains what a problem id from j2c_check means and how to fix it.",
				schema(explainProperties, Collections.<String> emptyList()), true,
				false, false));
		tools.add(tool("j2c_capabilities", "J2C capabilities",
				"Lists the J2C version, supported Java features, known limitations "
						+ "and problem ids.",
				schema(new LinkedHashMap<String, Object>(),
						Collections.<String> emptyList()), true, false, false));
		return tools;
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
