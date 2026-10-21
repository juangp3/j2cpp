package java.lang;

/**
 * Throwable, written from the Java SE specification. No stack frames are
 * recorded: getStackTrace() returns an empty array unless setStackTrace()
 * stored one, and printStackTrace() prints only the "\tat" lines of such a
 * stored trace. Suppressed exceptions and causes print as on the JVM.
 */
public class Throwable implements java.io.Serializable {
	private static final Throwable[] NO_THROWABLES = new Throwable[0];
	private static final StackTraceElement[] NO_FRAMES = new StackTraceElement[0];

	private final String detailMessage;
	private Throwable cause;
	/** Whether the cause was set, by a constructor or initCause */
	private boolean causeSet;
	private final boolean enableSuppression;
	private final boolean writableStackTrace;
	private Throwable[] suppressed = NO_THROWABLES;
	private StackTraceElement[] stackTrace = NO_FRAMES;

	public Throwable() {
		this(null, null, false, true, true);
	}

	public Throwable(String message) {
		this(message, null, false, true, true);
	}

	public Throwable(String message, Throwable cause) {
		this(message, cause, true, true, true);
	}

	public Throwable(Throwable cause) {
		this(cause == null ? null : cause.toString(), cause, true, true, true);
	}

	protected Throwable(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		this(message, cause, true, enableSuppression, writableStackTrace);
	}

	private Throwable(String message, Throwable cause, boolean causeSet, boolean enableSuppression,
			boolean writableStackTrace) {
		this.detailMessage = message;
		this.cause = cause;
		this.causeSet = causeSet;
		this.enableSuppression = enableSuppression;
		this.writableStackTrace = writableStackTrace;
		if (writableStackTrace) {
			fillInStackTrace();
		}
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
		if (causeSet) {
			throw new IllegalStateException("Can't overwrite cause with "
					+ (cause == null ? "a null" : cause.toString()), this);
		}
		if (cause == this) {
			throw new IllegalArgumentException("Self-causation not permitted", this);
		}
		this.cause = cause;
		this.causeSet = true;
		return this;
	}

	public String toString() {
		String message = getLocalizedMessage();
		String name = getClass().getName();
		return message != null ? name + ": " + message : name;
	}

	/** No frames are recorded, so this only returns this throwable */
	public Throwable fillInStackTrace() {
		return this;
	}

	public StackTraceElement[] getStackTrace() {
		StackTraceElement[] copy = new StackTraceElement[stackTrace.length];
		for (int i = 0; i < copy.length; i++) {
			copy[i] = stackTrace[i];
		}
		return copy;
	}

	public void setStackTrace(StackTraceElement[] stackTrace) {
		StackTraceElement[] copy = new StackTraceElement[stackTrace.length];
		for (int i = 0; i < copy.length; i++) {
			if (stackTrace[i] == null) {
				throw new NullPointerException("stackTrace[" + i + "]");
			}
			copy[i] = stackTrace[i];
		}
		if (writableStackTrace) {
			this.stackTrace = copy;
		}
	}

	public final void addSuppressed(Throwable exception) {
		if (exception == this) {
			throw new IllegalArgumentException("Self-suppression not permitted", exception);
		}
		if (exception == null) {
			throw new NullPointerException("Cannot suppress a null exception.");
		}
		if (!enableSuppression) {
			return;
		}
		Throwable[] grown = new Throwable[suppressed.length + 1];
		for (int i = 0; i < suppressed.length; i++) {
			grown[i] = suppressed[i];
		}
		grown[suppressed.length] = exception;
		suppressed = grown;
	}

	public final Throwable[] getSuppressed() {
		if (suppressed.length == 0) {
			return NO_THROWABLES;
		}
		Throwable[] copy = new Throwable[suppressed.length];
		for (int i = 0; i < copy.length; i++) {
			copy[i] = suppressed[i];
		}
		return copy;
	}

	public void printStackTrace() {
		printStackTrace(System.err);
	}

	public void printStackTrace(java.io.PrintStream s) {
		// As in the JDK, the whole trace under the stream's monitor
		synchronized (s) {
			Throwable[] seen = new Throwable[] { this };
			s.println(this);
			printFrames(s, "");
			Throwable[] ours = getSuppressed();
			for (int i = 0; i < ours.length; i++) {
				seen = ours[i].printEnclosed(s, "Suppressed: ", "\t", seen);
			}
			Throwable c = getCause();
			if (c != null) {
				c.printEnclosed(s, "Caused by: ", "", seen);
			}
		}
	}

	/** Prints a suppressed exception or a cause; returns the throwables seen so far */
	private Throwable[] printEnclosed(java.io.PrintStream s, String caption, String prefix, Throwable[] seen) {
		for (int i = 0; i < seen.length; i++) {
			if (seen[i] == this) {
				s.println(prefix + caption + "[CIRCULAR REFERENCE: " + this + "]");
				return seen;
			}
		}
		Throwable[] grown = new Throwable[seen.length + 1];
		for (int i = 0; i < seen.length; i++) {
			grown[i] = seen[i];
		}
		grown[seen.length] = this;
		seen = grown;

		s.println(prefix + caption + this);
		printFrames(s, prefix);
		Throwable[] ours = getSuppressed();
		for (int i = 0; i < ours.length; i++) {
			seen = ours[i].printEnclosed(s, "Suppressed: ", prefix + "\t", seen);
		}
		Throwable c = getCause();
		if (c != null) {
			seen = c.printEnclosed(s, "Caused by: ", prefix, seen);
		}
		return seen;
	}

	private void printFrames(java.io.PrintStream s, String prefix) {
		for (int i = 0; i < stackTrace.length; i++) {
			s.println(prefix + "\tat " + stackTrace[i]);
		}
	}
}
