package java.lang;

public class AssertionError extends Error {
	public AssertionError() {
	}

	public AssertionError(Object detailMessage) {
		super(String.valueOf(detailMessage));
		if (detailMessage instanceof Throwable) {
			initCause((Throwable) detailMessage);
		}
	}

	public AssertionError(String message, Throwable cause) {
		super(message, cause);
	}
}
