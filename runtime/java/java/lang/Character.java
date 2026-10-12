package java.lang;

/**
 * Simplified Character. Letter, case and digit classification covers ASCII
 * and Latin-1 (U+0000..U+00FF) only, plus the full-width digits and letters
 * that Java's digit rules name; other code points are not letters, digits or
 * cased. White space, code point and surrogate methods follow Java fully.
 */
public final class Character implements java.io.Serializable, Comparable<Character> {
	public static final int MIN_RADIX = 2;
	public static final int MAX_RADIX = 36;
	public static final char MIN_VALUE = '\u0000';
	public static final char MAX_VALUE = '\uffff';
	public static final char MIN_HIGH_SURROGATE = '\ud800';
	public static final char MAX_HIGH_SURROGATE = '\udbff';
	public static final char MIN_LOW_SURROGATE = '\udc00';
	public static final char MAX_LOW_SURROGATE = '\udfff';
	public static final char MIN_SURROGATE = MIN_HIGH_SURROGATE;
	public static final char MAX_SURROGATE = MAX_LOW_SURROGATE;
	public static final int MIN_SUPPLEMENTARY_CODE_POINT = 0x010000;
	public static final int MIN_CODE_POINT = 0x000000;
	public static final int MAX_CODE_POINT = 0x10ffff;
	public static final int SIZE = 16;
	public static final int BYTES = 2;

	private static final Character[] CACHE = new Character[128];

	private final char value;

	public Character(char value) {
		this.value = value;
	}

	public static Character valueOf(char c) {
		if (c < 128) {
			Character cached = CACHE[c];
			if (cached == null) {
				cached = new Character(c);
				CACHE[c] = cached;
			}
			return cached;
		}
		return new Character(c);
	}

	// Digits

	public static int digit(char ch, int radix) {
		return digit((int) ch, radix);
	}

	public static int digit(int codePoint, int radix) {
		int value;
		if (codePoint >= '0' && codePoint <= '9') {
			value = codePoint - '0';
		} else if (codePoint >= 'a' && codePoint <= 'z') {
			value = codePoint - 'a' + 10;
		} else if (codePoint >= 'A' && codePoint <= 'Z') {
			value = codePoint - 'A' + 10;
		} else if (codePoint >= 0xff10 && codePoint <= 0xff19) {
			// Full-width digits and letters
			value = codePoint - 0xff10;
		} else if (codePoint >= 0xff41 && codePoint <= 0xff5a) {
			value = codePoint - 0xff41 + 10;
		} else if (codePoint >= 0xff21 && codePoint <= 0xff3a) {
			value = codePoint - 0xff21 + 10;
		} else {
			return -1;
		}
		return radix >= MIN_RADIX && radix <= MAX_RADIX && value < radix ? value : -1;
	}

	public static char forDigit(int digit, int radix) {
		if (digit >= radix || digit < 0 || radix < MIN_RADIX || radix > MAX_RADIX) {
			return '\u0000';
		}
		return (char) (digit < 10 ? '0' + digit : 'a' - 10 + digit);
	}

	public static int getNumericValue(char ch) {
		return getNumericValue((int) ch);
	}

	public static int getNumericValue(int codePoint) {
		switch (codePoint) {
		case 0xb2:
			return 2;
		case 0xb3:
			return 3;
		case 0xb9:
			return 1;
		case 0xbc:
		case 0xbd:
		case 0xbe:
			// Fractions have no integer value
			return -2;
		default:
			return digit(codePoint, MAX_RADIX);
		}
	}

	public static boolean isDigit(char ch) {
		return isDigit((int) ch);
	}

	public static boolean isDigit(int codePoint) {
		return (codePoint >= '0' && codePoint <= '9') || (codePoint >= 0xff10 && codePoint <= 0xff19);
	}

	// Letters and case

	public static boolean isLetter(char ch) {
		return isLetter((int) ch);
	}

	public static boolean isLetter(int codePoint) {
		return isUpperCase(codePoint) || isLowerCase(codePoint);
	}

	public static boolean isLetterOrDigit(char ch) {
		return isLetterOrDigit((int) ch);
	}

	public static boolean isLetterOrDigit(int codePoint) {
		return isLetter(codePoint) || isDigit(codePoint);
	}

	public static boolean isAlphabetic(int codePoint) {
		return isLetter(codePoint);
	}

	public static boolean isUpperCase(char ch) {
		return isUpperCase((int) ch);
	}

	public static boolean isUpperCase(int codePoint) {
		return (codePoint >= 'A' && codePoint <= 'Z')
				|| (codePoint >= 0xc0 && codePoint <= 0xde && codePoint != 0xd7);
	}

	public static boolean isLowerCase(char ch) {
		return isLowerCase((int) ch);
	}

	public static boolean isLowerCase(int codePoint) {
		// Includes the ordinal indicators U+00AA and U+00BA, which are Other_Lowercase
		return (codePoint >= 'a' && codePoint <= 'z') || codePoint == 0xaa || codePoint == 0xb5
				|| codePoint == 0xba || (codePoint >= 0xdf && codePoint <= 0xff && codePoint != 0xf7);
	}

	public static char toUpperCase(char ch) {
		return (char) toUpperCase((int) ch);
	}

	public static int toUpperCase(int codePoint) {
		if ((codePoint >= 'a' && codePoint <= 'z')
				|| (codePoint >= 0xe0 && codePoint <= 0xfe && codePoint != 0xf7)) {
			return codePoint - 32;
		}
		if (codePoint == 0xb5) {
			// MICRO SIGN to GREEK CAPITAL LETTER MU
			return 0x39c;
		}
		if (codePoint == 0xff) {
			// y with diaeresis to LATIN CAPITAL LETTER Y WITH DIAERESIS
			return 0x178;
		}
		return codePoint;
	}

	public static char toLowerCase(char ch) {
		return (char) toLowerCase((int) ch);
	}

	public static int toLowerCase(int codePoint) {
		if ((codePoint >= 'A' && codePoint <= 'Z')
				|| (codePoint >= 0xc0 && codePoint <= 0xde && codePoint != 0xd7)) {
			return codePoint + 32;
		}
		return codePoint;
	}

	// Spaces and controls

	public static boolean isWhitespace(char ch) {
		return isWhitespace((int) ch);
	}

	public static boolean isWhitespace(int codePoint) {
		if (codePoint == 0xa0 || codePoint == 0x2007 || codePoint == 0x202f) {
			// Non-breaking spaces
			return false;
		}
		return (codePoint >= '\t' && codePoint <= '\r') || (codePoint >= 0x1c && codePoint <= 0x1f)
				|| isSpaceChar(codePoint);
	}

	public static boolean isSpaceChar(char ch) {
		return isSpaceChar((int) ch);
	}

	public static boolean isSpaceChar(int codePoint) {
		return codePoint == ' ' || codePoint == 0xa0 || codePoint == 0x1680
				|| (codePoint >= 0x2000 && codePoint <= 0x200a) || codePoint == 0x2028
				|| codePoint == 0x2029 || codePoint == 0x202f || codePoint == 0x205f
				|| codePoint == 0x3000;
	}

	public static boolean isISOControl(char ch) {
		return isISOControl((int) ch);
	}

	public static boolean isISOControl(int codePoint) {
		return (codePoint >= 0 && codePoint <= 0x1f) || (codePoint >= 0x7f && codePoint <= 0x9f);
	}

	// Java identifiers

	public static boolean isJavaIdentifierStart(char ch) {
		return isJavaIdentifierStart((int) ch);
	}

	public static boolean isJavaIdentifierStart(int codePoint) {
		// Letters, connector punctuation and currency symbols (U+00A2..U+00A5)
		return isLetter(codePoint) || codePoint == '_' || codePoint == '$'
				|| (codePoint >= 0xa2 && codePoint <= 0xa5);
	}

	public static boolean isJavaIdentifierPart(char ch) {
		return isJavaIdentifierPart((int) ch);
	}

	public static boolean isJavaIdentifierPart(int codePoint) {
		return isJavaIdentifierStart(codePoint) || isDigit(codePoint) || isIdentifierIgnorable(codePoint);
	}

	public static boolean isIdentifierIgnorable(char ch) {
		return isIdentifierIgnorable((int) ch);
	}

	public static boolean isIdentifierIgnorable(int codePoint) {
		// Controls that are not white space, and the soft hyphen (a format character)
		return (codePoint >= 0 && codePoint <= 8) || (codePoint >= 0x0e && codePoint <= 0x1b)
				|| (codePoint >= 0x7f && codePoint <= 0x9f) || codePoint == 0xad;
	}

	// Code points and surrogates

	public static boolean isValidCodePoint(int codePoint) {
		return codePoint >= MIN_CODE_POINT && codePoint <= MAX_CODE_POINT;
	}

	public static boolean isBmpCodePoint(int codePoint) {
		return codePoint >>> 16 == 0;
	}

	public static boolean isSupplementaryCodePoint(int codePoint) {
		return codePoint >= MIN_SUPPLEMENTARY_CODE_POINT && codePoint <= MAX_CODE_POINT;
	}

	public static boolean isHighSurrogate(char ch) {
		return ch >= MIN_HIGH_SURROGATE && ch <= MAX_HIGH_SURROGATE;
	}

	public static boolean isLowSurrogate(char ch) {
		return ch >= MIN_LOW_SURROGATE && ch <= MAX_LOW_SURROGATE;
	}

	public static boolean isSurrogate(char ch) {
		return ch >= MIN_SURROGATE && ch <= MAX_SURROGATE;
	}

	public static boolean isSurrogatePair(char high, char low) {
		return isHighSurrogate(high) && isLowSurrogate(low);
	}

	public static int charCount(int codePoint) {
		return codePoint >= MIN_SUPPLEMENTARY_CODE_POINT ? 2 : 1;
	}

	public static int toCodePoint(char high, char low) {
		return ((high - MIN_HIGH_SURROGATE) << 10) + (low - MIN_LOW_SURROGATE) + MIN_SUPPLEMENTARY_CODE_POINT;
	}

	public static char highSurrogate(int codePoint) {
		return (char) (((codePoint - MIN_SUPPLEMENTARY_CODE_POINT) >>> 10) + MIN_HIGH_SURROGATE);
	}

	public static char lowSurrogate(int codePoint) {
		return (char) ((codePoint & 0x3ff) + MIN_LOW_SURROGATE);
	}

	public static int codePointAt(CharSequence seq, int index) {
		char high = seq.charAt(index);
		if (isHighSurrogate(high) && index + 1 < seq.length()) {
			char low = seq.charAt(index + 1);
			if (isLowSurrogate(low)) {
				return toCodePoint(high, low);
			}
		}
		return high;
	}

	public static int codePointAt(char[] a, int index) {
		char high = a[index];
		if (isHighSurrogate(high) && index + 1 < a.length) {
			char low = a[index + 1];
			if (isLowSurrogate(low)) {
				return toCodePoint(high, low);
			}
		}
		return high;
	}

	public static char[] toChars(int codePoint) {
		checkCodePoint(codePoint);
		if (isBmpCodePoint(codePoint)) {
			return new char[] { (char) codePoint };
		}
		return new char[] { highSurrogate(codePoint), lowSurrogate(codePoint) };
	}

	public static int toChars(int codePoint, char[] dst, int dstIndex) {
		checkCodePoint(codePoint);
		if (isBmpCodePoint(codePoint)) {
			dst[dstIndex] = (char) codePoint;
			return 1;
		}
		dst[dstIndex + 1] = lowSurrogate(codePoint);
		dst[dstIndex] = highSurrogate(codePoint);
		return 2;
	}

	private static void checkCodePoint(int codePoint) {
		if (!isValidCodePoint(codePoint)) {
			char[] hex = Integer.toHexString(codePoint).toCharArray();
			for (int i = 0; i < hex.length; i++) {
				hex[i] = toUpperCase(hex[i]);
			}
			throw new IllegalArgumentException("Not a valid Unicode code point: 0x" + new String(hex));
		}
	}

	public static String toString(char c) {
		return String.valueOf(c);
	}

	public static String toString(int codePoint) {
		return new String(toChars(codePoint));
	}

	public static char reverseBytes(char ch) {
		return (char) (((ch & 0xff) << 8) | (ch >> 8));
	}

	public static int compare(char x, char y) {
		return x - y;
	}

	public static int hashCode(char value) {
		return value;
	}

	public char charValue() {
		return value;
	}

	public int compareTo(Character anotherCharacter) {
		return compare(value, anotherCharacter.value);
	}

	public int hashCode() {
		return value;
	}

	public boolean equals(Object obj) {
		return obj instanceof Character && value == ((Character) obj).value;
	}

	public String toString() {
		return String.valueOf(value);
	}
}
