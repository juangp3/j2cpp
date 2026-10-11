package java.lang;

public final class Double extends Number implements Comparable<Double> {
	public static final double POSITIVE_INFINITY = 1.0 / 0.0;
	public static final double NEGATIVE_INFINITY = -1.0 / 0.0;
	public static final double NaN = 0.0d / 0.0;
	public static final double MAX_VALUE = 0x1.fffffffffffffP+1023;
	public static final double MIN_VALUE = 0x0.0000000000001P-1022;

	private final double value;

	public Double(double value) {
		this.value = value;
	}

	public static Double valueOf(double d) {
		return new Double(d);
	}

	public static Double valueOf(String s) {
		return new Double(parseDouble(s));
	}

	/** Java's Double.toString format, from the shortest round-trip digits */
	public static native String toString(double d);

	public static double parseDouble(String s) {
		return parse(s, false);
	}

	/** Java's FloatingDecimal grammar, checked here; the conversion is native */
	static double parse(String s, boolean toFloat) {
		if (s == null) {
			throw new NullPointerException();
		}
		String in = s.trim();
		int i = 0;
		int n = in.length();
		if (i < n && (in.charAt(i) == '+' || in.charAt(i) == '-')) {
			i++;
		}
		String rest = in.substring(i);
		if (rest.equals("NaN") || rest.equals("Infinity")) {
			return convert(in, toFloat);
		}
		boolean hex = rest.startsWith("0x") || rest.startsWith("0X");
		int j = hex ? i + 2 : i;
		int digits = 0;
		while (j < n && isDigit(in.charAt(j), hex)) {
			j++;
			digits++;
		}
		if (j < n && in.charAt(j) == '.') {
			j++;
			while (j < n && isDigit(in.charAt(j), hex)) {
				j++;
				digits++;
			}
		}
		boolean valid = digits > 0;
		if (valid && j < n && (hex ? (in.charAt(j) | 0x20) == 'p' : (in.charAt(j) | 0x20) == 'e')) {
			j++;
			if (j < n && (in.charAt(j) == '+' || in.charAt(j) == '-')) {
				j++;
			}
			int exponentDigits = 0;
			while (j < n && in.charAt(j) >= '0' && in.charAt(j) <= '9') {
				j++;
				exponentDigits++;
			}
			valid = exponentDigits > 0;
		} else if (hex) {
			// A hexadecimal float needs its binary exponent
			valid = false;
		}
		int end = j;
		if (valid && j < n && "fFdD".indexOf(in.charAt(j)) >= 0) {
			j++;
		}
		if (!valid || j != n) {
			throw new NumberFormatException(n == 0 ? "empty String" : "For input string: \"" + s + "\"");
		}
		// The type suffix is Java syntax that strtod does not accept
		return convert(in.substring(0, end), toFloat);
	}

	private static boolean isDigit(char c, boolean hex) {
		return (c >= '0' && c <= '9') || (hex && ((c | 0x20) >= 'a' && (c | 0x20) <= 'f'));
	}

	/** strtod or strtof on text already checked against Java's grammar */
	private static native double convert(String s, boolean toFloat);

	public static boolean isNaN(double v) {
		return v != v;
	}

	public static boolean isInfinite(double v) {
		return v == POSITIVE_INFINITY || v == NEGATIVE_INFINITY;
	}

	public static boolean isFinite(double d) {
		return Math.abs(d) <= MAX_VALUE;
	}

	public static native long doubleToRawLongBits(double value);

	public static long doubleToLongBits(double value) {
		return isNaN(value) ? 0x7ff8000000000000L : doubleToRawLongBits(value);
	}

	public static native double longBitsToDouble(long bits);

	public static int compare(double d1, double d2) {
		if (d1 < d2) {
			return -1;
		}
		if (d1 > d2) {
			return 1;
		}
		long thisBits = doubleToLongBits(d1);
		long anotherBits = doubleToLongBits(d2);
		return thisBits == anotherBits ? 0 : (thisBits < anotherBits ? -1 : 1);
	}

	public static int hashCode(double value) {
		long bits = doubleToLongBits(value);
		return (int) (bits ^ (bits >>> 32));
	}

	public static double max(double a, double b) {
		return Math.max(a, b);
	}

	public static double min(double a, double b) {
		return Math.min(a, b);
	}

	public static double sum(double a, double b) {
		return a + b;
	}

	public boolean isNaN() {
		return isNaN(value);
	}

	public int compareTo(Double anotherDouble) {
		return compare(value, anotherDouble.value);
	}

	public int intValue() {
		return (int) value;
	}

	public long longValue() {
		return (long) value;
	}

	public float floatValue() {
		return (float) value;
	}

	public double doubleValue() {
		return value;
	}

	public int hashCode() {
		return hashCode(value);
	}

	public boolean equals(Object obj) {
		return obj instanceof Double
				&& doubleToLongBits(((Double) obj).value) == doubleToLongBits(value);
	}

	public String toString() {
		return toString(value);
	}
}
