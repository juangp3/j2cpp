package java.lang;

public final class Long extends Number implements Comparable<Long> {
	public static final long MIN_VALUE = 0x8000000000000000L;
	public static final long MAX_VALUE = 0x7fffffffffffffffL;
	public static final int SIZE = 64;
	public static final int BYTES = 8;

	private static final Long[] CACHE = new Long[256];

	private final long value;

	public Long(long value) {
		this.value = value;
	}

	public Long(String s) {
		this.value = parseLong(s, 10);
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
		return valueOf(parseLong(s, 10));
	}

	public static Long valueOf(String s, int radix) {
		return valueOf(parseLong(s, radix));
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

	public static String toUnsignedString(long i) {
		return toUnsignedString(i, 10);
	}

	public static String toUnsignedString(long i, int radix) {
		if (i >= 0) {
			return toString(i, radix);
		}
		if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
			radix = 10;
		}
		// The quotient by the radix is non-negative, so toString handles it
		long quotient = divideUnsigned(i, radix);
		int last = (int) (i - quotient * radix);
		return toString(quotient, radix) + Character.forDigit(last, radix);
	}

	public static String toHexString(long i) {
		return unsignedDigits(i, 4);
	}

	public static String toBinaryString(long i) {
		return unsignedDigits(i, 1);
	}

	public static String toOctalString(long i) {
		return unsignedDigits(i, 3);
	}

	/** Digits of an unsigned value in radix 1 << shift */
	static String unsignedDigits(long i, int shift) {
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
			throw new NumberFormatException("Cannot parse null string");
		}
		checkRadix(radix);
		if (s.length() == 0) {
			throw forInput(s, radix);
		}
		return parse(s, s.length(), radix, min, max, false);
	}

	private static void checkRadix(int radix) {
		if (radix < Character.MIN_RADIX) {
			throw new NumberFormatException("radix " + radix + " less than Character.MIN_RADIX");
		}
		if (radix > Character.MAX_RADIX) {
			throw new NumberFormatException("radix " + radix + " greater than Character.MAX_RADIX");
		}
	}

	/**
	 * Parses the first end characters of s, which are not empty, as a signed
	 * value in [min, max]. Errors name the whole string or, when indexed, the
	 * failing index within the parsed prefix.
	 */
	private static long parse(String s, int end, int radix, long min, long max, boolean indexed) {
		int i = 0;
		boolean negative = false;
		char first = s.charAt(0);
		if (first == '-' || first == '+') {
			negative = first == '-';
			i = 1;
			if (end == 1) {
				throw parseError(s, end, radix, 0, indexed);
			}
		}
		// Accumulate negatively, so the most negative value fits
		long limit = negative ? min : -max;
		long multmin = limit / radix;
		long result = 0;
		for (; i < end; i++) {
			int digit = Character.digit(s.charAt(i), radix);
			if (digit < 0 || result < multmin) {
				throw parseError(s, end, radix, i, indexed);
			}
			result *= radix;
			if (result < limit + digit) {
				throw parseError(s, end, radix, i, indexed);
			}
			result -= digit;
		}
		return negative ? result : -result;
	}

	private static NumberFormatException parseError(String s, int end, int radix, int index, boolean indexed) {
		if (indexed) {
			return new NumberFormatException("Error at index " + index + " in: \"" + s.substring(0, end) + "\"");
		}
		return forInput(s, radix);
	}

	static NumberFormatException forInput(String s, int radix) {
		return new NumberFormatException("For input string: \"" + s + "\"" + (radix == 10 ? "" : " under radix " + radix));
	}

	public static long parseUnsignedLong(String s) {
		return parseUnsignedLong(s, 10);
	}

	public static long parseUnsignedLong(String s, int radix) {
		if (s == null) {
			throw new NumberFormatException("Cannot parse null string");
		}
		int length = s.length();
		if (length == 0) {
			throw forInput(s, radix);
		}
		if (s.charAt(0) == '-') {
			throw new NumberFormatException("Illegal leading minus sign on unsigned string " + s + ".");
		}
		if (length <= 12 || (radix == 10 && length <= 18)) {
			// Short enough to fit in a signed long
			return parseLong(s, radix);
		}
		checkRadix(radix);
		// All but the last digit fit in a signed long; add the last one unsigned
		long first = parse(s, length - 1, radix, MIN_VALUE, MAX_VALUE, true);
		int last = Character.digit(s.charAt(length - 1), radix);
		if (last < 0) {
			throw new NumberFormatException("Bad digit at end of " + s);
		}
		if (compareUnsigned(first, divideUnsigned(-1L - last, radix)) > 0) {
			throw new NumberFormatException("String value " + s + " exceeds range of unsigned long.");
		}
		return first * radix + last;
	}

	public static Long decode(String nm) {
		return valueOf(decode(nm, MIN_VALUE, MAX_VALUE));
	}

	/**
	 * Java's decode grammar: an optional sign, then 0x, 0X, # or 0 for the
	 * radix, then digits; the value must lie in [min, max]
	 */
	static long decode(String nm, long min, long max) {
		if (nm.length() == 0) {
			throw new NumberFormatException("Zero length string");
		}
		int index = 0;
		boolean negative = false;
		char first = nm.charAt(0);
		if (first == '-' || first == '+') {
			negative = first == '-';
			index = 1;
		}
		int radix = 10;
		int length = nm.length();
		if (index + 1 < length && nm.charAt(index) == '0' && (nm.charAt(index + 1) | 0x20) == 'x') {
			index += 2;
			radix = 16;
		} else if (index < length && nm.charAt(index) == '#') {
			index++;
			radix = 16;
		} else if (index + 1 < length && nm.charAt(index) == '0') {
			index++;
			radix = 8;
		}
		if (index < length && (nm.charAt(index) == '-' || nm.charAt(index) == '+')) {
			throw new NumberFormatException("Sign character in wrong position");
		}
		// Parsing the sign with the digits lets the most negative value through
		String digits = nm.substring(index);
		return parse(negative ? "-" + digits : digits, radix, min, max);
	}

	public static int compare(long x, long y) {
		return x < y ? -1 : (x == y ? 0 : 1);
	}

	public static int compareUnsigned(long x, long y) {
		return compare(x + MIN_VALUE, y + MIN_VALUE);
	}

	public static long divideUnsigned(long dividend, long divisor) {
		if (divisor < 0) {
			// A divisor of 2^63 or more goes into the dividend at most once
			return compareUnsigned(dividend, divisor) < 0 ? 0 : 1;
		}
		if (dividend >= 0) {
			return dividend / divisor;
		}
		// Halve the dividend to divide it signed, then correct the estimate
		long quotient = ((dividend >>> 1) / divisor) << 1;
		long remainder = dividend - quotient * divisor;
		return compareUnsigned(remainder, divisor) >= 0 ? quotient + 1 : quotient;
	}

	public static long remainderUnsigned(long dividend, long divisor) {
		return dividend - divideUnsigned(dividend, divisor) * divisor;
	}

	public static int bitCount(long i) {
		return Integer.bitCount((int) i) + Integer.bitCount((int) (i >>> 32));
	}

	public static long highestOneBit(long i) {
		return i & (MIN_VALUE >>> numberOfLeadingZeros(i));
	}

	public static long lowestOneBit(long i) {
		return i & -i;
	}

	public static int numberOfLeadingZeros(long i) {
		int high = (int) (i >>> 32);
		return high != 0 ? Integer.numberOfLeadingZeros(high) : 32 + Integer.numberOfLeadingZeros((int) i);
	}

	public static int numberOfTrailingZeros(long i) {
		int low = (int) i;
		return low != 0 ? Integer.numberOfTrailingZeros(low) : 32 + Integer.numberOfTrailingZeros((int) (i >>> 32));
	}

	public static long reverse(long i) {
		return ((long) Integer.reverse((int) i) << 32) | (Integer.reverse((int) (i >>> 32)) & 0xffffffffL);
	}

	public static long reverseBytes(long i) {
		return ((long) Integer.reverseBytes((int) i) << 32) | (Integer.reverseBytes((int) (i >>> 32)) & 0xffffffffL);
	}

	public static long rotateLeft(long i, int distance) {
		return (i << distance) | (i >>> -distance);
	}

	public static long rotateRight(long i, int distance) {
		return (i >>> distance) | (i << -distance);
	}

	public static int signum(long i) {
		return i > 0 ? 1 : (i < 0 ? -1 : 0);
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

	public byte byteValue() {
		return (byte) value;
	}

	public short shortValue() {
		return (short) value;
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
