package java.util.regex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Pattern, written from the Java SE specification: a regular expression
 * compiled for a backtracking matcher (see Matcher).
 *
 * Supported: literals and escapes, '.', classes with ranges, negation,
 * nesting and && intersection, the predefined classes \d \D \s \S \w \W \h
 * \H \v \V, POSIX, java* and the basic Unicode properties (\p{L}, \p{Lu},
 * \p{Ll}, \p{N}, \p{Nd}, \p{Z}, \p{Zs}, \p{IsAlphabetic} and the like),
 * anchors ^ $ \b \B \A \G \Z \z, capturing, non-capturing, named and atomic
 * groups, backreferences \n and \k<name>, look-ahead and look-behind,
 * greedy, reluctant and possessive quantifiers, \R, \Q...\E, and the flags
 * CASE_INSENSITIVE, UNICODE_CASE, MULTILINE, DOTALL, UNIX_LINES, COMMENTS
 * and LITERAL, inline too.
 *
 * Limits: Unicode properties and UNICODE_CASE use java.lang.Character,
 * which in this runtime is exact for Latin-1 only. CANON_EQ,
 * UNICODE_CHARACTER_CLASS, \X, \N{...}, \b{g}, Unicode blocks and scripts
 * throw UnsupportedOperationException.
 */
public final class Pattern implements java.io.Serializable {
	public static final int UNIX_LINES = 0x01;
	public static final int CASE_INSENSITIVE = 0x02;
	public static final int COMMENTS = 0x04;
	public static final int MULTILINE = 0x08;
	public static final int LITERAL = 0x10;
	public static final int DOTALL = 0x20;
	public static final int UNICODE_CASE = 0x40;
	public static final int CANON_EQ = 0x80;
	public static final int UNICODE_CHARACTER_CLASS = 0x100;

	private static final int ALL_FLAGS = UNIX_LINES | CASE_INSENSITIVE | COMMENTS | MULTILINE | LITERAL | DOTALL
			| UNICODE_CASE | CANON_EQ | UNICODE_CHARACTER_CLASS;

	private final String pattern;
	private final int flags;

	// Set by Parser.compile
	int[] code;
	CharPredicate[] preds;
	int groupCount;
	int loopCount;
	HashMap<String, Integer> names;
	/** The pattern contains a supplementary character, so searches skip low surrogates */
	boolean hasSupplementary;
	/** The pattern starts with \A or ^ without MULTILINE */
	boolean anchored;

	private Pattern(String regex, int flags) {
		if ((flags & ~ALL_FLAGS) != 0) {
			throw new IllegalArgumentException("Unknown flag 0x" + Integer.toHexString(flags));
		}
		if ((flags & CANON_EQ) != 0) {
			throw new UnsupportedOperationException("java.util.regex in the J2C runtime does not support CANON_EQ");
		}
		if ((flags & UNICODE_CHARACTER_CLASS) != 0) {
			throw new UnsupportedOperationException(
					"java.util.regex in the J2C runtime does not support UNICODE_CHARACTER_CLASS");
		}
		this.pattern = regex;
		this.flags = flags;
		new Parser(regex, flags).compile(this);
	}

	public static Pattern compile(String regex) {
		return new Pattern(regex, 0);
	}

	public static Pattern compile(String regex, int flags) {
		return new Pattern(regex, flags);
	}

	public static boolean matches(String regex, CharSequence input) {
		return compile(regex).matcher(input).matches();
	}

	public String pattern() {
		return pattern;
	}

	public String toString() {
		return pattern;
	}

	public int flags() {
		return flags;
	}

	public Matcher matcher(CharSequence input) {
		return new Matcher(this, input);
	}

	Map<String, Integer> namedGroups() {
		return names;
	}

	public Predicate<String> asPredicate() {
		return s -> matcher(s).find();
	}

	public Predicate<String> asMatchPredicate() {
		return s -> matcher(s).matches();
	}

	public String[] split(CharSequence input) {
		return split(input, 0);
	}

	public String[] split(CharSequence input, int limit) {
		String text = input.toString();
		boolean limited = limit > 0;
		ArrayList<String> parts = new ArrayList<String>();
		int index = 0;
		Matcher m = matcher(text);
		while (m.find()) {
			if (!limited || parts.size() < limit - 1) {
				if (index == 0 && m.start() == 0 && m.end() == 0) {
					// A zero-width match at the start gives no leading empty string
					continue;
				}
				parts.add(text.substring(index, m.start()));
				index = m.end();
			} else if (parts.size() == limit - 1) {
				parts.add(text.substring(index));
				index = m.end();
				break;
			}
		}
		if (index == 0) {
			return new String[] { text };
		}
		if (!limited || parts.size() < limit) {
			parts.add(text.substring(index));
		}
		int count = parts.size();
		if (limit == 0) {
			while (count > 0 && parts.get(count - 1).isEmpty()) {
				count--;
			}
		}
		String[] result = new String[count];
		for (int i = 0; i < count; i++) {
			result[i] = parts.get(i);
		}
		return result;
	}

	/** Splits all at once, then streams the parts */
	public Stream<String> splitAsStream(CharSequence input) {
		return Arrays.stream(split(input));
	}

	public static String quote(String s) {
		int end = s.indexOf("\\E");
		if (end < 0) {
			return "\\Q" + s + "\\E";
		}
		StringBuilder sb = new StringBuilder(s.length() * 2);
		sb.append("\\Q");
		int current = 0;
		while (end >= 0) {
			sb.append(s, current, end).append("\\E\\\\E\\Q");
			current = end + 2;
			end = s.indexOf("\\E", current);
		}
		return sb.append(s, current, s.length()).append("\\E").toString();
	}
}
