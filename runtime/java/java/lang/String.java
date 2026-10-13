package java.lang;

/**
 * Simplified String: UTF-16 code units in a char[], written from the Java SE
 * specification. Literals and intern() share one table (j2c_runtime.cpp).
 *
 * Limits: case mapping (toUpperCase, toLowerCase, the ignoreCase methods)
 * is exact for ASCII and Latin-1 characters only, as in the JDK's root
 * locale; other characters keep their case. split accepts only patterns
 * without regular expression constructs (literal text, backslash-escaped
 * punctuation, or the empty pattern) and throws
 * UnsupportedOperationException for anything else.
 */
public final class String implements java.io.Serializable, Comparable<String>, CharSequence {
	private final char[] value;
	private int hash;

	public static final java.util.Comparator<String> CASE_INSENSITIVE_ORDER = new CaseInsensitiveComparator();

	private static final class CaseInsensitiveComparator implements java.util.Comparator<String>, java.io.Serializable {
		public int compare(String s1, String s2) {
			return s1.compareToIgnoreCase(s2);
		}
	}

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
			// The JDK's message depends on whether the string is Latin-1
			if (isLatin1()) {
				throw new StringIndexOutOfBoundsException(index);
			}
			throw new StringIndexOutOfBoundsException("index " + index + ", length " + value.length);
		}
		return value[index];
	}

	public int codePointAt(int index) {
		checkIndex(index, value.length);
		char high = value[index];
		if (isHighSurrogate(high) && index + 1 < value.length) {
			char low = value[index + 1];
			if (isLowSurrogate(low)) {
				return ((high - 0xD800) << 10) + (low - 0xDC00) + 0x10000;
			}
		}
		return high;
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

	public boolean contentEquals(CharSequence cs) {
		int n = cs.length();
		if (n != value.length) {
			return false;
		}
		for (int i = 0; i < n; i++) {
			if (value[i] != cs.charAt(i)) {
				return false;
			}
		}
		return true;
	}

	public boolean equalsIgnoreCase(String anotherString) {
		if (this == anotherString) {
			return true;
		}
		return anotherString != null && anotherString.value.length == value.length
				&& regionMatches(true, 0, anotherString, 0, value.length);
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

	public int compareToIgnoreCase(String str) {
		int limit = Math.min(value.length, str.value.length);
		for (int i = 0; i < limit; i++) {
			char c1 = value[i];
			char c2 = str.value[i];
			if (c1 != c2) {
				c1 = upper(c1);
				c2 = upper(c2);
				if (c1 != c2) {
					c1 = lower(c1);
					c2 = lower(c2);
					if (c1 != c2) {
						return c1 - c2;
					}
				}
			}
		}
		return value.length - str.value.length;
	}

	public boolean regionMatches(int toffset, String other, int ooffset, int len) {
		if (!regionInBounds(toffset, other, ooffset, len)) {
			return false;
		}
		for (int i = 0; i < len; i++) {
			if (value[toffset + i] != other.value[ooffset + i]) {
				return false;
			}
		}
		return true;
	}

	public boolean regionMatches(boolean ignoreCase, int toffset, String other, int ooffset, int len) {
		if (!ignoreCase) {
			return regionMatches(toffset, other, ooffset, len);
		}
		if (!regionInBounds(toffset, other, ooffset, len)) {
			return false;
		}
		for (int i = 0; i < len; i++) {
			char c1 = value[toffset + i];
			char c2 = other.value[ooffset + i];
			if (c1 == c2) {
				continue;
			}
			char u1 = upper(c1);
			char u2 = upper(c2);
			if (u1 != u2 && lower(u1) != lower(u2)) {
				return false;
			}
		}
		return true;
	}

	private boolean regionInBounds(int toffset, String other, int ooffset, int len) {
		return toffset >= 0 && ooffset >= 0 && toffset <= (long) value.length - len
				&& ooffset <= (long) other.value.length - len;
	}

	public int indexOf(int ch) {
		return indexOf(ch, 0);
	}

	public int indexOf(int ch, int fromIndex) {
		int from = Math.max(fromIndex, 0);
		if (ch < 0x10000) {
			for (int i = from; i < value.length; i++) {
				if (value[i] == ch) {
					return i;
				}
			}
		} else if (ch <= 0x10FFFF) {
			char high = highSurrogate(ch);
			char low = lowSurrogate(ch);
			for (int i = from; i < value.length - 1; i++) {
				if (value[i] == high && value[i + 1] == low) {
					return i;
				}
			}
		}
		return -1;
	}

	public int lastIndexOf(int ch) {
		return lastIndexOf(ch, value.length - 1);
	}

	public int lastIndexOf(int ch, int fromIndex) {
		if (ch < 0x10000) {
			for (int i = Math.min(fromIndex, value.length - 1); i >= 0; i--) {
				if (value[i] == ch) {
					return i;
				}
			}
		} else if (ch <= 0x10FFFF) {
			char high = highSurrogate(ch);
			char low = lowSurrogate(ch);
			for (int i = Math.min(fromIndex, value.length - 2); i >= 0; i--) {
				if (value[i] == high && value[i + 1] == low) {
					return i;
				}
			}
		}
		return -1;
	}

	public int indexOf(String str) {
		return indexOf(str, 0);
	}

	public int indexOf(String str, int fromIndex) {
		return indexOf(value, value.length, str, fromIndex);
	}

	public int lastIndexOf(String str) {
		return lastIndexOf(str, value.length);
	}

	public int lastIndexOf(String str, int fromIndex) {
		return lastIndexOf(value, value.length, str, fromIndex);
	}

	/** Searches the first count chars of source; shared with StringBuilder */
	static int indexOf(char[] source, int count, String str, int fromIndex) {
		int n = str.value.length;
		if (fromIndex >= count) {
			return n == 0 ? count : -1;
		}
		if (fromIndex < 0) {
			fromIndex = 0;
		}
		for (int i = fromIndex; i <= count - n; i++) {
			if (matchesAt(source, i, str)) {
				return i;
			}
		}
		return -1;
	}

	static int lastIndexOf(char[] source, int count, String str, int fromIndex) {
		int rightmost = count - str.value.length;
		if (fromIndex > rightmost) {
			fromIndex = rightmost;
		}
		for (int i = fromIndex; i >= 0; i--) {
			if (matchesAt(source, i, str)) {
				return i;
			}
		}
		return -1;
	}

	private static boolean matchesAt(char[] source, int offset, String str) {
		for (int j = 0; j < str.value.length; j++) {
			if (source[offset + j] != str.value[j]) {
				return false;
			}
		}
		return true;
	}

	public boolean contains(CharSequence s) {
		return indexOf(s.toString()) >= 0;
	}

	public boolean startsWith(String prefix) {
		return startsWith(prefix, 0);
	}

	public boolean startsWith(String prefix, int toffset) {
		return toffset >= 0 && toffset <= value.length - prefix.value.length && matchesAt(value, toffset, prefix);
	}

	public boolean endsWith(String suffix) {
		return startsWith(suffix, value.length - suffix.value.length);
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

	public String strip() {
		int begin = leadingWhitespace();
		return substring(begin, trailingWhitespace(begin));
	}

	public String stripLeading() {
		return substring(leadingWhitespace(), value.length);
	}

	public String stripTrailing() {
		return substring(0, trailingWhitespace(0));
	}

	public boolean isBlank() {
		return leadingWhitespace() == value.length;
	}

	private int leadingWhitespace() {
		int begin = 0;
		while (begin < value.length && Character.isWhitespace(value[begin])) {
			begin++;
		}
		return begin;
	}

	private int trailingWhitespace(int begin) {
		int end = value.length;
		while (end > begin && Character.isWhitespace(value[end - 1])) {
			end--;
		}
		return end;
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

	public String repeat(int count) {
		if (count < 0) {
			throw new IllegalArgumentException("count is negative: " + count);
		}
		if (count == 1) {
			return this;
		}
		int len = value.length;
		if (len == 0 || count == 0) {
			return "";
		}
		if (Integer.MAX_VALUE / count < len) {
			// The JDK throws OutOfMemoryError, which this runtime does not have yet
			throw new Error("Required length exceeds implementation limit");
		}
		char[] result = new char[len * count];
		for (int i = 0; i < count; i++) {
			System.arraycopy(value, 0, result, i * len, len);
		}
		return new String(result, true);
	}

	public String replace(char oldChar, char newChar) {
		if (oldChar != newChar) {
			int i = indexOf(oldChar);
			if (i >= 0) {
				char[] result = toCharArray();
				for (; i < result.length; i++) {
					if (result[i] == oldChar) {
						result[i] = newChar;
					}
				}
				return new String(result, true);
			}
		}
		return this;
	}

	public String replace(CharSequence target, CharSequence replacement) {
		String from = target.toString();
		String to = replacement.toString();
		int n = from.value.length;
		if (n == 0) {
			// The empty string matches before every char and at the end
			StringBuilder sb = new StringBuilder(value.length + (value.length + 1) * to.value.length);
			for (int i = 0; i < value.length; i++) {
				sb.append(to).append(value[i]);
			}
			return sb.append(to).toString();
		}
		int i = indexOf(from);
		if (i < 0) {
			return this;
		}
		StringBuilder sb = new StringBuilder(value.length);
		int done = 0;
		while (i >= 0) {
			sb.append(value, done, i - done).append(to);
			done = i + n;
			i = indexOf(from, done);
		}
		return sb.append(value, done, value.length - done).toString();
	}

	/** Maps ASCII and Latin-1 like the JDK's root locale; see the class comment */
	public String toUpperCase() {
		int extra = 0;
		boolean changed = false;
		for (int i = 0; i < value.length; i++) {
			char c = value[i];
			if (c == 0xDF) {
				extra++;
			} else if (upper(c) != c) {
				changed = true;
			}
		}
		if (!changed && extra == 0) {
			return this;
		}
		char[] result = new char[value.length + extra];
		int j = 0;
		for (int i = 0; i < value.length; i++) {
			char c = value[i];
			if (c == 0xDF) {
				// sharp s has no single-char upper case form
				result[j++] = 'S';
				result[j++] = 'S';
			} else {
				result[j++] = upper(c);
			}
		}
		return new String(result, true);
	}

	public String toLowerCase() {
		int i = 0;
		while (i < value.length && lower(value[i]) == value[i]) {
			i++;
		}
		if (i == value.length) {
			return this;
		}
		char[] result = toCharArray();
		for (; i < result.length; i++) {
			result[i] = lower(result[i]);
		}
		return new String(result, true);
	}

	/** Character's Latin-1 mapping plus the two Latin-1 letters whose upper case is outside it */
	private static char upper(char c) {
		if (c == 0xFF) {
			return (char) 0x178;
		}
		if (c == 0xB5) {
			return (char) 0x39C;
		}
		return Character.toUpperCase(c);
	}

	private static char lower(char c) {
		if (c == 0x178) {
			return (char) 0xFF;
		}
		if (c == 0x39C) {
			return (char) 0x3BC;
		}
		return Character.toLowerCase(c);
	}

	public String[] split(String regex) {
		return split(regex, 0);
	}

	public String[] split(String regex, int limit) {
		String literal = literalPattern(regex);
		if (literal == null) {
			throw new UnsupportedOperationException("String.split: the J2C runtime supports only literal patterns, not the regular expression \""
					+ regex + "\"");
		}
		return splitLiteral(literal, limit);
	}

	/**
	 * The text a pattern matches if it has no regular expression constructs:
	 * plain characters and backslash-escaped non-alphanumeric ones. null
	 * otherwise.
	 */
	private static String literalPattern(String regex) {
		StringBuilder sb = new StringBuilder(regex.length());
		for (int i = 0; i < regex.length(); i++) {
			char c = regex.charAt(i);
			if (isHighSurrogate(c) || isLowSurrogate(c)) {
				return null;
			}
			if (c == '\\') {
				if (++i == regex.length()) {
					return null;
				}
				c = regex.charAt(i);
				if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || isHighSurrogate(c)
						|| isLowSurrogate(c)) {
					return null;
				}
			} else if (".$|()[{^?*+".indexOf(c) >= 0) {
				return null;
			}
			sb.append(c);
		}
		return sb.toString();
	}

	/** split's rules for a pattern that matches exactly the text literal */
	private String[] splitLiteral(String literal, int limit) {
		int len = value.length;
		int n = literal.value.length;
		boolean limited = limit > 0;
		String[] parts = new String[len + 1];
		int count = 0;
		int index = 0;
		int searchFrom = 0;
		while (true) {
			int start;
			if (n == 0) {
				// The empty pattern matches at every position, the end included
				if (searchFrom > len) {
					break;
				}
				start = searchFrom++;
			} else {
				start = indexOf(literal, searchFrom);
				if (start < 0) {
					break;
				}
				searchFrom = start + n;
			}
			if (!limited || count < limit - 1) {
				if (start == 0 && n == 0) {
					// A zero-width match at the start gives no leading empty string
					continue;
				}
				parts[count++] = substring(index, start);
				index = start + n;
			} else {
				if (count == limit - 1) {
					parts[count++] = substring(index, len);
					index = start + n;
				}
				break;
			}
		}
		if (index == 0) {
			return new String[] { this };
		}
		if (!limited || count < limit) {
			parts[count++] = substring(index, len);
		}
		if (limit == 0) {
			while (count > 0 && parts[count - 1].isEmpty()) {
				count--;
			}
		}
		String[] result = new String[count];
		System.arraycopy(parts, 0, result, 0, count);
		return result;
	}

	public static String join(CharSequence delimiter, CharSequence... elements) {
		if (delimiter == null || elements == null) {
			throw new NullPointerException();
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < elements.length; i++) {
			if (i > 0) {
				sb.append(delimiter);
			}
			sb.append(elements[i]);
		}
		return sb.toString();
	}

	/** See java.util.Formatter for the supported syntax */
	public static String format(String format, Object... args) {
		return new java.util.Formatter().format(format, args).toString();
	}

	public String formatted(Object... args) {
		return new java.util.Formatter().format(this, args).toString();
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

	public static String valueOf(char[] data, int offset, int count) {
		return new String(data, offset, count);
	}

	public static String copyValueOf(char[] data) {
		return new String(data);
	}

	public static String copyValueOf(char[] data, int offset, int count) {
		return new String(data, offset, count);
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

	private boolean isLatin1() {
		for (int i = 0; i < value.length; i++) {
			if (value[i] > 0xFF) {
				return false;
			}
		}
		return true;
	}

	private static boolean isHighSurrogate(char c) {
		return c >= 0xD800 && c <= 0xDBFF;
	}

	private static boolean isLowSurrogate(char c) {
		return c >= 0xDC00 && c <= 0xDFFF;
	}

	private static char highSurrogate(int codePoint) {
		return (char) (((codePoint - 0x10000) >>> 10) + 0xD800);
	}

	private static char lowSurrogate(int codePoint) {
		return (char) (((codePoint - 0x10000) & 0x3FF) + 0xDC00);
	}

	static void checkIndex(int index, int length) {
		if (index < 0 || index >= length) {
			throw new StringIndexOutOfBoundsException("index " + index + ", length " + length);
		}
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
