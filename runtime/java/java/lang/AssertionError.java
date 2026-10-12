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

	public AssertionError(boolean detailMessage) {
		super("" + detailMessage);
	}

	public AssertionError(char detailMessage) {
		super("" + detailMessage);
	}

	public AssertionError(int detailMessage) {
		super("" + detailMessage);
	}

	public AssertionError(long detailMessage) {
		super("" + detailMessage);
	}

	public AssertionError(float detailMessage) {
		super("" + detailMessage);
	}

	public AssertionError(double detailMessage) {
		super("" + detailMessage);
	}

	public AssertionError(String message, Throwable cause) {
		super(message, cause);
	}
}
