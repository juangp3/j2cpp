package java.lang;

public final class Long extends Number implements Comparable<Long> {
	public static final long MIN_VALUE = 0x8000000000000000L;
	public static final long MAX_VALUE = 0x7fffffffffffffffL;

	private static final Long[] CACHE = new Long[256];

	private final long value;

	public Long(long value) {
		this.value = value;
	}

	public static Long valueOf(long l) {
		if (l >= -128 && l <= 127) {
			int index = (int) l + 128;
			Long cached = CACHE[index];
			if (cached == null) {
				cached = new Long(l);
				CACHE[index] = cached;
			}
			return cached;
		}
		return new Long(l);
	}

	public static Long valueOf(String s) {
		return valueOf(parseLong(s));
	}

	public static String toString(long i) {
		return toString(i, 10);
	}

	public static String toString(long i, int radix) {
		if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
			radix = 10;
		}
		if (i == 0) {
			return "0";
		}
		char[] buf = new char[65];
		int pos = buf.length;
		boolean negative = i < 0;
		// Work with negative values, so MIN_VALUE needs no special case
		long n = negative ? i : -i;
		while (n != 0) {
			buf[--pos] = Character.forDigit((int) -(n % radix), radix);
			n /= radix;
		}
		if (negative) {
			buf[--pos] = '-';
		}
		return new String(buf, pos, buf.length - pos);
	}

	public static String toHexString(long i) {
		return toUnsignedString(i, 4);
	}

	public static String toBinaryString(long i) {
		return toUnsignedString(i, 1);
	}

	public static String toOctalString(long i) {
		return toUnsignedString(i, 3);
	}

	/** Digits of an unsigned value in radix 1 << shift */
	static String toUnsignedString(long i, int shift) {
		char[] buf = new char[64];
		int pos = buf.length;
		int mask = (1 << shift) - 1;
		do {
			buf[--pos] = Character.forDigit((int) i & mask, 1 << shift);
			i >>>= shift;
		} while (i != 0);
		return new String(buf, pos, buf.length - pos);
	}

	public static long parseLong(String s) {
		return parseLong(s, 10);
	}

	public static long parseLong(String s, int radix) {
		return parse(s, radix, MIN_VALUE, MAX_VALUE);
	}

	/** Parses a signed integer in [min, max], with Java's error messages */
	static long parse(String s, int radix, long min, long max) {
		if (s == null) {
			throw new NumberFormatException("Cannot parse null string: null");
		}
		int length = s.length();
		if (length == 0) {
			throw new NumberFormatException("For input string: \"\"" + radixSuffix(radix));
		}
		int i = 0;
		boolean negative = false;
		char first = s.charAt(0);
		if (first == '-' || first == '+') {
			negative = first == '-';
			i = 1;
			if (length == 1) {
				throw forInput(s, radix);
			}
		}
		// Accumulate negatively, so the most negative value fits
		long limit = negative ? min : -max;
		long multmin = limit / radix;
		long result = 0;
		for (; i < length; i++) {
			int digit = Character.digit(s.charAt(i), radix);
			if (digit < 0 || result < multmin) {
				throw forInput(s, radix);
			}
			result *= radix;
			if (result < limit + digit) {
				throw forInput(s, radix);
			}
			result -= digit;
		}
		return negative ? result : -result;
	}

	private static NumberFormatException forInput(String s, int radix) {
		return new NumberFormatException("For input string: \"" + s + "\"" + radixSuffix(radix));
	}

	private static String radixSuffix(int radix) {
		return radix == 10 ? "" : " under radix " + radix;
	}

	public static int compare(long x, long y) {
		return x < y ? -1 : (x == y ? 0 : 1);
	}

	public static int hashCode(long value) {
		return (int) (value ^ (value >>> 32));
	}

	public static long max(long a, long b) {
		return Math.max(a, b);
	}

	public static long min(long a, long b) {
		return Math.min(a, b);
	}

	public static long sum(long a, long b) {
		return a + b;
	}

	public int compareTo(Long anotherLong) {
		return compare(value, anotherLong.value);
	}

	public int intValue() {
		return (int) value;
	}

	public long longValue() {
		return value;
	}

	public float floatValue() {
		return value;
	}

	public double doubleValue() {
		return value;
	}

	public int hashCode() {
		return hashCode(value);
	}

	public boolean equals(Object obj) {
		return obj instanceof Long && value == ((Long) obj).value;
	}

	public String toString() {
		return toString(value);
	}
}
