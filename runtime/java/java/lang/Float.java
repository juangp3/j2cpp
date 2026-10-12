package java.lang;

public final class Float extends Number implements Comparable<Float> {
	public static final float POSITIVE_INFINITY = 1.0f / 0.0f;
	public static final float NEGATIVE_INFINITY = -1.0f / 0.0f;
	public static final float NaN = 0.0f / 0.0f;
	public static final float MAX_VALUE = 0x1.fffffeP+127f;
	public static final float MIN_VALUE = 0x0.000002P-126f;
	public static final float MIN_NORMAL = 0x1.0p-126f;
	public static final int MAX_EXPONENT = 127;
	public static final int MIN_EXPONENT = -126;
	public static final int SIZE = 32;
	public static final int BYTES = 4;

	private final float value;

	public Float(float value) {
		this.value = value;
	}

	public Float(double value) {
		this.value = (float) value;
	}

	public Float(String s) {
		this.value = parseFloat(s);
	}

	public static Float valueOf(float f) {
		return new Float(f);
	}

	public static Float valueOf(String s) {
		return new Float(parseFloat(s));
	}

	/** Java's Float.toString format, from the shortest round-trip digits */
	public static native String toString(float f);

	public static String toHexString(float f) {
		int bits = floatToRawIntBits(f);
		int fraction = bits & 0x007fffff;
		if ((bits & 0x7f800000) == 0 && fraction != 0) {
			// Subnormal: 23 fraction bits, padded to six hex digits
			return (bits < 0 ? "-" : "") + "0x0." + Double.hexFraction((long) fraction << 1, 6) + "p-126";
		}
		// Every other float is exact, and printed the same, as a double
		return Double.toHexString(f);
	}

	public static float parseFloat(String s) {
		return (float) Double.parse(s, true);
	}

	public static boolean isNaN(float v) {
		return v != v;
	}

	public static boolean isInfinite(float v) {
		return v == POSITIVE_INFINITY || v == NEGATIVE_INFINITY;
	}

	public static boolean isFinite(float f) {
		return Math.abs(f) <= MAX_VALUE;
	}

	public static native int floatToRawIntBits(float value);

	public static int floatToIntBits(float value) {
		return isNaN(value) ? 0x7fc00000 : floatToRawIntBits(value);
	}

	public static native float intBitsToFloat(int bits);

	public static int compare(float f1, float f2) {
		if (f1 < f2) {
			return -1;
		}
		if (f1 > f2) {
			return 1;
		}
		// Equal, or unordered: -0.0 sorts before 0.0, NaN after everything
		int thisBits = floatToIntBits(f1);
		int anotherBits = floatToIntBits(f2);
		return thisBits == anotherBits ? 0 : (thisBits < anotherBits ? -1 : 1);
	}

	public static int hashCode(float value) {
		return floatToIntBits(value);
	}

	public static float max(float a, float b) {
		return Math.max(a, b);
	}

	public static float min(float a, float b) {
		return Math.min(a, b);
	}

	public static float sum(float a, float b) {
		return a + b;
	}

	public boolean isNaN() {
		return isNaN(value);
	}

	public boolean isInfinite() {
		return isInfinite(value);
	}

	public int compareTo(Float anotherFloat) {
		return compare(value, anotherFloat.value);
	}

	public int intValue() {
		return (int) value;
	}

	public long longValue() {
		return (long) value;
	}

	public float floatValue() {
		return value;
	}

	public double doubleValue() {
		return value;
	}

	public boolean equals(Object obj) {
		return obj instanceof Float && floatToIntBits(((Float) obj).value) == floatToIntBits(value);
	}

	public int hashCode() {
		return floatToIntBits(value);
	}

	public String toString() {
		return toString(value);
	}
}
