package java.lang;

public final class Double extends Number implements Comparable<Double> {
	public static final double POSITIVE_INFINITY = 1.0 / 0.0;
	public static final double NEGATIVE_INFINITY = -1.0 / 0.0;
	public static final double NaN = 0.0d / 0.0;
	public static final double MAX_VALUE = 0x1.fffffffffffffP+1023;
	public static final double MIN_VALUE = 0x0.0000000000001P-1022;
	public static final double MIN_NORMAL = 0x1.0p-1022;
	public static final int MAX_EXPONENT = 1023;
	public static final int MIN_EXPONENT = -1022;
	public static final int SIZE = 64;
	public static final int BYTES = 8;

	private final double value;

	public Double(double value) {
		this.value = value;
	}

	public Double(String s) {
		this.value = parseDouble(s);
	}

	/** Java's hexadecimal form, such as 0x1.8p1: hex fraction digits, binary exponent */
	public static String toHexString(double d) {
		if (isNaN(d)) {
			return "NaN";
		}
		if (isInfinite(d)) {
			return d > 0 ? "Infinity" : "-Infinity";
		}
		long bits = doubleToRawLongBits(d);
		String sign = bits < 0 ? "-" : "";
		long fraction = bits & 0x000fffffffffffffL;
		int exponent = (int) ((bits >>> 52) & 0x7ff);
		if (exponent == 0 && fraction == 0) {
			return sign + "0x0.0p0";
		}
		if (exponent == 0) {
			return sign + "0x0." + hexFraction(fraction, 13) + "p-1022";
		}
		return sign + "0x1." + hexFraction(fraction, 13) + "p" + (exponent - 1023);
	}

	/** The low 4 * count bits as count hex digits, without trailing zeros but one */
	static String hexFraction(long fraction, int count) {
		char[] digits = new char[count];
		int length = 1;
		for (int i = count - 1; i >= 0; i--) {
			int digit = (int) (fraction & 0xf);
			fraction >>>= 4;
			digits[i] = Character.forDigit(digit, 16);
			if (digit != 0 && length == 1) {
				length = i + 1;
			}
		}
		return new String(digits, 0, length);
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
		if (rest.equals("NaN")) {
			return NaN;
		}
		if (rest.equals("Infinity")) {
			return i > 0 && in.charAt(0) == '-' ? NEGATIVE_INFINITY : POSITIVE_INFINITY;
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
			if (!hex && j < n && in.charAt(j) == '.') {
				// Java reports a second point in the decimal digits specially
				throw new NumberFormatException("multiple points");
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
			// The message quotes the text without its surrounding white space
			throw new NumberFormatException(n == 0 ? "empty String" : "For input string: \"" + in + "\"");
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

	public boolean isInfinite() {
		return isInfinite(value);
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
