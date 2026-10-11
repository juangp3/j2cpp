package java.lang;

/** Simplified StringBuilder, written from the Java SE specification. */
public final class StringBuilder implements java.io.Serializable, CharSequence {
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

	private void ensureCapacity(int minimumCapacity) {
		if (minimumCapacity > value.length) {
			int newCapacity = Math.max(value.length * 2 + 2, minimumCapacity);
			char[] larger = new char[newCapacity];
			System.arraycopy(value, 0, larger, 0, count);
			value = larger;
		}
	}

	public int length() {
		return count;
	}

	public char charAt(int index) {
		if (index < 0 || index >= count) {
			throw new StringIndexOutOfBoundsException("index " + index + ",length " + count);
		}
		return value[index];
	}

	public void setCharAt(int index, char ch) {
		if (index < 0 || index >= count) {
			throw new StringIndexOutOfBoundsException("index " + index + ",length " + count);
		}
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

	public StringBuilder append(char[] str) {
		return append(new String(str));
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

	public StringBuilder insert(int offset, String str) {
		if (offset < 0 || offset > count) {
			throw new StringIndexOutOfBoundsException("offset " + offset + ", length " + count);
		}
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

	public StringBuilder deleteCharAt(int index) {
		if (index < 0 || index >= count) {
			throw new StringIndexOutOfBoundsException("index " + index + ",length " + count);
		}
		System.arraycopy(value, index + 1, value, index, count - index - 1);
		count--;
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
		return toString().indexOf(str);
	}

	public CharSequence subSequence(int start, int end) {
		return substring(start, end);
	}

	public String substring(int start, int end) {
		String.checkBoundsBeginEnd(start, end, count);
		return new String(value, start, end - start);
	}

	public String toString() {
		return new String(value, 0, count);
	}
}
