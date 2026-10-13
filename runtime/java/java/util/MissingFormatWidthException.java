package java.util;

/** MissingFormatWidthException, written from the Java SE specification. */
public class MissingFormatWidthException extends IllegalFormatException {
	private final String s;

	public MissingFormatWidthException(String s) {
		this.s = Objects.requireNonNull(s);
	}

	public String getFormatSpecifier() {
		return s;
	}

	public String getMessage() {
		return s;
	}
}
