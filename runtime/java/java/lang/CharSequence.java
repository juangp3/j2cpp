package java.lang;

public interface CharSequence {
	int length();

	char charAt(int index);

	CharSequence subSequence(int start, int end);

	String toString();

	default boolean isEmpty() {
		return length() == 0;
	}

	/** Compares lexicographically by char value, then by length */
	static int compare(CharSequence cs1, CharSequence cs2) {
		if (cs1 == cs2) {
			return 0;
		}
		int limit = Math.min(cs1.length(), cs2.length());
		for (int i = 0; i < limit; i++) {
			char a = cs1.charAt(i);
			char b = cs2.charAt(i);
			if (a != b) {
				return a - b;
			}
		}
		return cs1.length() - cs2.length();
	}
}
