package java.util;

/** UnknownFormatConversionException, written from the Java SE specification. */
public class UnknownFormatConversionException extends IllegalFormatException {
	private final String s;

	public UnknownFormatConversionException(String s) {
		this.s = Objects.requireNonNull(s);
	}

	public String getConversion() {
		return s;
	}

	public String getMessage() {
		return "Conversion = '" + s + "'";
	}
}
