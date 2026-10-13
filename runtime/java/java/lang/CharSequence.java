package java.lang;

import java.util.stream.IntStream;

public interface CharSequence {
	int length();

	char charAt(int index);

	CharSequence subSequence(int start, int end);

	String toString();

	default boolean isEmpty() {
		return length() == 0;
	}

	/**
	 * The char values. They are read when chars() is called; the JVM reads a
	 * mutable sequence (StringBuilder) when the stream runs.
	 */
	default IntStream chars() {
		int[] values = new int[length()];
		for (int i = 0; i < values.length; i++) {
			values[i] = charAt(i);
		}
		return IntStream.of(values);
	}

	/** The code points, read when codePoints() is called (see chars()) */
	default IntStream codePoints() {
		int length = length();
		int[] values = new int[length];
		int count = 0;
		for (int i = 0; i < length; i++) {
			char c = charAt(i);
			if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(charAt(i + 1))) {
				values[count++] = Character.toCodePoint(c, charAt(i + 1));
				i++;
			} else {
				values[count++] = c;
			}
		}
		return IntStream.of(java.util.Arrays.copyOf(values, count));
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
