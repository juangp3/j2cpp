package java.util;

/** IllegalFormatFlagsException, written from the Java SE specification. */
public class IllegalFormatFlagsException extends IllegalFormatException {
	private final String flags;

	public IllegalFormatFlagsException(String flags) {
		this.flags = Objects.requireNonNull(flags);
	}

	public String getFlags() {
		return flags;
	}

	public String getMessage() {
		return "Flags = '" + flags + "'";
	}
}
