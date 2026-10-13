package java.util;

/** DuplicateFormatFlagsException, written from the Java SE specification. */
public class DuplicateFormatFlagsException extends IllegalFormatException {
	private final String flags;

	public DuplicateFormatFlagsException(String flags) {
		this.flags = Objects.requireNonNull(flags);
	}

	public String getFlags() {
		return flags;
	}

	public String getMessage() {
		return "Flags = '" + flags + "'";
	}
}
