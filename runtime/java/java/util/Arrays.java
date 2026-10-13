package java.util;

/**
 * Arrays, written from the Java SE specification. Object arrays sort with a
 * stable merge sort, as the specification requires; primitive arrays use the
 * same algorithm, since any correct sort gives the same result. Floating-point
 * values sort as Double.compare orders them: -0.0 before 0.0, NaN last.
 */
public final class Arrays {
	private Arrays() {
	}

	/** A new array of the given length with the runtime type of a */
	static native Object[] newArrayLike(Object[] a, int length);

	private static void rangeCheck(int arrayLength, int fromIndex, int toIndex) {
		if (fromIndex > toIndex) {
			throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
		}
		if (fromIndex < 0) {
			throw new ArrayIndexOutOfBoundsException("Array index out of range: " + fromIndex);
		}
		if (toIndex > arrayLength) {
			throw new ArrayIndexOutOfBoundsException("Array index out of range: " + toIndex);
		}
	}

	@SafeVarargs
	public static <T> List<T> asList(T... a) {
		return new ArrayList<T>(a);
	}

	/** The fixed-size list asList returns, backed by the array */
	private static class ArrayList<E> extends AbstractList<E> implements RandomAccess, java.io.Serializable {
		private final E[] a;

		ArrayList(E[] array) {
			a = Objects.requireNonNull(array);
		}

		public int size() {
			return a.length;
		}

		public Object[] toArray() {
			Object[] copy = new Object[a.length];
			System.arraycopy(a, 0, copy, 0, a.length);
			return copy;
		}

		public E get(int index) {
			return a[index];
		}

		public E set(int index, E element) {
			E oldValue = a[index];
			a[index] = element;
			return oldValue;
		}

		public int indexOf(Object o) {
			for (int i = 0; i < a.length; i++) {
				if (Objects.equals(o, a[i])) {
					return i;
				}
			}
			return -1;
		}

		public boolean contains(Object o) {
			return indexOf(o) >= 0;
		}

		public void sort(Comparator<? super E> c) {
			Arrays.sort(a, c);
		}
	}

	public static String toString(int[] a) {
		if (a == null) {
			return "null";
		}
		if (a.length == 0) {
			return "[]";
		}
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; ; i++) {
			b.append(a[i]);
			if (i == a.length - 1) {
				return b.append(']').toString();
			}
			b.append(", ");
		}
	}

	public static int hashCode(int[] a) {
		if (a == null) {
			return 0;
		}
		int result = 1;
		for (int e : a) {
			result = 31 * result + e;
		}
		return result;
	}

	public static boolean equals(int[] a, int[] b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null || a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}

	public static void fill(int[] a, int val) {
		for (int i = 0; i < a.length; i++) {
			a[i] = val;
		}
	}

	public static void fill(int[] a, int fromIndex, int toIndex, int val) {
		rangeCheck(a.length, fromIndex, toIndex);
		for (int i = fromIndex; i < toIndex; i++) {
			a[i] = val;
		}
	}

	public static int[] copyOf(int[] original, int newLength) {
		int[] copy = new int[newLength];
		System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
		return copy;
	}

	public static int[] copyOfRange(int[] original, int from, int to) {
		int newLength = to - from;
		if (newLength < 0) {
			throw new IllegalArgumentException(from + " > " + to);
		}
		int[] copy = new int[newLength];
		System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
		return copy;
	}

	public static void sort(int[] a) {
		sort(a, 0, a.length);
	}

	public static void sort(int[] a, int fromIndex, int toIndex) {
		rangeCheck(a.length, fromIndex, toIndex);
		if (toIndex - fromIndex > 1) {
			int[] work = new int[toIndex - fromIndex];
			mergeSort(a, work, fromIndex, toIndex);
		}
	}

	private static void mergeSort(int[] a, int[] work, int from, int to) {
		if (to - from <= 7) {
			for (int i = from + 1; i < to; i++) {
				int x = a[i];
				int j = i - 1;
				while (j >= from && x < a[j]) {
					a[j + 1] = a[j];
					j--;
				}
				a[j + 1] = x;
			}
			return;
		}
		int mid = (from + to) >>> 1;
		mergeSort(a, work, from, mid);
		mergeSort(a, work, mid, to);
		if (!(a[mid] < a[mid - 1])) {
			return;
		}
		int n = mid - from;
		System.arraycopy(a, from, work, 0, n);
		int i = 0, j = mid, k = from;
		while (i < n && j < to) {
			a[k++] = a[j] < work[i] ? a[j++] : work[i++];
		}
		while (i < n) {
			a[k++] = work[i++];
		}
	}

	public static int binarySearch(int[] a, int key) {
		return binarySearch(a, 0, a.length, key);
	}

	public static int binarySearch(int[] a, int fromIndex, int toIndex, int key) {
		rangeCheck(a.length, fromIndex, toIndex);
		int low = fromIndex;
		int high = toIndex - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			int midVal = a[mid];
			if (midVal < key) {
				low = mid + 1;
			} else if (key < midVal) {
				high = mid - 1;
			} else {
				return mid;
			}
		}
		return -(low + 1);
	}

	public static String toString(long[] a) {
		if (a == null) {
			return "null";
		}
		if (a.length == 0) {
			return "[]";
		}
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; ; i++) {
			b.append(a[i]);
			if (i == a.length - 1) {
				return b.append(']').toString();
			}
			b.append(", ");
		}
	}

	public static int hashCode(long[] a) {
		if (a == null) {
			return 0;
		}
		int result = 1;
		for (long e : a) {
			result = 31 * result + Long.hashCode(e);
		}
		return result;
	}

	public static boolean equals(long[] a, long[] b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null || a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}

	public static void fill(long[] a, long val) {
		for (int i = 0; i < a.length; i++) {
			a[i] = val;
		}
	}

	public static void fill(long[] a, int fromIndex, int toIndex, long val) {
		rangeCheck(a.length, fromIndex, toIndex);
		for (int i = fromIndex; i < toIndex; i++) {
			a[i] = val;
		}
	}

	public static long[] copyOf(long[] original, int newLength) {
		long[] copy = new long[newLength];
		System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
		return copy;
	}

	public static long[] copyOfRange(long[] original, int from, int to) {
		int newLength = to - from;
		if (newLength < 0) {
			throw new IllegalArgumentException(from + " > " + to);
		}
		long[] copy = new long[newLength];
		System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
		return copy;
	}

	public static void sort(long[] a) {
		sort(a, 0, a.length);
	}

	public static void sort(long[] a, int fromIndex, int toIndex) {
		rangeCheck(a.length, fromIndex, toIndex);
		if (toIndex - fromIndex > 1) {
			long[] work = new long[toIndex - fromIndex];
			mergeSort(a, work, fromIndex, toIndex);
		}
	}

	private static void mergeSort(long[] a, long[] work, int from, int to) {
		if (to - from <= 7) {
			for (int i = from + 1; i < to; i++) {
				long x = a[i];
				int j = i - 1;
				while (j >= from && x < a[j]) {
					a[j + 1] = a[j];
					j--;
				}
				a[j + 1] = x;
			}
			return;
		}
		int mid = (from + to) >>> 1;
		mergeSort(a, work, from, mid);
		mergeSort(a, work, mid, to);
		if (!(a[mid] < a[mid - 1])) {
			return;
		}
		int n = mid - from;
		System.arraycopy(a, from, work, 0, n);
		int i = 0, j = mid, k = from;
		while (i < n && j < to) {
			a[k++] = a[j] < work[i] ? a[j++] : work[i++];
		}
		while (i < n) {
			a[k++] = work[i++];
		}
	}

	public static int binarySearch(long[] a, long key) {
		return binarySearch(a, 0, a.length, key);
	}

	public static int binarySearch(long[] a, int fromIndex, int toIndex, long key) {
		rangeCheck(a.length, fromIndex, toIndex);
		int low = fromIndex;
		int high = toIndex - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			long midVal = a[mid];
			if (midVal < key) {
				low = mid + 1;
			} else if (key < midVal) {
				high = mid - 1;
			} else {
				return mid;
			}
		}
		return -(low + 1);
	}

	public static String toString(short[] a) {
		if (a == null) {
			return "null";
		}
		if (a.length == 0) {
			return "[]";
		}
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; ; i++) {
			b.append(a[i]);
			if (i == a.length - 1) {
				return b.append(']').toString();
			}
			b.append(", ");
		}
	}

	public static int hashCode(short[] a) {
		if (a == null) {
			return 0;
		}
		int result = 1;
		for (short e : a) {
			result = 31 * result + e;
		}
		return result;
	}

	public static boolean equals(short[] a, short[] b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null || a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}

	public static void fill(short[] a, short val) {
		for (int i = 0; i < a.length; i++) {
			a[i] = val;
		}
	}

	public static void fill(short[] a, int fromIndex, int toIndex, short val) {
		rangeCheck(a.length, fromIndex, toIndex);
		for (int i = fromIndex; i < toIndex; i++) {
			a[i] = val;
		}
	}

	public static short[] copyOf(short[] original, int newLength) {
		short[] copy = new short[newLength];
		System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
		return copy;
	}

	public static short[] copyOfRange(short[] original, int from, int to) {
		int newLength = to - from;
		if (newLength < 0) {
			throw new IllegalArgumentException(from + " > " + to);
		}
		short[] copy = new short[newLength];
		System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
		return copy;
	}

	public static void sort(short[] a) {
		sort(a, 0, a.length);
	}

	public static void sort(short[] a, int fromIndex, int toIndex) {
		rangeCheck(a.length, fromIndex, toIndex);
		if (toIndex - fromIndex > 1) {
			short[] work = new short[toIndex - fromIndex];
			mergeSort(a, work, fromIndex, toIndex);
		}
	}

	private static void mergeSort(short[] a, short[] work, int from, int to) {
		if (to - from <= 7) {
			for (int i = from + 1; i < to; i++) {
				short x = a[i];
				int j = i - 1;
				while (j >= from && x < a[j]) {
					a[j + 1] = a[j];
					j--;
				}
				a[j + 1] = x;
			}
			return;
		}
		int mid = (from + to) >>> 1;
		mergeSort(a, work, from, mid);
		mergeSort(a, work, mid, to);
		if (!(a[mid] < a[mid - 1])) {
			return;
		}
		int n = mid - from;
		System.arraycopy(a, from, work, 0, n);
		int i = 0, j = mid, k = from;
		while (i < n && j < to) {
			a[k++] = a[j] < work[i] ? a[j++] : work[i++];
		}
		while (i < n) {
			a[k++] = work[i++];
		}
	}

	public static int binarySearch(short[] a, short key) {
		return binarySearch(a, 0, a.length, key);
	}

	public static int binarySearch(short[] a, int fromIndex, int toIndex, short key) {
		rangeCheck(a.length, fromIndex, toIndex);
		int low = fromIndex;
		int high = toIndex - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			short midVal = a[mid];
			if (midVal < key) {
				low = mid + 1;
			} else if (key < midVal) {
				high = mid - 1;
			} else {
				return mid;
			}
		}
		return -(low + 1);
	}

	public static String toString(char[] a) {
		if (a == null) {
			return "null";
		}
		if (a.length == 0) {
			return "[]";
		}
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; ; i++) {
			b.append(a[i]);
			if (i == a.length - 1) {
				return b.append(']').toString();
			}
			b.append(", ");
		}
	}

	public static int hashCode(char[] a) {
		if (a == null) {
			return 0;
		}
		int result = 1;
		for (char e : a) {
			result = 31 * result + e;
		}
		return result;
	}

	public static boolean equals(char[] a, char[] b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null || a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}

	public static void fill(char[] a, char val) {
		for (int i = 0; i < a.length; i++) {
			a[i] = val;
		}
	}

	public static void fill(char[] a, int fromIndex, int toIndex, char val) {
		rangeCheck(a.length, fromIndex, toIndex);
		for (int i = fromIndex; i < toIndex; i++) {
			a[i] = val;
		}
	}

	public static char[] copyOf(char[] original, int newLength) {
		char[] copy = new char[newLength];
		System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
		return copy;
	}

	public static char[] copyOfRange(char[] original, int from, int to) {
		int newLength = to - from;
		if (newLength < 0) {
			throw new IllegalArgumentException(from + " > " + to);
		}
		char[] copy = new char[newLength];
		System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
		return copy;
	}

	public static void sort(char[] a) {
		sort(a, 0, a.length);
	}

	public static void sort(char[] a, int fromIndex, int toIndex) {
		rangeCheck(a.length, fromIndex, toIndex);
		if (toIndex - fromIndex > 1) {
			char[] work = new char[toIndex - fromIndex];
			mergeSort(a, work, fromIndex, toIndex);
		}
	}

	private static void mergeSort(char[] a, char[] work, int from, int to) {
		if (to - from <= 7) {
			for (int i = from + 1; i < to; i++) {
				char x = a[i];
				int j = i - 1;
				while (j >= from && x < a[j]) {
					a[j + 1] = a[j];
					j--;
				}
				a[j + 1] = x;
			}
			return;
		}
		int mid = (from + to) >>> 1;
		mergeSort(a, work, from, mid);
		mergeSort(a, work, mid, to);
		if (!(a[mid] < a[mid - 1])) {
			return;
		}
		int n = mid - from;
		System.arraycopy(a, from, work, 0, n);
		int i = 0, j = mid, k = from;
		while (i < n && j < to) {
			a[k++] = a[j] < work[i] ? a[j++] : work[i++];
		}
		while (i < n) {
			a[k++] = work[i++];
		}
	}

	public static int binarySearch(char[] a, char key) {
		return binarySearch(a, 0, a.length, key);
	}

	public static int binarySearch(char[] a, int fromIndex, int toIndex, char key) {
		rangeCheck(a.length, fromIndex, toIndex);
		int low = fromIndex;
		int high = toIndex - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			char midVal = a[mid];
			if (midVal < key) {
				low = mid + 1;
			} else if (key < midVal) {
				high = mid - 1;
			} else {
				return mid;
			}
		}
		return -(low + 1);
	}

	public static String toString(byte[] a) {
		if (a == null) {
			return "null";
		}
		if (a.length == 0) {
			return "[]";
		}
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; ; i++) {
			b.append(a[i]);
			if (i == a.length - 1) {
				return b.append(']').toString();
			}
			b.append(", ");
		}
	}

	public static int hashCode(byte[] a) {
		if (a == null) {
			return 0;
		}
		int result = 1;
		for (byte e : a) {
			result = 31 * result + e;
		}
		return result;
	}

	public static boolean equals(byte[] a, byte[] b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null || a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}

	public static void fill(byte[] a, byte val) {
		for (int i = 0; i < a.length; i++) {
			a[i] = val;
		}
	}

	public static void fill(byte[] a, int fromIndex, int toIndex, byte val) {
		rangeCheck(a.length, fromIndex, toIndex);
		for (int i = fromIndex; i < toIndex; i++) {
			a[i] = val;
		}
	}

	public static byte[] copyOf(byte[] original, int newLength) {
		byte[] copy = new byte[newLength];
		System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
		return copy;
	}

	public static byte[] copyOfRange(byte[] original, int from, int to) {
		int newLength = to - from;
		if (newLength < 0) {
			throw new IllegalArgumentException(from + " > " + to);
		}
		byte[] copy = new byte[newLength];
		System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
		return copy;
	}

	public static void sort(byte[] a) {
		sort(a, 0, a.length);
	}

	public static void sort(byte[] a, int fromIndex, int toIndex) {
		rangeCheck(a.length, fromIndex, toIndex);
		if (toIndex - fromIndex > 1) {
			byte[] work = new byte[toIndex - fromIndex];
			mergeSort(a, work, fromIndex, toIndex);
		}
	}

	private static void mergeSort(byte[] a, byte[] work, int from, int to) {
		if (to - from <= 7) {
			for (int i = from + 1; i < to; i++) {
				byte x = a[i];
				int j = i - 1;
				while (j >= from && x < a[j]) {
					a[j + 1] = a[j];
					j--;
				}
				a[j + 1] = x;
			}
			return;
		}
		int mid = (from + to) >>> 1;
		mergeSort(a, work, from, mid);
		mergeSort(a, work, mid, to);
		if (!(a[mid] < a[mid - 1])) {
			return;
		}
		int n = mid - from;
		System.arraycopy(a, from, work, 0, n);
		int i = 0, j = mid, k = from;
		while (i < n && j < to) {
			a[k++] = a[j] < work[i] ? a[j++] : work[i++];
		}
		while (i < n) {
			a[k++] = work[i++];
		}
	}

	public static int binarySearch(byte[] a, byte key) {
		return binarySearch(a, 0, a.length, key);
	}

	public static int binarySearch(byte[] a, int fromIndex, int toIndex, byte key) {
		rangeCheck(a.length, fromIndex, toIndex);
		int low = fromIndex;
		int high = toIndex - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			byte midVal = a[mid];
			if (midVal < key) {
				low = mid + 1;
			} else if (key < midVal) {
				high = mid - 1;
			} else {
				return mid;
			}
		}
		return -(low + 1);
	}

	public static String toString(float[] a) {
		if (a == null) {
			return "null";
		}
		if (a.length == 0) {
			return "[]";
		}
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; ; i++) {
			b.append(a[i]);
			if (i == a.length - 1) {
				return b.append(']').toString();
			}
			b.append(", ");
		}
	}

	public static int hashCode(float[] a) {
		if (a == null) {
			return 0;
		}
		int result = 1;
		for (float e : a) {
			result = 31 * result + Float.floatToIntBits(e);
		}
		return result;
	}

	public static boolean equals(float[] a, float[] b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null || a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (Float.floatToIntBits(a[i]) != Float.floatToIntBits(b[i])) {
				return false;
			}
		}
		return true;
	}

	public static void fill(float[] a, float val) {
		for (int i = 0; i < a.length; i++) {
			a[i] = val;
		}
	}

	public static void fill(float[] a, int fromIndex, int toIndex, float val) {
		rangeCheck(a.length, fromIndex, toIndex);
		for (int i = fromIndex; i < toIndex; i++) {
			a[i] = val;
		}
	}

	public static float[] copyOf(float[] original, int newLength) {
		float[] copy = new float[newLength];
		System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
		return copy;
	}

	public static float[] copyOfRange(float[] original, int from, int to) {
		int newLength = to - from;
		if (newLength < 0) {
			throw new IllegalArgumentException(from + " > " + to);
		}
		float[] copy = new float[newLength];
		System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
		return copy;
	}

	public static void sort(float[] a) {
		sort(a, 0, a.length);
	}

	public static void sort(float[] a, int fromIndex, int toIndex) {
		rangeCheck(a.length, fromIndex, toIndex);
		if (toIndex - fromIndex > 1) {
			float[] work = new float[toIndex - fromIndex];
			mergeSort(a, work, fromIndex, toIndex);
		}
	}

	private static void mergeSort(float[] a, float[] work, int from, int to) {
		if (to - from <= 7) {
			for (int i = from + 1; i < to; i++) {
				float x = a[i];
				int j = i - 1;
				while (j >= from && Float.compare(x, a[j]) < 0) {
					a[j + 1] = a[j];
					j--;
				}
				a[j + 1] = x;
			}
			return;
		}
		int mid = (from + to) >>> 1;
		mergeSort(a, work, from, mid);
		mergeSort(a, work, mid, to);
		if (!(Float.compare(a[mid], a[mid - 1]) < 0)) {
			return;
		}
		int n = mid - from;
		System.arraycopy(a, from, work, 0, n);
		int i = 0, j = mid, k = from;
		while (i < n && j < to) {
			a[k++] = Float.compare(a[j], work[i]) < 0 ? a[j++] : work[i++];
		}
		while (i < n) {
			a[k++] = work[i++];
		}
	}

	public static int binarySearch(float[] a, float key) {
		return binarySearch(a, 0, a.length, key);
	}

	public static int binarySearch(float[] a, int fromIndex, int toIndex, float key) {
		rangeCheck(a.length, fromIndex, toIndex);
		int low = fromIndex;
		int high = toIndex - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			float midVal = a[mid];
			if (Float.compare(midVal, key) < 0) {
				low = mid + 1;
			} else if (Float.compare(key, midVal) < 0) {
				high = mid - 1;
			} else {
				return mid;
			}
		}
		return -(low + 1);
	}

	public static String toString(double[] a) {
		if (a == null) {
			return "null";
		}
		if (a.length == 0) {
			return "[]";
		}
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; ; i++) {
			b.append(a[i]);
			if (i == a.length - 1) {
				return b.append(']').toString();
			}
			b.append(", ");
		}
	}

	public static int hashCode(double[] a) {
		if (a == null) {
			return 0;
		}
		int result = 1;
		for (double e : a) {
			result = 31 * result + Double.hashCode(e);
		}
		return result;
	}

	public static boolean equals(double[] a, double[] b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null || a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (Double.doubleToLongBits(a[i]) != Double.doubleToLongBits(b[i])) {
				return false;
			}
		}
		return true;
	}

	public static void fill(double[] a, double val) {
		for (int i = 0; i < a.length; i++) {
			a[i] = val;
		}
	}

	public static void fill(double[] a, int fromIndex, int toIndex, double val) {
		rangeCheck(a.length, fromIndex, toIndex);
		for (int i = fromIndex; i < toIndex; i++) {
			a[i] = val;
		}
	}

	public static double[] copyOf(double[] original, int newLength) {
		double[] copy = new double[newLength];
		System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
		return copy;
	}

	public static double[] copyOfRange(double[] original, int from, int to) {
		int newLength = to - from;
		if (newLength < 0) {
			throw new IllegalArgumentException(from + " > " + to);
		}
		double[] copy = new double[newLength];
		System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
		return copy;
	}

	public static void sort(double[] a) {
		sort(a, 0, a.length);
	}

	public static void sort(double[] a, int fromIndex, int toIndex) {
		rangeCheck(a.length, fromIndex, toIndex);
		if (toIndex - fromIndex > 1) {
			double[] work = new double[toIndex - fromIndex];
			mergeSort(a, work, fromIndex, toIndex);
		}
	}

	private static void mergeSort(double[] a, double[] work, int from, int to) {
		if (to - from <= 7) {
			for (int i = from + 1; i < to; i++) {
				double x = a[i];
				int j = i - 1;
				while (j >= from && Double.compare(x, a[j]) < 0) {
					a[j + 1] = a[j];
					j--;
				}
				a[j + 1] = x;
			}
			return;
		}
		int mid = (from + to) >>> 1;
		mergeSort(a, work, from, mid);
		mergeSort(a, work, mid, to);
		if (!(Double.compare(a[mid], a[mid - 1]) < 0)) {
			return;
		}
		int n = mid - from;
		System.arraycopy(a, from, work, 0, n);
		int i = 0, j = mid, k = from;
		while (i < n && j < to) {
			a[k++] = Double.compare(a[j], work[i]) < 0 ? a[j++] : work[i++];
		}
		while (i < n) {
			a[k++] = work[i++];
		}
	}

	public static int binarySearch(double[] a, double key) {
		return binarySearch(a, 0, a.length, key);
	}

	public static int binarySearch(double[] a, int fromIndex, int toIndex, double key) {
		rangeCheck(a.length, fromIndex, toIndex);
		int low = fromIndex;
		int high = toIndex - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			double midVal = a[mid];
			if (Double.compare(midVal, key) < 0) {
				low = mid + 1;
			} else if (Double.compare(key, midVal) < 0) {
				high = mid - 1;
			} else {
				return mid;
			}
		}
		return -(low + 1);
	}

	public static String toString(boolean[] a) {
		if (a == null) {
			return "null";
		}
		if (a.length == 0) {
			return "[]";
		}
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; ; i++) {
			b.append(a[i]);
			if (i == a.length - 1) {
				return b.append(']').toString();
			}
			b.append(", ");
		}
	}

	public static int hashCode(boolean[] a) {
		if (a == null) {
			return 0;
		}
		int result = 1;
		for (boolean e : a) {
			result = 31 * result + (e ? 1231 : 1237);
		}
		return result;
	}

	public static boolean equals(boolean[] a, boolean[] b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null || a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (a[i] != b[i]) {
				return false;
			}
		}
		return true;
	}

	public static void fill(boolean[] a, boolean val) {
		for (int i = 0; i < a.length; i++) {
			a[i] = val;
		}
	}

	public static void fill(boolean[] a, int fromIndex, int toIndex, boolean val) {
		rangeCheck(a.length, fromIndex, toIndex);
		for (int i = fromIndex; i < toIndex; i++) {
			a[i] = val;
		}
	}

	public static boolean[] copyOf(boolean[] original, int newLength) {
		boolean[] copy = new boolean[newLength];
		System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
		return copy;
	}

	public static boolean[] copyOfRange(boolean[] original, int from, int to) {
		int newLength = to - from;
		if (newLength < 0) {
			throw new IllegalArgumentException(from + " > " + to);
		}
		boolean[] copy = new boolean[newLength];
		System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
		return copy;
	}

	public static String toString(Object[] a) {
		if (a == null) {
			return "null";
		}
		if (a.length == 0) {
			return "[]";
		}
		StringBuilder b = new StringBuilder();
		b.append('[');
		for (int i = 0; ; i++) {
			b.append(String.valueOf(a[i]));
			if (i == a.length - 1) {
				return b.append(']').toString();
			}
			b.append(", ");
		}
	}

	public static String deepToString(Object[] a) {
		if (a == null) {
			return "null";
		}
		StringBuilder b = new StringBuilder();
		deepToString(a, b, new Object[0]);
		return b.toString();
	}

	/** Appends a; path holds the arrays being printed, to show cycles as [...] */
	private static void deepToString(Object[] a, StringBuilder b, Object[] path) {
		if (a.length == 0) {
			b.append("[]");
			return;
		}
		Object[] inside = copyOf(path, path.length + 1);
		inside[path.length] = a;
		b.append('[');
		for (int i = 0; ; i++) {
			Object e = a[i];
			if (e == null) {
				b.append("null");
			} else if (e instanceof Object[]) {
				boolean cycle = false;
				for (Object p : inside) {
					if (p == e) {
						cycle = true;
					}
				}
				if (cycle) {
					b.append("[...]");
				} else {
					deepToString((Object[]) e, b, inside);
				}
			} else if (e instanceof int[]) {
				b.append(toString((int[]) e));
			} else if (e instanceof long[]) {
				b.append(toString((long[]) e));
			} else if (e instanceof short[]) {
				b.append(toString((short[]) e));
			} else if (e instanceof char[]) {
				b.append(toString((char[]) e));
			} else if (e instanceof byte[]) {
				b.append(toString((byte[]) e));
			} else if (e instanceof boolean[]) {
				b.append(toString((boolean[]) e));
			} else if (e instanceof float[]) {
				b.append(toString((float[]) e));
			} else if (e instanceof double[]) {
				b.append(toString((double[]) e));
			} else {
				b.append(e.toString());
			}
			if (i == a.length - 1) {
				break;
			}
			b.append(", ");
		}
		b.append(']');
	}

	public static int hashCode(Object[] a) {
		if (a == null) {
			return 0;
		}
		int result = 1;
		for (Object e : a) {
			result = 31 * result + (e == null ? 0 : e.hashCode());
		}
		return result;
	}

	public static int deepHashCode(Object[] a) {
		if (a == null) {
			return 0;
		}
		int result = 1;
		for (Object e : a) {
			int h;
			if (e instanceof Object[]) {
				h = deepHashCode((Object[]) e);
			} else if (e instanceof int[]) {
				h = hashCode((int[]) e);
			} else if (e instanceof long[]) {
				h = hashCode((long[]) e);
			} else if (e instanceof short[]) {
				h = hashCode((short[]) e);
			} else if (e instanceof char[]) {
				h = hashCode((char[]) e);
			} else if (e instanceof byte[]) {
				h = hashCode((byte[]) e);
			} else if (e instanceof boolean[]) {
				h = hashCode((boolean[]) e);
			} else if (e instanceof float[]) {
				h = hashCode((float[]) e);
			} else if (e instanceof double[]) {
				h = hashCode((double[]) e);
			} else {
				h = e == null ? 0 : e.hashCode();
			}
			result = 31 * result + h;
		}
		return result;
	}

	public static boolean equals(Object[] a, Object[] b) {
		if (a == b) {
			return true;
		}
		if (a == null || b == null || a.length != b.length) {
			return false;
		}
		for (int i = 0; i < a.length; i++) {
			if (!Objects.equals(a[i], b[i])) {
				return false;
			}
		}
		return true;
	}

	public static boolean deepEquals(Object[] a, Object[] b) {
		return Objects.deepEquals(a, b);
	}

	public static void fill(Object[] a, Object val) {
		for (int i = 0; i < a.length; i++) {
			a[i] = val;
		}
	}

	public static void fill(Object[] a, int fromIndex, int toIndex, Object val) {
		rangeCheck(a.length, fromIndex, toIndex);
		for (int i = fromIndex; i < toIndex; i++) {
			a[i] = val;
		}
	}

	@SuppressWarnings("unchecked")
	public static <T> T[] copyOf(T[] original, int newLength) {
		T[] copy = (T[]) newArrayLike(original, newLength);
		System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
		return copy;
	}

	@SuppressWarnings("unchecked")
	public static <T> T[] copyOfRange(T[] original, int from, int to) {
		int newLength = to - from;
		if (newLength < 0) {
			throw new IllegalArgumentException(from + " > " + to);
		}
		T[] copy = (T[]) newArrayLike(original, newLength);
		System.arraycopy(original, from, copy, 0, Math.min(original.length - from, newLength));
		return copy;
	}

	public static void sort(Object[] a) {
		sort(a, 0, a.length, null);
	}

	public static void sort(Object[] a, int fromIndex, int toIndex) {
		sort(a, fromIndex, toIndex, null);
	}

	public static <T> void sort(T[] a, Comparator<? super T> c) {
		sort(a, 0, a.length, c);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> void sort(T[] a, int fromIndex, int toIndex, Comparator<? super T> c) {
		rangeCheck(a.length, fromIndex, toIndex);
		if (c == null) {
			c = (Comparator) Comparators.NATURAL;
		}
		if (toIndex - fromIndex > 1) {
			Object[] work = new Object[toIndex - fromIndex];
			mergeSort(a, work, fromIndex, toIndex, c);
		}
	}

	/** Stable: equal elements keep their order */
	@SuppressWarnings("unchecked")
	private static <T> void mergeSort(T[] a, Object[] work, int from, int to, Comparator<? super T> c) {
		if (to - from <= 7) {
			for (int i = from + 1; i < to; i++) {
				T x = a[i];
				int j = i - 1;
				while (j >= from && c.compare(x, a[j]) < 0) {
					a[j + 1] = a[j];
					j--;
				}
				a[j + 1] = x;
			}
			return;
		}
		int mid = (from + to) >>> 1;
		mergeSort(a, work, from, mid, c);
		mergeSort(a, work, mid, to, c);
		if (c.compare(a[mid], a[mid - 1]) >= 0) {
			return;
		}
		int n = mid - from;
		System.arraycopy(a, from, work, 0, n);
		int i = 0, j = mid, k = from;
		while (i < n && j < to) {
			a[k++] = c.compare(a[j], (T) work[i]) < 0 ? a[j++] : (T) work[i++];
		}
		while (i < n) {
			a[k++] = (T) work[i++];
		}
	}

	public static int binarySearch(Object[] a, Object key) {
		return binarySearch(a, 0, a.length, key, null);
	}

	public static <T> int binarySearch(T[] a, T key, Comparator<? super T> c) {
		return binarySearch(a, 0, a.length, key, c);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> int binarySearch(T[] a, int fromIndex, int toIndex, T key, Comparator<? super T> c) {
		rangeCheck(a.length, fromIndex, toIndex);
		if (c == null) {
			c = (Comparator) Comparators.NATURAL;
		}
		int low = fromIndex;
		int high = toIndex - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			int cmp = c.compare(a[mid], key);
			if (cmp < 0) {
				low = mid + 1;
			} else if (cmp > 0) {
				high = mid - 1;
			} else {
				return mid;
			}
		}
		return -(low + 1);
	}
}
