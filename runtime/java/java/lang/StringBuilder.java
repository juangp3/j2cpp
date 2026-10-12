package java.lang;

/** Simplified StringBuilder, written from the Java SE specification. */
public final class StringBuilder implements java.io.Serializable, Comparable<StringBuilder>, CharSequence {
	private char[] value;
	private int count;

	public StringBuilder() {
		value = new char[16];
	}

	public StringBuilder(int capacity) {
		if (capacity < 0) {
			throw new NegativeArraySizeException("" + capacity);
		}
		value = new char[capacity];
	}

	public StringBuilder(String str) {
		value = new char[str.length() + 16];
		append(str);
	}

	public StringBuilder(CharSequence seq) {
		this(seq.toString());
	}

	public int capacity() {
		return value.length;
	}

	public void ensureCapacity(int minimumCapacity) {
		if (minimumCapacity > value.length) {
			int newCapacity = Math.max(value.length * 2 + 2, minimumCapacity);
			char[] larger = new char[newCapacity];
			System.arraycopy(value, 0, larger, 0, count);
			value = larger;
		}
	}

	public void trimToSize() {
		if (count < value.length) {
			char[] smaller = new char[count];
			System.arraycopy(value, 0, smaller, 0, count);
			value = smaller;
		}
	}

	public int length() {
		return count;
	}

	public char charAt(int index) {
		String.checkIndex(index, count);
		return value[index];
	}

	public void setCharAt(int index, char ch) {
		String.checkIndex(index, count);
		value[index] = ch;
	}

	public void setLength(int newLength) {
		if (newLength < 0) {
			throw new StringIndexOutOfBoundsException(newLength);
		}
		ensureCapacity(newLength);
		for (int i = count; i < newLength; i++) {
			value[i] = '\0';
		}
		count = newLength;
	}

	public StringBuilder append(String str) {
		if (str == null) {
			str = "null";
		}
		int len = str.length();
		ensureCapacity(count + len);
		str.getChars(0, len, value, count);
		count += len;
		return this;
	}

	public StringBuilder append(Object obj) {
		return append(String.valueOf(obj));
	}

	public StringBuilder append(CharSequence s) {
		return append(String.valueOf(s));
	}

	public StringBuilder append(CharSequence s, int start, int end) {
		if (s == null) {
			s = "null";
		}
		checkRange(start, end, s.length());
		ensureCapacity(count + end - start);
		for (int i = start; i < end; i++) {
			value[count++] = s.charAt(i);
		}
		return this;
	}

	public StringBuilder append(char[] str) {
		return append(new String(str));
	}

	public StringBuilder append(char[] str, int offset, int len) {
		checkRange(offset, offset + len, str.length);
		ensureCapacity(count + len);
		System.arraycopy(str, offset, value, count, len);
		count += len;
		return this;
	}

	public StringBuilder append(char c) {
		ensureCapacity(count + 1);
		value[count++] = c;
		return this;
	}

	public StringBuilder append(boolean b) {
		return append(b ? "true" : "false");
	}

	public StringBuilder append(int i) {
		return append(Integer.toString(i));
	}

	public StringBuilder append(long l) {
		return append(Long.toString(l));
	}

	public StringBuilder append(float f) {
		return append(Float.toString(f));
	}

	public StringBuilder append(double d) {
		return append(Double.toString(d));
	}

	public StringBuilder appendCodePoint(int codePoint) {
		if (codePoint >= 0 && codePoint < 0x10000) {
			return append((char) codePoint);
		}
		if (codePoint >= 0x10000 && codePoint <= 0x10FFFF) {
			ensureCapacity(count + 2);
			value[count++] = (char) (((codePoint - 0x10000) >>> 10) + 0xD800);
			value[count++] = (char) (((codePoint - 0x10000) & 0x3FF) + 0xDC00);
			return this;
		}
		throw new IllegalArgumentException("Not a valid Unicode code point: 0x"
				+ Integer.toHexString(codePoint).toUpperCase());
	}

	public StringBuilder insert(int offset, String str) {
		checkOffset(offset);
		if (str == null) {
			str = "null";
		}
		int len = str.length();
		ensureCapacity(count + len);
		System.arraycopy(value, offset, value, offset + len, count - offset);
		str.getChars(0, len, value, offset);
		count += len;
		return this;
	}

	public StringBuilder insert(int offset, char c) {
		return insert(offset, String.valueOf(c));
	}

	public StringBuilder insert(int offset, Object obj) {
		return insert(offset, String.valueOf(obj));
	}

	public StringBuilder insert(int offset, char[] str) {
		checkOffset(offset);
		return insert(offset, new String(str));
	}

	public StringBuilder insert(int index, char[] str, int offset, int len) {
		checkOffset(index);
		checkRangeString(offset, offset + len, str.length);
		return insert(index, new String(str, offset, len));
	}

	public StringBuilder insert(int dstOffset, CharSequence s) {
		return insert(dstOffset, String.valueOf(s));
	}

	public StringBuilder insert(int dstOffset, CharSequence s, int start, int end) {
		if (s == null) {
			s = "null";
		}
		checkOffset(dstOffset);
		checkRange(start, end, s.length());
		return insert(dstOffset, s.subSequence(start, end).toString());
	}

	public StringBuilder insert(int offset, boolean b) {
		return insert(offset, String.valueOf(b));
	}

	public StringBuilder insert(int offset, int i) {
		return insert(offset, String.valueOf(i));
	}

	public StringBuilder insert(int offset, long l) {
		return insert(offset, String.valueOf(l));
	}

	public StringBuilder insert(int offset, float f) {
		return insert(offset, String.valueOf(f));
	}

	public StringBuilder insert(int offset, double d) {
		return insert(offset, String.valueOf(d));
	}

	public StringBuilder delete(int start, int end) {
		if (end > count) {
			end = count;
		}
		checkRangeString(start, end, count);
		System.arraycopy(value, end, value, start, count - end);
		count -= end - start;
		return this;
	}

	public StringBuilder deleteCharAt(int index) {
		String.checkIndex(index, count);
		System.arraycopy(value, index + 1, value, index, count - index - 1);
		count--;
		return this;
	}

	public StringBuilder replace(int start, int end, String str) {
		if (end > count) {
			end = count;
		}
		checkRangeString(start, end, count);
		int len = str.length();
		int newCount = count + len - (end - start);
		ensureCapacity(newCount);
		System.arraycopy(value, end, value, start + len, count - end);
		str.getChars(0, len, value, start);
		count = newCount;
		return this;
	}

	public StringBuilder reverse() {
		for (int i = 0, j = count - 1; i < j; i++, j--) {
			char c = value[i];
			value[i] = value[j];
			value[j] = c;
		}
		return this;
	}

	public int indexOf(String str) {
		return indexOf(str, 0);
	}

	public int indexOf(String str, int fromIndex) {
		return String.indexOf(value, count, str, fromIndex);
	}

	public int lastIndexOf(String str) {
		return lastIndexOf(str, count);
	}

	public int lastIndexOf(String str, int fromIndex) {
		return String.lastIndexOf(value, count, str, fromIndex);
	}

	public int compareTo(StringBuilder another) {
		return CharSequence.compare(this, another);
	}

	public CharSequence subSequence(int start, int end) {
		return substring(start, end);
	}

	public String substring(int start) {
		return substring(start, count);
	}

	public String substring(int start, int end) {
		checkRangeString(start, end, count);
		return new String(value, start, end - start);
	}

	public String toString() {
		return new String(value, 0, count);
	}

	private void checkOffset(int offset) {
		if (offset < 0 || offset > count) {
			throw new StringIndexOutOfBoundsException("offset " + offset + ", length " + count);
		}
	}

	private static void checkRange(int start, int end, int length) {
		if (start < 0 || start > end || end > length) {
			throw new IndexOutOfBoundsException("start " + start + ", end " + end + ", length " + length);
		}
	}

	private static void checkRangeString(int start, int end, int length) {
		if (start < 0 || start > end || end > length) {
			throw new StringIndexOutOfBoundsException("start " + start + ", end " + end + ", length " + length);
		}
	}
}
