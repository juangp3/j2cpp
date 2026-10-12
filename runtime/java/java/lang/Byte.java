package java.lang;

public final class Byte extends Number implements Comparable<Byte> {
	public static final byte MIN_VALUE = -128;
	public static final byte MAX_VALUE = 127;
	public static final int SIZE = 8;
	public static final int BYTES = 1;

	// Every byte value is cached, as Java requires for boxing
	private static final Byte[] CACHE = new Byte[256];

	private final byte value;

	public Byte(byte value) {
		this.value = value;
	}

	public Byte(String s) {
		this.value = parseByte(s, 10);
	}

	public static Byte valueOf(byte b) {
		int index = b + 128;
		Byte cached = CACHE[index];
		if (cached == null) {
			cached = new Byte(b);
			CACHE[index] = cached;
		}
		return cached;
	}

	public static Byte valueOf(String s) {
		return valueOf(parseByte(s, 10));
	}

	public static Byte valueOf(String s, int radix) {
		return valueOf(parseByte(s, radix));
	}

	public static byte parseByte(String s) {
		return parseByte(s, 10);
	}

	public static byte parseByte(String s, int radix) {
		int i = Integer.parseInt(s, radix);
		if (i < MIN_VALUE || i > MAX_VALUE) {
			throw new NumberFormatException("Value out of range. Value:\"" + s + "\" Radix:" + radix);
		}
		return (byte) i;
	}

	public static Byte decode(String nm) {
		int i = Integer.decode(nm).intValue();
		if (i < MIN_VALUE || i > MAX_VALUE) {
			throw new NumberFormatException("Value " + i + " out of range from input " + nm);
		}
		return valueOf((byte) i);
	}

	public static String toString(byte b) {
		return Integer.toString(b);
	}

	public static int toUnsignedInt(byte x) {
		return x & 0xff;
	}

	public static long toUnsignedLong(byte x) {
		return x & 0xffL;
	}

	public static int compare(byte x, byte y) {
		return x - y;
	}

	public static int compareUnsigned(byte x, byte y) {
		return toUnsignedInt(x) - toUnsignedInt(y);
	}

	public static int hashCode(byte value) {
		return value;
	}

	public int compareTo(Byte anotherByte) {
		return compare(value, anotherByte.value);
	}

	public byte byteValue() {
		return value;
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
		return obj instanceof Byte && value == ((Byte) obj).value;
	}

	public String toString() {
		return Integer.toString(value);
	}
}
