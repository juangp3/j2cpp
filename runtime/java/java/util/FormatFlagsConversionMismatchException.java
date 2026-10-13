package java.util;

/** FormatFlagsConversionMismatchException, written from the Java SE specification. */
public class FormatFlagsConversionMismatchException extends IllegalFormatException {
	private final String f;
	private final char c;

	public FormatFlagsConversionMismatchException(String f, char c) {
		this.f = Objects.requireNonNull(f);
		this.c = c;
	}

	public String getFlags() {
		return f;
	}

	public char getConversion() {
		return c;
	}

	public String getMessage() {
		return "Conversion = " + c + ", Flags = " + f;
	}
}
