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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import se.arnetheduck.j2c.transform.GeneratedTest;

/**
 * j2c test and the j2c_test tool: translates JUnit tests with --tests,
 * builds the GoogleTest binary (make tests, through Builder), runs it with a
 * JSON report, and returns each test's result at its Java class, method,
 * file and line.
 */
final class TestRunner {
	static final String PASSED = "passed";
	static final String FAILED = "failed";
	static final String SKIPPED = "skipped";
	static final String DISABLED = "disabled";
	/** Selected, but the test process ended (crashed or timed out) first */
	static final String NOT_RUN = "notRun";

	/** Default seconds the test binary may run */
	static final int DEFAULT_TIMEOUT = 300;
	/** Seconds the WSL side's timeout waits before killing after TERM */
	private static final int KILL_AFTER = 5;

	static final class Options {
		final Builder.Options build = new Builder.Options();
		/** Java class or method names; empty runs every test */
		final List<String> filter = new ArrayList<String>();
		/** Seconds the test binary may run; 0 for no limit */
		int timeout = DEFAULT_TIMEOUT;
	}

	static final class TestResult {
		final String suite;
		final String name;
		/** Null when the test is not one J2C generated */
		final GeneratedTest java;
		String status;
		/** The Java failure message (the AssertionError's), or null */
		String message;
		/** The Java exception class a failing test threw, or null */
		String exception;
		/** GoogleTest's text of every failure, or null */
		String failure;
		long timeMillis;
		/** Started, but the test process ended before it finished */
		boolean unfinished;

		TestResult(String suite, String name, GeneratedTest java) {
			this.suite = suite;
			this.name = name;
			this.java = java;
		}
	}

	static final class Result {
		J2cService.TranslateResult translation;
		Builder.Result build;
		/** Whether the build failed, so no test ran */
		boolean buildFailed;
		File binary;
		List<String> command = Collections.emptyList();
		/** --gtest_filter's value, or null for every test */
		String gtestFilter;
		int exitCode;
		boolean timedOut;
		int timeout;
		/** Whether GoogleTest wrote its JSON report */
		boolean reportRead;
		final List<TestResult> tests = new ArrayList<TestResult>();
		final List<String> log = new ArrayList<String>();
		boolean logTruncated;
	}

	// GoogleTest's console lines
	private static final Pattern RUN = Pattern.compile(
			"^\\[ RUN      \\] (\\S+)\\.(\\S+)$");
	private static final Pattern END = Pattern.compile(
			"^\\[ +(OK|FAILED|SKIPPED) +\\] (\\S+)\\.(\\S+) \\((\\d+) ms\\)$");
	private static final Pattern FAILURE_START = Pattern.compile(
			"^.*:\\d+: (Failure|Skipped)$");
	private static final Pattern THROWN = Pattern.compile(
			"^Test threw ([\\w.$]+)(?:: ([\\s\\S]*))?$");
	private static final Pattern MAKE_NAME = Pattern.compile(
			"(?m)^NAME := (.+)$");

	private TestRunner() {
	}

	/**
	 * Translates the request with --tests and #line directives, builds the
	 * tests target, and runs the selected tests. A build failure returns its
	 * diagnostics; cancelling stops make or the tests and throws
	 * OperationCanceledException.
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
		request.tests = true;
		request.lineDirectives = true;
		Result result = new Result();
		result.timeout = options.timeout;
		result.translation = service.translate(request, progress);
		List<GeneratedTest> tests = result.translation.tests;
		if (tests.isEmpty()) {
			throw new IllegalArgumentException("No runnable JUnit test in the "
					+ "inputs (JUnit 4 or 5 @Test methods in concrete classes)");
		}
		List<GeneratedTest> selected = select(tests, options.filter);
		if (selected.size() < tests.size()) {
			result.gtestFilter = gtestFilter(tests, selected);
		}

		options.build.target = "tests";
		result.build = Builder.build(result.translation.output, options.build,
				log, progress);
		result.build.translated = true;
		result.build.translation = result.translation;
		if (result.build.exitCode != 0) {
			result.buildFailed = true;
			return result;
		}
		execute(result, selected, log, progress);
		return result;
	}

	/** The tests the filter names: classes, Class.method or Class#method, or methods */
	static List<GeneratedTest> select(List<GeneratedTest> tests,
			List<String> filter) {
		if (filter.isEmpty()) {
			return tests;
		}
		List<GeneratedTest> selected = new ArrayList<GeneratedTest>();
		for (String entry : filter) {
			String name = entry.trim();
			List<GeneratedTest> matches = new ArrayList<GeneratedTest>();
			int hash = name.indexOf('#');
			int dot = name.lastIndexOf('.');
			for (GeneratedTest test : tests) {
				boolean match;
				if (hash >= 0) {
					match = isClass(test, name.substring(0, hash))
							&& test.method.equals(name.substring(hash + 1));
				} else {
					match = isClass(test, name) || dot < 0
							&& test.method.equals(name) || dot > 0
							&& isClass(test, name.substring(0, dot))
							&& test.method.equals(name.substring(dot + 1));
				}
				if (match) {
					matches.add(test);
				}
			}
			if (matches.isEmpty()) {
				throw new IllegalArgumentException("The filter " + name
						+ " matches no JUnit test; give test classes (simple or "
						+ "qualified names), Class.method, Class#method or method "
						+ "names");
			}
			for (GeneratedTest test : matches) {
				if (!selected.contains(test)) {
					selected.add(test);
				}
			}
		}
		// In the binary's order
		List<GeneratedTest> ordered = new ArrayList<GeneratedTest>();
		for (GeneratedTest test : tests) {
			if (selected.contains(test)) {
				ordered.add(test);
			}
		}
		return ordered;
	}

	private static boolean isClass(GeneratedTest test, String name) {
		String qualified = test.javaClass;
		return qualified.equals(name)
				|| qualified.substring(qualified.lastIndexOf('.') + 1).equals(name)
				|| test.suite.equals(name);
	}

	/** Suite.* for wholly selected suites, else each Suite.name */
	private static String gtestFilter(List<GeneratedTest> tests,
			List<GeneratedTest> selected) {
		Map<String, List<GeneratedTest>> bySuite = new LinkedHashMap<String, List<GeneratedTest>>();
		for (GeneratedTest test : tests) {
			List<GeneratedTest> suite = bySuite.get(test.suite);
			if (suite == null) {
				suite = new ArrayList<GeneratedTest>();
				bySuite.put(test.suite, suite);
			}
			suite.add(test);
		}
		StringBuilder filter = new StringBuilder();
		for (Map.Entry<String, List<GeneratedTest>> suite : bySuite.entrySet()) {
			List<String> names = new ArrayList<String>();
			boolean all = true;
			for (GeneratedTest test : suite.getValue()) {
				if (selected.contains(test)) {
					names.add(test.gtestName());
				} else {
					all = false;
				}
			}
			if (all) {
				names = Collections.singletonList(suite.getKey() + ".*");
			}
			for (String name : names) {
				filter.append(filter.length() == 0 ? "" : ":").append(name);
			}
		}
		return filter.toString();
	}

	/** The GoogleTest binary the Makefile's tests target links */
	static File binary(File output) throws IOException {
		String makefile = new String(Files.readAllBytes(new File(output,
				"Makefile").toPath()), StandardCharsets.UTF_8);
		Matcher name = MAKE_NAME.matcher(makefile);
		if (!name.find()) {
			throw new IOException("No NAME in " + new File(output, "Makefile"));
		}
		File bin = new File(output, "bin");
		String base = name.group(1).trim() + "-tests";
		if (isWindows()) {
			// A native Windows toolchain adds .exe; a wrapper script is a .cmd
			for (String extension : new String[] { ".exe", ".cmd" }) {
				File candidate = new File(bin, base + extension);
				if (candidate.isFile()) {
					return candidate;
				}
			}
		}
		return new File(bin, base);
	}

	private static boolean isWindows() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT)
				.startsWith("windows");
	}

	/** Runs the binary, then reads its report (or, without one, its output) */
	private static void execute(Result result, List<GeneratedTest> selected,
			final PrintStream log, Progress progress) throws Exception {
		File output = result.translation.output;
		Builder.Result build = result.build;
		result.binary = binary(output);
		if (!result.binary.isFile()) {
			throw new Builder.BuildException("make tests did not produce "
					+ result.binary);
		}
		File report = new File(result.binary.getPath() + ".json");
		Files.deleteIfExists(report.toPath());

		List<String> command = new ArrayList<String>(build.prefix);
		if (build.wsl && result.timeout > 0) {
			// Killing wsl.exe need not stop the Linux processes
			command.add("timeout");
			command.add("-k");
			command.add(String.valueOf(KILL_AFTER));
			command.add(String.valueOf(result.timeout));
		}
		command.add(build.wsl ? Builder.wslPath(result.binary) : result.binary
				.getPath());
		command.add("--gtest_output=json:" + (build.wsl ? Builder.wslPath(report)
				: report.getPath()));
		command.add("--gtest_color=no");
		if (result.gtestFilter != null) {
			command.add("--gtest_filter=" + result.gtestFilter);
		}
		result.command = command;

		Map<String, GeneratedTest> byName = new LinkedHashMap<String, GeneratedTest>();
		for (GeneratedTest test : selected) {
			byName.put(test.gtestName(), test);
		}

		ProcessBuilder process = new ProcessBuilder(command).directory(output)
				.redirectErrorStream(true);
		process.environment().put("LC_ALL", "C");
		progress.begin(selected.size(), "Running " + selected.size()
				+ " test(s)");
		long started = System.nanoTime();
		final Process running = process.start();
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
					// The tests ended
				}
			}
		});
		reader.setDaemon(true);
		reader.start();
		// A WSL timeout stops the tests itself; this one is the backstop
		long limit = result.timeout == 0 ? Long.MAX_VALUE : TimeUnit.SECONDS
				.toNanos(result.timeout + (build.wsl ? KILL_AFTER + 5 : 0));
		int reported = 0;
		boolean exited = false;
		while (!exited) {
			exited = running.waitFor(100, TimeUnit.MILLISECONDS);
			if (progress.cancelled()) {
				Builder.stop(running);
				progress.checkCancelled();
			}
			if (!exited && System.nanoTime() - started > limit) {
				Builder.stop(running);
				result.timedOut = true;
				exited = true;
			}
			reported = reportFinished(lines, reported, byName, progress);
		}
		reader.join(10000);
		result.exitCode = running.exitValue();
		long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
		if (build.wsl && result.timeout > 0 && (result.exitCode == 124
				|| result.exitCode == 137)
				&& elapsed >= TimeUnit.SECONDS.toMillis(result.timeout) - 500) {
			result.timedOut = true;
		}

		List<String> all;
		synchronized (lines) {
			all = new ArrayList<String>(lines);
		}
		int from = Math.max(0, all.size() - Builder.LOG_LINES);
		result.logTruncated = from > 0;
		result.log.addAll(all.subList(from, all.size()));

		Map<String, TestResult> console = parseConsole(all, byName);
		if (report.isFile()) {
			result.reportRead = true;
			parseReport(new String(Files.readAllBytes(report.toPath()),
					StandardCharsets.UTF_8), byName, console, result.tests);
		} else {
			// Crashed or stopped: what the console shows, then the rest
			for (TestResult test : console.values()) {
				if (test.unfinished) {
					test.message = result.timedOut ? "Did not finish within the "
							+ "timeout of " + result.timeout + " s; the test process "
							+ "was stopped" : "The test process ended (exit code "
							+ result.exitCode + ") before this test finished";
				}
				result.tests.add(test);
			}
			for (GeneratedTest test : selected) {
				if (!console.containsKey(test.gtestName())) {
					TestResult notRun = new TestResult(test.suite, test.name, test);
					notRun.status = NOT_RUN;
					result.tests.add(notRun);
				}
			}
		}
		for (TestResult test : result.tests) {
			if (DISABLED.equals(test.status)) {
				// The reason given in Java, rather than the wrapper's text
				String reason = test.java.disabledReason;
				test.message = reason.isEmpty() ? null : reason;
			}
		}
	}

	/** Reports each test that finished since the last call */
	private static int reportFinished(List<String> lines, int reported,
			Map<String, GeneratedTest> byName, Progress progress) {
		List<String> finished = new ArrayList<String>();
		int seen;
		synchronized (lines) {
			seen = lines.size();
			for (int i = reported; i < seen; ++i) {
				Matcher end = END.matcher(lines.get(i));
				if (end.matches()) {
					GeneratedTest test = byName.get(end.group(2) + "."
							+ end.group(3));
					String name = test == null ? end.group(2) + "." + end.group(3)
							: test.javaClass + "." + test.method;
					finished.add(name + " " + status(end.group(1), test).toLowerCase(
							Locale.ROOT));
				}
			}
		}
		for (String message : finished) {
			progress.worked(1, message);
		}
		return seen;
	}

	private static String status(String gtest, GeneratedTest test) {
		if ("OK".equals(gtest)) {
			return PASSED;
		}
		if ("FAILED".equals(gtest)) {
			return FAILED;
		}
		return test != null && test.disabledReason != null ? DISABLED : SKIPPED;
	}

	/**
	 * The results the console shows, with skip messages (which the JSON report
	 * lacks); a test that started but never finished failed with the process
	 */
	private static Map<String, TestResult> parseConsole(List<String> lines,
			Map<String, GeneratedTest> byName) {
		Map<String, TestResult> results = new LinkedHashMap<String, TestResult>();
		TestResult current = null;
		List<String> body = new ArrayList<String>();
		for (String line : lines) {
			Matcher matcher = RUN.matcher(line);
			if (matcher.matches()) {
				current = new TestResult(matcher.group(1), matcher.group(2),
						byName.get(matcher.group(1) + "." + matcher.group(2)));
				body.clear();
				results.put(current.suite + "." + current.name, current);
				continue;
			}
			matcher = END.matcher(line);
			if (current != null && matcher.matches()
					&& matcher.group(2).equals(current.suite)
					&& matcher.group(3).equals(current.name)) {
				current.status = status(matcher.group(1), current.java);
				current.timeMillis = Long.parseLong(matcher.group(4));
				if (!PASSED.equals(current.status)) {
					consoleMessage(current, body);
				}
				current = null;
				continue;
			}
			if (current != null) {
				body.add(line);
			}
		}
		if (current != null) {
			// Still running when the process ended
			current.status = FAILED;
			current.unfinished = true;
			consoleMessage(current, body);
		}
		return results;
	}

	/** The text after GoogleTest's "file:line: Failure" (or Skipped) lines */
	private static void consoleMessage(TestResult result, List<String> body) {
		StringBuilder text = new StringBuilder();
		boolean inside = false;
		for (String line : body) {
			if (FAILURE_START.matcher(line).matches()) {
				inside = true;
				continue;
			}
			if (inside && !(text.length() == 0 && line.equals("Failed"))) {
				text.append(text.length() == 0 ? "" : "\n").append(line);
			}
		}
		String message = text.toString().replaceAll("\\n+$", "");
		if (SKIPPED.equals(result.status) || DISABLED.equals(result.status)) {
			result.message = message.isEmpty() ? null : message;
		} else if (!message.isEmpty()) {
			failure(result, message, message);
		}
	}

	/**
	 * Sets the failure text, and the Java message and exception the first
	 * failure shows
	 */
	private static void failure(TestResult result, String first, String text) {
		result.failure = text;
		Matcher thrown = THROWN.matcher(first);
		if (thrown.matches()) {
			result.exception = thrown.group(1);
			result.message = thrown.group(2) == null ? "" : thrown.group(2);
		} else {
			result.message = first;
		}
	}

	/** Reads GoogleTest's JSON report (--gtest_output=json:...) */
	private static void parseReport(String text,
			Map<String, GeneratedTest> byName, Map<String, TestResult> console,
			List<TestResult> results) {
		Object parsed = Json.parse(text);
		if (!(parsed instanceof Map)) {
			return;
		}
		Object suites = ((Map<?, ?>) parsed).get("testsuites");
		if (!(suites instanceof List)) {
			return;
		}
		for (Object suite : (List<?>) suites) {
			Map<?, ?> suiteJson = (Map<?, ?>) suite;
			Object tests = suiteJson.get("testsuite");
			if (!(tests instanceof List)) {
				continue;
			}
			String suiteName = String.valueOf(suiteJson.get("name"));
			for (Object test : (List<?>) tests) {
				Map<?, ?> testJson = (Map<?, ?>) test;
				String name = String.valueOf(testJson.get("name"));
				if ("NOTRUN".equals(testJson.get("status"))) {
					continue; // GoogleTest's own DISABLED_ tests
				}
				TestResult result = new TestResult(suiteName, name, byName.get(
						suiteName + "." + name));
				result.timeMillis = millis(testJson.get("time"));
				Object failures = testJson.get("failures");
				if ("SKIPPED".equals(testJson.get("result"))) {
					result.status = result.java != null
							&& result.java.disabledReason != null ? DISABLED
							: SKIPPED;
					TestResult shown = console.get(suiteName + "." + name);
					result.message = shown == null ? null : shown.message;
				} else if (failures instanceof List && !((List<?>) failures)
						.isEmpty()) {
					result.status = FAILED;
					StringBuilder all = new StringBuilder();
					String first = null;
					for (Object failure : (List<?>) failures) {
						// "<file>:<line>\nFailed\n<text>\n"
						String message = String.valueOf(((Map<?, ?>) failure)
								.get("failure")).replaceFirst("^[^\\n]*\\n(?:Failed\\n)?", "")
								.replaceFirst("\\n$", "");
						all.append(all.length() == 0 ? "" : "\n\n").append(message);
						if (first == null) {
							first = message;
						}
					}
					failure(result, first, all.toString());
				} else {
					result.status = PASSED;
				}
				results.add(result);
			}
		}
	}

	/** GoogleTest's "0.012s" in milliseconds */
	private static long millis(Object time) {
		if (!(time instanceof String)) {
			return 0;
		}
		try {
			return Math.round(Double.parseDouble(((String) time).replaceFirst(
					"s$", "")) * 1000);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	// JSON

	static Map<String, Object> json(Result result) {
		Map<String, Object> json = J2cService.header("test");
		String status;
		Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
		for (String name : new String[] { PASSED, FAILED, SKIPPED, DISABLED,
				NOT_RUN }) {
			counts.put(name, 0);
		}
		long time = 0;
		List<Object> tests = new ArrayList<Object>();
		for (TestResult test : result.tests) {
			counts.put(test.status, counts.get(test.status) + 1);
			time += test.timeMillis;
			tests.add(testJson(test));
		}
		if (result.buildFailed) {
			status = "build-failed";
		} else if (result.timedOut) {
			status = "timeout";
		} else if (result.exitCode != 0 || counts.get(FAILED) > 0
				|| counts.get(NOT_RUN) > 0) {
			status = "failed";
		} else {
			status = "ok";
		}
		json.put("status", status);
		json.put("output", result.translation.output.getPath());
		json.put("testFiles", testFiles(result.translation));
		json.put("binary", result.binary == null ? null : result.binary.getPath());
		json.put("testCommand", result.command);
		json.put("gtestFilter", result.gtestFilter);
		json.put("timeout", result.timeout);
		json.put("timedOut", result.timedOut);
		json.put("exitCode", result.buildFailed ? null : result.exitCode);
		json.put("reportRead", result.reportRead);
		json.put("tests", tests);
		Map<String, Object> summary = new LinkedHashMap<String, Object>();
		summary.put("total", result.tests.size());
		summary.putAll(counts);
		summary.put("timeMillis", time);
		summary.put("exitCode", result.buildFailed ? null : result.exitCode);
		json.put("summary", summary);
		// The build's diagnostics; its log only when it failed
		json.put("build", Builder.body(result.build, result.buildFailed));
		json.put("log", result.buildFailed ? Collections.emptyList() : result.log);
		json.put("logTruncated", result.logTruncated);
		J2cService.putProjects(json, result.translation.projects);
		return json;
	}

	private static List<String> testFiles(J2cService.TranslateResult translation) {
		List<String> files = new ArrayList<String>();
		for (String file : translation.generatedFiles) {
			if (file.startsWith("test/") && file.endsWith("_test.cpp")) {
				files.add(file);
			}
		}
		return files;
	}

	private static Map<String, Object> testJson(TestResult test) {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		GeneratedTest java = test.java;
		json.put("javaClass", java == null ? null : java.javaClass);
		json.put("javaMethod", java == null ? null : java.method);
		json.put("declaringClass", java == null ? null : java.declaringClass);
		json.put("file", java == null || java.file == null ? null : java.file
				.getPath());
		json.put("line", java == null || java.line == 0 ? null : java.line);
		json.put("status", test.status);
		json.put("message", test.message);
		json.put("exception", test.exception);
		json.put("failure", test.failure);
		json.put("disabledReason", java == null ? null : java.disabledReason);
		json.put("timeMillis", test.timeMillis);
		json.put("cppTest", test.suite + "." + test.name);
		json.put("cppFile", java == null ? null : java.wrapper);
		return json;
	}

	/** One line per test that did not pass, at its Java location, then a summary */
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
			text.append("J2C test: the build failed: make exited with ").append(
					result.build.exitCode).append(", ").append(errors).append(
					" error(s)\n");
			return text.toString();
		}
		Map<String, Object> json = json(result);
		for (TestResult test : result.tests) {
			if (PASSED.equals(test.status)) {
				continue;
			}
			GeneratedTest java = test.java;
			if (java != null && java.file != null) {
				text.append(java.file.getPath()).append(':').append(java.line)
						.append(": ");
			}
			text.append(test.status).append(": ").append(java == null ? test.suite
					+ "." + test.name : java.javaClass + "." + java.method);
			String message = test.message;
			if (message != null && !message.isEmpty()) {
				text.append(": ").append(message.replace("\n", "\n    "));
			}
			text.append('\n');
		}
		Map<?, ?> summary = (Map<?, ?>) json.get("summary");
		text.append("J2C test: ").append(summary.get(PASSED)).append(" passed, ")
				.append(summary.get(FAILED)).append(" failed, ").append(
						summary.get(SKIPPED)).append(" skipped, ").append(
						summary.get(DISABLED)).append(" disabled");
		if (!summary.get(NOT_RUN).equals(0)) {
			text.append(", ").append(summary.get(NOT_RUN)).append(" not run");
		}
		text.append(" (exit code ").append(result.exitCode).append(
				result.timedOut ? ", stopped after the timeout of "
						+ result.timeout + " s" : "").append(")\n");
		return text.toString();
	}
}
