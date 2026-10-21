package se.arnetheduck.j2c.cli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import se.arnetheduck.j2c.transform.LineDirectives;

/**
 * j2c build and the j2c_build tool: runs make in a translated output
 * directory (natively, or in WSL on Windows), and parses the GCC and Clang
 * diagnostics in its output, mapping them to Java files and lines through the
 * #line directives of --line-directives.
 */
final class Builder {
	/** Lines of make's output kept for the log */
	static final int LOG_LINES = 100;
	/** How locations were mapped to Java */
	static final String MAPPED_DIRECTIVE = "line-directive";
	static final String MAPPED_NEAREST = "nearest";
	static final String MAPPED_NONE = "none";

	static final String WSL_AUTO = "auto";
	static final String WSL_ALWAYS = "always";
	static final String WSL_NEVER = "never";

	/** make could not be run */
	static final class BuildException extends Exception {
		private static final long serialVersionUID = 1L;

		BuildException(String message) {
			super(message);
		}
	}

	/** How make is run; the defaults come from J2C_MAKE, J2C_WSL and J2C_WSL_DISTRO */
	static final class Options {
		String make = env("J2C_MAKE", "make");
		/** auto runs make in WSL on Windows when it is not on the PATH */
		String wsl = env("J2C_WSL", WSL_AUTO);
		/** null for WSL's default distribution */
		String wslDistro = env("J2C_WSL_DISTRO", null);
		/** null for the Makefile's default target (all) */
		String target;
		/** Parallel jobs; 0 for one per processor */
		int jobs;
	}

	static final class Location {
		/** Absolute path, or the compiler's text for files outside the output */
		final String file;
		final int line;
		/** 1-based, or 0 when unknown */
		final int column;

		Location(String file, int line, int column) {
			this.file = file;
			this.line = line;
			this.column = column;
		}
	}

	static final class Diagnostic {
		/** error, warning or note */
		final String severity;
		final String message;
		Location java;
		Location cpp;
		String mapped = MAPPED_NONE;
		/** Such as "In member function 'void A::f()'" */
		String context;
		final List<Location> includedFrom = new ArrayList<Location>();
		final List<Diagnostic> notes = new ArrayList<Diagnostic>();

		Diagnostic(String severity, String message) {
			this.severity = severity;
			this.message = message;
		}
	}

	static final class Result {
		File output;
		/** Whether the inputs were translated first */
		boolean translated;
		J2cService.TranslateResult translation;
		int exitCode;
		/** Whether make ran in WSL, as programs built by it must */
		boolean wsl;
		/** What runs a program in make's environment (wsl ... -- env LC_ALL=C) */
		List<String> prefix = Collections.emptyList();
		List<String> command = Collections.emptyList();
		final List<Diagnostic> diagnostics = new ArrayList<Diagnostic>();
		final List<String> log = new ArrayList<String>();
		boolean logTruncated;
	}

	// file:line:column: severity: message (the column is missing for some)
	private static final Pattern DIAGNOSTIC = Pattern.compile(
			"^(.+?):(\\d+):(?:(\\d+):)? (fatal error|error|warning|note|remark): (.*)$");
	// GCC's instantiation backtraces: file:line:column:   required from here
	private static final Pattern BACKTRACE = Pattern.compile(
			"^(.+?):(\\d+):(\\d+):\\s+((?:required|instantiated) (?:from|by) .*)$");
	private static final Pattern INCLUDED = Pattern.compile(
			"^(?:In file included from|\\s+from) (.+?):(\\d+)(?::(\\d+))?[:,]$");
	private static final Pattern CONTEXT = Pattern.compile(
			"^(.+?): ((?:In|At) [^:].*):$");
	// ld: file:line:(.text+0x10): undefined reference to ...
	private static final Pattern LINKER = Pattern.compile(
			"^(.+?):(\\d+):\\(\\.[^)]*\\): (.*)$");
	private static final Pattern LINKER_ERROR = Pattern.compile(
			"^(?:\\S*/)?ld(?:\\.\\w+)?: (?!.*: in function )(.*)$");
	// The Makefile's "Compile src/a/B.cpp", naming the unit being compiled
	private static final Pattern COMPILE = Pattern.compile("^Compile (\\S+)$");
	private static final Pattern DIRECTIVE = Pattern.compile(
			"^#line (\\d+) \"((?:[^\"\\\\]|\\\\.)*)\"\\s*$");
	private static final Pattern GNU_MAKE = Pattern.compile(
			"GNU Make (\\d+)\\.");

	private final File output;
	private final boolean wsl;
	/** Line maps of generated files, by absolute path */
	private final Map<String, LineMap> lineMaps = new HashMap<String, LineMap>();
	/** Generated files with directives naming each Java file */
	private Map<String, List<String>> byJavaFile;

	private Builder(File output, boolean wsl) {
		this.output = output;
		this.wsl = wsl;
	}

	private static String env(String name, String fallback) {
		String value = System.getenv(name);
		return value == null || value.isEmpty() ? fallback : value;
	}

	/** Whether J2C translated into output (its marker and Makefile exist) */
	static void requireBuildable(File output) {
		if (!output.isDirectory()) {
			throw new IllegalArgumentException("Output directory does not exist: "
					+ output);
		}
		if (!new File(output, J2cService.OUTPUT_MARKER).isFile()) {
			throw new IllegalArgumentException("Not a J2C output directory (no "
					+ J2cService.OUTPUT_MARKER + " marker): " + output
					+ ". Translate into it first, or pass the Java inputs to build.");
		}
		if (!new File(output, "Makefile").isFile()) {
			throw new IllegalArgumentException("No Makefile in the output "
					+ "directory: " + output);
		}
	}

	/**
	 * Runs make in output, echoing its output to log, and returns the
	 * diagnostics it printed
	 */
	static Result build(File output, Options options, PrintStream log)
			throws IOException, InterruptedException, BuildException {
		return build(output, options, log, Progress.NONE);
	}

	/**
	 * The same, reporting each file compiled as progress; once cancelled,
	 * make and everything it started are stopped and
	 * OperationCanceledException is thrown
	 */
	static Result build(File output, Options options, final PrintStream log,
			Progress progress) throws IOException, InterruptedException,
			BuildException {
		output = output.getAbsoluteFile();
		requireBuildable(output);
		if (!WSL_AUTO.equals(options.wsl) && !WSL_ALWAYS.equals(options.wsl)
				&& !WSL_NEVER.equals(options.wsl)) {
			throw new IllegalArgumentException("Invalid --wsl value: "
					+ options.wsl + " (expected auto, always or never)");
		}
		boolean windows = System.getProperty("os.name", "").toLowerCase(
				Locale.ROOT).startsWith("windows");
		boolean useWsl = WSL_ALWAYS.equals(options.wsl) || WSL_AUTO.equals(
				options.wsl) && windows && makeVersion(prefix(options, false),
				options.make) < 0;
		Builder builder = new Builder(output, useWsl);

		List<String> prefix = prefix(options, useWsl);
		int version = makeVersion(prefix, options.make);
		if (version < 0) {
			throw new BuildException("Could not run " + options.make + (useWsl
					? " in WSL (" + (options.wslDistro == null ? "its default distribution"
							: options.wslDistro) + "); install GNU make and a C++20 "
							+ "compiler there, or choose a distribution with "
							+ "--wsl-distro or J2C_WSL_DISTRO"
					: "; install GNU make and a C++20 compiler"
							+ (windows ? ", or build in WSL (--wsl always)" : "")));
		}

		List<String> command = new ArrayList<String>(prefix);
		command.add(options.make);
		if (useWsl) {
			command.add("-C");
			command.add(wslPath(output));
		}
		// Keep going, to report the errors of every file
		command.add("-k");
		int jobs = options.jobs > 0 ? options.jobs : Runtime.getRuntime()
				.availableProcessors();
		command.add("-j" + jobs);
		if (jobs > 1 && version >= 4) {
			// Each file's diagnostics together, after its Compile line
			command.add("--output-sync=target");
		}
		if (options.target != null) {
			command.add(options.target);
		}

		Result result = new Result();
		result.output = output;
		result.command = command;
		result.wsl = useWsl;
		result.prefix = prefix;
		ProcessBuilder process = new ProcessBuilder(command).directory(output)
				.redirectErrorStream(true);
		// English messages with plain quotes
		process.environment().put("LC_ALL", "C");
		progress.phase("Running " + options.make + " in " + output
				+ (useWsl ? " (WSL)" : ""));
		final Process running = process.start();
		// make must not read J2C's stdin, which carries MCP messages
		running.getOutputStream().close();
		final List<String> lines = new ArrayList<String>();
		Thread reader = new Thread(new Runnable() {
			@Override
			public void run() {
				BufferedReader in = new BufferedReader(new InputStreamReader(
						running.getInputStream(), StandardCharsets.UTF_8));
				try {
					for (String line = in.readLine(); line != null; line = in
							.readLine()) {
						log.println(line);
						synchronized (lines) {
							lines.add(line);
						}
					}
				} catch (IOException e) {
					// make ended
				}
			}
		});
		reader.setDaemon(true);
		reader.start();
		// Progress is reported here, on the thread doing the work
		int reported = 0;
		boolean exited = false;
		while (!exited) {
			exited = running.waitFor(100, TimeUnit.MILLISECONDS);
			if (progress.cancelled()) {
				stop(running);
				progress.checkCancelled();
			}
			reported = reportCompiles(lines, reported, progress);
		}
		reader.join(10000);
		reportCompiles(lines, reported, progress);
		result.exitCode = running.exitValue();

		int from = Math.max(0, lines.size() - LOG_LINES);
		result.logTruncated = from > 0;
		result.log.addAll(lines.subList(from, lines.size()));
		builder.parse(lines, result.diagnostics);
		return result;
	}

	/** Reports the Compile lines after the first reported ones */
	private static int reportCompiles(List<String> lines, int reported,
			Progress progress) {
		List<String> compiled = new ArrayList<String>();
		int seen;
		synchronized (lines) {
			seen = lines.size();
			for (int i = reported; i < seen; ++i) {
				Matcher compile = COMPILE.matcher(lines.get(i));
				if (compile.matches()) {
					compiled.add(compile.group(1));
				}
			}
		}
		for (String file : compiled) {
			progress.phase("Compiled " + file);
		}
		return seen;
	}

	/** Kills make and every process it started (WSL's with wsl.exe) */
	static void stop(Process process) throws InterruptedException {
		Iterator<ProcessHandle> descendants = process.descendants().iterator();
		while (descendants.hasNext()) {
			descendants.next().destroyForcibly();
		}
		process.destroyForcibly();
		process.waitFor(10, TimeUnit.SECONDS);
	}

	/** env LC_ALL=C for WSL, which does not pass the environment on */
	private static List<String> prefix(Options options, boolean useWsl) {
		List<String> prefix = new ArrayList<String>();
		if (useWsl) {
			prefix.add("wsl");
			if (options.wslDistro != null) {
				prefix.add("-d");
				prefix.add(options.wslDistro);
			}
			prefix.add("--");
			prefix.add("env");
			prefix.add("LC_ALL=C");
		}
		return prefix;
	}

	/** GNU make's major version, 0 for another make, -1 if it cannot run */
	private static int makeVersion(List<String> prefix, String make) {
		List<String> command = new ArrayList<String>(prefix);
		command.add(make);
		command.add("--version");
		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(
					true).start();
			process.getOutputStream().close();
			String text = new String(process.getInputStream().readAllBytes(),
					StandardCharsets.UTF_8);
			if (process.waitFor() != 0) {
				return -1;
			}
			Matcher matcher = GNU_MAKE.matcher(text);
			return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
		} catch (IOException e) {
			return -1;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return -1;
		}
	}

	/** C:\a\b as /mnt/c/a/b */
	static String wslPath(File file) {
		String path = file.getAbsolutePath();
		if (path.length() < 3 || path.charAt(1) != ':') {
			throw new IllegalArgumentException("WSL builds need an output "
					+ "directory on a drive letter: " + file);
		}
		return "/mnt/" + Character.toLowerCase(path.charAt(0))
				+ path.substring(2).replace('\\', '/');
	}

	/** A path the compiler printed, as an absolute path where it can */
	private String resolve(String path) {
		if (wsl) {
			Matcher drive = Pattern.compile("^/mnt/([a-zA-Z])(/.*)?$").matcher(path);
			if (drive.matches()) {
				String rest = drive.group(2) == null ? "\\" : drive.group(2)
						.replace('/', '\\');
				return Character.toUpperCase(drive.group(1).charAt(0)) + ":" + rest;
			}
			if (path.startsWith("/")) {
				return path; // inside WSL, such as a system header
			}
		}
		File file = new File(path);
		if (!file.isAbsolute()) {
			file = new File(output, path);
		}
		return file.toPath().normalize().toString();
	}

	private static boolean isJava(String path) {
		return path.endsWith(".java");
	}

	void parse(List<String> lines, List<Diagnostic> diagnostics) {
		String unit = null;
		String context = null;
		List<Location> included = new ArrayList<Location>();
		Diagnostic last = null;
		for (String line : lines) {
			Matcher matcher = COMPILE.matcher(line);
			if (matcher.matches()) {
				unit = resolve(matcher.group(1));
				last = null;
				continue;
			}
			matcher = INCLUDED.matcher(line);
			if (matcher.matches()) {
				included.add(new Location(resolve(matcher.group(1)), Integer
						.parseInt(matcher.group(2)), matcher.group(3) == null ? 0
						: Integer.parseInt(matcher.group(3))));
				continue;
			}
			matcher = DIAGNOSTIC.matcher(line);
			Diagnostic diagnostic = null;
			if (matcher.matches()) {
				String severity = matcher.group(4);
				severity = severity.equals("fatal error") ? "error" : severity
						.equals("remark") ? "note" : severity;
				diagnostic = new Diagnostic(severity, matcher.group(5));
				locate(diagnostic, matcher.group(1), Integer.parseInt(matcher
						.group(2)), matcher.group(3) == null ? 0 : Integer
						.parseInt(matcher.group(3)), unit, included);
			} else if ((matcher = BACKTRACE.matcher(line)).matches()) {
				diagnostic = new Diagnostic("note", matcher.group(4));
				locate(diagnostic, matcher.group(1), Integer.parseInt(matcher
						.group(2)), Integer.parseInt(matcher.group(3)), unit,
						included);
			} else if ((matcher = LINKER.matcher(line)).matches()) {
				diagnostic = new Diagnostic("error", matcher.group(3));
				locate(diagnostic, matcher.group(1), Integer.parseInt(matcher
						.group(2)), 0, null, included);
			} else if ((matcher = LINKER_ERROR.matcher(line)).matches()) {
				diagnostic = new Diagnostic("error", matcher.group(1));
			} else if ((matcher = CONTEXT.matcher(line)).matches()) {
				context = matcher.group(2);
				continue;
			} else {
				continue;
			}

			diagnostic.context = context;
			diagnostic.includedFrom.addAll(included);
			included.clear();
			context = null;
			if (diagnostic.severity.equals("note") && last != null) {
				last.notes.add(diagnostic);
			} else {
				diagnostics.add(diagnostic);
				last = diagnostic;
			}
		}
	}

	/** Sets the diagnostic's Java and C++ locations */
	private void locate(Diagnostic diagnostic, String path, int line,
			int column, String unit, List<Location> included) {
		String file = resolve(path);
		if (isJava(file)) {
			diagnostic.java = new Location(file, line, 0);
			diagnostic.mapped = MAPPED_DIRECTIVE;
			diagnostic.cpp = generated(file, line, column, unit, included);
			return;
		}
		diagnostic.cpp = new Location(file, line, column);
		LineMap map = lineMap(file);
		Location nearest = map == null ? null : map.nearestJava(line);
		if (nearest != null) {
			diagnostic.java = nearest;
			diagnostic.mapped = MAPPED_NEAREST;
		}
	}

	/**
	 * The generated line mapped to a Java line, or null when none is. The
	 * compiler names only the Java file, so the C++ file is looked for: the
	 * unit being compiled, or for an error in an included file (the compiler
	 * then names what included it), the headers generated from that Java
	 * file first.
	 */
	private Location generated(String javaFile, int line, int column,
			String unit, List<Location> included) {
		List<String> candidates = new ArrayList<String>();
		List<String> others = generatedFiles().get(javaFile);
		if (others == null) {
			others = Collections.emptyList();
		}
		if (!included.isEmpty()) {
			for (String other : others) {
				if (other.endsWith(".hpp")) {
					candidates.add(other);
				}
			}
		}
		if (unit != null && !candidates.contains(unit)) {
			candidates.add(unit);
		}
		for (Location location : included) {
			if (!candidates.contains(location.file)) {
				candidates.add(location.file);
			}
		}
		for (String other : others) {
			if (!candidates.contains(other)) {
				candidates.add(other);
			}
		}
		for (String candidate : candidates) {
			LineMap map = lineMap(candidate);
			int physical = map == null ? -1 : map.physical(javaFile, line,
					column);
			if (physical > 0) {
				return new Location(candidate, physical, column);
			}
		}
		return null;
	}

	/** Generated files with directives, by the Java files they name */
	private Map<String, List<String>> generatedFiles() {
		if (byJavaFile == null) {
			byJavaFile = new HashMap<String, List<String>>();
			List<File> files = new ArrayList<File>();
			collect(output, files);
			Collections.sort(files);
			for (File file : files) {
				LineMap map = lineMap(file.getPath());
				if (map == null) {
					continue;
				}
				for (String java : map.javaFiles()) {
					List<String> list = byJavaFile.get(java);
					if (list == null) {
						list = new ArrayList<String>();
						byJavaFile.put(java, list);
					}
					list.add(file.getPath());
				}
			}
		}
		return byJavaFile;
	}

	private static void collect(File directory, List<File> files) {
		File[] entries = directory.listFiles();
		if (entries == null) {
			return;
		}
		for (File entry : entries) {
			if (entry.isDirectory()) {
				if (!entry.getName().equals("obj") && !entry.getName().equals("bin")) {
					collect(entry, files);
				}
			} else if (entry.getName().endsWith(".cpp")
					|| entry.getName().endsWith(".hpp")) {
				files.add(entry.getAbsoluteFile().toPath().normalize().toFile());
			}
		}
	}

	/** The line map of a generated file, or null without directives */
	private LineMap lineMap(String file) {
		if (lineMaps.containsKey(file)) {
			return lineMaps.get(file);
		}
		LineMap map = null;
		File path = new File(file);
		if (path.isFile() && path.isAbsolute()) {
			try {
				map = LineMap.read(this, path);
			} catch (IOException e) {
				map = null;
			}
		}
		lineMaps.put(file, map);
		return map;
	}

	/** What each line of a generated file maps to, after its directives */
	private static final class LineMap {
		/**
		 * Index 0 is line 1; null entries are the file itself, and empty ones
		 * blank lines and directives
		 */
		final String[] files;
		final int[] lines;
		final int[] lengths;

		private LineMap(String[] files, int[] lines, int[] lengths) {
			this.files = files;
			this.lines = lines;
			this.lengths = lengths;
		}

		static LineMap read(Builder builder, File file) throws IOException {
			String text = new String(Files.readAllBytes(file.toPath()),
					StandardCharsets.UTF_8);
			if (!text.contains("#line ")) {
				return null;
			}
			String[] physical = text.split("\r?\n", -1);
			String[] files = new String[physical.length];
			int[] lines = new int[physical.length];
			int[] lengths = new int[physical.length];
			String current = null;
			int line = 1;
			Map<String, String> resolved = new HashMap<String, String>();
			for (int i = 0; i < physical.length; ++i) {
				Matcher directive = DIRECTIVE.matcher(physical[i]);
				// Blank lines and access specifiers are nobody's line
				files[i] = physical[i].trim().isEmpty()
						|| LineDirectives.ACCESS_SPECIFIER.matcher(physical[i])
								.matches() ? "" : current;
				lengths[i] = physical[i].length();
				lines[i] = line;
				if (directive.matches()) {
					// No diagnostic is ever on a directive itself
					files[i] = "";
					String path = directive.group(2).replace("\\\"", "\"")
							.replace("\\\\", "\\");
					String absolute = resolved.get(path);
					if (absolute == null) {
						absolute = builder.resolve(path);
						resolved.put(path, absolute);
					}
					current = absolute.equals(file.getAbsoluteFile().toPath()
							.normalize().toString()) ? null : absolute;
					line = Integer.parseInt(directive.group(1));
				} else {
					++line;
				}
			}
			return new LineMap(files, lines, lengths);
		}

		List<String> javaFiles() {
			List<String> java = new ArrayList<String>();
			for (String file : files) {
				if (file != null && isJava(file) && !java.contains(file)) {
					java.add(file);
				}
			}
			return java;
		}

		/**
		 * The first physical line the compiler puts at javaFile:line that is
		 * long enough for column (several C++ lines can share a Java line)
		 */
		int physical(String javaFile, int line, int column) {
			int first = -1;
			for (int i = 0; i < files.length; ++i) {
				if (javaFile.equals(files[i]) && lines[i] == line) {
					if (column <= lengths[i]) {
						return i + 1;
					}
					if (first < 0) {
						first = i + 1;
					}
				}
			}
			return first;
		}

		/** The Java line of the closest mapped line before a C++ line */
		Location nearestJava(int line) {
			for (int i = Math.min(line, files.length) - 1; i >= 0; --i) {
				if (files[i] != null && isJava(files[i])) {
					return new Location(files[i], lines[i], 0);
				}
			}
			return null;
		}
	}

	// JSON

	static Map<String, Object> json(Result result) {
		Map<String, Object> json = J2cService.header("build");
		json.putAll(body(result, true));
		if (result.translation != null) {
			json.put("generatedFiles", result.translation.generatedFiles);
		}
		return json;
	}

	/** The build's fields, without the header; the log only when asked */
	static Map<String, Object> body(Result result, boolean log) {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		json.put("status", result.exitCode == 0 ? "ok" : "failed");
		json.put("exitCode", result.exitCode);
		json.put("output", result.output.getPath());
		json.put("translated", result.translated);
		json.put("makeCommand", result.command);
		int errors = 0;
		int warnings = 0;
		List<Object> diagnostics = new ArrayList<Object>();
		for (Diagnostic diagnostic : result.diagnostics) {
			diagnostics.add(diagnosticJson(diagnostic));
			if (diagnostic.severity.equals("error")) {
				++errors;
			} else if (diagnostic.severity.equals("warning")) {
				++warnings;
			}
		}
		json.put("diagnostics", diagnostics);
		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.put("errors", errors);
		summary.put("warnings", warnings);
		json.put("summary", summary);
		if (log) {
			json.put("log", result.log);
			json.put("logTruncated", result.logTruncated);
		}
		return json;
	}

	private static Map<String, Object> diagnosticJson(Diagnostic diagnostic) {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		json.put("severity", diagnostic.severity);
		json.put("message", diagnostic.message);
		json.put("java", diagnostic.java == null ? null : locationJson(
				diagnostic.java));
		json.put("cpp", diagnostic.cpp == null ? null : locationJson(diagnostic.cpp));
		json.put("mapped", diagnostic.mapped);
		json.put("context", diagnostic.context);
		List<Object> included = new ArrayList<Object>();
		for (Location location : diagnostic.includedFrom) {
			included.add(locationJson(location));
		}
		json.put("includedFrom", included);
		List<Object> notes = new ArrayList<Object>();
		for (Diagnostic note : diagnostic.notes) {
			notes.add(diagnosticJson(note));
		}
		json.put("notes", notes);
		return json;
	}

	private static Map<String, Object> locationJson(Location location) {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		json.put("file", location.file);
		json.put("line", location.line);
		if (location.column > 0) {
			json.put("column", location.column);
		}
		return json;
	}

	/** file:line: severity: message, at the Java line when it is known */
	static String text(Diagnostic diagnostic) {
		Location at = diagnostic.java != null ? diagnostic.java : diagnostic.cpp;
		StringBuilder text = new StringBuilder();
		if (at != null) {
			text.append(at.file).append(':').append(at.line).append(": ");
		}
		text.append(diagnostic.severity).append(": ").append(diagnostic.message);
		if (diagnostic.java != null && diagnostic.cpp != null) {
			text.append(" [C++ ").append(diagnostic.cpp.file).append(':').append(
					diagnostic.cpp.line);
			if (diagnostic.cpp.column > 0) {
				text.append(':').append(diagnostic.cpp.column);
			}
			text.append(diagnostic.mapped.equals(MAPPED_NEAREST) ? ", nearest Java line"
					: "").append(']');
		}
		return text.toString();
	}
}
