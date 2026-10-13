package java.util;

/** MissingFormatArgumentException, written from the Java SE specification. */
public class MissingFormatArgumentException extends IllegalFormatException {
	private final String s;

	public MissingFormatArgumentException(String s) {
		this.s = Objects.requireNonNull(s);
	}

	public String getFormatSpecifier() {
		return s;
	}

	public String getMessage() {
		return "Format specifier '" + s + "'";
	}
}
