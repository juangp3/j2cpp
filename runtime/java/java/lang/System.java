package java.lang;

/** Simplified System: no initPhase1, properties or charsets. */
public final class System {
	private System() {
	}

	public static final java.io.PrintStream out = new java.io.PrintStream(new java.io.FileOutputStream(1));
	public static final java.io.PrintStream err = new java.io.PrintStream(new java.io.FileOutputStream(2));

	/** For the make_args hook: the String[] passed to main */
	static String[] j2cArgs(int count) {
		return new String[count];
	}
}
