package se.arnetheduck.j2c.cli;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import se.arnetheduck.j2c.transform.Problem;
import se.arnetheduck.j2c.transform.ProblemCatalog;
import se.arnetheduck.j2c.transform.TranslationException;
import se.arnetheduck.j2c.transform.Transformer;

public class Main {
	private static final String NL = System.lineSeparator();
	private static final String USAGE = "Usage: j2c (--output <directory> | --check) "
			+ "[--sourcepath <paths>] [--classpath <paths>] [--name <name>] "
			+ "[--sealed-types <advisory|reject>] [--encoding <charset>] "
			+ "[--format <text|json>] [--force] [--tests] [--runtime <directory>] "
			+ "[--project <auto|none|directory>] [--no-build-tool] "
			+ "<java-file-or-source-directory-or-project>..." + NL
			+ "       j2c fix [--apply] [--format <text|json>] [--sourcepath <paths>] "
			+ "[--classpath <paths>] [--encoding <charset>] [--tests] "
			+ "[--project <auto|none|directory>] [--no-build-tool] "
			+ "<java-file-or-source-directory-or-project>..." + NL
			+ "       j2c report [--format <markdown|json>] [--sourcepath <paths>] "
			+ "[--classpath <paths>] [--encoding <charset>] [--tests] "
			+ "[--runtime <directory>] [--project <auto|none|directory>] "
			+ "[--no-build-tool] <java-file-or-source-directory-or-project>..." + NL
			+ "       j2c explain [<problem-id>] [--format <text|json>]" + NL
			+ "       j2c capabilities" + NL
			+ "       j2c mcp [--root <directory>] [--project <auto|none>] "
			+ "[--no-build-tool]" + NL
			+ "       j2c --version" + NL
			+ "--check reports every problem that would stop translation "
			+ "without writing output." + NL
			+ "--force translates into a non-empty directory that J2C did not "
			+ "create; its contents are moved to a backup in the temporary folder." + NL
			+ "--tests checks JUnit tests and generates GoogleTest wrappers that run "
			+ "them (make test)." + NL
			+ "fix shows the mechanical fixes of problems as a unified diff; "
			+ "--apply writes them into the Java files." + NL
			+ "report prints a migration readiness report: how much translates, "
			+ "what blocks the rest, and the JDK API used compared with the J2C "
			+ "runtime (Markdown, or --format json)." + NL
			+ "--runtime translates against a J2C runtime directory instead of "
			+ "the running JDK: java/ holds its Java sources, cpp/ its C++ natives "
			+ "and hooks (see runtime/README.md)." + NL
			+ "--project auto (the default) treats an input directory with a "
			+ "pom.xml, build.gradle or settings.gradle as a Maven or Gradle "
			+ "project: its source roots (and test roots with --tests) are "
			+ "translated, and its dependency class path is resolved with the "
			+ "project's wrapper or mvn/gradle and cached; a directory uses that "
			+ "build for the given inputs; none turns detection off." + NL
			+ "--no-build-tool never runs Maven or Gradle (a cached class path is "
			+ "still used)." + NL
			+ "mcp serves J2C as a Model Context Protocol server on stdin/stdout "
			+ "for AI agents.";

	public static void main(String[] args) {
		boolean json = isJson(args);
		String command = command(args);
		PrintStream stdout = json ? utf8(System.out) : System.out;
		if (json || "fix".equals(command) || "report".equals(command)) {
			// The translator reports progress on stdout; keep it for JSON
			// and for diffs
			System.setOut(System.err);
		}
		try {
			System.exit(run(args, stdout, json));
		} catch (IllegalArgumentException e) {
			if (json) {
				stdout.println(Json.write(J2cService.errorJson(command,
						"usage-error", e.getMessage())));
			} else {
				System.err.println(e.getMessage());
				System.err.println(USAGE);
			}
			System.exit(2);
		} catch (TranslationException e) {
			if (json) {
				stdout.println(Json.write(J2cService.failedJson(command,
						e.problems())));
			} else {
				System.err.println("J2C translation failed:");
				System.err.println(e.getMessage());
			}
			System.exit(1);
		} catch (Exception e) {
			if (json) {
				stdout.println(Json.write(J2cService.errorJson(command, "error",
						String.valueOf(e.getMessage()))));
			} else {
				System.err.println("J2C translation failed: " + e.getMessage());
			}
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	private static boolean isJson(String[] args) {
		for (int i = 0; i + 1 < args.length; ++i) {
			if ("--format".equals(args[i]) && "json".equals(args[i + 1])) {
				return true;
			}
		}
		return args.length > 0 && "capabilities".equals(args[0]);
	}

	private static String command(String[] args) {
		if (args.length > 0
				&& ("explain".equals(args[0]) || "capabilities".equals(args[0])
						|| "fix".equals(args[0]) || "report".equals(args[0]))) {
			return args[0];
		}
		for (String arg : args) {
			if ("--check".equals(arg)) {
				return "check";
			}
		}
		return "translate";
	}

	private static int run(String[] args, PrintStream stdout, boolean json)
			throws Exception {
		if (args.length > 0 && "mcp".equals(args[0])) {
			return mcp(args);
		}
		if (args.length > 0 && "capabilities".equals(args[0])) {
			stdout.println(Json.write(J2cService.capabilitiesJson()));
			return 0;
		}
		if (args.length > 0 && "explain".equals(args[0])) {
			return explain(args, stdout, json);
		}
		if (args.length > 0 && "fix".equals(args[0])) {
			return fix(args, stdout, json);
		}
		if (args.length > 0 && "report".equals(args[0])) {
			return report(args, json ? stdout : utf8(stdout));
		}

		J2cService.Request request = new J2cService.Request();
		boolean check = false;
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if ("--help".equals(arg) || "-h".equals(arg)) {
				stdout.println(USAGE);
				return 0;
			} else if ("--version".equals(arg)) {
				stdout.println("j2c " + J2cService.version());
				return 0;
			} else if ("--check".equals(arg)) {
				check = true;
			} else if ("--output".equals(arg)) {
				request.output = new File(value(args, ++i, arg));
			} else if ("--sourcepath".equals(arg)) {
				request.sourcePaths.addAll(pathList(value(args, ++i, arg)));
			} else if ("--classpath".equals(arg)) {
				request.classPaths.addAll(pathList(value(args, ++i, arg)));
			} else if ("--name".equals(arg)) {
				request.name = value(args, ++i, arg);
			} else if ("--sealed-types".equals(arg)) {
				request.sealedTypes = sealedTypes(value(args, ++i, arg));
			} else if ("--encoding".equals(arg)) {
				request.encoding = charset(value(args, ++i, arg));
			} else if ("--format".equals(arg)) {
				format(value(args, ++i, arg));
			} else if ("--force".equals(arg)) {
				request.force = true;
			} else if ("--tests".equals(arg)) {
				request.tests = true;
			} else if ("--runtime".equals(arg)) {
				request.runtime = new File(value(args, ++i, arg));
			} else if ("--project".equals(arg)) {
				request.project = project(value(args, ++i, arg), true);
			} else if ("--no-build-tool".equals(arg)) {
				request.buildTool = false;
			} else if (arg.startsWith("-")) {
				throw new IllegalArgumentException("Unknown option: " + arg);
			} else {
				request.inputs.add(new File(arg));
			}
		}

		if (check && request.output != null) {
			throw new IllegalArgumentException(
					"--check does not write output; omit --output");
		}

		J2cService service = new J2cService(null);
		if (check) {
			J2cService.CheckResult result = service.check(request);
			if (json) {
				stdout.println(Json.write(J2cService.checkJson(result)));
			} else {
				report(stdout, result);
			}
			return result.problems.isEmpty() ? 0 : 1;
		}

		J2cService.TranslateResult result;
		try {
			result = service.translate(request);
		} catch (TranslationException e) {
			if (!json) {
				throw e;
			}
			// The same report as main's, with the detected builds
			stdout.println(Json.write(J2cService.failedJson("translate",
					e.problems(), service.projects())));
			return 1;
		}
		if (json) {
			stdout.println(Json.write(J2cService.translateJson(result)));
		}
		return 0;
	}

	private static int mcp(String[] args) throws Exception {
		File root = new File(".");
		String project = J2cService.PROJECT_AUTO;
		boolean buildTool = true;
		for (int i = 1; i < args.length; i++) {
			if ("--root".equals(args[i])) {
				root = new File(value(args, ++i, args[i - 1]));
			} else if ("--project".equals(args[i])) {
				project = project(value(args, ++i, args[i - 1]), false);
			} else if ("--no-build-tool".equals(args[i])) {
				buildTool = false;
			} else {
				throw new IllegalArgumentException("Unknown mcp option: "
						+ args[i]);
			}
		}
		if (!root.isDirectory()) {
			throw new IllegalArgumentException("MCP root is not a directory: "
					+ root);
		}
		new McpServer(root.getCanonicalFile(), project, buildTool).serve(
				System.in, System.out);
		return 0;
	}

	/**
	 * Previews (a unified diff on stdout) or applies the mechanical fixes.
	 * Exits with 0 when no problem is left without a fix, else 1.
	 */
	private static int fix(String[] args, PrintStream stdout, boolean json)
			throws Exception {
		J2cService.Request request = new J2cService.Request();
		boolean apply = false;
		for (int i = 1; i < args.length; i++) {
			String arg = args[i];
			if ("--apply".equals(arg)) {
				apply = true;
			} else if ("--format".equals(arg)) {
				format(value(args, ++i, arg));
			} else if ("--sourcepath".equals(arg)) {
				request.sourcePaths.addAll(pathList(value(args, ++i, arg)));
			} else if ("--classpath".equals(arg)) {
				request.classPaths.addAll(pathList(value(args, ++i, arg)));
			} else if ("--sealed-types".equals(arg)) {
				request.sealedTypes = sealedTypes(value(args, ++i, arg));
			} else if ("--encoding".equals(arg)) {
				request.encoding = charset(value(args, ++i, arg));
			} else if ("--tests".equals(arg)) {
				request.tests = true;
			} else if ("--runtime".equals(arg)) {
				request.runtime = new File(value(args, ++i, arg));
			} else if ("--project".equals(arg)) {
				request.project = project(value(args, ++i, arg), true);
			} else if ("--no-build-tool".equals(arg)) {
				request.buildTool = false;
			} else if (arg.startsWith("-")) {
				throw new IllegalArgumentException("Unknown fix option: " + arg);
			} else {
				request.inputs.add(new File(arg));
			}
		}

		Fixer fixer = new Fixer(new J2cService(null), new File("."));
		Fixer.Result result = fixer.fix(request, apply);
		if (json) {
			stdout.println(Json.write(fixer.json(result)));
		} else {
			stdout.print(fixer.diff(result));
			stdout.flush();
			PrintStream report = System.err;
			for (Problem problem : result.remaining) {
				report.println(problem);
			}
			report.println("J2C fix: " + (apply ? "applied " : "")
					+ result.fixed.size() + " fix(es) in "
					+ result.changes.size() + " file(s)"
					+ (result.deferred.isEmpty() ? "" : "; "
							+ result.deferred.size()
							+ " more overlap them and are applied by later rounds of --apply")
					+ "; " + result.remaining.size()
					+ " problem(s) need changes by hand");
			if (result.message != null) {
				report.println(result.message);
			}
		}
		return result.remaining.isEmpty() ? 0 : 1;
	}

	/**
	 * Prints the migration readiness report, as Markdown or JSON. Exits with 0
	 * whenever the report was produced, blockers or not.
	 */
	private static int report(String[] args, PrintStream stdout)
			throws Exception {
		J2cService.Request request = new J2cService.Request();
		boolean json = false;
		List<String> inputs = new ArrayList<String>();
		for (int i = 1; i < args.length; i++) {
			String arg = args[i];
			if ("--format".equals(arg)) {
				String format = value(args, ++i, arg);
				if (!"markdown".equals(format) && !"json".equals(format)) {
					throw new IllegalArgumentException("Invalid --format value: "
							+ format + " (expected markdown or json)");
				}
				json = "json".equals(format);
			} else if ("--sourcepath".equals(arg)) {
				request.sourcePaths.addAll(pathList(value(args, ++i, arg)));
			} else if ("--classpath".equals(arg)) {
				request.classPaths.addAll(pathList(value(args, ++i, arg)));
			} else if ("--sealed-types".equals(arg)) {
				request.sealedTypes = sealedTypes(value(args, ++i, arg));
			} else if ("--encoding".equals(arg)) {
				request.encoding = charset(value(args, ++i, arg));
			} else if ("--tests".equals(arg)) {
				request.tests = true;
			} else if ("--runtime".equals(arg)) {
				request.runtime = new File(value(args, ++i, arg));
			} else if ("--project".equals(arg)) {
				request.project = project(value(args, ++i, arg), true);
			} else if ("--no-build-tool".equals(arg)) {
				request.buildTool = false;
			} else if (arg.startsWith("-")) {
				throw new IllegalArgumentException("Unknown report option: " + arg);
			} else {
				request.inputs.add(new File(arg));
				inputs.add(arg);
			}
		}

		Report report = new Report(new J2cService(null), new File("."));
		Report.Result result = report.run(request);
		if (json) {
			stdout.println(Json.write(report.json(result, inputs)));
		} else {
			stdout.print(report.markdown(result, inputs));
			stdout.flush();
		}
		return 0;
	}

	private static int explain(String[] args, PrintStream stdout, boolean json) {
		String id = null;
		for (int i = 1; i < args.length; i++) {
			if ("--format".equals(args[i])) {
				format(value(args, ++i, args[i - 1]));
			} else if (args[i].startsWith("-")) {
				throw new IllegalArgumentException("Unknown option: " + args[i]);
			} else {
				id = args[i];
			}
		}

		Map<String, Object> explanation = J2cService.explainJson(id);
		if (json) {
			stdout.println(Json.write(explanation));
			return 0;
		}
		if (id == null) {
			for (ProblemCatalog.Entry entry : ProblemCatalog.all()) {
				stdout.println(entry.id + "  " + entry.title);
			}
			return 0;
		}
		ProblemCatalog.Entry entry = ProblemCatalog.get(id);
		stdout.println(entry.id + ": " + entry.title);
		stdout.println();
		stdout.println(entry.explanation);
		stdout.println();
		stdout.println("How to fix: " + entry.hint);
		return 0;
	}

	/** Prints each problem and a per-kind summary */
	private static void report(PrintStream stdout, J2cService.CheckResult result) {
		Map<String, Integer> kinds = new TreeMap<String, Integer>();
		List<File> files = new ArrayList<File>();
		for (Problem problem : result.problems) {
			stdout.println(problem);
			Integer count = kinds.get(problem.kind);
			kinds.put(problem.kind, count == null ? 1 : count + 1);
			if (!files.contains(problem.file)) {
				files.add(problem.file);
			}
		}

		if (result.problems.isEmpty()) {
			stdout.println("J2C check: no problems found in " + result.fileCount
					+ " file(s)");
			return;
		}
		stdout.println("J2C check: " + result.problems.size() + " problem(s) in "
				+ files.size() + " of " + result.fileCount + " file(s)");
		for (Map.Entry<String, Integer> kind : kinds.entrySet()) {
			stdout.println("  " + kind.getValue() + " x " + kind.getKey());
		}
	}

	private static void format(String format) {
		if (!"text".equals(format) && !"json".equals(format)) {
			throw new IllegalArgumentException("Invalid --format value: " + format
					+ " (expected text or json)");
		}
	}

	static Transformer.SealedTypes sealedTypes(String policy) {
		if ("advisory".equals(policy)) {
			return Transformer.SealedTypes.ADVISORY;
		}
		if ("reject".equals(policy)) {
			return Transformer.SealedTypes.REJECT;
		}
		throw new IllegalArgumentException("Invalid --sealed-types value: "
				+ policy + " (expected advisory or reject)");
	}

	/** auto, none, or (when directories are allowed) an existing directory */
	static String project(String value, boolean directories) {
		if (J2cService.PROJECT_AUTO.equals(value)
				|| J2cService.PROJECT_NONE.equals(value)) {
			return value;
		}
		if (directories && new File(value).isDirectory()) {
			return value;
		}
		throw new IllegalArgumentException("Invalid --project value: " + value
				+ (directories ? " (expected auto, none or a project directory)"
						: " (expected auto or none)"));
	}

	static Charset charset(String name) {
		try {
			return Charset.forName(name);
		} catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
			throw new IllegalArgumentException("Unsupported --encoding value: "
					+ name);
		}
	}

	private static String value(String[] args, int index, String option) {
		if (index >= args.length || args[index].startsWith("--")) {
			throw new IllegalArgumentException("Missing value for " + option);
		}
		return args[index];
	}

	private static List<File> pathList(String value) {
		List<File> paths = new ArrayList<File>();
		for (String path : value.split(java.util.regex.Pattern.quote(
				File.pathSeparator))) {
			if (!path.isEmpty()) {
				paths.add(new File(path));
			}
		}
		return paths;
	}

	/** UTF-8, so JSON on stdout is not mangled by the platform charset */
	static PrintStream utf8(PrintStream stream) {
		return new PrintStream(stream, true, StandardCharsets.UTF_8);
	}
}
