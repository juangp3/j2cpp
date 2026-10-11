package java.lang;

public final class Float extends Number implements Comparable<Float> {
	public static final float POSITIVE_INFINITY = 1.0f / 0.0f;
	public static final float NEGATIVE_INFINITY = -1.0f / 0.0f;
	public static final float NaN = 0.0f / 0.0f;
	public static final float MAX_VALUE = 0x1.fffffeP+127f;
	public static final float MIN_VALUE = 0x0.000002P-126f;

	private final float value;

	public Float(float value) {
		this.value = value;
	}

	public static Float valueOf(float f) {
		return new Float(f);
	}

	public static Float valueOf(String s) {
		return new Float(parseFloat(s));
	}

	/** Java's Float.toString format, from the shortest round-trip digits */
	public static native String toString(float f);

	public static float parseFloat(String s) {
		return (float) Double.parse(s, true);
	}

	public static boolean isNaN(float v) {
		return v != v;
	}

	public static boolean isInfinite(float v) {
		return v == POSITIVE_INFINITY || v == NEGATIVE_INFINITY;
	}

	public static int compare(float f1, float f2) {
		return Double.compare(f1, f2);
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
		return obj instanceof Float && compare(((Float) obj).value, value) == 0;
	}

	public int hashCode() {
		return Double.hashCode(value);
	}

	public String toString() {
		return toString(value);
	}
}
