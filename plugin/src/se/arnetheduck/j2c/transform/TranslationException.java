package se.arnetheduck.j2c.transform;

import java.util.Collections;
import java.util.List;

/**
 * A problem in the Java input, such as a compile error or unsupported syntax,
 * reported to the user without a stack trace.
 */
public class TranslationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final List<Problem> problems;

	public TranslationException(String message) {
		this(message, Collections.<Problem> emptyList());
	}

	public TranslationException(String message, List<Problem> problems) {
		super(message);
		this.problems = problems;
	}

	/** The problems behind this failure, when it came from the Java input */
	public List<Problem> problems() {
		return problems;
	}
}
