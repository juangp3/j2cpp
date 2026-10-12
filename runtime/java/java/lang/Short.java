package java.lang;

public final class Short extends Number implements Comparable<Short> {
	public static final short MIN_VALUE = -32768;
	public static final short MAX_VALUE = 32767;
	public static final int SIZE = 16;
	public static final int BYTES = 2;

	// Boxing caches -128..127, which Java guarantees to be identical objects
	private static final Short[] CACHE = new Short[256];

	private final short value;

	public Short(short value) {
		this.value = value;
	}

	public Short(String s) {
		this.value = parseShort(s, 10);
	}

	public static Short valueOf(short s) {
		if (s >= -128 && s <= 127) {
			int index = s + 128;
			Short cached = CACHE[index];
			if (cached == null) {
				cached = new Short(s);
				CACHE[index] = cached;
			}
			return cached;
		}
		return new Short(s);
	}

	public static Short valueOf(String s) {
		return valueOf(parseShort(s, 10));
	}

	public static Short valueOf(String s, int radix) {
		return valueOf(parseShort(s, radix));
	}

	public static short parseShort(String s) {
		return parseShort(s, 10);
	}

	public static short parseShort(String s, int radix) {
		int i = Integer.parseInt(s, radix);
		if (i < MIN_VALUE || i > MAX_VALUE) {
			throw new NumberFormatException("Value out of range. Value:\"" + s + "\" Radix:" + radix);
		}
		return (short) i;
	}

	public static Short decode(String nm) {
		int i = Integer.decode(nm).intValue();
		if (i < MIN_VALUE || i > MAX_VALUE) {
			throw new NumberFormatException("Value " + i + " out of range from input " + nm);
		}
		return valueOf((short) i);
	}

	public static String toString(short s) {
		return Integer.toString(s);
	}

	public static int toUnsignedInt(short x) {
		return x & 0xffff;
	}

	public static long toUnsignedLong(short x) {
		return x & 0xffffL;
	}

	public static short reverseBytes(short i) {
		return (short) (((i & 0xff) << 8) | ((i >> 8) & 0xff));
	}

	public static int compare(short x, short y) {
		return x - y;
	}

	public static int compareUnsigned(short x, short y) {
		return toUnsignedInt(x) - toUnsignedInt(y);
	}

	public static int hashCode(short value) {
		return value;
	}

	public int compareTo(Short anotherShort) {
		return compare(value, anotherShort.value);
	}

	public byte byteValue() {
		return (byte) value;
	}

	public short shortValue() {
		return value;
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
		return obj instanceof Short && value == ((Short) obj).value;
	}

	public String toString() {
		return Integer.toString(value);
	}
}
