package se.arnetheduck.j2c.transform;

/**
 * A problem in the Java input, such as a compile error or unsupported syntax,
 * reported to the user without a stack trace.
 */
public class TranslationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public TranslationException(String message) {
		super(message);
	}
}
