package se.arnetheduck.j2c.cli;

import java.io.PrintStream;

/**
 * Keeps JDT's internal log out of J2C's output. Outside Eclipse, JDT prints
 * each status it logs to System.err as one "Status ERROR:
 * org.eclipse.jdt.core ..." line. Translating with a runtime and a dependency
 * JAR, it logs "Could not retrieve declared methods" with a
 * NullPointerException a few times: reading a JAR type's bases reaches the
 * runtime's own java.lang.Object or java.io.Serializable, whose compile scope
 * JDT has already released. The output does not change, but the lines look
 * like a failure. The first one is replaced by a one-line note, and the rest
 * are dropped.
 */
final class JdtLog extends PrintStream {
	private static final String PREFIX = "Status ";

	private static final String PLUGIN = " org.eclipse.jdt.core code=";

	private final PrintStream err;

	private boolean reported;

	private JdtLog(PrintStream err) {
		super(err, true);
		this.err = err;
	}

	/** Replaces System.err with a stream that filters JDT's log lines */
	static void install() {
		if (!(System.err instanceof JdtLog)) {
			System.setErr(new JdtLog(System.err));
		}
	}

	/** Whether a line is a status that JDT logged */
	private static boolean isJdtStatus(String line) {
		return line != null && line.startsWith(PREFIX)
				&& line.contains(PLUGIN);
	}

	@Override
	public void println(String line) {
		if (!isJdtStatus(line)) {
			err.println(line);
			return;
		}
		synchronized (this) {
			if (reported) {
				return;
			}
			reported = true;
		}
		err.println("J2C: note: JDT logged an internal error while reading "
				+ "types (" + message(line) + "); J2C goes on, and further "
				+ "ones are not shown.");
	}

	@Override
	public void print(String text) {
		// Not through this stream's own encoder: System.err's may differ
		err.print(text);
	}

	@Override
	public void println(Object value) {
		println(String.valueOf(value));
	}

	/** The status's message, without its severity, plugin and exception */
	private static String message(String line) {
		int start = line.indexOf(PLUGIN) + PLUGIN.length();
		int space = line.indexOf(' ', start);
		String message = space < 0 ? "" : line.substring(space + 1);
		int exception = message.indexOf(" java.");
		if (exception >= 0) {
			message = message.substring(0, exception);
		}
		return message.isEmpty() ? line : message;
	}
}
