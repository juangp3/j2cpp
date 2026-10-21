package se.arnetheduck.j2c.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.core.runtime.OperationCanceledException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import se.arnetheduck.j2c.transform.Problem;
import se.arnetheduck.j2c.transform.ProblemCatalog;

/**
 * Project auto-setup: finds a Maven or Gradle build in a directory, reads its
 * source and test roots, and asks the build tool (the project's wrapper when
 * it has one) for the dependency class path. Resolved class paths are cached
 * by a hash of the build files. A missing or failing build tool is reported
 * as a warning, never as an error: the source roots are still used.
 */
final class ProjectSetup {
	static final String MAVEN = "maven";
	static final String GRADLE = "gradle";
	/** The Maven goal that prints a module's class path */
	private static final String MAVEN_CLASSPATH_GOAL = "org.apache.maven.plugins:"
			+ "maven-dependency-plugin:3.7.0:build-classpath";
	private static final String GRADLE_TASK = "j2cProjectInfo";
	/** Prints each Java project's source directories and class paths */
	private static final String GRADLE_INIT_SCRIPT = "// Written by J2C for project auto-setup\n"
			+ "allprojects { p ->\n"
			+ "\tp.tasks.register('" + GRADLE_TASK + "') { task ->\n"
			+ "\t\tif (task.metaClass.respondsTo(task, 'notCompatibleWithConfigurationCache', String)) {\n"
			+ "\t\t\ttask.notCompatibleWithConfigurationCache('reads the project model')\n"
			+ "\t\t}\n"
			+ "\t\ttask.doLast {\n"
			+ "\t\t\tdef sourceSets = p.extensions.findByName('sourceSets')\n"
			+ "\t\t\tif (sourceSets == null) {\n"
			+ "\t\t\t\treturn\n"
			+ "\t\t\t}\n"
			+ "\t\t\t[main: 'compileClasspath', test: 'testCompileClasspath'].each { name, configuration ->\n"
			+ "\t\t\t\tdef sourceSet = sourceSets.findByName(name)\n"
			+ "\t\t\t\tif (sourceSet != null) {\n"
			+ "\t\t\t\t\tsourceSet.java.srcDirs.each { println \"j2c-${name}-source=${it.absolutePath}\" }\n"
			+ "\t\t\t\t}\n"
			+ "\t\t\t\tdef classpath = p.configurations.findByName(configuration)\n"
			+ "\t\t\t\tif (classpath != null && classpath.canBeResolved) {\n"
			+ "\t\t\t\t\tclasspath.incoming.artifactView { lenient = true }.files.each {\n"
			+ "\t\t\t\t\t\tprintln \"j2c-${name}-classpath=${it.absolutePath}\"\n"
			+ "\t\t\t\t\t}\n"
			+ "\t\t\t\t}\n"
			+ "\t\t\t}\n"
			+ "\t\t}\n"
			+ "\t}\n"
			+ "}\n";
	private static final long TIMEOUT_MINUTES = 15;
	private static final boolean WINDOWS = File.separatorChar == '\\';
	private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)\\}");
	private static final Pattern QUOTED = Pattern.compile("['\"]([^'\"]+)['\"]");
	private static final Pattern GRADLE_PROJECT_DIR = Pattern.compile(
			"project\\(\\s*['\"]([^'\"]+)['\"]\\s*\\)\\.projectDir\\s*=\\s*"
					+ "(?:new\\s+File\\(\\s*(?:settingsDir|rootDir)\\s*,\\s*|file\\(\\s*)"
					+ "['\"]([^'\"]+)['\"]");

	/** A detected build, and what was resolved from it */
	static final class Project {
		final String buildTool;
		final File root;
		final File buildFile;
		/** Module or subproject directories relative to root; "." is root */
		final List<String> modules = new ArrayList<String>();
		/** Existing source roots */
		final List<File> sourceRoots = new ArrayList<File>();
		final List<File> testRoots = new ArrayList<File>();
		final List<File> classPath = new ArrayList<File>();
		/** maven, gradle, cache, none (nothing to resolve) or unresolved */
		String classPathSource = "unresolved";
		/** The build tool command that was run, if any */
		String command;
		final List<Problem> warnings = new ArrayList<Problem>();

		/** Every file whose contents decide the class path */
		final List<File> buildFiles = new ArrayList<File>();
		/** Maven: groupId:artifactId of each reactor module */
		final Set<String> reactor = new LinkedHashSet<String>();
		/** Maven: local repository path fragments of reactor artifacts */
		final List<String> reactorPaths = new ArrayList<String>();
		/** Maven: groupId:artifactId of every declared dependency and parent */
		final Set<String> references = new LinkedHashSet<String>();
		/** Directories holding build output, whose contents are not dependencies */
		final List<File> outputDirectories = new ArrayList<File>();

		Project(String buildTool, File root, File buildFile) {
			this.buildTool = buildTool;
			this.root = root;
			this.buildFile = buildFile;
		}

		String toolName() {
			return MAVEN.equals(buildTool) ? "Maven" : "Gradle";
		}
	}

	private ProjectSetup() {
	}

	/** The build in directory, or null when it has none */
	static Project detect(File directory) throws IOException {
		File root = normalize(directory);
		File pom = new File(root, "pom.xml");
		if (pom.isFile()) {
			return maven(root, pom);
		}
		File settings = firstFile(root, "settings.gradle", "settings.gradle.kts");
		File build = firstFile(root, "build.gradle", "build.gradle.kts");
		if (settings != null || build != null) {
			return gradle(root, settings != null ? settings : build);
		}
		return null;
	}

	// Maven

	private static Project maven(File root, File pom) throws IOException {
		Project project = new Project(MAVEN, root, pom);
		List<File> main = new ArrayList<File>();
		List<File> test = new ArrayList<File>();
		readPom(project, root, pom, new HashMap<String, String>(), main, test,
				new LinkedHashSet<String>());
		addExisting(project.sourceRoots, main);
		addExisting(project.testRoots, test);
		return project;
	}

	private static void readPom(Project project, File directory, File pom,
			Map<String, String> inherited, List<File> main, List<File> test,
			Set<String> visited) throws IOException {
		if (!visited.add(pom.getCanonicalPath())) {
			return;
		}
		Element element;
		try {
			element = parseXml(pom).getDocumentElement();
		} catch (Exception e) {
			throw new IllegalArgumentException("Could not read " + pom + ": "
					+ e.getMessage());
		}
		project.buildFiles.add(pom);
		project.modules.add(relative(project.root, directory));
		project.outputDirectories.add(new File(directory, "target"));

		Map<String, String> properties = new HashMap<String, String>(inherited);
		Element propertiesElement = child(element, "properties");
		if (propertiesElement != null) {
			for (Element property : children(propertiesElement, null)) {
				properties.put(name(property), property.getTextContent().trim());
			}
		}
		Element parent = child(element, "parent");
		String groupId = text(element, "groupId");
		String version = text(element, "version");
		if (parent != null) {
			if (groupId == null) {
				groupId = text(parent, "groupId");
			}
			if (version == null) {
				version = text(parent, "version");
			}
		}
		String artifactId = text(element, "artifactId");
		properties.put("project.groupId", String.valueOf(groupId));
		properties.put("project.artifactId", String.valueOf(artifactId));
		properties.put("project.version", String.valueOf(version));
		properties.put("project.basedir", directory.getPath());
		properties.put("basedir", directory.getPath());
		properties.put("project.build.directory",
				new File(directory, "target").getPath());
		if (parent != null) {
			properties.put("project.parent.version", String.valueOf(text(parent,
					"version")));
			project.references.add(interpolate(text(parent, "groupId"), properties)
					+ ":" + interpolate(text(parent, "artifactId"), properties));
		}

		groupId = interpolate(groupId, properties);
		artifactId = interpolate(artifactId, properties);
		version = interpolate(version, properties);
		project.reactor.add(groupId + ":" + artifactId);
		if (groupId != null && artifactId != null && version != null) {
			project.reactorPaths.add("/" + groupId.replace('.', '/') + "/"
					+ artifactId + "/" + version + "/");
		}

		String packaging = text(element, "packaging");
		Element build = child(element, "build");
		if (!"pom".equals(packaging)) {
			main.add(path(directory, interpolate(orDefault(text(build,
					"sourceDirectory"), "src/main/java"), properties)));
			test.add(path(directory, interpolate(orDefault(text(build,
					"testSourceDirectory"), "src/test/java"), properties)));
			addBuildHelperSources(build, directory, properties, main, test);
		}

		Element dependencies = child(element, "dependencies");
		if (dependencies != null) {
			for (Element dependency : children(dependencies, "dependency")) {
				project.references.add(interpolate(text(dependency, "groupId"),
						properties) + ":" + interpolate(text(dependency,
						"artifactId"), properties));
			}
		}

		Element modules = child(element, "modules");
		if (modules != null) {
			for (Element module : children(modules, "module")) {
				File location = path(directory, interpolate(module
						.getTextContent().trim(), properties));
				File modulePom = location.isFile() ? location : new File(location,
						"pom.xml");
				if (modulePom.isFile()) {
					readPom(project, modulePom.getParentFile(), modulePom,
							properties, main, test, visited);
				}
			}
		}
	}

	/** Source roots added with build-helper-maven-plugin's add-source goals */
	private static void addBuildHelperSources(Element build, File directory,
			Map<String, String> properties, List<File> main, List<File> test) {
		Element plugins = child(build, "plugins");
		if (plugins == null) {
			return;
		}
		for (Element plugin : children(plugins, "plugin")) {
			if (!"build-helper-maven-plugin".equals(text(plugin, "artifactId"))) {
				continue;
			}
			Element executions = child(plugin, "executions");
			if (executions == null) {
				continue;
			}
			for (Element execution : children(executions, "execution")) {
				Element configuration = child(execution, "configuration");
				Element sources = child(configuration != null ? configuration
						: child(plugin, "configuration"), "sources");
				Element goals = child(execution, "goals");
				if (sources == null || goals == null) {
					continue;
				}
				for (Element goal : children(goals, "goal")) {
					String name = goal.getTextContent().trim();
					List<File> roots = "add-source".equals(name) ? main
							: "add-test-source".equals(name) ? test : null;
					if (roots == null) {
						continue;
					}
					for (Element source : children(sources, "source")) {
						roots.add(path(directory, interpolate(source
								.getTextContent().trim(), properties)));
					}
				}
			}
		}
	}

	private static List<String> mavenCommand(Project project, boolean tests) {
		List<String> command = executable(project.root, "mvnw", "mvn");
		if (command == null) {
			return null;
		}
		command.add("-B");
		command.add("-fae");
		command.add("-Dstyle.color=never");
		if (hasReactorDependencies(project)) {
			// Maven resolves a module's dependency on another module of the
			// build only when the compile phase runs; compiling is skipped
			command.add("-Dmaven.main.skip=true");
			command.add("-Dmaven.resources.skip=true");
			command.add("compile");
		}
		command.add(MAVEN_CLASSPATH_GOAL);
		command.add("-DincludeScope=" + (tests ? "test" : "compile"));
		return command;
	}

	private static boolean hasReactorDependencies(Project project) {
		if (project.reactor.size() < 2) {
			return false;
		}
		for (String reference : project.references) {
			if (project.reactor.contains(reference)) {
				return true;
			}
		}
		return false;
	}

	/** Whether anything outside the build can contribute dependencies */
	private static boolean needsMaven(Project project) {
		for (String reference : project.references) {
			if (!project.reactor.contains(reference)) {
				return true;
			}
		}
		return false;
	}

	/** Each module's class path, printed after "Dependencies classpath:" */
	private static List<String> parseMaven(List<String> lines) {
		List<String> entries = new ArrayList<String>();
		for (int i = 0; i + 1 < lines.size(); ++i) {
			if (!lines.get(i).endsWith("Dependencies classpath:")) {
				continue;
			}
			String next = lines.get(i + 1).trim();
			if (next.isEmpty() || next.startsWith("[")) {
				continue;
			}
			for (String entry : next.split(Pattern.quote(File.pathSeparator))) {
				if (!entry.isEmpty()) {
					entries.add(entry);
				}
			}
		}
		return entries;
	}

	// Gradle

	private static Project gradle(File root, File buildFile) throws IOException {
		Project project = new Project(GRADLE, root, buildFile);
		List<File> directories = new ArrayList<File>();
		directories.add(root);
		File settings = firstFile(root, "settings.gradle", "settings.gradle.kts");
		if (settings != null) {
			project.buildFiles.add(settings);
			String text = new String(Files.readAllBytes(settings.toPath()),
					StandardCharsets.UTF_8);
			Map<String, String> projectDirectories = new HashMap<String, String>();
			Matcher projectDirectory = GRADLE_PROJECT_DIR.matcher(text);
			while (projectDirectory.find()) {
				projectDirectories.put(gradlePath(projectDirectory.group(1)),
						projectDirectory.group(2));
			}
			for (String include : gradleIncludes(text)) {
				String path = gradlePath(include);
				String location = projectDirectories.get(path);
				directories.add(path(root, location != null ? location : path
						.replace(':', '/')));
			}
		}
		for (String name : new String[] { "gradle.properties",
				"gradle/libs.versions.toml" }) {
			File file = new File(root, name);
			if (file.isFile()) {
				project.buildFiles.add(file);
			}
		}
		List<File> main = new ArrayList<File>();
		List<File> test = new ArrayList<File>();
		for (File directory : directories) {
			project.modules.add(relative(root, directory));
			project.outputDirectories.add(new File(directory, "build"));
			File build = firstFile(directory, "build.gradle", "build.gradle.kts");
			if (build != null) {
				project.buildFiles.add(build);
			}
			main.add(new File(directory, "src/main/java"));
			test.add(new File(directory, "src/test/java"));
		}
		addExisting(project.sourceRoots, main);
		addExisting(project.testRoots, test);
		return project;
	}

	/** The project paths in include statements, which may span lines */
	private static List<String> gradleIncludes(String settings) {
		List<String> includes = new ArrayList<String>();
		Matcher include = Pattern.compile("(?m)^\\s*include\\b").matcher(settings);
		while (include.find()) {
			int end = settings.indexOf('\n', include.end());
			String statement = settings.substring(include.end(), end < 0 ? settings
					.length() : end);
			if (statement.trim().startsWith("(") && !statement.contains(")")) {
				int close = settings.indexOf(')', include.end());
				statement = settings.substring(include.end(), close < 0 ? settings
						.length() : close);
			}
			Matcher quoted = QUOTED.matcher(statement);
			while (quoted.find()) {
				includes.add(quoted.group(1));
			}
		}
		return includes;
	}

	private static String gradlePath(String path) {
		return path.startsWith(":") ? path.substring(1) : path;
	}

	private static List<String> gradleCommand(Project project, File initScript,
			boolean offline) {
		List<String> command = executable(project.root, "gradlew", "gradle");
		if (command == null) {
			return null;
		}
		command.add("-q");
		command.add("--console=plain");
		if (offline) {
			command.add("--offline");
		}
		command.add("--init-script");
		command.add(initScript.getPath());
		command.add(GRADLE_TASK);
		return command;
	}

	// Resolution

	/**
	 * Fills in the project's class path, from the cache or by running its
	 * build tool when runTool is set. Never throws: problems become warnings.
	 */
	static void resolve(Project project, boolean tests, boolean runTool,
			PrintStream log) {
		resolve(project, tests, runTool, log, Progress.NONE);
	}

	/**
	 * Like resolve, reporting the build tool run as a phase of progress.
	 * Cancelling stops the build tool and throws OperationCanceledException.
	 */
	static void resolve(Project project, boolean tests, boolean runTool,
			PrintStream log, Progress progress) {
		log.println("J2C: " + project.toolName() + " project in " + project.root
				+ (project.modules.size() > 1 ? " (" + (project.modules.size() - 1)
						+ " module(s))" : ""));
		if (MAVEN.equals(project.buildTool) && !needsMaven(project)) {
			project.classPathSource = "none";
			logRoots(project, tests, log);
			log.println("J2C:   no dependencies to resolve");
			return;
		}

		File cacheFile = cacheFile(project, tests);
		if (cacheFile != null && readCache(project, cacheFile, tests)) {
			project.classPathSource = "cache";
			logRoots(project, tests, log);
			log.println("J2C:   class path: " + entries(project) + ", cached in "
					+ cacheFile);
			return;
		}
		if (!runTool) {
			logRoots(project, tests, log);
			warn(project, log, "The dependency class path was not resolved because "
					+ "running the build tool was turned off (--no-build-tool, or "
					+ "buildTool: false in MCP), and no "
					+ "cached class path matches the build files. Types from "
					+ "dependencies will not resolve.");
			return;
		}

		try {
			if (MAVEN.equals(project.buildTool)) {
				runMaven(project, tests, log, progress);
			} else {
				runGradle(project, tests, log, progress);
			}
		} catch (OperationCanceledException e) {
			throw e;
		} catch (Exception e) {
			warn(project, log, project.toolName() + " could not be run to resolve "
					+ "the class path: " + e.getMessage());
		}
		logRoots(project, tests, log);
		if (!"unresolved".equals(project.classPathSource)) {
			log.println("J2C:   class path: " + entries(project));
			if (cacheFile != null && project.warnings.isEmpty()) {
				writeCache(project, cacheFile, tests, log);
			}
		}
	}

	private static String entries(Project project) {
		int count = project.classPath.size();
		return count + (count == 1 ? " entry" : " entries");
	}

	private static void runMaven(Project project, boolean tests, PrintStream log,
			Progress progress)
			throws IOException, InterruptedException {
		List<String> command = mavenCommand(project, tests);
		if (command == null) {
			warn(project, log, "Maven was not found: the project has no Maven wrapper "
					+ "(mvnw) and mvn is not on the PATH, so dependency JARs are not "
					+ "on the class path. Types from dependencies will not resolve.");
			return;
		}
		Run run = run(project, command, log, progress);
		List<String> entries = parseMaven(run.lines);
		if (run.status != 0 && entries.isEmpty()) {
			warn(project, log, failure(project, run));
			return;
		}
		setClassPath(project, entries);
		project.classPathSource = MAVEN;
		if (run.status != 0) {
			warn(project, log, failure(project, run) + " The class path of the "
					+ "modules that did resolve is used.");
		}
	}

	private static void runGradle(Project project, boolean tests, PrintStream log,
			Progress progress)
			throws IOException, InterruptedException {
		File initScript = File.createTempFile("j2c-init", ".gradle");
		try {
			Files.write(initScript.toPath(), GRADLE_INIT_SCRIPT
					.getBytes(StandardCharsets.UTF_8));
			List<String> command = gradleCommand(project, initScript, false);
			if (command == null) {
				warn(project, log, "Gradle was not found: the project has no Gradle "
						+ "wrapper (gradlew) and gradle is not on the PATH, so "
						+ "dependency JARs are not on the class path. Types from "
						+ "dependencies will not resolve.");
				return;
			}
			Run run = run(project, command, log, progress);
			if (run.status != 0) {
				log.println("J2C:   " + project.toolName() + " failed; retrying offline");
				run = run(project, gradleCommand(project, initScript, true), log,
						progress);
			}
			if (run.status != 0) {
				warn(project, log, failure(project, run));
				return;
			}
			applyGradle(project, run.lines, tests);
			project.classPathSource = GRADLE;
		} finally {
			Files.deleteIfExists(initScript.toPath());
		}
	}

	private static void applyGradle(Project project, List<String> lines,
			boolean tests) {
		List<File> main = new ArrayList<File>();
		List<File> test = new ArrayList<File>();
		List<String> classPath = new ArrayList<String>();
		for (String line : lines) {
			String trimmed = line.trim();
			int equals = trimmed.indexOf('=');
			if (!trimmed.startsWith("j2c-") || equals < 0) {
				continue;
			}
			String key = trimmed.substring(0, equals);
			String value = trimmed.substring(equals + 1);
			if ("j2c-main-source".equals(key)) {
				main.add(normalize(new File(value)));
			} else if ("j2c-test-source".equals(key)) {
				test.add(normalize(new File(value)));
			} else if ("j2c-main-classpath".equals(key)
					|| (tests && "j2c-test-classpath".equals(key))) {
				classPath.add(value);
			}
		}
		if (!main.isEmpty() || !test.isEmpty()) {
			project.sourceRoots.clear();
			project.testRoots.clear();
			addExisting(project.sourceRoots, main);
			addExisting(project.testRoots, test);
		}
		setClassPath(project, classPath);
	}

	/**
	 * The class path without the build's own output (other modules' classes
	 * and JARs, whose sources are translated instead), each entry once
	 */
	private static void setClassPath(Project project, List<String> entries) {
		Set<String> seen = new LinkedHashSet<String>();
		project.classPath.clear();
		for (String entry : entries) {
			File file = normalize(new File(entry));
			if (!file.exists() || !seen.add(file.getPath()) || isBuildOutput(project,
					file)) {
				continue;
			}
			project.classPath.add(file);
		}
	}

	private static boolean isBuildOutput(Project project, File file) {
		String path = file.getPath();
		if (file.isDirectory() && isInside(path, project.root.getPath())) {
			return true;
		}
		for (File output : project.outputDirectories) {
			if (isInside(path, output.getPath())) {
				return true;
			}
		}
		String slashed = path.replace('\\', '/');
		for (String reactorPath : project.reactorPaths) {
			if (slashed.contains(reactorPath)) {
				return true;
			}
		}
		return false;
	}

	private static String failure(Project project, Run run) {
		String detail = null;
		for (String line : run.lines) {
			if (line.startsWith("[ERROR]") && line.length() > 8) {
				detail = line.substring(7).trim();
				break;
			}
		}
		if (detail == null) {
			for (int i = run.lines.size() - 1; i >= 0 && detail == null; --i) {
				if (!run.lines.get(i).trim().isEmpty()) {
					detail = run.lines.get(i).trim();
				}
			}
		}
		return project.toolName() + " failed to resolve the class path ("
				+ (run.timedOut ? "timed out" : "exit status " + run.status) + ")"
				+ (detail == null ? "." : ": " + detail + ".")
				+ " Types from dependencies will not resolve.";
	}

	private static void warn(Project project, PrintStream log, String message) {
		project.classPathSource = "unresolved";
		Problem warning = Problem.of(ProblemCatalog.PROJECT_CLASSPATH_UNRESOLVED,
				"project setup", message).at(project.buildFile, 1, 0);
		project.warnings.add(warning);
		log.println("J2C: warning: " + project.buildFile + ": " + message + " ["
				+ warning.id + "]");
	}

	private static void logRoots(Project project, boolean tests, PrintStream log) {
		log.println("J2C:   source roots: " + relativeList(project,
				project.sourceRoots));
		if (tests) {
			log.println("J2C:   test roots: " + relativeList(project,
					project.testRoots));
		}
	}

	private static String relativeList(Project project, List<File> files) {
		if (files.isEmpty()) {
			return "(none)";
		}
		StringBuilder text = new StringBuilder();
		for (File file : files) {
			if (text.length() > 0) {
				text.append(", ");
			}
			text.append(relative(project.root, file));
		}
		return text.toString();
	}

	// Running the build tool

	private static final class Run {
		int status;
		boolean timedOut;
		List<String> lines = new ArrayList<String>();
	}

	private static Run run(Project project, List<String> command, PrintStream log,
			Progress progress)
			throws IOException, InterruptedException {
		project.command = join(command);
		log.println("J2C:   resolving the class path: " + project.command
				+ " (--no-build-tool skips this)");
		progress.phase("Resolving the class path with " + project.toolName()
				+ " in " + project.root);
		ProcessBuilder builder = new ProcessBuilder(command);
		builder.directory(project.root);
		builder.redirectErrorStream(true);
		final Process process = builder.start();
		// The build tool must not read J2C's stdin, which carries MCP messages
		process.getOutputStream().close();
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		Thread reader = new Thread(new Runnable() {
			@Override
			public void run() {
				byte[] buffer = new byte[8192];
				try {
					InputStream in = process.getInputStream();
					for (int count = in.read(buffer); count >= 0; count = in
							.read(buffer)) {
						synchronized (output) {
							output.write(buffer, 0, count);
						}
					}
				} catch (IOException e) {
					// The process ended
				}
			}
		});
		reader.setDaemon(true);
		reader.start();
		Run run = new Run();
		long deadline = System.nanoTime()
				+ TimeUnit.MINUTES.toNanos(TIMEOUT_MINUTES);
		boolean exited = false;
		while (!exited && !progress.cancelled()
				&& System.nanoTime() < deadline) {
			exited = process.waitFor(100, TimeUnit.MILLISECONDS);
		}
		if (!exited) {
			run.timedOut = true;
			Iterator<ProcessHandle> descendants = process.descendants().iterator();
			while (descendants.hasNext()) {
				descendants.next().destroyForcibly();
			}
			process.destroyForcibly();
			process.waitFor(10, TimeUnit.SECONDS);
			// Stopped for a cancelled request, not timed out
			progress.checkCancelled();
		}
		reader.join(10000);
		run.status = run.timedOut ? -1 : process.exitValue();
		String text;
		synchronized (output) {
			text = new String(output.toByteArray(), Charset.defaultCharset());
		}
		for (String line : text.split("\r?\n")) {
			run.lines.add(line);
		}
		if (run.status != 0) {
			// The tool's output is shown only when it fails
			for (int i = Math.max(0, run.lines.size() - 20); i < run.lines.size(); ++i) {
				log.println("J2C:   | " + run.lines.get(i));
			}
		}
		return run;
	}

	/**
	 * The command that starts the project's wrapper script, else the tool on
	 * the PATH, or null when neither exists
	 */
	private static List<String> executable(File root, String wrapper, String tool) {
		List<String> command = new ArrayList<String>();
		File script = new File(root, WINDOWS ? wrapper + ("mvnw".equals(wrapper)
				? ".cmd" : ".bat") : wrapper);
		if (script.isFile()) {
			if (!WINDOWS && !script.canExecute()) {
				// Checked out without its executable bit
				command.add("sh");
			}
			command.add(script.getAbsolutePath());
			return command;
		}
		String path = System.getenv("PATH");
		if (path == null) {
			return null;
		}
		List<String> extensions = new ArrayList<String>();
		if (WINDOWS) {
			String pathExt = System.getenv("PATHEXT");
			for (String extension : (pathExt == null ? ".COM;.EXE;.BAT;.CMD"
					: pathExt).split(";")) {
				if (!extension.isEmpty()) {
					extensions.add(extension.toLowerCase());
				}
			}
		} else {
			extensions.add("");
		}
		for (String directory : path.split(Pattern.quote(File.pathSeparator))) {
			if (directory.isEmpty()) {
				continue;
			}
			for (String extension : extensions) {
				File candidate = new File(directory, tool + extension);
				if (candidate.isFile() && (WINDOWS || candidate.canExecute())) {
					command.add(candidate.getAbsolutePath());
					return command;
				}
			}
		}
		return null;
	}

	// The cache

	/**
	 * J2C's cache directory: J2C_CACHE_DIR, else ~/.j2c/cache. It holds the
	 * resolved class paths and the extracted bundled runtime.
	 */
	static File cacheDirectory() {
		String configured = System.getenv("J2C_CACHE_DIR");
		return configured != null && !configured.isEmpty() ? new File(
				configured) : new File(System.getProperty("user.home"), ".j2c"
				+ File.separator + "cache");
	}

	/** The cache file for the project's current build files, or null */
	private static File cacheFile(Project project, boolean tests) {
		File directory = cacheDirectory();
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			update(digest, "j2c-classpath-1\n" + project.buildTool + "\n"
					+ project.root.getPath() + "\n" + (tests ? "test" : "main") + "\n");
			List<File> files = new ArrayList<File>(project.buildFiles);
			Collections.sort(files, new Comparator<File>() {
				@Override
				public int compare(File a, File b) {
					return a.getPath().compareTo(b.getPath());
				}
			});
			for (File file : files) {
				update(digest, relative(project.root, file) + "\n");
				digest.update(Files.readAllBytes(file.toPath()));
			}
			StringBuilder name = new StringBuilder("classpath-");
			byte[] hash = digest.digest();
			for (int i = 0; i < 16; ++i) {
				name.append(String.format("%02x", hash[i] & 0xff));
			}
			return new File(directory, name.append(".json").toString());
		} catch (NoSuchAlgorithmException | IOException e) {
			return null;
		}
	}

	private static void update(MessageDigest digest, String text) {
		digest.update(text.getBytes(StandardCharsets.UTF_8));
	}

	private static boolean readCache(Project project, File cacheFile,
			boolean tests) {
		if (!cacheFile.isFile()) {
			return false;
		}
		try {
			Object parsed = Json.parse(new String(Files.readAllBytes(cacheFile
					.toPath()), StandardCharsets.UTF_8));
			if (!(parsed instanceof Map)) {
				return false;
			}
			Map<?, ?> cache = (Map<?, ?>) parsed;
			List<File> classPath = files(cache.get("classPath"));
			for (File entry : classPath) {
				if (!entry.exists()) {
					// A dependency was deleted, for example from ~/.m2
					return false;
				}
			}
			List<File> main = files(cache.get("sourceRoots"));
			List<File> test = files(cache.get("testRoots"));
			if (!main.isEmpty() || !test.isEmpty()) {
				project.sourceRoots.clear();
				project.testRoots.clear();
				addExisting(project.sourceRoots, main);
				addExisting(project.testRoots, test);
			}
			project.classPath.clear();
			project.classPath.addAll(classPath);
			Object command = cache.get("command");
			project.command = command instanceof String ? (String) command : null;
			return true;
		} catch (IOException | IllegalArgumentException e) {
			return false;
		}
	}

	private static void writeCache(Project project, File cacheFile,
			boolean tests, PrintStream log) {
		Map<String, Object> cache = new LinkedHashMap<String, Object>();
		cache.put("buildTool", project.buildTool);
		cache.put("root", project.root.getPath());
		cache.put("scope", tests ? "test" : "main");
		cache.put("command", project.command);
		// Gradle reports its source sets; Maven's are read from the POMs
		boolean toolRoots = GRADLE.equals(project.buildTool);
		cache.put("sourceRoots", toolRoots ? paths(project.sourceRoots)
				: Collections.emptyList());
		cache.put("testRoots", toolRoots ? paths(project.testRoots)
				: Collections.emptyList());
		cache.put("classPath", paths(project.classPath));
		try {
			Files.createDirectories(cacheFile.getParentFile().toPath());
			Files.write(cacheFile.toPath(), (Json.write(cache) + "\n")
					.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			log.println("J2C:   could not cache the class path in " + cacheFile
					+ ": " + e.getMessage());
		}
	}

	private static List<File> files(Object value) {
		List<File> files = new ArrayList<File>();
		if (value instanceof List) {
			for (Object item : (List<?>) value) {
				if (item instanceof String) {
					files.add(new File((String) item));
				}
			}
		}
		return files;
	}

	static List<String> paths(List<File> files) {
		List<String> paths = new ArrayList<String>();
		for (File file : files) {
			paths.add(file.getPath());
		}
		return paths;
	}

	/** The detection result as reported in JSON output */
	static Map<String, Object> json(Project project) {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		json.put("buildTool", project.buildTool);
		json.put("root", project.root.getPath());
		json.put("buildFile", project.buildFile.getPath());
		json.put("modules", project.modules);
		json.put("sourceRoots", paths(project.sourceRoots));
		json.put("testRoots", paths(project.testRoots));
		json.put("classPath", paths(project.classPath));
		json.put("classPathSource", project.classPathSource);
		json.put("command", project.command);
		return json;
	}

	// Helpers

	private static Document parseXml(File file) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(false);
		factory.setExpandEntityReferences(false);
		try {
			factory.setFeature(
					"http://apache.org/xml/features/disallow-doctype-decl", true);
		} catch (Exception e) {
			// Not supported by this parser
		}
		DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse(file);
	}

	private static String name(Node node) {
		String name = node.getNodeName();
		int colon = name.indexOf(':');
		return colon < 0 ? name : name.substring(colon + 1);
	}

	/** The child elements named name, or all child elements when it is null */
	private static List<Element> children(Element parent, String name) {
		List<Element> elements = new ArrayList<Element>();
		if (parent == null) {
			return elements;
		}
		NodeList nodes = parent.getChildNodes();
		for (int i = 0; i < nodes.getLength(); ++i) {
			Node node = nodes.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE
					&& (name == null || name.equals(name(node)))) {
				elements.add((Element) node);
			}
		}
		return elements;
	}

	private static Element child(Element parent, String name) {
		List<Element> elements = children(parent, name);
		return elements.isEmpty() ? null : elements.get(0);
	}

	private static String text(Element parent, String name) {
		Element element = child(parent, name);
		if (element == null) {
			return null;
		}
		String text = element.getTextContent().trim();
		return text.isEmpty() ? null : text;
	}

	private static String orDefault(String value, String fallback) {
		return value == null ? fallback : value;
	}

	/** Replaces ${property} references; unknown ones are kept */
	private static String interpolate(String value, Map<String, String> properties) {
		if (value == null) {
			return null;
		}
		String result = value;
		for (int pass = 0; pass < 5 && result.contains("${"); ++pass) {
			Matcher matcher = PROPERTY.matcher(result);
			StringBuffer replaced = new StringBuffer();
			while (matcher.find()) {
				String property = properties.get(matcher.group(1));
				matcher.appendReplacement(replaced, Matcher.quoteReplacement(
						property != null ? property : matcher.group()));
			}
			matcher.appendTail(replaced);
			if (replaced.toString().equals(result)) {
				break;
			}
			result = replaced.toString();
		}
		return result;
	}

	private static File path(File directory, String path) {
		File file = new File(path);
		return normalize(file.isAbsolute() ? file : new File(directory, path));
	}

	static File normalize(File file) {
		return file.getAbsoluteFile().toPath().normalize().toFile();
	}

	private static File firstFile(File directory, String... names) {
		for (String name : names) {
			File file = new File(directory, name);
			if (file.isFile()) {
				return file;
			}
		}
		return null;
	}

	private static void addExisting(List<File> roots, List<File> candidates) {
		for (File candidate : candidates) {
			File root = normalize(candidate);
			if (root.isDirectory() && !roots.contains(root)) {
				roots.add(root);
			}
		}
	}

	private static boolean isInside(String path, String directory) {
		return path.equals(directory) || path.startsWith(directory.endsWith(
				File.separator) ? directory : directory + File.separator);
	}

	private static String relative(File root, File file) {
		String rootPath = root.getPath();
		String path = normalize(file).getPath();
		if (path.equals(rootPath)) {
			return ".";
		}
		if (isInside(path, rootPath)) {
			return path.substring(rootPath.length()
					+ (rootPath.endsWith(File.separator) ? 0 : 1)).replace('\\', '/');
		}
		return path;
	}

	private static String join(List<String> command) {
		StringBuilder text = new StringBuilder();
		for (String part : command) {
			if (text.length() > 0) {
				text.append(' ');
			}
			text.append(part.contains(" ") ? "\"" + part + "\"" : part);
		}
		return text.toString();
	}
}
