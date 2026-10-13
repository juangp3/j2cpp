package java.util;

/** IllegalFormatCodePointException, written from the Java SE specification. */
public class IllegalFormatCodePointException extends IllegalFormatException {
	private final int c;

	public IllegalFormatCodePointException(int c) {
		this.c = c;
	}

	public int getCodePoint() {
		return c;
	}

	public String getMessage() {
		return "Code point = 0x" + Integer.toHexString(c);
	}
}
