package java.util;

/**
 * Objects, written from the Java SE specification. The Supplier and
 * Comparator forms (requireNonNull with a Supplier, requireNonNullElseGet,
 * compare) wait for java.util.function and Comparator.
 */
public final class Objects {
	private Objects() {
	}

	public static boolean equals(Object a, Object b) {
		return a == b || (a != null && a.equals(b));
	}

	/** Arrays compare element by element, nested arrays included */
	public static boolean deepEquals(Object a, Object b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null) {
			return false;
		}
		if (a instanceof Object[] && b instanceof Object[]) {
			Object[] x = (Object[]) a;
			Object[] y = (Object[]) b;
			if (x.length != y.length) {
				return false;
			}
			for (int i = 0; i < x.length; i++) {
				if (!deepEquals(x[i], y[i])) {
					return false;
				}
			}
			return true;
		}
		if (a instanceof int[] && b instanceof int[]) {
			int[] x = (int[]) a;
			int[] y = (int[]) b;
			if (x.length != y.length) {
				return false;
			}
			for (int i = 0; i < x.length; i++) {
				if (x[i] != y[i]) {
					return false;
				}
			}
			return true;
		}
		if (a instanceof long[] && b instanceof long[]) {
			long[] x = (long[]) a;
			long[] y = (long[]) b;
			if (x.length != y.length) {
				return false;
			}
			for (int i = 0; i < x.length; i++) {
				if (x[i] != y[i]) {
					return false;
				}
			}
			return true;
		}
		if (a instanceof short[] && b instanceof short[]) {
			short[] x = (short[]) a;
			short[] y = (short[]) b;
			if (x.length != y.length) {
				return false;
			}
			for (int i = 0; i < x.length; i++) {
				if (x[i] != y[i]) {
					return false;
				}
			}
			return true;
		}
		if (a instanceof byte[] && b instanceof byte[]) {
			byte[] x = (byte[]) a;
			byte[] y = (byte[]) b;
			if (x.length != y.length) {
				return false;
			}
			for (int i = 0; i < x.length; i++) {
				if (x[i] != y[i]) {
					return false;
				}
			}
			return true;
		}
		if (a instanceof char[] && b instanceof char[]) {
			char[] x = (char[]) a;
			char[] y = (char[]) b;
			if (x.length != y.length) {
				return false;
			}
			for (int i = 0; i < x.length; i++) {
				if (x[i] != y[i]) {
					return false;
				}
			}
			return true;
		}
		if (a instanceof boolean[] && b instanceof boolean[]) {
			boolean[] x = (boolean[]) a;
			boolean[] y = (boolean[]) b;
			if (x.length != y.length) {
				return false;
			}
			for (int i = 0; i < x.length; i++) {
				if (x[i] != y[i]) {
					return false;
				}
			}
			return true;
		}
		if (a instanceof double[] && b instanceof double[]) {
			double[] x = (double[]) a;
			double[] y = (double[]) b;
			if (x.length != y.length) {
				return false;
			}
			// As Double.equals: NaN equals NaN, 0.0 differs from -0.0
			for (int i = 0; i < x.length; i++) {
				if (Double.doubleToLongBits(x[i]) != Double.doubleToLongBits(y[i])) {
					return false;
				}
			}
			return true;
		}
		if (a instanceof float[] && b instanceof float[]) {
			float[] x = (float[]) a;
			float[] y = (float[]) b;
			if (x.length != y.length) {
				return false;
			}
			for (int i = 0; i < x.length; i++) {
				if (!sameFloat(x[i], y[i])) {
					return false;
				}
			}
			return true;
		}
		return a.equals(b);
	}

	private static boolean sameFloat(float x, float y) {
		if (x != x) {
			return y != y;
		}
		if (x == 0.0f && y == 0.0f) {
			return Double.doubleToRawLongBits(x) == Double.doubleToRawLongBits(y);
		}
		return x == y;
	}

	public static int hashCode(Object o) {
		return o != null ? o.hashCode() : 0;
	}

	/** As Arrays.hashCode(Object[]) */
	public static int hash(Object... values) {
		if (values == null) {
			return 0;
		}
		int result = 1;
		for (int i = 0; i < values.length; i++) {
			Object element = values[i];
			result = 31 * result + (element == null ? 0 : element.hashCode());
		}
		return result;
	}

	public static String toString(Object o) {
		return String.valueOf(o);
	}

	public static String toString(Object o, String nullDefault) {
		return o != null ? o.toString() : nullDefault;
	}

	public static <T> T requireNonNull(T obj) {
		if (obj == null) {
			throw new NullPointerException();
		}
		return obj;
	}

	public static <T> T requireNonNull(T obj, String message) {
		if (obj == null) {
			throw new NullPointerException(message);
		}
		return obj;
	}

	public static <T> T requireNonNullElse(T obj, T defaultObj) {
		return obj != null ? obj : requireNonNull(defaultObj, "defaultObj");
	}

	public static boolean isNull(Object obj) {
		return obj == null;
	}

	public static boolean nonNull(Object obj) {
		return obj != null;
	}

	public static int checkIndex(int index, int length) {
		if (index < 0 || index >= length) {
			throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
		}
		return index;
	}

	public static long checkIndex(long index, long length) {
		if (index < 0 || index >= length) {
			throw new IndexOutOfBoundsException("Index " + index + " out of bounds for length " + length);
		}
		return index;
	}

	public static int checkFromToIndex(int fromIndex, int toIndex, int length) {
		if (fromIndex < 0 || fromIndex > toIndex || toIndex > length) {
			throw new IndexOutOfBoundsException("Range [" + fromIndex + ", " + toIndex
					+ ") out of bounds for length " + length);
		}
		return fromIndex;
	}

	public static long checkFromToIndex(long fromIndex, long toIndex, long length) {
		if (fromIndex < 0 || fromIndex > toIndex || toIndex > length) {
			throw new IndexOutOfBoundsException("Range [" + fromIndex + ", " + toIndex
					+ ") out of bounds for length " + length);
		}
		return fromIndex;
	}

	public static int checkFromIndexSize(int fromIndex, int size, int length) {
		if ((length | fromIndex | size) < 0 || size > length - fromIndex) {
			throw new IndexOutOfBoundsException("Range [" + fromIndex + ", " + fromIndex + " + " + size
					+ ") out of bounds for length " + length);
		}
		return fromIndex;
	}

	public static long checkFromIndexSize(long fromIndex, long size, long length) {
		if ((length | fromIndex | size) < 0 || size > length - fromIndex) {
			throw new IndexOutOfBoundsException("Range [" + fromIndex + ", " + fromIndex + " + " + size
					+ ") out of bounds for length " + length);
		}
		return fromIndex;
	}
}
