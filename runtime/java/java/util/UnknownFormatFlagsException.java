package java.util;

/** UnknownFormatFlagsException, written from the Java SE specification. */
public class UnknownFormatFlagsException extends IllegalFormatException {
	private final String flags;

	public UnknownFormatFlagsException(String flags) {
		this.flags = Objects.requireNonNull(flags);
	}

	public String getFlags() {
		return flags;
	}

	public String getMessage() {
		return "Flags = " + flags;
	}
}
