package se.arnetheduck.j2c.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.OperationCanceledException;

import se.arnetheduck.j2c.transform.Fix;
import se.arnetheduck.j2c.transform.Problem;
import se.arnetheduck.j2c.transform.ProblemCatalog;
import se.arnetheduck.j2c.transform.SourceEdit;
import se.arnetheduck.j2c.transform.SourceText;

/**
 * The fix command: checks the inputs, and previews or applies the mechanical
 * fixes of their problems. Applying repeats check and fix until no fixable
 * problem is left, since overlapping fixes are applied one per round, and
 * undoes a round that adds compile errors.
 */
final class Fixer {
	/** Rounds of applying, which overlapping fixes need */
	private static final int MAX_ROUNDS = 5;

	static final class Result {
		boolean applied;
		int fileCount;
		/** Problems whose first fix was applied, or would be */
		final List<Problem> fixed = new ArrayList<Problem>();
		/** Problems without a fix, left for a person */
		List<Problem> remaining = new ArrayList<Problem>();
		/**
		 * Previewed problems whose fix overlaps one before it, which applying
		 * does in a later round
		 */
		final List<Problem> deferred = new ArrayList<Problem>();
		/** Changed files: the original text and the fixed text */
		final Map<File, String[]> changes = new LinkedHashMap<File, String[]>();
		/** Why applying stopped early, or null */
		String message;
	}

	/** The fixes of a round that can be applied together */
	private static final class Plan {
		final Map<File, List<SourceEdit>> edits = new LinkedHashMap<File, List<SourceEdit>>();
		final List<Problem> fixed = new ArrayList<Problem>();
		final List<Problem> unfixed = new ArrayList<Problem>();
		final List<Problem> conflicting = new ArrayList<Problem>();
	}

	private final J2cService service;
	/** Diffs name files relative to this directory */
	private final File base;

	Fixer(J2cService service, File base) {
		this.service = service;
		this.base = base;
	}

	Result fix(J2cService.Request request, boolean apply) throws Exception {
		return fix(request, apply, Progress.NONE);
	}

	/**
	 * Like fix. Cancelling stops at the next check; a round whose check was
	 * cancelled is undone, so the files keep the fixes of the rounds before.
	 */
	Result fix(J2cService.Request request, boolean apply, Progress progress)
			throws Exception {
		Result result = new Result();
		result.applied = apply;
		J2cService.CheckResult check = service.check(request, progress);
		result.fileCount = check.fileCount;
		if (!apply) {
			Plan plan = plan(check.problems);
			for (Map.Entry<File, List<SourceEdit>> file : plan.edits.entrySet()) {
				String before = read(file.getKey(), request);
				result.changes.put(file.getKey(), new String[] { before,
						edit(before, file.getValue(), file.getKey()) });
			}
			result.fixed.addAll(plan.fixed);
			result.remaining = plan.unfixed;
			result.deferred.addAll(plan.conflicting);
			return result;
		}

		Map<File, String> originals = new LinkedHashMap<File, String>();
		for (int round = 0; round < MAX_ROUNDS; ++round) {
			Plan plan = plan(check.problems);
			if (plan.fixed.isEmpty()) {
				break;
			}
			Map<File, String> before = new LinkedHashMap<File, String>();
			for (Map.Entry<File, List<SourceEdit>> file : plan.edits.entrySet()) {
				service.requireInsideRoot(file.getKey(), "Fixed file");
				String text = read(file.getKey(), request);
				before.put(file.getKey(), text);
				if (!originals.containsKey(file.getKey())) {
					originals.put(file.getKey(), text);
				}
			}
			progress.checkCancelled();
			J2cService.CheckResult next;
			try {
				for (Map.Entry<File, List<SourceEdit>> file : plan.edits
						.entrySet()) {
					write(file.getKey(), edit(before.get(file.getKey()),
							file.getValue(), file.getKey()), request);
				}
				next = service.check(request, progress);
			} catch (OperationCanceledException e) {
				// An unchecked round is not kept
				for (Map.Entry<File, String> file : before.entrySet()) {
					write(file.getKey(), file.getValue(), request);
				}
				throw e;
			}
			if (count(next.problems, true) > count(check.problems, true)) {
				// A fix that breaks compilation is a J2C bug; keep the user's code
				for (Map.Entry<File, String> file : before.entrySet()) {
					write(file.getKey(), file.getValue(), request);
				}
				result.message = "Fixes for " + plan.fixed.size() + " problem(s) "
						+ "were undone because they caused compile errors; please "
						+ "report this as a J2C bug.";
				break;
			}
			result.fixed.addAll(plan.fixed);
			boolean fewer = count(next.problems, false) < count(
					check.problems, false);
			check = next;
			if (!fewer) {
				break;
			}
		}
		for (Map.Entry<File, String> file : originals.entrySet()) {
			String after = read(file.getKey(), request);
			if (!after.equals(file.getValue())) {
				result.changes.put(file.getKey(), new String[] {
						file.getValue(), after });
			}
		}
		result.remaining = check.problems;
		return result;
	}

	/** Compile errors, or problems with a fix */
	private static int count(List<Problem> problems, boolean compileErrors) {
		int count = 0;
		for (Problem problem : problems) {
			if (compileErrors ? ProblemCatalog.COMPILE_ERROR.equals(problem.id)
					: !problem.fixes.isEmpty()) {
				++count;
			}
		}
		return count;
	}

	/**
	 * Each problem's first fix, unless it overlaps a fix taken before it.
	 * Edits that two fixes share, such as one declaration that both need, are
	 * applied once.
	 */
	private static Plan plan(List<Problem> problems) {
		Plan plan = new Plan();
		for (Problem problem : problems) {
			if (problem.fixes.isEmpty()) {
				plan.unfixed.add(problem);
				continue;
			}
			Fix fix = problem.fixes.get(0);
			List<SourceEdit> added = new ArrayList<SourceEdit>();
			boolean conflict = false;
			for (SourceEdit edit : fix.edits) {
				List<SourceEdit> taken = plan.edits.get(key(edit.file));
				if (taken != null && taken.contains(edit)) {
					continue;
				}
				for (SourceEdit other : taken == null ? added : concat(taken,
						added)) {
					conflict |= other.overlaps(edit);
				}
				added.add(edit);
			}
			if (conflict) {
				plan.conflicting.add(problem);
				continue;
			}
			for (SourceEdit edit : added) {
				List<SourceEdit> taken = plan.edits.get(key(edit.file));
				if (taken == null) {
					taken = new ArrayList<SourceEdit>();
					plan.edits.put(key(edit.file), taken);
				}
				taken.add(edit);
			}
			plan.fixed.add(problem);
		}
		return plan;
	}

	private static List<SourceEdit> concat(List<SourceEdit> a,
			List<SourceEdit> b) {
		List<SourceEdit> all = new ArrayList<SourceEdit>(a);
		all.addAll(b);
		return all;
	}

	private static File key(File file) {
		return file.getAbsoluteFile();
	}

	/** The text with the edits applied, keeping a byte order mark first */
	private static String edit(String text, List<SourceEdit> edits, File file) {
		boolean bom = text.startsWith("﻿");
		String edited = new SourceText(bom ? text.substring(1) : text)
				.apply(edits);
		if (edited == null) {
			throw new IllegalStateException("A fix does not match " + file
					+ "; it may have changed since it was checked");
		}
		return bom ? "﻿" + edited : edited;
	}

	private static String read(File file, J2cService.Request request)
			throws IOException {
		return new String(Files.readAllBytes(file.toPath()), request.encoding);
	}

	private static void write(File file, String text,
			J2cService.Request request) throws IOException {
		Files.write(file.toPath(), text.getBytes(request.encoding));
	}

	/** The changes as one unified diff */
	String diff(Result result) throws IOException {
		StringBuilder diff = new StringBuilder();
		for (Map.Entry<File, String[]> change : result.changes.entrySet()) {
			diff.append(UnifiedDiff.diff(displayPath(change.getKey()),
					change.getValue()[0], change.getValue()[1]));
		}
		return diff.toString();
	}

	/** The path relative to the base directory, with / separators */
	String displayPath(File file) throws IOException {
		String path = file.getCanonicalPath();
		String basePath = base.getCanonicalPath();
		if (!basePath.endsWith(File.separator)) {
			basePath += File.separator;
		}
		if (path.startsWith(basePath)) {
			path = path.substring(basePath.length());
		}
		return path.replace(File.separatorChar, '/');
	}

	Map<String, Object> json(Result result) throws IOException {
		Map<String, Object> json = J2cService.header("fix");
		json.put("status", result.remaining.isEmpty() ? "ok" : "problems");
		json.put("applied", result.applied);
		json.put("files", result.fileCount);
		List<Object> fixed = new ArrayList<Object>();
		for (Problem problem : result.fixed) {
			Map<String, Object> item = new LinkedHashMap<String, Object>();
			item.put("id", problem.id);
			item.put("file", problem.file.getPath());
			item.put("line", problem.line);
			item.put("column", problem.column);
			item.put("title", problem.fixes.get(0).title);
			fixed.add(item);
		}
		json.put("fixed", fixed);
		List<Object> changed = new ArrayList<Object>();
		for (File file : result.changes.keySet()) {
			changed.add(file.getPath());
		}
		json.put("changedFiles", changed);
		json.put("deferred", J2cService.problemsJson(result.deferred));
		json.put("diff", diff(result));
		json.put("problems", J2cService.problemsJson(result.remaining));
		json.put("summary", J2cService.summaryJson(result.remaining));
		if (result.message != null) {
			json.put("message", result.message);
		}
		return json;
	}
}
