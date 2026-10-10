package se.arnetheduck.j2c.transform;

import java.io.File;

/**
 * Something in the Java input that stops translation, such as a compile error
 * or unsupported syntax. Printed as "path:line: message" so editors and the
 * VS Code extension can link it to the source.
 */
public class Problem {
	/** Groups problems for summaries, e.g. "lambda expression" */
	public final String kind;
	public final String message;
	public final File file;
	public final int line;

	private Problem(String kind, String message, File file, int line) {
		this.kind = kind;
		this.message = message;
		this.file = file;
		this.line = line;
	}

	/** A problem whose location is supplied later with {@link #at} */
	public static Problem of(String kind, String message) {
		return new Problem(kind, message, null, 0);
	}

	public Problem at(File file, int line) {
		return new Problem(kind, message, file, line);
	}

	@Override
	public String toString() {
		return file.getPath() + ":" + line + ": " + message;
	}
}
