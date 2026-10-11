package java.lang;

/** Math, written from the Java SE specification; transcendentals use <cmath>. */
public final class Math {
	public static final double E = 2.718281828459045;
	public static final double PI = 3.141592653589793;

	private Math() {
	}

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

	public static int floorDiv(int x, int y) {
		int q = x / y;
		if ((x % y != 0) && ((x ^ y) < 0)) {
			q--;
		}
		return q;
	}

	public static int floorMod(int x, int y) {
		return x - floorDiv(x, y) * y;
	}

	public static long floorDiv(long x, long y) {
		long q = x / y;
		if ((x % y != 0) && ((x ^ y) < 0)) {
			q--;
		}
		return q;
	}

	public static long floorMod(long x, long y) {
		return x - floorDiv(x, y) * y;
	}

	public static native double sqrt(double a);

	public static native double pow(double a, double b);

	public static native double floor(double a);

	public static native double ceil(double a);

	public static long round(double a) {
		return (long) floor(a + 0.5d);
	}

	public static int round(float a) {
		return (int) floor(a + 0.5d);
	}

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

	public static int multiplyExact(int x, int y) {
		long r = (long) x * (long) y;
		if ((int) r != r) {
			throw new ArithmeticException("integer overflow");
		}
		return (int) r;
	}

	public static int toIntExact(long value) {
		if ((int) value != value) {
			throw new ArithmeticException("integer overflow");
		}
		return (int) value;
	}
}
