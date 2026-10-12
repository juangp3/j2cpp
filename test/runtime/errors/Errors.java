import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;
import java.util.Objects;

// R2: Math, Objects, try-with-resources, Throwable and the exception classes
public class Errors {
	static void line(String label, Object value) {
		System.out.println(label + ": " + value);
	}

	static void math() {
		line("trig", Math.sin(0.0) + " " + Math.cos(0.0) + " " + Math.tan(0.0) + " " + Math.asin(1.0) + " "
				+ Math.acos(1.0) + " " + Math.atan(1.0) + " " + Math.atan2(1.0, 1.0) + " " + Math.atan2(0.0, -1.0));
		line("exp/log", Math.exp(0.0) + " " + Math.exp(1.0) + " " + Math.log(1.0) + " " + Math.log(Math.E) + " "
				+ Math.log10(1000.0) + " " + Math.log1p(0.0) + " " + Math.expm1(0.0) + " " + Math.log(0.0) + " "
				+ Math.exp(1000.0) + " " + Math.log(-1.0));
		line("roots", Math.cbrt(27.0) + " " + Math.cbrt(-8.0) + " " + Math.cbrt(1e-300) + " " + Math.hypot(3.0, 4.0) + " " + Math.sqrt(-1.0)
				+ " " + Math.pow(2.0, -1.0) + " " + Math.pow(1.0, Double.NaN) + " " + Math.pow(Double.NaN, 0.0));
		line("rint", Math.rint(2.5) + " " + Math.rint(3.5) + " " + Math.rint(-2.5) + " " + Math.rint(-0.2));
		line("round", Math.round(-2.5) + " " + Math.round(2.5) + " " + Math.round(-0.5) + " "
				+ Math.round(0.49999999999999994) + " " + Math.round(Double.NaN) + " " + Math.round(1e300) + " "
				+ Math.round(-1e300) + " " + Math.round(Double.POSITIVE_INFINITY) + " " + Math.round(4503599627370497.0));
		line("round float", Math.round(-2.5f) + " " + Math.round(0.49999997f) + " " + Math.round(Float.NaN) + " "
				+ Math.round(1e20f) + " " + Math.round(-1e20f) + " " + Math.round(8388609f));
		line("signum", Math.signum(-3.2) + " " + Math.signum(0.0) + " " + Math.signum(-0.0) + " " + Math.signum(7.5f)
				+ " " + Math.signum(Double.NaN));
		line("copySign", Math.copySign(3.0, -0.0) + " " + Math.copySign(-2.0, 1.0) + " " + Math.copySign(1.5f, -2f));
		line("angles", Math.toDegrees(Math.PI) + " " + Math.toRadians(180.0) + " " + Math.toDegrees(1.0) + " "
				+ Math.toRadians(1.0));
		line("ulp", Math.ulp(1.0) + " " + Math.ulp(1.0f) + " " + (Math.ulp(0.0) == Double.MIN_VALUE) + " "
				+ (Math.ulp(Double.MAX_VALUE) == Math.pow(2.0, 971.0))
				+ " " + Math.ulp(Float.MAX_VALUE) + " " + Math.ulp(Double.NEGATIVE_INFINITY));
		line("next", Math.nextUp(1.0) + " " + Math.nextDown(1.0) + " " + Math.nextUp(1.0f) + " "
				+ Math.nextDown(1.0f) + " " + (Math.nextAfter(0.0, -1.0) == -Double.MIN_VALUE) + " " + Math.nextAfter(1.0f, 1.0));
		line("exponent", Math.getExponent(1024.0) + " " + Math.getExponent(0.1) + " " + Math.getExponent(0.0) + " "
				+ Math.getExponent(Double.NaN) + " " + Math.getExponent(1e-40f) + " " + Math.getExponent(3.0f));
		line("scalb", Math.scalb(3.0, 4) + " " + Math.scalb(1.0f, -1) + " " + Math.IEEEremainder(10.0, 3.0) + " "
				+ Math.fma(2.0, 3.0, 1.0));
		line("abs", Math.abs(Integer.MIN_VALUE) + " " + Math.abs(Long.MIN_VALUE) + " " + Math.abs(-0.0) + " "
				+ Math.abs(-7) + " " + Math.abs(-2.5f));
		line("min/max", Math.max(-0.0, 0.0) + " " + Math.min(-0.0, 0.0) + " " + Math.max(Double.NaN, 1.0) + " "
				+ Math.min(3L, -4L));
		line("floor", Math.floorDiv(-7, 2) + " " + Math.floorMod(-7, 2) + " " + Math.floorDiv(-7L, 2) + " "
				+ Math.floorMod(-7L, 3) + " " + Math.floorMod(7L, -3L) + " " + Math.floorDiv(Integer.MIN_VALUE, -1)
				+ " " + Math.floorMod(Integer.MIN_VALUE, -1));
		line("wide", Math.multiplyFull(Integer.MAX_VALUE, Integer.MAX_VALUE) + " "
				+ Math.multiplyHigh(Long.MAX_VALUE, Long.MAX_VALUE) + " " + Math.multiplyHigh(-1L, 5L));
		line("strict", StrictMath.sqrt(16.0) + " " + StrictMath.copySign(2.0, Double.NaN) + " " + StrictMath.abs(-4)
				+ " " + StrictMath.floorMod(-1, 5) + " " + StrictMath.PI);
		double r = Math.random();
		line("random in range", r >= 0.0 && r < 1.0);
	}

	static void exact(String label, int which) {
		try {
			long value;
			switch (which) {
			case 0: value = Math.addExact(Integer.MAX_VALUE, 1); break;
			case 1: value = Math.addExact(Long.MAX_VALUE, 1L); break;
			case 2: value = Math.subtractExact(Integer.MIN_VALUE, 1); break;
			case 3: value = Math.subtractExact(Long.MIN_VALUE, 1L); break;
			case 4: value = Math.multiplyExact(1 << 16, 1 << 16); break;
			case 5: value = Math.multiplyExact(1L << 32, 1L << 31); break;
			case 6: value = Math.multiplyExact(Long.MIN_VALUE, -1L); break;
			case 7: value = Math.multiplyExact(-(1L << 31), 1 << 30); break;
			case 8: value = Math.negateExact(Integer.MIN_VALUE); break;
			case 9: value = Math.negateExact(Long.MIN_VALUE); break;
			case 10: value = Math.incrementExact(Integer.MAX_VALUE); break;
			case 11: value = Math.incrementExact(Long.MAX_VALUE); break;
			case 12: value = Math.decrementExact(Integer.MIN_VALUE); break;
			case 13: value = Math.decrementExact(Long.MIN_VALUE); break;
			case 14: value = Math.absExact(Integer.MIN_VALUE); break;
			case 15: value = Math.absExact(Long.MIN_VALUE); break;
			case 16: value = Math.toIntExact(1L << 31); break;
			case 17: value = Math.multiplyExact(-3037000500L, 3037000499L); break;
			case 18: value = Math.subtractExact(-5L, Long.MAX_VALUE); break;
			case 19: value = Math.floorDiv(1, 0); break;
			default: value = Math.floorMod(5L, 0L); break;
			}
			line(label, value);
		} catch (ArithmeticException e) {
			line(label, e);
		}
	}

	static void objects() {
		line("equals", Objects.equals(null, null) + " " + Objects.equals("a", null) + " " + Objects.equals("a", "a"));
		line("deepEquals", Objects.deepEquals(new int[] { 1, 2 }, new int[] { 1, 2 }) + " "
				+ Objects.deepEquals(new int[] { 1 }, new long[] { 1 }) + " "
				+ Objects.deepEquals(new Object[] { "x", new int[] { 3 } }, new Object[] { "x", new int[] { 3 } }) + " "
				+ Objects.deepEquals(new double[] { Double.NaN }, new double[] { Double.NaN }) + " "
				+ Objects.deepEquals(new double[] { 0.0 }, new double[] { -0.0 }) + " "
				+ Objects.deepEquals(new String[] { "a" }, new String[] { "b" }) + " "
				+ Objects.deepEquals(new char[] { 'q' }, new char[] { 'q' }));
		line("hashCode", Objects.hashCode(null) + " " + Objects.hashCode("abc"));
		line("hash", Objects.hash() + " " + Objects.hash((Object) null) + " " + Objects.hash("a", "b") + " "
				+ Objects.hash("a long string to overflow", "and another one", 42));
		line("toString", Objects.toString(null) + " " + Objects.toString(null, "fallback") + " "
				+ Objects.toString("value", "fallback"));
		line("isNull", Objects.isNull(null) + " " + Objects.nonNull(null) + " " + Objects.nonNull("x"));
		line("requireNonNullElse", Objects.requireNonNullElse(null, "default") + " "
				+ Objects.requireNonNullElse("given", "default"));
		line("checkIndex", Objects.checkIndex(2, 3) + " " + Objects.checkFromToIndex(1, 3, 3) + " "
				+ Objects.checkFromIndexSize(0, 3, 3));
		for (int i = 0; i < 9; i++) {
			try {
				switch (i) {
				case 0: Objects.requireNonNull(null); break;
				case 1: Objects.requireNonNull(null, "thing must not be null"); break;
				case 2: Objects.requireNonNullElse(null, null); break;
				case 3: Objects.checkIndex(5, 3); break;
				case 4: Objects.checkIndex(-1, 3); break;
				case 5: Objects.checkFromToIndex(2, 1, 3); break;
				case 6: Objects.checkFromIndexSize(2, 5, 3); break;
				case 7: Objects.checkIndex(7L, 4L); break;
				default: Objects.checkFromIndexSize(1, -1, 3); break;
				}
				line("no exception", i);
			} catch (RuntimeException e) {
				line("objects " + i, e);
			}
		}
	}

	static void constructors() {
		line("AIOOBE", new ArrayIndexOutOfBoundsException(5).getMessage());
		line("SIOOBE", new StringIndexOutOfBoundsException(5).getMessage());
		line("IOOBE", new IndexOutOfBoundsException(5).getMessage() + " / " + new IndexOutOfBoundsException(6L));
		Throwable[] all = { new InterruptedException("sleep"), new CloneNotSupportedException(),
				new ClassNotFoundException("com.example.Missing"), new SecurityException("denied", new Error("x")),
				new StackOverflowError(), new OutOfMemoryError("heap"), new InternalError("bad"),
				new NoSuchElementException("empty"), new ConcurrentModificationException("changed"),
				new IllegalMonitorStateException(), new NoSuchMethodException("m"), new NoSuchFieldException("f"),
				new InstantiationException(), new IllegalAccessException("private"), new LinkageError("link"),
				new NoClassDefFoundError("Gone"), new ReflectiveOperationException(new Error("inner")),
				new IOException(new Error("io")), new UnsupportedOperationException(), new AssertionError(42),
				new AssertionError(true), new AssertionError(2.5), new AssertionError((Object) null),
				new AssertionError(new IOException("wrapped")) };
		for (Throwable t : all) {
			line("toString", t + " cause=" + t.getCause());
		}
		ClassNotFoundException cnf = new ClassNotFoundException("Lost", new IOException("io"));
		line("getException", cnf.getException());
		UncheckedIOException unchecked = new UncheckedIOException(new IOException("x"));
		IOException io = unchecked.getCause();
		line("unchecked", unchecked + " | " + io.getMessage() + " | " + new UncheckedIOException("m", io));
		try {
			new UncheckedIOException("no cause", null);
		} catch (NullPointerException e) {
			line("unchecked null", e);
		}
	}

	static void throwableRules() {
		Exception e = new Exception("a");
		try {
			e.addSuppressed(e);
		} catch (IllegalArgumentException x) {
			line("self-suppression", x + " cause=" + x.getCause());
		}
		try {
			e.addSuppressed(null);
		} catch (NullPointerException x) {
			line("null suppression", x);
		}
		line("no suppressed", e.getSuppressed().length);
		e.addSuppressed(new Silent("one"));
		e.addSuppressed(new Silent("two"));
		Throwable[] suppressed = e.getSuppressed();
		line("suppressed", suppressed.length + " " + suppressed[0].getMessage() + " " + suppressed[1].getMessage());
		Silent silent = new Silent("quiet");
		silent.addSuppressed(new Silent("dropped"));
		line("disabled suppression", silent.getSuppressed().length);

		Exception noCause = new Exception("late");
		noCause.initCause(new Error("root"));
		line("initCause", noCause.getCause());
		try {
			noCause.initCause(null);
		} catch (IllegalStateException x) {
			line("initCause twice", x + " cause=" + x.getCause());
		}
		try {
			new RuntimeException("set", null).initCause(new Error());
		} catch (IllegalStateException x) {
			line("initCause after constructor", x.getMessage());
		}
		Exception self = new Exception();
		try {
			self.initCause(self);
		} catch (IllegalArgumentException x) {
			line("self-causation", x.getMessage());
		}
		line("wrapping", new RuntimeException(new Error("inner")).getMessage() + " / "
				+ new RuntimeException((Throwable) null).getMessage());
		line("fillInStackTrace", e.fillInStackTrace() == e);
		StackTraceElement frame = new StackTraceElement("Errors", "main", "Errors.java", 10);
		Exception framed = new Exception("framed");
		framed.setStackTrace(new StackTraceElement[] { frame });
		line("stack trace", framed.getStackTrace().length + " " + framed.getStackTrace()[0]);
		line("frame", frame + " " + new StackTraceElement("A", "b", null, -1) + " "
				+ new StackTraceElement("A", "c", "A.java", -2) + " " + frame.getMethodName());
	}

	static void resources() {
		try (Resource r = new Resource("plain", false)) {
			System.out.println("body uses " + r.name);
		}
		try (Resource a = new Resource("first", false); Resource b = new Resource("second", false)) {
			System.out.println("body throws");
			throw new IllegalStateException("body failed");
		} catch (IllegalStateException e) {
			System.out.println("caught " + e.getMessage() + ", suppressed " + e.getSuppressed().length);
		} finally {
			System.out.println("finally runs after close");
		}
		Resource none = null;
		try (Resource r = none) {
			System.out.println("null resource is not closed");
		}
	}

	/** try-with-resources, written out as the Java language specification defines it */
	static void resourcesByHand() {
		try {
			Resource r = new Resource("failing", true);
			IllegalStateException primary = null;
			try {
				System.out.println("body throws too");
				throw new IllegalStateException("body failed");
			} catch (IllegalStateException t) {
				primary = t;
			}
			try {
				r.close();
			} catch (RuntimeException closeFailure) {
				if (primary == null) {
					throw closeFailure;
				}
				primary.addSuppressed(closeFailure);
			}
			if (primary != null) {
				throw primary;
			}
		} catch (IllegalStateException e) {
			Throwable[] suppressed = e.getSuppressed();
			System.out.println("caught " + e.getMessage() + ", suppressed " + suppressed.length + ": "
					+ suppressed[0].getMessage());
			e.printStackTrace();
		}
		try {
			Stream s = new Stream();
			s.close();
		} catch (IOException e) {
			UncheckedIOException wrapped = new UncheckedIOException("wrapped", e);
			System.out.println("caught " + wrapped + " cause " + wrapped.getCause());
		}
	}

	static void chain() {
		Exception root = new Exception("root");
		Quiet middle = new Quiet("middle", root);
		middle.addSuppressed(new Quiet("side", new Quiet("side cause", null)));
		RuntimeException top = new RuntimeException("top", middle);
		top.addSuppressed(new Quiet("first suppressed", null));
		Quiet nested = new Quiet("second suppressed", null);
		nested.addSuppressed(new Quiet("nested suppressed", null));
		top.addSuppressed(nested);
		top.printStackTrace();
		for (Throwable t = top; t != null; t = t.getCause()) {
			System.out.println("chain: " + t.getMessage());
		}
		// A cause cycle prints as a circular reference
		RuntimeException a = new RuntimeException("cycle a");
		RuntimeException b = new RuntimeException("cycle b", a);
		a.initCause(b);
		a.printStackTrace();
	}

	public static void main(String[] args) {
		math();
		String[] labels = { "addExact int", "addExact long", "subtractExact int", "subtractExact long",
				"multiplyExact int", "multiplyExact long", "multiplyExact MIN*-1", "multiplyExact long*int",
				"negateExact int", "negateExact long", "incrementExact int", "incrementExact long",
				"decrementExact int", "decrementExact long", "absExact int", "absExact long", "toIntExact",
				"multiplyExact near", "subtractExact long 2", "floorDiv zero", "floorMod zero" };
		for (int i = 0; i < labels.length; i++) {
			exact(labels[i], i);
		}
		objects();
		constructors();
		throwableRules();
		resources();
		resourcesByHand();
		chain();
		throw new IllegalStateException("uncaught", new UncheckedIOException(new IOException("cause")));
	}
}

/** No stack trace, so printStackTrace output has no frames on the JVM either */
class Quiet extends RuntimeException {
	Quiet(String message, Throwable cause) {
		super(message, cause, true, false);
	}
}

/** Neither suppression nor a stack trace */
class Silent extends Exception {
	Silent(String message) {
		super(message, null, false, false);
	}
}

class Resource implements AutoCloseable {
	final String name;
	private final boolean failOnClose;

	Resource(String name, boolean failOnClose) {
		this.name = name;
		this.failOnClose = failOnClose;
		System.out.println("open " + name);
	}

	public void close() {
		System.out.println("close " + name);
		if (failOnClose) {
			throw new Quiet("close failed: " + name, null);
		}
	}
}

class Stream implements Closeable {
	public void close() throws IOException {
		System.out.println("close stream");
		throw new IOException("disk gone");
	}
}
