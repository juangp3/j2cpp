package se.arnetheduck.j2c.cli;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * j2c run and the j2c_run tool: translates a program against a J2C runtime
 * (the bundled one by default), builds its main class with make (through
 * Builder), runs it, runs the same main class on the JVM (compiled with the
 * javac of the JDK running J2C), and compares their output and exit codes.
 */
final class ProgramRunner {
	/** Default seconds each run may take */
	static final int DEFAULT_TIMEOUT = 60;
	/** Default characters of each output stream returned */
	static final int DEFAULT_OUTPUT_LIMIT = 20000;
	/** Bytes of each output stream kept for the comparison */
	private static final int CAPTURE_LIMIT = 16 << 20;
	/** Seconds the WSL side's timeout waits before killing after TERM */
	private static final int KILL_AFTER = 5;
	/** The working directories and classes, inside the output directory */
	static final String RUN_DIRECTORY = "run";
	/** The Java release the JVM run is compiled for */
	private static final String RELEASE = "17";
	/** Main sources the translator writes, one per class with a main method */
	private static final String MAIN_SUFFIX = "-main.cpp";

	static final String EQUAL = "equal";
	static final String DIFFERS = "differs";
	static final String SKIPPED = "skipped";

	/** The JVM run's outcomes besides running */
	static final String JVM_RAN = "ran";
	static final String JVM_UNAVAILABLE = "unavailable";
	static final String JVM_COMPILE_FAILED = "compile-failed";

	/** The JVM formats numbers like Locale.US, as the runtime does */
	private static final List<String> JVM_FLAGS = Arrays.asList(
			"-Duser.language=en", "-Duser.country=US",
			"-Duser.language.format=en", "-Duser.country.format=US",
			"-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8",
			"-Dsun.stderr.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
			"-Dstderr.encoding=UTF-8");

	static final class Options {
		final Builder.Options build = new Builder.Options();
		/** The main class, qualified or simple; null when there is one */
		String main;
		final List<String> args = new ArrayList<String>();
		/** Standard input of both runs; null for none */
		byte[] stdin;
		/** Whether to run the main class on the JVM and compare */
		boolean compare = true;
		/** Seconds each run may take; 0 for no limit */
		int timeout = DEFAULT_TIMEOUT;
		/** Characters of each output stream returned */
		int outputLimit = DEFAULT_OUTPUT_LIMIT;
		/** The java program, where the C++ program runs; null to find one */
		String java;
	}

	/** One run of a program */
	static final class Execution {
		List<String> command = Collections.emptyList();
		File directory;
		int exitCode;
		boolean timedOut;
		long millis;
		byte[] stdout = new byte[0];
		byte[] stderr = new byte[0];
		long stdoutBytes;
		long stderrBytes;
		/** Whether a signal (not the timeout) ended it, such as SIGSEGV */
		String signal;
	}

	static final class JvmRun {
		String status;
		/** Why it did not run, or a note on how it ran */
		String reason;
		String java;
		boolean wsl;
		String mainClass;
		long compileMillis;
		final List<Map<String, Object>> diagnostics = new ArrayList<Map<String, Object>>();
		Execution execution;
	}

	static final class Result {
		J2cService.TranslateResult translation;
		Builder.Result build;
		boolean buildFailed;
		/** The Java names of the classes with a main method */
		final List<String> mains = new ArrayList<String>();
		String main;
		File binary;
		Options options;
		Execution cpp;
		/** null when not compared */
		JvmRun jvm;
	}

	/** A main class: its Java name and the path of its binary in bin/ */
	private static final class MainClass {
		final String name;
		final String path;

		MainClass(String path) {
			this.path = path;
			this.name = path.replace('/', '.');
		}
	}

	private ProgramRunner() {
	}

	/**
	 * Translates the request with #line directives and the runtime, builds
	 * the main class's binary, runs it and, unless disabled, the same class
	 * on the JVM. A build failure returns its diagnostics; cancelling stops
	 * make or the program and throws OperationCanceledException.
	 */
	static Result run(J2cService service, J2cService.Request request,
			Options options, PrintStream log, Progress progress)
			throws Exception {
		if (request.output == null) {
			throw new IllegalArgumentException("Missing --output directory");
		}
		if (options.timeout < 0) {
			throw new IllegalArgumentException("Invalid timeout: "
					+ options.timeout + " (expected seconds, 0 for none)");
		}
		if (options.outputLimit < 0) {
			throw new IllegalArgumentException("Invalid output limit: "
					+ options.outputLimit + " (expected characters)");
		}
		request.lineDirectives = true;
		if (request.runtime == null) {
			request.runtime = J2cService.runtime(BundledRuntime.NAME, null);
		}
		Result result = new Result();
		result.options = options;
		result.translation = service.translate(request, progress);
		List<MainClass> mains = mains(result.translation.generatedFiles);
		for (MainClass main : mains) {
			result.mains.add(main.name);
		}
		MainClass main = select(mains, options.main);
		result.main = main.name;

		options.build.target = "bin/" + main.path;
		result.build = Builder.build(result.translation.output, options.build,
				log, progress);
		result.build.translated = true;
		result.build.translation = result.translation;
		if (result.build.exitCode != 0) {
			result.buildFailed = true;
			return result;
		}
		result.binary = binary(result.translation.output, main.path);
		if (!result.binary.isFile()) {
			throw new Builder.BuildException("make " + options.build.target
					+ " did not produce " + result.binary);
		}

		File runs = new File(result.translation.output, RUN_DIRECTORY);
		delete(runs);
		File cppDirectory = new File(runs, "cpp");
		Files.createDirectories(cppDirectory.toPath());
		boolean wsl = result.build.wsl;
		List<String> command = new ArrayList<String>(runPrefix(result.build));
		addTimeout(command, wsl, options.timeout);
		command.add(wsl ? Builder.wslPath(result.binary) : result.binary
				.getPath());
		command.addAll(options.args);
		progress.phase("Running " + main.name);
		result.cpp = execute(command, cppDirectory, options, wsl, progress);
		if (result.cpp.signal == null && !result.cpp.timedOut
				&& (wsl || !isWindows()) && result.cpp.exitCode > 128
				&& result.cpp.exitCode < 160) {
			result.cpp.signal = signal(result.cpp.exitCode - 128);
		}

		if (options.compare) {
			result.jvm = runJvm(result, main, runs, wsl, progress);
		}
		return result;
	}

	/** The classes with a main method, from the generated main sources */
	private static List<MainClass> mains(List<String> generatedFiles) {
		List<MainClass> mains = new ArrayList<MainClass>();
		for (String file : generatedFiles) {
			if (file.startsWith("src/") && file.endsWith(MAIN_SUFFIX)) {
				mains.add(new MainClass(file.substring("src/".length(), file.length()
						- MAIN_SUFFIX.length())));
			}
		}
		return mains;
	}

	/** The main class named (qualified or simple), or the only one */
	private static MainClass select(List<MainClass> mains, String name) {
		if (mains.isEmpty()) {
			throw new IllegalArgumentException("No class with a main method "
					+ "(public static void main(String[])) in the inputs");
		}
		List<String> names = new ArrayList<String>();
		for (MainClass main : mains) {
			names.add(main.name);
		}
		if (name == null || name.trim().isEmpty()) {
			if (mains.size() > 1) {
				throw new IllegalArgumentException("Several classes have a main "
						+ "method; choose one with --main (main): " + names);
			}
			return mains.get(0);
		}
		// Nested classes are named Outer_Inner in the output
		String wanted = name.trim().replace('$', '_');
		List<MainClass> matches = new ArrayList<MainClass>();
		for (MainClass main : mains) {
			if (main.name.equals(wanted) || main.name.endsWith("." + wanted)
					|| main.name.equals(wanted.replace('.', '_'))) {
				matches.add(main);
			}
		}
		if (matches.size() == 1) {
			return matches.get(0);
		}
		throw new IllegalArgumentException("The main class " + name
				+ (matches.isEmpty() ? " has no main method in the inputs"
						: " is ambiguous") + "; classes with a main method: "
				+ names);
	}

	/** bin/<path>, with the extension a native Windows toolchain adds */
	private static File binary(File output, String path) {
		File binary = new File(output, "bin/" + path);
		if (isWindows()) {
			for (String extension : new String[] { ".exe", ".cmd" }) {
				File candidate = new File(binary.getPath() + extension);
				if (candidate.isFile()) {
					return candidate;
				}
			}
		}
		return binary;
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
				.startsWith("windows");
	}

	/**
	 * What runs a program where make ran: wsl --exec, so no shell reads the
	 * program's arguments
	 */
	private static List<String> runPrefix(Builder.Result build) {
		List<String> prefix = new ArrayList<String>(build.prefix);
		int separator = prefix.indexOf("--");
		if (build.wsl && separator >= 0) {
			prefix.set(separator, "--exec");
		}
		return prefix;
	}

	private static void addTimeout(List<String> command, boolean wsl,
			int timeout) {
		if (wsl && timeout > 0) {
			// Killing wsl.exe need not stop the Linux processes
			command.add("timeout");
			command.add("-k");
			command.add(String.valueOf(KILL_AFTER));
			command.add(String.valueOf(timeout));
		}
	}

	private static String signal(int number) {
		switch (number) {
		case 1:
			return "SIGHUP";
		case 2:
			return "SIGINT";
		case 3:
			return "SIGQUIT";
		case 4:
			return "SIGILL";
		case 5:
			return "SIGTRAP";
		case 6:
			return "SIGABRT";
		case 7:
			return "SIGBUS";
		case 8:
			return "SIGFPE";
		case 9:
			return "SIGKILL";
		case 11:
			return "SIGSEGV";
		case 13:
			return "SIGPIPE";
		case 15:
			return "SIGTERM";
		default:
			return "signal " + number;
		}
	}

	/** Deletes a file or directory and everything in it, if it exists */
	private static void delete(File file) throws IOException {
		File[] entries = file.isDirectory() && !Files.isSymbolicLink(file
				.toPath()) ? file.listFiles() : null;
		if (entries != null) {
			for (File entry : entries) {
				delete(entry);
			}
		}
		Files.deleteIfExists(file.toPath());
	}

	// Running

	/** Keeps the first bytes of a stream and counts the rest */
	private static final class Capture implements Runnable {
		private final InputStream in;
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		private long total;

		Capture(InputStream in) {
			this.in = in;
		}

		@Override
		public void run() {
			byte[] buffer = new byte[8192];
			try {
				for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
					synchronized (this) {
						int keep = (int) Math.min(n, CAPTURE_LIMIT - bytes.size());
						if (keep > 0) {
							bytes.write(buffer, 0, keep);
						}
						total += n;
					}
				}
			} catch (IOException e) {
				// The program ended
			}
		}

		synchronized byte[] bytes() {
			return bytes.toByteArray();
		}

		synchronized long total() {
			return total;
		}
	}

	/**
	 * Runs a command with the options' standard input and timeout, stopping
	 * it when the call is cancelled
	 */
	private static Execution execute(List<String> command, File directory,
			Options options, boolean wsl, Progress progress) throws Exception {
		Execution execution = new Execution();
		execution.command = command;
		execution.directory = directory;
		ProcessBuilder builder = new ProcessBuilder(command).directory(directory);
		builder.environment().put("LC_ALL", "C");
		long started = System.nanoTime();
		final Process process = builder.start();
		final byte[] stdin = options.stdin;
		Thread writer = new Thread(new Runnable() {
			@Override
			public void run() {
				OutputStream out = process.getOutputStream();
				try {
					if (stdin != null) {
						out.write(stdin);
					}
				} catch (IOException e) {
					// The program ended without reading all of it
				} finally {
					try {
						out.close();
					} catch (IOException e) {
						// Already closed
					}
				}
			}
		});
		writer.setDaemon(true);
		writer.start();
		Capture stdout = new Capture(process.getInputStream());
		Capture stderr = new Capture(process.getErrorStream());
		Thread[] readers = { new Thread(stdout), new Thread(stderr) };
		for (Thread reader : readers) {
			reader.setDaemon(true);
			reader.start();
		}
		// A WSL timeout stops the program itself; this one is the backstop
		long limit = options.timeout == 0 ? Long.MAX_VALUE : TimeUnit.SECONDS
				.toNanos(options.timeout + (wsl ? KILL_AFTER + 5 : 0));
		boolean exited = false;
		while (!exited) {
			exited = process.waitFor(100, TimeUnit.MILLISECONDS);
			if (progress.cancelled()) {
				Builder.stop(process);
				progress.checkCancelled();
			}
			if (!exited && System.nanoTime() - started > limit) {
				Builder.stop(process);
				execution.timedOut = true;
				exited = true;
			}
		}
		for (Thread reader : readers) {
			reader.join(10000);
		}
		execution.millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()
				- started);
		execution.exitCode = process.exitValue();
		if (wsl && options.timeout > 0 && (execution.exitCode == 124
				|| execution.exitCode == 137) && execution.millis >= TimeUnit.SECONDS
				.toMillis(options.timeout) - 500) {
			execution.timedOut = true;
		}
		execution.stdout = stdout.bytes();
		execution.stderr = stderr.bytes();
		execution.stdoutBytes = stdout.total();
		execution.stderrBytes = stderr.total();
		return execution;
	}

	// The JVM

	/** Compiles the translated Java files with javac and runs the main class */
	private static JvmRun runJvm(Result result, MainClass main, File runs,
			boolean wsl, Progress progress) throws Exception {
		JvmRun jvm = new JvmRun();
		Options options = result.options;
		J2cService.TranslateResult translation = result.translation;
		JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
		if (javac == null) {
			jvm.status = JVM_UNAVAILABLE;
			jvm.reason = "J2C runs on a Java runtime without javac; run J2C with "
					+ "a JDK " + RELEASE + " or later to compare with the JVM";
			return jvm;
		}
		boolean release = false;
		for (SourceVersion version : javac.getSourceVersions()) {
			release |= version.name().equals("RELEASE_" + RELEASE);
		}
		if (!release) {
			jvm.status = JVM_UNAVAILABLE;
			jvm.reason = "The javac of the JDK running J2C cannot compile Java "
					+ RELEASE + "; run J2C with a JDK " + RELEASE + " or later";
			return jvm;
		}

		progress.phase("Compiling with javac");
		File classes = new File(runs, "classes");
		Files.createDirectories(classes.toPath());
		List<String> arguments = new ArrayList<String>(Arrays.asList("-d",
				classes.getPath(), "--release", RELEASE, "-encoding",
				translation.encoding.name(), "-proc:none", "-nowarn",
				"-implicit:class"));
		if (!translation.sourcePaths.isEmpty()) {
			arguments.add("-sourcepath");
			arguments.add(join(translation.sourcePaths, File.pathSeparator, false));
		}
		if (!translation.classPaths.isEmpty()) {
			arguments.add("-classpath");
			arguments.add(join(translation.classPaths, File.pathSeparator, false));
		}
		long started = System.nanoTime();
		DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
		boolean compiled;
		try (StandardJavaFileManager files = javac.getStandardFileManager(
				diagnostics, Locale.ROOT, translation.encoding)) {
			compiled = javac.getTask(null, files, diagnostics, arguments, null,
					files.getJavaFileObjectsFromFiles(translation.javaFiles)).call();
		}
		jvm.compileMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime()
				- started);
		progress.checkCancelled();
		for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics
				.getDiagnostics()) {
			if (diagnostic.getKind() != Diagnostic.Kind.ERROR) {
				continue;
			}
			Map<String, Object> json = new LinkedHashMap<String, Object>();
			JavaFileObject source = diagnostic.getSource();
			json.put("file", source == null ? null : new File(source.toUri())
					.getPath());
			json.put("line", diagnostic.getLineNumber() > 0 ? diagnostic
					.getLineNumber() : null);
			json.put("column", diagnostic.getColumnNumber() > 0 ? diagnostic
					.getColumnNumber() : null);
			json.put("message", diagnostic.getMessage(Locale.ROOT));
			jvm.diagnostics.add(json);
		}
		if (!compiled) {
			jvm.status = JVM_COMPILE_FAILED;
			jvm.reason = "javac failed to compile the Java sources";
			return jvm;
		}

		// Nested classes are Outer_Inner in the output and Outer$Inner here
		jvm.mainClass = main.name;
		if (!new File(classes, main.path + ".class").isFile()) {
			jvm.mainClass = main.name.replace('_', '$');
		}
		jvm.wsl = wsl;
		jvm.java = options.java;
		if (jvm.java == null && wsl) {
			jvm.java = wslJava(result.build);
			if (jvm.java == null) {
				jvm.wsl = false;
				jvm.reason = "WSL has no java (set J2C_WSL_JAVA to a JDK "
						+ RELEASE + " there), so the JVM ran on Windows; file paths "
						+ "and OS messages may differ";
			}
		}
		if (jvm.java == null) {
			jvm.java = new File(System.getProperty("java.home"), "bin"
					+ File.separator + (isWindows() ? "java.exe" : "java"))
					.getPath();
		}

		File directory = new File(runs, "jvm");
		Files.createDirectories(directory.toPath());
		List<File> classPath = new ArrayList<File>();
		classPath.add(classes);
		classPath.addAll(translation.classPaths);
		List<String> command = new ArrayList<String>(jvm.wsl ? runPrefix(result
				.build) : Collections.<String> emptyList());
		addTimeout(command, jvm.wsl, options.timeout);
		command.add(jvm.java);
		command.addAll(JVM_FLAGS);
		command.add("-cp");
		command.add(join(classPath, jvm.wsl ? ":" : File.pathSeparator, jvm.wsl));
		command.add(jvm.mainClass);
		command.addAll(options.args);
		progress.phase("Running " + main.name + " on the JVM");
		try {
			jvm.execution = execute(command, directory, options, jvm.wsl,
					progress);
		} catch (IOException e) {
			jvm.status = JVM_UNAVAILABLE;
			jvm.reason = "Could not run " + jvm.java + ": " + e.getMessage();
			return jvm;
		}
		jvm.status = JVM_RAN;
		return jvm;
	}

	/** J2C_WSL_JAVA, or the java on WSL's PATH, or null */
	private static String wslJava(Builder.Result build) {
		String configured = System.getenv("J2C_WSL_JAVA");
		if (configured != null && !configured.isEmpty()) {
			return configured;
		}
		List<String> command = new ArrayList<String>(runPrefix(build));
		command.add("sh");
		command.add("-c");
		command.add("command -v java");
		try {
			Process process = new ProcessBuilder(command).start();
			process.getOutputStream().close();
			String path = new String(process.getInputStream().readAllBytes(),
					StandardCharsets.UTF_8).trim();
			return process.waitFor() == 0 && path.startsWith("/") ? path : null;
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		}
	}

	private static String join(List<File> files, String separator, boolean wsl) {
		StringBuilder joined = new StringBuilder();
		for (File file : files) {
			joined.append(joined.length() == 0 ? "" : separator).append(wsl
					? Builder.wslPath(file) : file.getAbsolutePath());
		}
		return joined.toString();
	}

	// Comparison

	private static String text(byte[] bytes) {
		return new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n");
	}

	/**
	 * Standard error without the JVM's stack frames ("\tat ..." and "\t...
	 * n more"), which J2C does not record
	 */
	static String withoutFrames(String text) {
		StringBuilder kept = new StringBuilder();
		for (String line : text.split("\n", -1)) {
			if (line.matches("^\\t+(at |\\.\\.\\. ).*")) {
				continue;
			}
			kept.append(kept.length() == 0 ? "" : "\n").append(line);
		}
		return kept.toString();
	}

	/** Whether two outputs are equal, and their first differing line */
	private static Map<String, Object> compareText(String cpp, String jvm) {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		json.put("equal", cpp.equals(jvm));
		Map<String, Object> difference = null;
		if (!cpp.equals(jvm)) {
			String[] cppLines = cpp.split("\n", -1);
			String[] jvmLines = jvm.split("\n", -1);
			int line = 0;
			while (line < cppLines.length && line < jvmLines.length
					&& cppLines[line].equals(jvmLines[line])) {
				++line;
			}
			difference = new LinkedHashMap<String, Object>();
			difference.put("line", line + 1);
			difference.put("cpp", line < cppLines.length ? cppLines[line] : null);
			difference.put("jvm", line < jvmLines.length ? jvmLines[line] : null);
		}
		json.put("firstDifference", difference);
		return json;
	}

	private static Map<String, Object> comparison(Result result) {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		JvmRun jvm = result.jvm;
		if (jvm == null || !JVM_RAN.equals(jvm.status)) {
			json.put("status", SKIPPED);
			json.put("reason", jvm == null ? "Not compared (compare: false, or "
					+ "--no-compare)" : jvm.reason);
			return json;
		}
		Execution cpp = result.cpp;
		Execution java = jvm.execution;
		Map<String, Object> stdout = compareText(text(cpp.stdout), text(java.stdout));
		Map<String, Object> stderr = compareText(withoutFrames(text(cpp.stderr)),
				withoutFrames(text(java.stderr)));
		stderr.put("stackFramesIgnored", true);
		Map<String, Object> exitCode = new LinkedHashMap<String, Object>();
		exitCode.put("equal", cpp.exitCode == java.exitCode);
		exitCode.put("cpp", cpp.exitCode);
		exitCode.put("jvm", java.exitCode);
		boolean equal = Boolean.TRUE.equals(stdout.get("equal"))
				&& Boolean.TRUE.equals(stderr.get("equal"))
				&& cpp.exitCode == java.exitCode;
		json.put("status", equal ? EQUAL : DIFFERS);
		json.put("reason", jvm.reason);
		json.put("exitCode", exitCode);
		json.put("stdout", stdout);
		json.put("stderr", stderr);
		return json;
	}

	// JSON

	static Map<String, Object> json(Result result) {
		Map<String, Object> json = J2cService.header("run");
		Map<String, Object> comparison = result.buildFailed ? null
				: comparison(result);
		String status;
		if (result.buildFailed) {
			status = "build-failed";
		} else if (result.cpp.timedOut || result.jvm != null
				&& result.jvm.execution != null && result.jvm.execution.timedOut) {
			status = "timeout";
		} else if (result.cpp.signal != null) {
			status = "crashed";
		} else if (DIFFERS.equals(comparison.get("status"))) {
			status = DIFFERS;
		} else if (EQUAL.equals(comparison.get("status"))
				|| result.cpp.exitCode == 0) {
			status = "ok";
		} else {
			status = "failed";
		}
		Options options = result.options;
		json.put("status", status);
		json.put("output", result.translation.output.getPath());
		json.put("main", result.main);
		json.put("mains", result.mains);
		json.put("binary", result.binary == null ? null : result.binary.getPath());
		json.put("args", options.args);
		json.put("stdinBytes", options.stdin == null ? 0 : options.stdin.length);
		json.put("timeout", options.timeout);
		json.put("outputLimit", options.outputLimit);
		json.put("cpp", result.cpp == null ? null : executionJson(result.cpp,
				options.outputLimit));
		Map<String, Object> jvmJson = null;
		if (result.jvm != null) {
			JvmRun jvm = result.jvm;
			jvmJson = new LinkedHashMap<String, Object>();
			jvmJson.put("status", jvm.status);
			jvmJson.put("reason", jvm.reason);
			jvmJson.put("java", jvm.java);
			jvmJson.put("wsl", jvm.wsl);
			jvmJson.put("mainClass", jvm.mainClass);
			jvmJson.put("compileMillis", jvm.compileMillis);
			jvmJson.put("diagnostics", jvm.diagnostics);
			if (jvm.execution != null) {
				jvmJson.putAll(executionJson(jvm.execution, options.outputLimit));
			}
		}
		json.put("jvm", jvmJson);
		json.put("comparison", comparison);
		// The build's diagnostics; its log only when it failed
		json.put("build", Builder.body(result.build, result.buildFailed));
		J2cService.putProjects(json, result.translation.projects);
		return json;
	}

	private static Map<String, Object> executionJson(Execution execution,
			int limit) {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		json.put("command", execution.command);
		json.put("workingDirectory", execution.directory.getPath());
		json.put("exitCode", execution.exitCode);
		json.put("signal", execution.signal);
		json.put("timedOut", execution.timedOut);
		json.put("durationMillis", execution.millis);
		putStream(json, "stdout", execution.stdout, execution.stdoutBytes, limit);
		putStream(json, "stderr", execution.stderr, execution.stderrBytes, limit);
		return json;
	}

	private static void putStream(Map<String, Object> json, String name,
			byte[] bytes, long total, int limit) {
		String text = text(bytes);
		boolean truncated = total > bytes.length || text.length() > limit;
		json.put(name, text.length() > limit ? text.substring(0, limit) : text);
		json.put(name + "Bytes", total);
		json.put(name + "Truncated", truncated);
	}

	/**
	 * The C++ program's output, then how it ended and how it compares with
	 * the JVM's
	 */
	static String text(Result result) {
		StringBuilder text = new StringBuilder();
		if (result.buildFailed) {
			int errors = 0;
			for (Builder.Diagnostic diagnostic : result.build.diagnostics) {
				text.append(Builder.text(diagnostic)).append('\n');
				if (diagnostic.severity.equals("error")) {
					++errors;
				}
			}
			text.append("J2C run: the build failed: make exited with ").append(
					result.build.exitCode).append(", ").append(errors).append(
					" error(s)\n");
			return text.toString();
		}
		Map<String, Object> json = json(result);
		Map<?, ?> cpp = (Map<?, ?>) json.get("cpp");
		for (String stream : new String[] { "stdout", "stderr" }) {
			String output = (String) cpp.get(stream);
			if (output.isEmpty()) {
				continue;
			}
			text.append("--- ").append(stream).append(" (C++)\n").append(output);
			if (!output.endsWith("\n")) {
				text.append('\n');
			}
			if (Boolean.TRUE.equals(cpp.get(stream + "Truncated"))) {
				text.append("[truncated: ").append(cpp.get(stream + "Bytes"))
						.append(" bytes in all]\n");
			}
		}
		text.append("J2C run: ").append(result.main).append(' ').append(
				ended(result.cpp, result.options.timeout));
		JvmRun jvm = result.jvm;
		if (jvm != null && jvm.execution != null) {
			text.append("; on the JVM ").append(ended(jvm.execution,
					result.options.timeout));
		}
		text.append('\n');
		Map<?, ?> comparison = (Map<?, ?>) json.get("comparison");
		if (SKIPPED.equals(comparison.get("status"))) {
			text.append("J2C run: not compared with the JVM: ").append(
					comparison.get("reason")).append('\n');
			for (Map<String, Object> diagnostic : jvm == null ? Collections
					.<Map<String, Object>> emptyList() : jvm.diagnostics) {
				text.append(diagnostic.get("file")).append(':').append(
						diagnostic.get("line")).append(": error: ").append(
						diagnostic.get("message")).append('\n');
			}
		} else if (EQUAL.equals(comparison.get("status"))) {
			text.append("J2C run: the same output and exit code as the JVM\n");
		} else {
			for (String stream : new String[] { "stdout", "stderr" }) {
				Map<?, ?> streamJson = (Map<?, ?>) comparison.get(stream);
				Map<?, ?> difference = (Map<?, ?>) streamJson.get(
						"firstDifference");
				if (difference != null) {
					text.append("J2C run: ").append(stream).append(
							" differs from the JVM's at line ").append(
							difference.get("line")).append(":\n  C++: ").append(
							quoted(difference.get("cpp"))).append("\n  JVM: ")
							.append(quoted(difference.get("jvm"))).append('\n');
				}
			}
			Map<?, ?> exitCode = (Map<?, ?>) comparison.get("exitCode");
			if (!Boolean.TRUE.equals(exitCode.get("equal"))) {
				text.append("J2C run: exit code ").append(exitCode.get("cpp"))
						.append(", the JVM's ").append(exitCode.get("jvm")).append(
								'\n');
			}
		}
		text.append("J2C run: ").append(json.get("status")).append('\n');
		return text.toString();
	}

	private static String ended(Execution execution, int timeout) {
		if (execution.timedOut) {
			return "was stopped after the timeout of " + timeout + " s";
		}
		return "exited with " + execution.exitCode + (execution.signal == null
				? "" : " (" + execution.signal + ")") + " in "
				+ execution.millis + " ms";
	}

	private static String quoted(Object line) {
		return line == null ? "(no line)" : Json.write(line);
	}
}
