package java.util;

/** IllegalFormatConversionException, written from the Java SE specification. */
public class IllegalFormatConversionException extends IllegalFormatException {
	private final char c;
	private final Class<?> arg;

	public IllegalFormatConversionException(char c, Class<?> arg) {
		this.c = c;
		this.arg = Objects.requireNonNull(arg);
	}

	public char getConversion() {
		return c;
	}

	public Class<?> getArgumentClass() {
		return arg;
	}

	public String getMessage() {
		return c + " != " + arg.getName();
	}
}
