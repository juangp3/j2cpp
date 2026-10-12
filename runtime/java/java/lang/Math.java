package java.lang;

/**
 * Math, written from the Java SE specification. Transcendental functions
 * use the C library (<cmath>), which may differ from the JVM in the last
 * bit for some arguments, as Math's specification allows. Generated code is
 * built with -fwrapv, so integer arithmetic wraps as in Java.
 */
public final class Math {
	public static final double E = 2.718281828459045;
	public static final double PI = 3.141592653589793;

	private static final double DEGREES_PER_RADIAN = 57.29577951308232;
	private static final double RADIANS_PER_DEGREE = 0.017453292519943295;

	private Math() {
	}

	// Absolute values, minimum and maximum

	/** abs(Integer.MIN_VALUE) is Integer.MIN_VALUE, as negation wraps */
	public static int abs(int a) {
		return a < 0 ? -a : a;
	}

	public static long abs(long a) {
		return a < 0 ? -a : a;
	}

	public static float abs(float a) {
		return a <= 0.0f ? 0.0f - a : a;
	}

	public static double abs(double a) {
		return a <= 0.0d ? 0.0d - a : a;
	}

	public static int absExact(int a) {
		if (a == Integer.MIN_VALUE) {
			throw new ArithmeticException("Overflow to represent absolute value of Integer.MIN_VALUE");
		}
		return abs(a);
	}

	public static long absExact(long a) {
		if (a == Long.MIN_VALUE) {
			throw new ArithmeticException("Overflow to represent absolute value of Long.MIN_VALUE");
		}
		return abs(a);
	}

	public static int max(int a, int b) {
		return a >= b ? a : b;
	}

	public static long max(long a, long b) {
		return a >= b ? a : b;
	}

	public static int min(int a, int b) {
		return a <= b ? a : b;
	}

	public static long min(long a, long b) {
		return a <= b ? a : b;
	}

	public static double max(double a, double b) {
		if (a != a) {
			return a;
		}
		// max(-0.0, 0.0) is 0.0
		if (a == 0.0d && b == 0.0d && Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(-0.0d)) {
			return b;
		}
		return a >= b ? a : b;
	}

	public static double min(double a, double b) {
		if (a != a) {
			return a;
		}
		// min(0.0, -0.0) is -0.0
		if (a == 0.0d && b == 0.0d && Double.doubleToRawLongBits(b) == Double.doubleToRawLongBits(-0.0d)) {
			return b;
		}
		return a <= b ? a : b;
	}

	public static float max(float a, float b) {
		return (float) max((double) a, (double) b);
	}

	public static float min(float a, float b) {
		return (float) min((double) a, (double) b);
	}

	// Integer division rounding toward negative infinity

	public static int floorDiv(int x, int y) {
		int q = x / y;
		if ((x % y != 0) && ((x ^ y) < 0)) {
			q--;
		}
		return q;
	}

	public static long floorDiv(long x, int y) {
		return floorDiv(x, (long) y);
	}

	public static long floorDiv(long x, long y) {
		long q = x / y;
		if ((x % y != 0) && ((x ^ y) < 0)) {
			q--;
		}
		return q;
	}

	public static int floorMod(int x, int y) {
		int m = x % y;
		if (m != 0 && ((m ^ y) < 0)) {
			m += y;
		}
		return m;
	}

	public static int floorMod(long x, int y) {
		return (int) floorMod(x, (long) y);
	}

	public static long floorMod(long x, long y) {
		long m = x % y;
		if (m != 0 && ((m ^ y) < 0)) {
			m += y;
		}
		return m;
	}

	// Exact arithmetic: the operation, or ArithmeticException on overflow

	public static int addExact(int x, int y) {
		int r = x + y;
		if (((x ^ r) & (y ^ r)) < 0) {
			throw new ArithmeticException("integer overflow");
		}
		return r;
	}

	public static long addExact(long x, long y) {
		long r = x + y;
		if (((x ^ r) & (y ^ r)) < 0) {
			throw new ArithmeticException("long overflow");
		}
		return r;
	}

	public static int subtractExact(int x, int y) {
		int r = x - y;
		if (((x ^ y) & (x ^ r)) < 0) {
			throw new ArithmeticException("integer overflow");
		}
		return r;
	}

	public static long subtractExact(long x, long y) {
		long r = x - y;
		if (((x ^ y) & (x ^ r)) < 0) {
			throw new ArithmeticException("long overflow");
		}
		return r;
	}

	public static int multiplyExact(int x, int y) {
		long r = (long) x * (long) y;
		if ((int) r != r) {
			throw new ArithmeticException("integer overflow");
		}
		return (int) r;
	}

	public static long multiplyExact(long x, int y) {
		return multiplyExact(x, (long) y);
	}

	public static long multiplyExact(long x, long y) {
		long r = x * y;
		if (multiplyHigh(x, y) != (r >> 63)) {
			throw new ArithmeticException("long overflow");
		}
		return r;
	}

	public static int incrementExact(int a) {
		if (a == Integer.MAX_VALUE) {
			throw new ArithmeticException("integer overflow");
		}
		return a + 1;
	}

	public static long incrementExact(long a) {
		if (a == Long.MAX_VALUE) {
			throw new ArithmeticException("long overflow");
		}
		return a + 1L;
	}

	public static int decrementExact(int a) {
		if (a == Integer.MIN_VALUE) {
			throw new ArithmeticException("integer overflow");
		}
		return a - 1;
	}

	public static long decrementExact(long a) {
		if (a == Long.MIN_VALUE) {
			throw new ArithmeticException("long overflow");
		}
		return a - 1L;
	}

	public static int negateExact(int a) {
		if (a == Integer.MIN_VALUE) {
			throw new ArithmeticException("integer overflow");
		}
		return -a;
	}

	public static long negateExact(long a) {
		if (a == Long.MIN_VALUE) {
			throw new ArithmeticException("long overflow");
		}
		return -a;
	}

	public static int toIntExact(long value) {
		if ((int) value != value) {
			throw new ArithmeticException("integer overflow");
		}
		return (int) value;
	}

	public static long multiplyFull(int x, int y) {
		return (long) x * (long) y;
	}

	/** The high 64 bits of the 128-bit product */
	public static native long multiplyHigh(long x, long y);

	// Rounding

	public static native double floor(double a);

	public static native double ceil(double a);

	/** The closest integer value, ties to even */
	public static native double rint(double a);

	/** The closest long, ties toward positive infinity; NaN is 0, and the cast saturates */
	public static long round(double a) {
		if (a != a) {
			return 0L;
		}
		double f = floor(a);
		// a - f is exact, so 0.49999999999999994 is not rounded up
		if (a - f >= 0.5d) {
			f += 1.0d;
		}
		return (long) f;
	}

	public static int round(float a) {
		if (a != a) {
			return 0;
		}
		double d = a;
		double f = floor(d);
		if (d - f >= 0.5d) {
			f += 1.0d;
		}
		return (int) f;
	}

	// Floating-point bits

	public static double signum(double d) {
		if (d != d || d == 0.0d) {
			return d;
		}
		return d > 0.0d ? 1.0d : -1.0d;
	}

	public static float signum(float f) {
		if (f != f || f == 0.0f) {
			return f;
		}
		return f > 0.0f ? 1.0f : -1.0f;
	}

	public static double copySign(double magnitude, double sign) {
		long bits = (Double.doubleToRawLongBits(magnitude) & Long.MAX_VALUE)
				| (Double.doubleToRawLongBits(sign) & Long.MIN_VALUE);
		return Double.longBitsToDouble(bits);
	}

	public static float copySign(float magnitude, float sign) {
		return (float) copySign((double) magnitude, (double) sign);
	}

	public static int getExponent(double d) {
		return (int) ((Double.doubleToRawLongBits(d) >> 52) & 0x7ffL) - 1023;
	}

	public static int getExponent(float f) {
		if (f != f || f == Float.POSITIVE_INFINITY || f == Float.NEGATIVE_INFINITY) {
			return 128;
		}
		int e = getExponent((double) f);
		return e < -126 ? -127 : e;
	}

	public static double ulp(double d) {
		if (d != d) {
			return d;
		}
		double a = abs(d);
		if (a == Double.POSITIVE_INFINITY) {
			return a;
		}
		if (a == Double.MAX_VALUE) {
			return 1.9958403095347198E292; // 2^971
		}
		return nextUp(a) - a;
	}

	public static float ulp(float f) {
		if (f != f) {
			return f;
		}
		float a = abs(f);
		if (a == Float.POSITIVE_INFINITY) {
			return a;
		}
		if (a == Float.MAX_VALUE) {
			return 2.028241E31f; // 2^104
		}
		return nextUp(a) - a;
	}

	public static native double nextAfter(double start, double direction);

	public static native float nextAfter(float start, double direction);

	public static double nextUp(double d) {
		return nextAfter(d, Double.POSITIVE_INFINITY);
	}

	public static float nextUp(float f) {
		return nextAfter(f, Double.POSITIVE_INFINITY);
	}

	public static double nextDown(double d) {
		return nextAfter(d, Double.NEGATIVE_INFINITY);
	}

	public static float nextDown(float f) {
		return nextAfter(f, Double.NEGATIVE_INFINITY);
	}

	public static native double scalb(double d, int scaleFactor);

	public static native float scalb(float f, int scaleFactor);

	// Powers, roots, exponentials and logarithms

	public static native double sqrt(double a);

	public static native double cbrt(double a);

	public static native double pow(double a, double b);

	public static native double hypot(double x, double y);

	public static native double exp(double a);

	public static native double expm1(double x);

	public static native double log(double a);

	public static native double log10(double a);

	public static native double log1p(double x);

	public static native double IEEEremainder(double f1, double f2);

	public static native double fma(double a, double b, double c);

	public static native float fma(float a, float b, float c);

	// Trigonometry

	public static native double sin(double a);

	public static native double cos(double a);

	public static native double tan(double a);

	public static native double asin(double a);

	public static native double acos(double a);

	public static native double atan(double a);

	public static native double atan2(double y, double x);

	public static native double sinh(double x);

	public static native double cosh(double x);

	public static native double tanh(double x);

	public static double toRadians(double angdeg) {
		return angdeg * RADIANS_PER_DEGREE;
	}

	public static double toDegrees(double angrad) {
		return angrad * DEGREES_PER_RADIAN;
	}

	// Random numbers

	private static long seed;
	private static boolean seeded;

	/**
	 * A value in [0.0, 1.0), from the linear congruential generator that
	 * java.util.Random's specification defines, seeded from nanoTime().
	 */
	public static double random() {
		if (!seeded) {
			seed = (System.nanoTime() ^ 0x5DEECE66DL) & ((1L << 48) - 1);
			seeded = true;
		}
		long high = next(26);
		long low = next(27);
		return ((high << 27) + low) * 1.1102230246251565E-16; // 2^-53
	}

	private static long next(int bits) {
		seed = (seed * 0x5DEECE66DL + 0xBL) & ((1L << 48) - 1);
		return seed >>> (48 - bits);
	}
}
