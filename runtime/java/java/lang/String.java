package java.lang;

/**
 * Simplified String: UTF-16 code units in a char[], written from the Java SE
 * specification. Literals and intern() share one table (j2c_runtime.cpp).
 */
public final class String implements java.io.Serializable, Comparable<String>, CharSequence {
	private final char[] value;
	private int hash;

	public String() {
		this.value = new char[0];
	}

	public String(char[] value) {
		this(value, 0, value.length);
	}

	public String(char[] value, int offset, int count) {
		checkBoundsOffCount(offset, count, value.length);
		this.value = new char[count];
		System.arraycopy(value, offset, this.value, 0, count);
	}

	public String(String original) {
		this.value = original.value;
		this.hash = original.hash;
	}

	public String(StringBuilder builder) {
		this(builder.toString());
	}

	/** Shares the array; for StringBuilder and the runtime's own use */
	String(char[] value, boolean share) {
		this.value = value;
	}

	public int length() {
		return value.length;
	}

	public boolean isEmpty() {
		return value.length == 0;
	}

	public char charAt(int index) {
		if (index < 0 || index >= value.length) {
			throw new StringIndexOutOfBoundsException(index);
		}
		return value[index];
	}

	public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
		checkBoundsBeginEnd(srcBegin, srcEnd, value.length);
		System.arraycopy(value, srcBegin, dst, dstBegin, srcEnd - srcBegin);
	}

	public char[] toCharArray() {
		char[] result = new char[value.length];
		System.arraycopy(value, 0, result, 0, value.length);
		return result;
	}

	public boolean equals(Object anObject) {
		if (this == anObject) {
			return true;
		}
		if (!(anObject instanceof String)) {
			return false;
		}
		String other = (String) anObject;
		if (other.value.length != value.length) {
			return false;
		}
		for (int i = 0; i < value.length; i++) {
			if (value[i] != other.value[i]) {
				return false;
			}
		}
		return true;
	}

	public int hashCode() {
		int h = hash;
		if (h == 0) {
			for (int i = 0; i < value.length; i++) {
				h = 31 * h + value[i];
			}
			hash = h;
		}
		return h;
	}

	public int compareTo(String anotherString) {
		int limit = Math.min(value.length, anotherString.value.length);
		for (int i = 0; i < limit; i++) {
			if (value[i] != anotherString.value[i]) {
				return value[i] - anotherString.value[i];
			}
		}
		return value.length - anotherString.value.length;
	}

	public int indexOf(int ch) {
		return indexOf(ch, 0);
	}

	public int indexOf(int ch, int fromIndex) {
		for (int i = Math.max(fromIndex, 0); i < value.length; i++) {
			if (value[i] == ch) {
				return i;
			}
		}
		return -1;
	}

	public int lastIndexOf(int ch) {
		for (int i = value.length - 1; i >= 0; i--) {
			if (value[i] == ch) {
				return i;
			}
		}
		return -1;
	}

	public int indexOf(String str) {
		return indexOf(str, 0);
	}

	public int indexOf(String str, int fromIndex) {
		int max = value.length - str.value.length;
		for (int i = Math.max(fromIndex, 0); i <= max; i++) {
			if (regionMatches(i, str)) {
				return i;
			}
		}
		return -1;
	}

	public boolean contains(CharSequence s) {
		return indexOf(s.toString()) >= 0;
	}

	public boolean startsWith(String prefix) {
		return prefix.value.length <= value.length && regionMatches(0, prefix);
	}

	public boolean endsWith(String suffix) {
		int offset = value.length - suffix.value.length;
		return offset >= 0 && regionMatches(offset, suffix);
	}

	private boolean regionMatches(int offset, String other) {
		for (int j = 0; j < other.value.length; j++) {
			if (value[offset + j] != other.value[j]) {
				return false;
			}
		}
		return true;
	}

	public String substring(int beginIndex) {
		return substring(beginIndex, value.length);
	}

	public String substring(int beginIndex, int endIndex) {
		checkBoundsBeginEnd(beginIndex, endIndex, value.length);
		if (beginIndex == 0 && endIndex == value.length) {
			return this;
		}
		return new String(value, beginIndex, endIndex - beginIndex);
	}

	public CharSequence subSequence(int beginIndex, int endIndex) {
		return substring(beginIndex, endIndex);
	}

	public String trim() {
		int begin = 0;
		int end = value.length;
		while (begin < end && value[begin] <= ' ') {
			begin++;
		}
		while (end > begin && value[end - 1] <= ' ') {
			end--;
		}
		return substring(begin, end);
	}

	public String concat(String str) {
		if (str.isEmpty()) {
			return this;
		}
		char[] result = new char[value.length + str.value.length];
		System.arraycopy(value, 0, result, 0, value.length);
		System.arraycopy(str.value, 0, result, value.length, str.value.length);
		return new String(result, true);
	}

	public String toString() {
		return this;
	}

	// The intern table: open addressing over hashCode(), shared by literals
	private static String[] internTable = new String[64];
	private static int internCount;

	public String intern() {
		int mask = internTable.length - 1;
		int i = hashCode() & mask;
		for (String s = internTable[i]; s != null; s = internTable[i]) {
			if (s.equals(this)) {
				return s;
			}
			i = (i + 1) & mask;
		}
		internTable[i] = this;
		if (++internCount * 2 > internTable.length) {
			String[] old = internTable;
			internTable = new String[old.length * 2];
			internCount = 0;
			for (int j = 0; j < old.length; j++) {
				if (old[j] != null) {
					old[j].intern();
				}
			}
		}
		return this;
	}

	public static String valueOf(Object obj) {
		return obj == null ? "null" : obj.toString();
	}

	public static String valueOf(char[] data) {
		return new String(data);
	}

	public static String valueOf(boolean b) {
		return b ? "true" : "false";
	}

	public static String valueOf(char c) {
		return new String(new char[] { c }, true);
	}

	public static String valueOf(int i) {
		return Integer.toString(i);
	}

	public static String valueOf(long l) {
		return Long.toString(l);
	}

	public static String valueOf(float f) {
		return Float.toString(f);
	}

	public static String valueOf(double d) {
		return Double.toString(d);
	}

	static void checkBoundsBeginEnd(int begin, int end, int length) {
		if (begin < 0 || begin > end || end > length) {
			throw new StringIndexOutOfBoundsException("begin " + begin + ", end " + end
					+ ", length " + length);
		}
	}

	static void checkBoundsOffCount(int offset, int count, int length) {
		if (offset < 0 || count < 0 || offset > length - count) {
			throw new StringIndexOutOfBoundsException("offset " + offset + ", count "
					+ count + ", length " + length);
		}
	}
}
