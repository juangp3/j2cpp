package java.lang;

/** Simplified Throwable: no stack trace is recorded. */
public class Throwable implements java.io.Serializable {
	private final String detailMessage;
	private Throwable cause;

	public Throwable() {
		this.detailMessage = null;
	}

	public Throwable(String message) {
		this.detailMessage = message;
	}

	public Throwable(String message, Throwable cause) {
		this.detailMessage = message;
		this.cause = cause;
	}

	public Throwable(Throwable cause) {
		this.detailMessage = cause == null ? null : cause.toString();
		this.cause = cause;
	}

	public String getMessage() {
		return detailMessage;
	}

	public String getLocalizedMessage() {
		return getMessage();
	}

	public Throwable getCause() {
		return cause;
	}

	public Throwable initCause(Throwable cause) {
		if (cause == this) {
			throw new IllegalArgumentException("Self-causation not permitted");
		}
		this.cause = cause;
		return this;
	}

	public String toString() {
		String message = getLocalizedMessage();
		String name = getClass().getName();
		return message != null ? name + ": " + message : name;
	}

	/** Prints this throwable and its causes; there are no "\tat" frames */
	public void printStackTrace() {
		System.err.println(toString());
		for (Throwable c = cause; c != null && c != this; c = c.getCause()) {
			System.err.println("Caused by: " + c);
		}
	}
}
