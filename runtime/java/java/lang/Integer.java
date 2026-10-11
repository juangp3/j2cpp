package java.lang;

public final class Integer extends Number implements Comparable<Integer> {
	public static final int MIN_VALUE = 0x80000000;
	public static final int MAX_VALUE = 0x7fffffff;

	// Boxing caches -128..127, which Java guarantees to be identical objects
	private static final Integer[] CACHE = new Integer[256];

	private final int value;

	public Integer(int value) {
		this.value = value;
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
		return valueOf(parseInt(s));
	}

	public static String toString(int i) {
		return Long.toString(i);
	}

	public static String toString(int i, int radix) {
		return Long.toString(i, radix);
	}

	public static String toHexString(int i) {
		return Long.toUnsignedString(i & 0xffffffffL, 4);
	}

	public static String toBinaryString(int i) {
		return Long.toUnsignedString(i & 0xffffffffL, 1);
	}

	public static String toOctalString(int i) {
		return Long.toUnsignedString(i & 0xffffffffL, 3);
	}

	public static int parseInt(String s) {
		return parseInt(s, 10);
	}

	public static int parseInt(String s, int radix) {
		return (int) Long.parse(s, radix, MIN_VALUE, MAX_VALUE);
	}

	public static int compare(int x, int y) {
		return x < y ? -1 : (x == y ? 0 : 1);
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
