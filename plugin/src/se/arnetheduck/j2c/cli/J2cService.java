package se.arnetheduck.j2c.cli;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.Path;

import se.arnetheduck.j2c.transform.Fix;
import se.arnetheduck.j2c.transform.Problem;
import se.arnetheduck.j2c.transform.ProblemCatalog;
import se.arnetheduck.j2c.transform.SourceEdit;
import se.arnetheduck.j2c.transform.SourceProject;
import se.arnetheduck.j2c.transform.SourceUnit;
import se.arnetheduck.j2c.transform.Transformer;

/**
 * Checking, translation and metadata shared by the command line, its JSON
 * output, and the MCP server. Invalid requests throw
 * IllegalArgumentException; problems in the Java input throw
 * TranslationException.
 */
final class J2cService {
	/** Written into every output directory, so it can be safely replaced */
	static final String OUTPUT_MARKER = ".j2c-output";
	// Module and package declarations have nothing to translate, and checking
	// a module declaration fails when its packages are compiled as sources
	private static final List<String> DECLARES_NO_TYPES = Arrays.asList(
			"module-info.java", "package-info.java");
	static final String PROJECT_AUTO = "auto";
	static final String PROJECT_NONE = "none";
	/**
	 * Version of the JSON shapes produced below, raised whenever a field is
	 * added, changed or removed. 2 added problem fixes, the fix command, and
	 * the project setup fields (projects, warnings, buildTools); 3 the report
	 * command.
	 */
	static final int SCHEMA_VERSION = 3;

	static final class Request {
		final List<File> inputs = new ArrayList<File>();
		final List<File> sourcePaths = new ArrayList<File>();
		final List<File> classPaths = new ArrayList<File>();
		File output;
		boolean force;
		/** Check and wrap JUnit tests for GoogleTest */
		boolean tests;
		/** A J2C runtime directory replacing the running JDK's library */
		File runtime;
		String name;
		Transformer.SealedTypes sealedTypes = Transformer.SealedTypes.ADVISORY;
		Charset encoding = StandardCharsets.UTF_8;
		/**
		 * Project auto-setup: "auto" detects a Maven or Gradle build in each
		 * input directory, "none" turns detection off, and anything else is
		 * a project directory whose build supplies the source roots and
		 * class path for the inputs
		 */
		String project = PROJECT_AUTO;
		/** Whether the build tool may be run to resolve the class path */
		boolean buildTool = true;
		/** Emit #line directives so C++ diagnostics name Java lines */
		boolean lineDirectives;
	}

	static final class CheckResult {
		final List<Problem> problems;
		final int fileCount;
		/** Builds detected by project auto-setup */
		List<ProjectSetup.Project> projects = Collections.emptyList();

		CheckResult(List<Problem> problems, int fileCount) {
			this.problems = problems;
			this.fileCount = fileCount;
		}
	}

	static final class TranslateResult {
		final File output;
		final List<String> generatedFiles;
		final int fileCount;
		List<ProjectSetup.Project> projects = Collections.emptyList();

		TranslateResult(File output, List<String> generatedFiles, int fileCount) {
			this.output = output;
			this.generatedFiles = generatedFiles;
			this.fileCount = fileCount;
		}
	}

	private static final class Prepared {
		final SourceProject project;
		final SourceUnit[] units;

		final List<File> sourcePaths;
		final List<File> classPaths;

		Prepared(SourceProject project, SourceUnit[] units,
				List<File> sourcePaths, List<File> classPaths) {
			this.project = project;
			this.units = units;
			this.sourcePaths = sourcePaths;
			this.classPaths = classPaths;
		}
	}

	/** When set, every input, source path and output must be inside it */
	private final File root;
	/** The builds detected for the latest request */
	private List<ProjectSetup.Project> projects = Collections.emptyList();
	/** Results kept between checks, in the MCP server; null checks all */
	private CheckCache cache;

	J2cService(File root) {
		this.root = root;
	}

	/** Keeps check results between calls; null checks everything each time */
	void setCache(CheckCache cache) {
		this.cache = cache;
	}

	CheckResult check(Request request) throws Exception {
		return check(request, null, Progress.NONE);
	}

	CheckResult check(Request request, Progress progress) throws Exception {
		return check(request, null, progress);
	}

	/**
	 * Checks the request's files. Problems are grouped by file in path order,
	 * each file's in the order they were found. A listener (the report) gets
	 * each unit's AST, so all units are checked; otherwise the cache, when
	 * set, re-checks only what may have changed.
	 */
	CheckResult check(final Request request,
			final Transformer.CheckListener listener, final Progress progress)
			throws Exception {
		final Prepared prepared = prepare(request, false, progress);
		CheckCache.Checker checker = new CheckCache.Checker() {
			@Override
			public List<Problem> check(SourceUnit[] units) throws Exception {
				// Checking writes nothing, so the output root is never used
				Transformer checker = new Transformer(prepared.project, "check",
						new Path(new File(".").getAbsolutePath()),
						request.sealedTypes);
				checker.setTests(request.tests);
				return checker.check(progress.monitor(), listener, units);
			}
		};
		List<Problem> problems;
		if (cache == null || listener != null) {
			problems = checker.check(prepared.units);
		} else {
			List<File> buildFiles = new ArrayList<File>();
			for (ProjectSetup.Project project : projects) {
				buildFiles.addAll(project.buildFiles);
			}
			problems = cache.check(prepared.sourcePaths, prepared.classPaths,
					request.runtime == null, request.sealedTypes + " tests="
							+ request.tests + " runtime=" + request.runtime
							+ " encoding=" + request.encoding.name(), buildFiles,
					request.encoding, prepared.units, checker, progress);
			System.err.println("J2C: " + cache.last());
		}
		CheckResult result = new CheckResult(byFile(problems),
				prepared.units.length);
		result.projects = projects;
		return result;
	}

	/** Sorts by file; problems in one file keep their order */
	private static List<Problem> byFile(List<Problem> problems) {
		List<Problem> sorted = new ArrayList<Problem>(problems);
		Collections.sort(sorted, new Comparator<Problem>() {
			@Override
			public int compare(Problem a, Problem b) {
				return a.file.getPath().compareTo(b.file.getPath());
			}
		});
		return sorted;
	}

	/** The builds detected for the latest request, for failure reports */
	List<ProjectSetup.Project> projects() {
		return projects;
	}

	TranslateResult translate(Request request) throws Exception {
		return translate(request, Progress.NONE);
	}

	/**
	 * Translates the request's files. The output directory is marked as J2C
	 * output before anything is written to it, so a translation that fails or
	 * is cancelled half-way leaves generated files that the next translation
	 * replaces without --force.
	 */
	TranslateResult translate(Request request, Progress progress)
			throws Exception {
		Prepared prepared = prepare(request, true, progress);
		File output = request.output.getAbsoluteFile();
		String name = request.name == null ? output.getName() : request.name;
		Transformer transformer = new Transformer(prepared.project, name,
				new Path(output.getPath()), request.sealedTypes);
		transformer.setTests(request.tests);
		transformer.setLineDirectives(request.lineDirectives);
		if (request.runtime != null) {
			transformer.setRuntimeCpp(new File(request.runtime, "cpp"));
		}
		progress.checkCancelled();
		Files.write(new File(output, OUTPUT_MARKER).toPath(), ("Generated by J2C "
				+ version() + ". Translating into this directory again moves its "
				+ "current contents to a backup in the temporary folder.\n")
				.getBytes(StandardCharsets.UTF_8));
		transformer.process(progress.monitor(), prepared.units);

		List<String> generated = new ArrayList<String>();
		listFiles(output, "", generated);
		generated.remove(OUTPUT_MARKER);
		Collections.sort(generated);
		TranslateResult result = new TranslateResult(output, generated,
				prepared.units.length);
		result.projects = projects;
		return result;
	}

	private Prepared prepare(Request request, boolean writes, Progress progress)
			throws IOException {
		projects = Collections.emptyList();
		if (writes) {
			File output = request.output;
			if (output == null) {
				throw new IllegalArgumentException("Missing --output directory");
			}
			if (!output.isDirectory()) {
				throw new IllegalArgumentException(
						"Output directory must already exist: " + output);
			}
			requireInsideRoot(output, "Output directory");
			if (!request.force && !replaceable(output)) {
				// Translation moves an output directory's contents aside first
				throw new IllegalArgumentException("Output directory is not "
						+ "empty and was not created by J2C: " + output
						+ ". Translating moves its contents to a backup in the "
						+ "temporary folder; use --force to do so anyway.");
			}
		}

		if (request.inputs.isEmpty()) {
			throw new IllegalArgumentException("No Java input was provided");
		}
		for (File input : request.inputs) {
			if (!input.exists()) {
				throw new IllegalArgumentException("Input does not exist: "
						+ input);
			}
			if (!input.isDirectory() && !input.getName().endsWith(".java")) {
				throw new IllegalArgumentException(
						"Input must be a .java file or source directory: " + input);
			}
			requireInsideRoot(input, "Input");
		}

		// A detected build replaces its directory with its source roots
		List<File> inputs = new ArrayList<File>(request.inputs);
		List<File> projectRoots = new ArrayList<File>();
		List<File> classPaths = new ArrayList<File>(request.classPaths);
		projects = setUpProjects(request, inputs, projectRoots, classPaths,
				progress);

		List<File> sourcePaths = new ArrayList<File>(request.sourcePaths);
		if (sourcePaths.isEmpty()) {
			for (File root : projectRoots) {
				if (!containsPath(sourcePaths, root)) {
					sourcePaths.add(root);
				}
			}
			for (File input : inputs) {
				File sourcePath = input.isDirectory() ? input : inferSourcePath(
						input.getAbsoluteFile(), request.encoding);
				// A package folder inside a build's source root is not a root
				if (input.isDirectory() && isUnder(input, projectRoots)) {
					continue;
				}
				if (!containsPath(sourcePaths, sourcePath)) {
					sourcePaths.add(sourcePath);
				}
			}
		}
		for (File sourcePath : sourcePaths) {
			if (!sourcePath.isDirectory()) {
				throw new IllegalArgumentException(
						"Source path is not a directory: " + sourcePath);
			}
			requireInsideRoot(sourcePath, "Source path");
		}
		// Class path entries are only read, and usually live outside the
		// project (a Maven or Gradle cache), so they are not restricted
		for (File classPath : classPaths) {
			if (!classPath.exists()) {
				throw new IllegalArgumentException("Class path does not exist: "
						+ classPath);
			}
		}

		// A runtime is only read, like the class path, and replaces the
		// running JDK's library with its own java.* sources
		if (request.runtime != null) {
			File runtimeJava = new File(request.runtime, "java");
			if (!new File(runtimeJava, "java/lang/Object.java").isFile()) {
				throw new IllegalArgumentException("Not a J2C runtime (missing "
						+ "java/java/lang/Object.java): " + request.runtime);
			}
			sourcePaths.add(runtimeJava);
		}

		List<File> selectedFiles = new ArrayList<File>();
		for (File input : inputs) {
			if (input.isDirectory()) {
				collectSources(input, selectedFiles);
			} else {
				selectedFiles.add(input);
			}
		}
		if (selectedFiles.isEmpty()) {
			throw new IllegalArgumentException(
					"No Java source files found in the selected inputs");
		}

		SourceProject project = new SourceProject(sourcePaths, classPaths,
				request.encoding);
		project.setSystemLibrary(request.runtime == null);
		SourceUnit[] units = new SourceUnit[selectedFiles.size()];
		for (int i = 0; i < units.length; ++i) {
			units[i] = project.getUnit(selectedFiles.get(i));
		}
		return new Prepared(project, units, sourcePaths, classPaths);
	}

	/**
	 * Detects the builds the request selects and resolves their class paths.
	 * Input directories that are build roots are replaced in inputs by the
	 * build's source roots (and test roots with tests); every detected root is
	 * added to roots, and the class path to classPaths.
	 */
	private List<ProjectSetup.Project> setUpProjects(Request request,
			List<File> inputs, List<File> roots, List<File> classPaths,
			Progress progress)
			throws IOException {
		if (PROJECT_NONE.equals(request.project)) {
			return Collections.emptyList();
		}
		List<ProjectSetup.Project> detected = new ArrayList<ProjectSetup.Project>();
		if (PROJECT_AUTO.equals(request.project)) {
			for (int i = 0; i < inputs.size(); ++i) {
				File input = inputs.get(i);
				ProjectSetup.Project project = input.isDirectory() ? ProjectSetup
						.detect(input) : null;
				if (project == null) {
					continue;
				}
				detected.add(project);
				List<File> replacement = new ArrayList<File>(project.sourceRoots);
				if (request.tests) {
					replacement.addAll(project.testRoots);
				}
				if (replacement.isEmpty()) {
					throw new IllegalArgumentException(project.toolName()
							+ " project " + input + " has no Java source roots (such "
							+ "as src/main/java); pass the source folders instead, or "
							+ "use --project none");
				}
				inputs.remove(i);
				inputs.addAll(i, replacement);
				i += replacement.size() - 1;
			}
		} else {
			File directory = new File(request.project);
			requireInsideRoot(directory, "Project directory");
			ProjectSetup.Project project = directory.isDirectory() ? ProjectSetup
					.detect(directory) : null;
			if (project == null) {
				throw new IllegalArgumentException("No Maven or Gradle build "
						+ "(pom.xml, build.gradle or settings.gradle) in the project "
						+ "directory: " + directory);
			}
			detected.add(project);
		}

		for (ProjectSetup.Project project : detected) {
			ProjectSetup.resolve(project, request.tests, request.buildTool,
					System.err, progress);
			List<File> projectRoots = new ArrayList<File>(project.sourceRoots);
			if (request.tests) {
				projectRoots.addAll(project.testRoots);
			}
			for (File projectRoot : projectRoots) {
				if (!isInsideRoot(projectRoot)) {
					// A generated or shared source root outside the workspace
					System.err.println("J2C:   skipping source root outside the "
							+ "workspace root: " + projectRoot);
					inputs.remove(projectRoot);
				} else if (!containsPath(roots, projectRoot)) {
					roots.add(projectRoot);
				}
			}
			for (File entry : project.classPath) {
				if (!containsPath(classPaths, entry)) {
					classPaths.add(entry);
				}
			}
		}
		return Collections.unmodifiableList(detected);
	}

	private static boolean isUnder(File file, List<File> directories) {
		String path = ProjectSetup.normalize(file).getPath();
		for (File directory : directories) {
			String prefix = ProjectSetup.normalize(directory).getPath();
			if (path.equals(prefix) || path.startsWith(prefix + File.separator)) {
				return true;
			}
		}
		return false;
	}

	private boolean isInsideRoot(File file) throws IOException {
		try {
			requireInsideRoot(file, "Source root");
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	void requireInsideRoot(File file, String what) throws IOException {
		if (root == null) {
			return;
		}
		String rootPath = root.getCanonicalPath();
		String path = file.getCanonicalPath();
		if (!path.equals(rootPath)
				&& !path.startsWith(rootPath.endsWith(File.separator) ? rootPath
						: rootPath + File.separator)) {
			throw new IllegalArgumentException(what + " is outside the "
					+ "workspace root " + rootPath + ": " + file);
		}
	}

	private static boolean replaceable(File output) {
		String[] entries = output.list();
		return entries == null || entries.length == 0
				|| new File(output, OUTPUT_MARKER).isFile();
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
			} else {
				files.add(name);
			}
		}
	}

	private static boolean containsPath(List<File> paths, File candidate) {
		String absolute = candidate.getAbsolutePath();
		for (File path : paths) {
			if (absolute.equals(path.getAbsolutePath())) {
				return true;
			}
		}
		return false;
	}

	private static void collectSources(File directory, List<File> sources)
			throws IOException {
		File[] entries = directory.listFiles();
		if (entries == null) {
			throw new IOException("Could not read input directory " + directory);
		}
		for (File entry : entries) {
			if (entry.isDirectory()) {
				collectSources(entry, sources);
			} else if (entry.getName().endsWith(".java")
					&& !DECLARES_NO_TYPES.contains(entry.getName())) {
				sources.add(entry);
			}
		}
	}

	private static File inferSourcePath(File sourceFile, Charset encoding)
			throws IOException {
		Matcher packageName = Pattern.compile("(?m)^\\s*package\\s+([\\w.$]+)\\s*;")
				.matcher(new String(Files.readAllBytes(sourceFile.toPath()),
						encoding));
		if (!packageName.find()) {
			return sourceFile.getParentFile();
		}

		String[] segments = packageName.group(1).split("\\.");
		File root = sourceFile.getParentFile();
		for (int i = segments.length - 1; i >= 0; i--) {
			if (root == null || !root.getName().equals(segments[i])) {
				return sourceFile.getParentFile();
			}
			root = root.getParentFile();
		}
		return root == null ? sourceFile.getParentFile() : root;
	}

	static String version() {
		String version = J2cService.class.getPackage()
				.getImplementationVersion();
		return version == null ? "development build" : version;
	}

	// JSON shapes, shared by --format json and the MCP tools

	static Map<String, Object> header(String command) {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		json.put("schemaVersion", SCHEMA_VERSION);
		json.put("tool", "j2c");
		json.put("version", version());
		json.put("command", command);
		return json;
	}

	static Map<String, Object> checkJson(CheckResult result) {
		Map<String, Object> json = header("check");
		json.put("status", result.problems.isEmpty() ? "ok" : "problems");
		json.put("files", result.fileCount);
		json.put("problems", problemsJson(result.problems));
		json.put("summary", summaryJson(result.problems));
		putProjects(json, result.projects);
		return json;
	}

	static Map<String, Object> translateJson(TranslateResult result) {
		Map<String, Object> json = header("translate");
		json.put("status", "ok");
		json.put("files", result.fileCount);
		json.put("output", result.output.getPath());
		json.put("makefile", result.generatedFiles.contains("Makefile") ? "Makefile"
				: null);
		json.put("generatedFiles", result.generatedFiles);
		List<String> tests = new ArrayList<String>();
		for (String file : result.generatedFiles) {
			if (file.startsWith("test/") && file.endsWith("_test.cpp")) {
				tests.add(file);
			}
		}
		json.put("testFiles", tests);
		json.put("problems", Collections.emptyList());
		putProjects(json, result.projects);
		return json;
	}

	/** A failure report that also shows the builds detected on the way */
	static Map<String, Object> failedJson(String command, List<Problem> problems,
			List<ProjectSetup.Project> projects) {
		Map<String, Object> json = failedJson(command, problems);
		putProjects(json, projects);
		return json;
	}

	/** Detected builds, and warnings such as an unresolved class path */
	static void putProjects(Map<String, Object> json,
			List<ProjectSetup.Project> projects) {
		List<Object> list = new ArrayList<Object>();
		List<Problem> warnings = new ArrayList<Problem>();
		for (ProjectSetup.Project project : projects) {
			list.add(ProjectSetup.json(project));
			warnings.addAll(project.warnings);
		}
		json.put("projects", list);
		json.put("warnings", problemsJson(warnings, "warning"));
	}

	static Map<String, Object> failedJson(String command, List<Problem> problems) {
		Map<String, Object> json = header(command);
		json.put("status", "problems");
		json.put("problems", problemsJson(problems));
		json.put("summary", summaryJson(problems));
		return json;
	}

	static Map<String, Object> errorJson(String command, String status,
			String message) {
		Map<String, Object> json = header(command);
		json.put("status", status);
		json.put("message", message);
		return json;
	}

	static List<Object> problemsJson(List<Problem> problems) {
		return problemsJson(problems, "error");
	}

	static List<Object> problemsJson(List<Problem> problems,
			String severity) {
		List<Object> list = new ArrayList<Object>();
		for (Problem problem : problems) {
			Map<String, Object> json = new LinkedHashMap<String, Object>();
			json.put("id", problem.id);
			json.put("kind", problem.kind);
			json.put("severity", severity);
			json.put("file", problem.file.getPath());
			json.put("line", problem.line);
			json.put("column", problem.column);
			json.put("message", problem.message);
			json.put("hint", problem.hint());
			json.put("fixes", fixesJson(problem.fixes));
			list.add(json);
		}
		return list;
	}

	static List<Object> fixesJson(List<Fix> fixes) {
		List<Object> list = new ArrayList<Object>();
		for (Fix fix : fixes) {
			Map<String, Object> json = new LinkedHashMap<String, Object>();
			json.put("title", fix.title);
			List<Object> edits = new ArrayList<Object>();
			for (SourceEdit edit : fix.edits) {
				edits.add(editJson(edit));
			}
			json.put("edits", edits);
			list.add(json);
		}
		return list;
	}

	static Map<String, Object> editJson(SourceEdit edit) {
		Map<String, Object> range = new LinkedHashMap<String, Object>();
		range.put("start", position(edit.startLine, edit.startColumn));
		range.put("end", position(edit.endLine, edit.endColumn));
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		json.put("file", edit.file.getPath());
		json.put("range", range);
		json.put("newText", edit.newText);
		return json;
	}

	private static Map<String, Object> position(int line, int column) {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		json.put("line", line);
		json.put("column", column);
		return json;
	}

	static Map<String, Object> summaryJson(List<Problem> problems) {
		Map<String, Integer> byKind = new TreeMap<String, Integer>();
		List<File> files = new ArrayList<File>();
		for (Problem problem : problems) {
			Integer count = byKind.get(problem.kind);
			byKind.put(problem.kind, count == null ? 1 : count + 1);
			if (!files.contains(problem.file)) {
				files.add(problem.file);
			}
		}
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		json.put("problemCount", problems.size());
		json.put("filesWithProblems", files.size());
		json.put("byKind", byKind);
		return json;
	}

	static Map<String, Object> explainJson(String id) {
		Map<String, Object> json = header("explain");
		List<Object> entries = new ArrayList<Object>();
		for (ProblemCatalog.Entry entry : ProblemCatalog.all()) {
			if (id == null || entry.id.equals(id)) {
				Map<String, Object> item = new LinkedHashMap<String, Object>();
				item.put("id", entry.id);
				item.put("title", entry.title);
				item.put("explanation", entry.explanation);
				item.put("hint", entry.hint);
				entries.add(item);
			}
		}
		if (id != null && entries.isEmpty()) {
			throw new IllegalArgumentException("Unknown problem ID: " + id
					+ " (run j2c explain to list them)");
		}
		json.put("problems", entries);
		return json;
	}

	static Map<String, Object> capabilitiesJson() {
		Map<String, Object> json = header("capabilities");
		json.put("javaSourceLevel", "17");
		json.put("cppStandard", "c++20");
		json.put("commands", Arrays.asList("translate", "check", "fix", "report",
				"explain", "capabilities", "mcp"));
		json.put("buildTools", Arrays.asList(ProjectSetup.MAVEN,
				ProjectSetup.GRADLE));
		json.put("supportedFeatures", Arrays.asList(
				"Java 1-7 language features handled by the original J2C translator",
				"Lambda expressions and method references (rewritten into anonymous classes)",
				"Pattern-matching instanceof in any expression position, with flow scoping",
				"Switch expressions and statements, arrow and colon form, on primitive, boxed, enum and string selectors",
				"Records, including object, array and nested-type components",
				"Sealed and non-sealed types (advisory inheritance, or rejected with --sealed-types reject)",
				"Text blocks",
				"Try-with-resources, including try (existingResource)",
				"Default, static and private interface methods",
				"Java integer arithmetic semantics (division by zero, overflow, shifts, saturating casts)",
				"JUnit 4 and 5 tests run with GoogleTest (--tests): lifecycle methods, "
						+ "disabled tests and expected exceptions",
				"Maven and Gradle project auto-setup: a project directory as input "
						+ "uses the build's source and test roots, and the dependency "
						+ "class path from mvn or gradle (--project, --no-build-tool)",
				"Mechanical fixes for some problems, as exact edits to the Java source "
						+ "(fixes in JSON output, j2c fix, the j2c_fix tool)",
				"A migration readiness report: translatable files, types and methods, "
						+ "blockers by problem id and package, and JDK API use compared "
						+ "with the J2C runtime (j2c report, the j2c_report tool)",
				"MCP prompts (j2c-migrate, j2c-assess) and resources (the problem "
						+ "catalog, the runtime contract, translated files)"));
		json.put("limitations", Arrays.asList(
				"Generated programs need a C++ implementation of the JDK classes they use; "
						+ "JDK classes are generated as stubs that call unimplemented_()",
				"Generated objects are never freed unless the program is built with "
						+ "make J2C_GC=boehm, which links the Boehm garbage collector",
				"Lambdas that use this inside an anonymous class are not translated; "
						+ "j2c fix rewrites most of them to use a local variable",
				"instanceof patterns in field initializers are not translated; "
						+ "j2c fix moves the initializer into a method",
				"Exceptions from close() during exception unwinding terminate the program",
				"In translated tests, assertEquals on JDK objects (strings, boxed numbers, "
						+ "collections) and custom assertion messages need a Java runtime "
						+ "(--runtime runtime); mocking libraries are not supported"));
		List<String> ids = new ArrayList<String>();
		for (ProblemCatalog.Entry entry : ProblemCatalog.all()) {
			ids.add(entry.id);
		}
		json.put("problemIds", ids);
		return json;
	}
}
