package java.lang;

/** Simplified Character: ASCII and Latin-1 classification only. */
public final class Character implements java.io.Serializable, Comparable<Character> {
	public static final int MIN_RADIX = 2;
	public static final int MAX_RADIX = 36;
	public static final char MIN_VALUE = '\u0000';
	public static final char MAX_VALUE = '￿';

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

	public static int digit(char ch, int radix) {
		int value;
		if (ch >= '0' && ch <= '9') {
			value = ch - '0';
		} else if (ch >= 'a' && ch <= 'z') {
			value = ch - 'a' + 10;
		} else if (ch >= 'A' && ch <= 'Z') {
			value = ch - 'A' + 10;
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

	public static boolean isDigit(char ch) {
		return ch >= '0' && ch <= '9';
	}

	public static boolean isLetter(char ch) {
		return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
				|| (ch >= 'À' && ch <= 'ÿ' && ch != '×' && ch != '÷');
	}

	public static boolean isLetterOrDigit(char ch) {
		return isLetter(ch) || isDigit(ch);
	}

	public static boolean isUpperCase(char ch) {
		return (ch >= 'A' && ch <= 'Z') || (ch >= 'À' && ch <= 'Þ' && ch != '×');
	}

	public static boolean isLowerCase(char ch) {
		return (ch >= 'a' && ch <= 'z') || (ch >= 'ß' && ch <= 'ÿ' && ch != '÷');
	}

	public static boolean isWhitespace(char ch) {
		return ch == ' ' || (ch >= '\t' && ch <= '\r') || (ch >= '\u001c' && ch <= '\u001f');
	}

	public static char toUpperCase(char ch) {
		if ((ch >= 'a' && ch <= 'z') || (ch >= 'à' && ch <= 'þ' && ch != '÷')) {
			return (char) (ch - 32);
		}
		return ch;
	}

	public static char toLowerCase(char ch) {
		if ((ch >= 'A' && ch <= 'Z') || (ch >= 'À' && ch <= 'Þ' && ch != '×')) {
			return (char) (ch + 32);
		}
		return ch;
	}

	public static String toString(char c) {
		return String.valueOf(c);
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
