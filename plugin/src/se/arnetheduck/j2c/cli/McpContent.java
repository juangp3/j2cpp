package se.arnetheduck.j2c.cli;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The MCP server's prompts and resources. Resources are the problem catalog
 * (j2c://problems, j2c://problems/{id}), the runtime's contract and API
 * (j2c://runtime), and translated files under the workspace root
 * (j2c://output/{path}, only inside a directory with J2C's output marker).
 */
final class McpContent {
	static final String PROBLEMS = "j2c://problems";
	static final String RUNTIME = "j2c://runtime";
	static final String OUTPUT = "j2c://output/";
	/** Translated files listed by resources/list, per output directory */
	private static final int LISTED_FILES = 500;
	/** Larger files are not served */
	private static final long MAX_SIZE = 8L * 1024 * 1024;
	private static final List<String> TEXT_EXTENSIONS = Arrays.asList(".cpp",
			".hpp", ".h", ".c", ".cc", ".txt", ".md", ".json", ".mk", ".tmpl");

	/** A resource that does not exist (JSON-RPC error -32002) */
	static final class NotFound extends Exception {
		private static final long serialVersionUID = 1L;

		NotFound(String message) {
			super(message);
		}
	}

	private final File root;
	private final J2cService service;
	/** Output directories translated in this session, for resources/list */
	private final List<File> outputs = new ArrayList<File>();

	McpContent(File root, J2cService service) {
		this.root = root;
		this.service = service;
	}

	void translated(J2cService.TranslateResult result) {
		File output = result.output.getAbsoluteFile();
		if (!outputs.contains(output)) {
			outputs.add(output);
		}
	}

	// Prompts

	static Map<String, Object> promptList(boolean titles) {
		List<Object> prompts = new ArrayList<Object>();
		prompts.add(promptDefinition("j2c-migrate", "Migrate Java to C++",
				"Check, fix, translate and build Java code with J2C, fixing "
						+ "problems in the Java source until it translates.",
				Arrays.asList(
						argument("path", "Java file, source folder, or Maven or "
								+ "Gradle project folder, relative to the workspace "
								+ "root", true),
						argument("output", "Output directory for the C++ code "
								+ "(default build/cpp)", false),
						argument("tests", "true to also translate JUnit tests into "
								+ "GoogleTest", false)), titles));
		prompts.add(promptDefinition("j2c-assess", "Assess a Java to C++ migration",
				"Run the J2C migration readiness report and summarize how much "
						+ "translates, what blocks the rest, and the effort left. "
						+ "Changes nothing.",
				Arrays.asList(
						argument("path", "Java file, source folder, or Maven or "
								+ "Gradle project folder, relative to the workspace "
								+ "root", true),
						argument("tests", "true to include JUnit tests", false)),
				titles));
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("prompts", prompts);
		return result;
	}

	private static Map<String, Object> promptDefinition(String name,
			String title, String description, List<Object> arguments,
			boolean titles) {
		Map<String, Object> prompt = new LinkedHashMap<String, Object>();
		prompt.put("name", name);
		if (titles) {
			prompt.put("title", title);
		}
		prompt.put("description", description);
		prompt.put("arguments", arguments);
		return prompt;
	}

	private static Map<String, Object> argument(String name, String description,
			boolean required) {
		Map<String, Object> argument = new LinkedHashMap<String, Object>();
		argument.put("name", name);
		argument.put("description", description);
		argument.put("required", required);
		return argument;
	}

	static Map<String, Object> prompt(Map<?, ?> params) {
		Object name = params.get("name");
		Map<?, ?> arguments = params.get("arguments") instanceof Map ? (Map<?, ?>) params
				.get("arguments") : Collections.emptyMap();
		String path = stringArgument(arguments, "path");
		String tests = stringArgument(arguments, "tests");
		boolean withTests = "true".equalsIgnoreCase(tests);
		if (tests != null && !withTests && !"false".equalsIgnoreCase(tests)) {
			throw new IllegalArgumentException("tests must be true or false");
		}
		String text;
		String description;
		if ("j2c-migrate".equals(name)) {
			requireArgument(path, "path");
			String output = stringArgument(arguments, "output");
			if (output == null || output.isEmpty()) {
				output = "build/cpp";
			}
			description = "Migrate " + path + " to C++ with J2C";
			text = migrateText(path, output, withTests);
		} else if ("j2c-assess".equals(name)) {
			requireArgument(path, "path");
			description = "Assess migrating " + path + " to C++ with J2C";
			text = assessText(path, withTests);
		} else {
			throw new IllegalArgumentException("Unknown prompt: " + name);
		}

		Map<String, Object> content = new LinkedHashMap<String, Object>();
		content.put("type", "text");
		content.put("text", text);
		Map<String, Object> message = new LinkedHashMap<String, Object>();
		message.put("role", "user");
		message.put("content", content);
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("description", description);
		result.put("messages", Collections.singletonList(message));
		return result;
	}

	private static String stringArgument(Map<?, ?> arguments, String key) {
		Object value = arguments.get(key);
		if (value != null && !(value instanceof String)) {
			throw new IllegalArgumentException("Prompt argument " + key
					+ " must be a string");
		}
		return (String) value;
	}

	private static void requireArgument(String value, String name) {
		if (value == null || value.trim().isEmpty()) {
			throw new IllegalArgumentException("Missing required argument: "
					+ name);
		}
	}

	private static String json(String value) {
		return Json.write(value);
	}

	private static String migrateText(String path, String output, boolean tests) {
		String testArgument = tests ? ", tests: true" : "";
		return "Migrate the Java code in " + json(path) + " to C++20 with the J2C "
				+ "tools, working in the Java source and never editing generated C++ "
				+ "to work around a problem.\n\n"
				+ "1. Call j2c_check with paths: [" + json(path) + "]" + testArgument
				+ ". If it reports a J2C-PROJECT-CLASSPATH-UNRESOLVED warning, the "
				+ "compile errors may only be missing dependencies: fix the build or "
				+ "pass the dependency JARs in classPath before changing any Java.\n"
				+ "2. If problems have fixes, call j2c_fix with the same arguments to "
				+ "preview them, show me the diff, and after I agree call it again "
				+ "with apply: true.\n"
				+ "3. Fix each remaining problem in the Java source using its hint "
				+ "(j2c_explain explains a problem id, and the resource "
				+ "j2c://problems/{id} holds the same text). Keep the program's "
				+ "behavior; ask me when a fix needs a design decision.\n"
				+ "4. Repeat j2c_check until its status is ok.\n"
				+ "5. Call j2c_translate with paths: [" + json(path) + "], output: "
				+ json(output) + testArgument + ". The output directory must exist "
				+ "and be empty or earlier J2C output; create it if needed, and never "
				+ "pass force: true without asking me.\n"
				+ "6. If a C++20 compiler and make are available, build with make -C "
				+ output + (tests ? " (and make -C " + output + " test to run the "
						+ "tests)" : "") + " and report errors. Generated files can be "
				+ "read as resources j2c://output/{path}.\n\n"
				+ "Translated programs use stubs for JDK classes the J2C runtime "
				+ "does not provide (resource j2c://runtime). Finish with a short "
				+ "summary: the problems fixed, by hand and automatically, what is "
				+ "left, and whether the C++ built.";
	}

	private static String assessText(String path, boolean tests) {
		return "Assess how ready the Java code in " + json(path) + " is to be "
				+ "translated to C++20 with J2C. Do not change any file.\n\n"
				+ "1. Call j2c_report with paths: [" + json(path) + "]"
				+ (tests ? ", tests: true" : "") + ".\n"
				+ "2. Summarize for a team deciding whether to migrate:\n"
				+ "   - the share of files, types and methods that translate now;\n"
				+ "   - the main blockers by problem id and package, and how many "
				+ "j2c_fix can fix automatically;\n"
				+ (tests ? "   - test features that translated tests cannot run;\n" : "")
				+ "   - the JDK API the code uses that the J2C runtime does not "
				+ "provide (it would fail at run time), most used first;\n"
				+ "   - a rough estimate of the manual work left, and the next "
				+ "steps in order.\n"
				+ "Use the problem hints (j2c_explain, or the resource "
				+ "j2c://problems/{id}) to explain each kind of blocker in a "
				+ "sentence. Keep the summary short, and say what the numbers do not "
				+ "cover: run-time behavior and C++ build errors are not checked.";
	}

	// Resources

	Map<String, Object> resourceList(boolean titles) {
		List<Object> resources = new ArrayList<Object>();
		resources.add(resource(PROBLEMS, "problems", "J2C problem catalog",
				"Every problem id J2C reports, with an explanation and a hint "
						+ "for fixing it in the Java source",
				"application/json", titles));
		if (hasRuntime()) {
			resources.add(resource(RUNTIME, "runtime", "J2C runtime contract",
					"What the bundled J2C runtime (--runtime) implements of the "
							+ "Java class library, its limits, and the types it provides",
					"text/markdown", titles));
		}
		for (File output : outputs) {
			if (!new File(output, J2cService.OUTPUT_MARKER).isFile()) {
				continue;
			}
			List<String> files = new ArrayList<String>();
			listFiles(output, "", files);
			Collections.sort(files);
			for (int i = 0; i < files.size() && i < LISTED_FILES; ++i) {
				String path = relativeToRoot(new File(output, files.get(i)));
				if (path == null) {
					continue;
				}
				resources.add(resource(OUTPUT + path, path, null,
						"Translated by J2C", mimeType(path), titles));
			}
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("resources", resources);
		return result;
	}

	static Map<String, Object> templateList(boolean titles) {
		List<Object> templates = new ArrayList<Object>();
		templates.add(template(PROBLEMS + "/{id}", "problem", "J2C problem",
				"One problem id, such as J2C-LAMBDA-ANONYMOUS-THIS, with its "
						+ "explanation and hint", "application/json", titles));
		templates.add(template(OUTPUT + "{path}", "output", "Translated file",
				"A file J2C generated, by its path relative to the workspace root "
						+ "(inside an output directory J2C created)", null, titles));
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("resourceTemplates", templates);
		return result;
	}

	private static Map<String, Object> resource(String uri, String name,
			String title, String description, String mimeType, boolean titles) {
		Map<String, Object> resource = new LinkedHashMap<String, Object>();
		resource.put("uri", uri);
		resource.put("name", name);
		if (titles && title != null) {
			resource.put("title", title);
		}
		resource.put("description", description);
		if (mimeType != null) {
			resource.put("mimeType", mimeType);
		}
		return resource;
	}

	private static Map<String, Object> template(String uriTemplate, String name,
			String title, String description, String mimeType, boolean titles) {
		Map<String, Object> template = new LinkedHashMap<String, Object>();
		template.put("uriTemplate", uriTemplate);
		template.put("name", name);
		if (titles) {
			template.put("title", title);
		}
		template.put("description", description);
		if (mimeType != null) {
			template.put("mimeType", mimeType);
		}
		return template;
	}

	Map<String, Object> read(Map<?, ?> params) throws Exception {
		Object uri = params.get("uri");
		if (!(uri instanceof String)) {
			throw new IllegalArgumentException("Missing uri");
		}
		String text = (String) uri;
		Map<String, Object> contents;
		if (PROBLEMS.equals(text)) {
			contents = textContents(text, "application/json", Json.write(J2cService
					.explainJson(null)));
		} else if (text.startsWith(PROBLEMS + "/")) {
			String id = decode(text.substring(PROBLEMS.length() + 1));
			try {
				contents = textContents(text, "application/json",
						Json.write(J2cService.explainJson(id)));
			} catch (IllegalArgumentException e) {
				throw new NotFound("Unknown problem ID: " + id);
			}
		} else if (RUNTIME.equals(text)) {
			String runtime = runtimeMarkdown();
			if (runtime == null) {
				throw new NotFound("This J2C build has no bundled runtime");
			}
			contents = textContents(text, "text/markdown", runtime);
		} else if (text.startsWith(OUTPUT)) {
			contents = output(text, decode(text.substring(OUTPUT.length())));
		} else {
			throw new NotFound("Resource not found: " + text);
		}
		Map<String, Object> result = new LinkedHashMap<String, Object>();
		result.put("contents", Collections.singletonList(contents));
		return result;
	}

	private static String decode(String text) {
		try {
			// Plus signs are literal in URI paths
			return URLDecoder.decode(text.replace("+", "%2B"), "UTF-8");
		} catch (UnsupportedEncodingException | IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid resource URI: " + text);
		}
	}

	private static Map<String, Object> textContents(String uri, String mimeType,
			String text) {
		Map<String, Object> contents = new LinkedHashMap<String, Object>();
		contents.put("uri", uri);
		contents.put("mimeType", mimeType);
		contents.put("text", text);
		return contents;
	}

	/** A translated file: inside the root and an output directory J2C made */
	private Map<String, Object> output(String uri, String path)
			throws IOException, NotFound {
		if (path.isEmpty()) {
			throw new IllegalArgumentException("Missing output file path in " + uri);
		}
		File file = new File(path);
		if (!file.isAbsolute()) {
			file = new File(root, path);
		}
		service.requireInsideRoot(file, "Output file");
		File canonical = file.getCanonicalFile();
		if (!canonical.isFile()) {
			throw new NotFound("No such file: " + path);
		}
		if (canonical.getName().equals(J2cService.OUTPUT_MARKER)
				|| outputDirectory(canonical) == null) {
			throw new IllegalArgumentException("Not a file J2C generated (no "
					+ J2cService.OUTPUT_MARKER + " marker in its directory or "
					+ "above it inside the workspace root): " + path);
		}
		if (canonical.length() > MAX_SIZE) {
			throw new IllegalArgumentException("File is larger than "
					+ (MAX_SIZE / 1024 / 1024) + " MB: " + path);
		}
		byte[] bytes = Files.readAllBytes(canonical.toPath());
		String mimeType = mimeType(canonical.getName());
		if (mimeType.startsWith("text/")) {
			return textContents(uri, mimeType, new String(bytes,
					StandardCharsets.UTF_8));
		}
		Map<String, Object> contents = new LinkedHashMap<String, Object>();
		contents.put("uri", uri);
		contents.put("mimeType", mimeType);
		contents.put("blob", Base64.getEncoder().encodeToString(bytes));
		return contents;
	}

	/** The nearest directory with the output marker, up to the root */
	private File outputDirectory(File file) throws IOException {
		File rootDirectory = root.getCanonicalFile();
		for (File directory = file.getParentFile(); directory != null; directory = directory
				.getParentFile()) {
			if (new File(directory, J2cService.OUTPUT_MARKER).isFile()) {
				return directory;
			}
			if (directory.equals(rootDirectory)) {
				return null;
			}
		}
		return null;
	}

	private String relativeToRoot(File file) {
		try {
			String rootPath = root.getCanonicalPath();
			String path = file.getCanonicalPath();
			if (!path.startsWith(rootPath + File.separator)) {
				return null;
			}
			return path.substring(rootPath.length() + 1).replace(
					File.separatorChar, '/');
		} catch (IOException e) {
			return null;
		}
	}

	private static String mimeType(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		if (name.equals("Makefile")) {
			return "text/x-makefile";
		}
		if (name.endsWith(".cpp") || name.endsWith(".hpp") || name.endsWith(".h")
				|| name.endsWith(".cc")) {
			return "text/x-c++src";
		}
		if (name.endsWith(".md")) {
			return "text/markdown";
		}
		for (String extension : TEXT_EXTENSIONS) {
			if (name.endsWith(extension)) {
				return "text/plain";
			}
		}
		// Object files and libraries from a build
		return "application/octet-stream";
	}

	private static void listFiles(File directory, String prefix,
			List<String> files) {
		File[] entries = directory.listFiles();
		if (entries == null) {
			return;
		}
		for (File entry : entries) {
			String name = prefix + entry.getName();
			if (entry.isDirectory()) {
				listFiles(entry, name + "/", files);
			} else if (!entry.getName().equals(J2cService.OUTPUT_MARKER)) {
				files.add(name);
			}
		}
	}

	private static boolean hasRuntime() {
		return McpContent.class.getResource(RuntimeApi.RESOURCE) != null
				|| McpContent.class.getResource(RuntimeApi.README_RESOURCE) != null;
	}

	/** The runtime README, then the types its API provides by package */
	static String runtimeMarkdown() throws IOException {
		String readme = RuntimeApi.bundledReadme();
		RuntimeApi api = RuntimeApi.bundled();
		if (readme == null && api == null) {
			return null;
		}
		StringBuilder md = new StringBuilder();
		if (readme != null) {
			md.append(readme.replace("\r\n", "\n"));
			if (!readme.endsWith("\n")) {
				md.append('\n');
			}
		}
		if (api != null) {
			md.append("\n## Types the runtime provides\n\n");
			md.append("Generated from the runtime's sources (").append(api.typeCount())
					.append(" types, with their non-private member counts). A JDK "
							+ "type or member not listed is generated as a stub that "
							+ "fails at run time; j2c report compares a program's JDK "
							+ "use with this list.\n");
			Map<String, List<String>> packages = new TreeMap<String, List<String>>();
			for (String type : api.types()) {
				int dot = type.lastIndexOf('.');
				String packageName = type.substring(0, dot);
				List<String> types = packages.get(packageName);
				if (types == null) {
					types = new ArrayList<String>();
					packages.put(packageName, types);
				}
				types.add(type.substring(dot + 1).replace('$', '.') + " ("
						+ api.memberCount(type) + ")");
			}
			for (Map.Entry<String, List<String>> entry : packages.entrySet()) {
				md.append("\n### `").append(entry.getKey()).append("`\n\n");
				String separator = "";
				for (String type : entry.getValue()) {
					md.append(separator).append(type);
					separator = ", ";
				}
				md.append('\n');
			}
		}
		return md.toString();
	}
}
