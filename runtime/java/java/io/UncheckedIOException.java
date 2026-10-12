package java.io;

/** Wraps an IOException; the cause is required */
public class UncheckedIOException extends RuntimeException {
	public UncheckedIOException(String message, IOException cause) {
		super(message, requireCause(cause));
	}

	public UncheckedIOException(IOException cause) {
		super(requireCause(cause));
	}

	private static IOException requireCause(IOException cause) {
		if (cause == null) {
			throw new NullPointerException();
		}
		return cause;
	}

	public IOException getCause() {
		return (IOException) super.getCause();
	}
}
