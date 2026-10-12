package java.lang;

public final class Boolean implements java.io.Serializable, Comparable<Boolean> {
	public static final Boolean TRUE = new Boolean(true);
	public static final Boolean FALSE = new Boolean(false);

	private final boolean value;

	public Boolean(boolean value) {
		this.value = value;
	}

	public static Boolean valueOf(boolean b) {
		return b ? TRUE : FALSE;
	}

	public static Boolean valueOf(String s) {
		return parseBoolean(s) ? TRUE : FALSE;
	}

	public static boolean parseBoolean(String s) {
		return s != null && s.length() == 4
				&& (s.charAt(0) | 0x20) == 't' && (s.charAt(1) | 0x20) == 'r'
				&& (s.charAt(2) | 0x20) == 'u' && (s.charAt(3) | 0x20) == 'e';
	}

	public static String toString(boolean b) {
		return b ? "true" : "false";
	}

	public static boolean logicalAnd(boolean a, boolean b) {
		return a && b;
	}

	public static boolean logicalOr(boolean a, boolean b) {
		return a || b;
	}

	public static boolean logicalXor(boolean a, boolean b) {
		return a ^ b;
	}

	public static int compare(boolean x, boolean y) {
		return x == y ? 0 : (x ? 1 : -1);
	}

	public static int hashCode(boolean value) {
		return value ? 1231 : 1237;
	}

	public boolean booleanValue() {
		return value;
	}

	public int compareTo(Boolean b) {
		return compare(value, b.value);
	}

	public int hashCode() {
		return hashCode(value);
	}

	public boolean equals(Object obj) {
		return obj instanceof Boolean && value == ((Boolean) obj).value;
	}

	public String toString() {
		return toString(value);
	}
}
