package se.arnetheduck.j2c.transform;

import java.io.File;

/**
 * Something in the Java input that stops translation, such as a compile error
 * or unsupported syntax. Printed as "path:line: message" so editors and the
 * VS Code extension can link it to the source.
 */
public class Problem {
	/** Stable identifier from {@link ProblemCatalog} */
	public final String id;
	/** Groups problems for summaries, e.g. "lambda expression" */
	public final String kind;
	public final String message;
	public final File file;
	public final int line;
	/** 1-based, or 0 when unknown */
	public final int column;

	private Problem(String id, String kind, String message, File file,
			int line, int column) {
		this.id = id;
		this.kind = kind;
		this.message = message;
		this.file = file;
		this.line = line;
		this.column = column;
	}

	/** A problem whose location is supplied later with {@link #at} */
	public static Problem of(String id, String kind, String message) {
		return new Problem(id, kind, message, null, 0, 0);
	}

	public Problem at(File file, int line, int column) {
		return new Problem(id, kind, message, file, line, column);
	}

	/** How to fix it, from the catalog */
	public String hint() {
		ProblemCatalog.Entry entry = ProblemCatalog.get(id);
		return entry == null ? null : entry.hint;
	}

	@Override
	public String toString() {
		return file.getPath() + ":" + line + ": " + message;
	}
}
