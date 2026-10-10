package se.arnetheduck.j2c.test;

// Integer arithmetic where C++ and Java differ; see Java17ArithmeticSmoke.cpp
public class Java17Arithmetic {
	public static int div(int a, int b) {
		return a / b;
	}

	public static int rem(int a, int b) {
		return a % b;
	}

	public static long divLong(long a, long b) {
		return a / b;
	}

	// (int / int) is divided as int before being promoted for the long step
	public static long chain(int a, int b, long c) {
		return a / b / c;
	}

	public static int half(int a) {
		return a / 2;
	}

	public static int add(int a, int b) {
		return a + b;
	}

	public static int shl(int a, int s) {
		return a << s;
	}

	public static long shlLong(long a, int s) {
		return a << s;
	}

	public static int shr(int a, int s) {
		return a >> s;
	}

	public static int ushr(int a, int s) {
		return a >>> s;
	}

	public static int d2i(double d) {
		return (int) d;
	}

	public static long d2l(double d) {
		return (long) d;
	}

	public static int f2i(float f) {
		return (int) f;
	}

	public static byte d2b(double d) {
		return (byte) d;
	}

	public static int divAssign(int a, int b) {
		a /= b;
		return a;
	}

	public static int remAssign(int a, int b) {
		a %= b;
		return a;
	}

	public static int shlAssign(int a, int s) {
		a <<= s;
		return a;
	}

	public static int ushrAssign(int a, int s) {
		a >>>= s;
		return a;
	}

	public static byte byteDivAssign(byte a, int b) {
		a /= b;
		return a;
	}

	// The target of a compound assignment is evaluated once
	public static int compoundIndex(int[] values, int b) {
		int i = 0;
		values[i++] /= b;
		return i;
	}

	public static double fmodAssign(double a, double b) {
		a %= b;
		return a;
	}
}
