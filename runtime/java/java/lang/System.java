package java.lang;

/** Simplified System: no initPhase1, properties or charsets. */
public final class System {
	private System() {
	}

	// As the JDK sets them up; not final here, so setIn, setOut and setErr can replace them
	public static java.io.InputStream in = new java.io.BufferedInputStream(
			new java.io.FileInputStream(java.io.FileDescriptor.in));
	public static java.io.PrintStream out = new java.io.PrintStream(
			new java.io.BufferedOutputStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), 128), true);
	public static java.io.PrintStream err = new java.io.PrintStream(
			new java.io.BufferedOutputStream(new java.io.FileOutputStream(java.io.FileDescriptor.err), 128), true);

	public static void setIn(java.io.InputStream in) {
		System.in = in;
	}

	public static void setOut(java.io.PrintStream out) {
		System.out = out;
	}

	public static void setErr(java.io.PrintStream err) {
		System.err = err;
	}

	public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length) {
		if (src == null || dest == null) {
			throw new NullPointerException();
		}
		int srcLength = arrayLength(src);
		int destLength = arrayLength(dest);
		if (srcLength < 0) {
			throw new ArrayStoreException("arraycopy: source type " + src.getClass().getName()
					+ " is not an array");
		}
		if (destLength < 0) {
			throw new ArrayStoreException("arraycopy: destination type "
					+ dest.getClass().getName() + " is not an array");
		}
		if (length < 0) {
			throw new ArrayIndexOutOfBoundsException("arraycopy: length " + length + " is negative");
		}
		if (srcPos < 0 || srcPos > srcLength - length) {
			throw new ArrayIndexOutOfBoundsException("arraycopy: "
					+ (srcPos < 0 ? "source index " + srcPos : "last source index " + (srcPos + length))
					+ " out of bounds for length " + srcLength);
		}
		if (destPos < 0 || destPos > destLength - length) {
			throw new ArrayIndexOutOfBoundsException("arraycopy: "
					+ (destPos < 0 ? "destination index " + destPos
							: "last destination index " + (destPos + length))
					+ " out of bounds for length " + destLength);
		}
		int result = arraycopy0(src, srcPos, dest, destPos, length);
		if (result == 1) {
			throw new ArrayStoreException("arraycopy: type mismatch");
		}
		if (result == 2) {
			throw new ArrayStoreException("arraycopy: element type mismatch");
		}
	}

	/** The length of an array, or -1 for another object */
	private static native int arrayLength(Object array);

	/** Copies checked ranges: 0 on success, 1 for mismatched array types, 2 when a store fails */
	private static native int arraycopy0(Object src, int srcPos, Object dest, int destPos, int length);

	public static native long currentTimeMillis();

	public static native long nanoTime();

	public static native int identityHashCode(Object x);

	public static native void exit(int status);

	public static String lineSeparator() {
		return "\n";
	}

	/** For the make_args hook: the String[] passed to main */
	static String[] j2cArgs(int count) {
		return new String[count];
	}

	/** For the j2c_uncaught hook, as the JVM's default handler prints it */
	static void j2cUncaught(Throwable t) {
		err.print("Exception in thread \"main\" ");
		t.printStackTrace();
	}
}
