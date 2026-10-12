package java.lang;

public final class Integer extends Number implements Comparable<Integer> {
	public static final int MIN_VALUE = 0x80000000;
	public static final int MAX_VALUE = 0x7fffffff;
	public static final int SIZE = 32;
	public static final int BYTES = 4;

	// Boxing caches -128..127, which Java guarantees to be identical objects
	private static final Integer[] CACHE = new Integer[256];

	private final int value;

	public Integer(int value) {
		this.value = value;
	}

	public Integer(String s) {
		this.value = parseInt(s, 10);
	}

	public static Integer valueOf(int i) {
		if (i >= -128 && i <= 127) {
			Integer cached = CACHE[i + 128];
			if (cached == null) {
				cached = new Integer(i);
				CACHE[i + 128] = cached;
			}
			return cached;
		}
		return new Integer(i);
	}

	public static Integer valueOf(String s) {
		return valueOf(parseInt(s, 10));
	}

	public static Integer valueOf(String s, int radix) {
		return valueOf(parseInt(s, radix));
	}

	public static String toString(int i) {
		return Long.toString(i);
	}

	public static String toString(int i, int radix) {
		return Long.toString(i, radix);
	}

	public static String toUnsignedString(int i) {
		return Long.toString(toUnsignedLong(i));
	}

	public static String toUnsignedString(int i, int radix) {
		return Long.toString(toUnsignedLong(i), radix);
	}

	public static String toHexString(int i) {
		return Long.unsignedDigits(toUnsignedLong(i), 4);
	}

	public static String toBinaryString(int i) {
		return Long.unsignedDigits(toUnsignedLong(i), 1);
	}

	public static String toOctalString(int i) {
		return Long.unsignedDigits(toUnsignedLong(i), 3);
	}

	public static int parseInt(String s) {
		return parseInt(s, 10);
	}

	public static int parseInt(String s, int radix) {
		return (int) Long.parse(s, radix, MIN_VALUE, MAX_VALUE);
	}

	public static int parseUnsignedInt(String s) {
		return parseUnsignedInt(s, 10);
	}

	public static int parseUnsignedInt(String s, int radix) {
		if (s == null) {
			throw new NumberFormatException("Cannot parse null string");
		}
		if (s.length() == 0) {
			throw Long.forInput(s, radix);
		}
		if (s.charAt(0) == '-') {
			throw new NumberFormatException("Illegal leading minus sign on unsigned string " + s + ".");
		}
		long value = Long.parseLong(s, radix);
		if ((value >>> 32) != 0) {
			throw new NumberFormatException("String value " + s + " exceeds range of unsigned int.");
		}
		return (int) value;
	}

	public static Integer decode(String nm) {
		return valueOf((int) Long.decode(nm, MIN_VALUE, MAX_VALUE));
	}

	public static long toUnsignedLong(int x) {
		return x & 0xffffffffL;
	}

	public static int compare(int x, int y) {
		return x < y ? -1 : (x == y ? 0 : 1);
	}

	public static int compareUnsigned(int x, int y) {
		return compare(x + MIN_VALUE, y + MIN_VALUE);
	}

	public static int divideUnsigned(int dividend, int divisor) {
		return (int) (toUnsignedLong(dividend) / toUnsignedLong(divisor));
	}

	public static int remainderUnsigned(int dividend, int divisor) {
		return (int) (toUnsignedLong(dividend) % toUnsignedLong(divisor));
	}

	public static int bitCount(int i) {
		// Sum adjacent bit fields of growing width
		i = i - ((i >>> 1) & 0x55555555);
		i = (i & 0x33333333) + ((i >>> 2) & 0x33333333);
		i = (i + (i >>> 4)) & 0x0f0f0f0f;
		return (i * 0x01010101) >>> 24;
	}

	public static int highestOneBit(int i) {
		return i & (MIN_VALUE >>> numberOfLeadingZeros(i));
	}

	public static int lowestOneBit(int i) {
		return i & -i;
	}

	public static int numberOfLeadingZeros(int i) {
		if (i == 0) {
			return 32;
		}
		int n = 0;
		while (i > 0) {
			i <<= 1;
			n++;
		}
		return n;
	}

	public static int numberOfTrailingZeros(int i) {
		if (i == 0) {
			return 32;
		}
		int n = 0;
		while ((i & 1) == 0) {
			i >>>= 1;
			n++;
		}
		return n;
	}

	public static int reverse(int i) {
		// Swap bits, then bit pairs, then nibbles, then reverse the bytes
		i = ((i & 0x55555555) << 1) | ((i >>> 1) & 0x55555555);
		i = ((i & 0x33333333) << 2) | ((i >>> 2) & 0x33333333);
		i = ((i & 0x0f0f0f0f) << 4) | ((i >>> 4) & 0x0f0f0f0f);
		return reverseBytes(i);
	}

	public static int reverseBytes(int i) {
		return (i << 24) | ((i & 0xff00) << 8) | ((i >>> 8) & 0xff00) | (i >>> 24);
	}

	public static int rotateLeft(int i, int distance) {
		return (i << distance) | (i >>> -distance);
	}

	public static int rotateRight(int i, int distance) {
		return (i >>> distance) | (i << -distance);
	}

	public static int signum(int i) {
		return (i >> 31) | (-i >>> 31);
	}

	public static int sum(int a, int b) {
		return a + b;
	}

	public static int max(int a, int b) {
		return Math.max(a, b);
	}

	public static int min(int a, int b) {
		return Math.min(a, b);
	}

	public static int hashCode(int value) {
		return value;
	}

	public int compareTo(Integer anotherInteger) {
		return compare(value, anotherInteger.value);
	}

	public byte byteValue() {
		return (byte) value;
	}

	public short shortValue() {
		return (short) value;
	}

	public int intValue() {
		return value;
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
		return value;
	}

	public boolean equals(Object obj) {
		return obj instanceof Integer && value == ((Integer) obj).value;
	}

	public String toString() {
		return toString(value);
	}
}
