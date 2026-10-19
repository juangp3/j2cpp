package java.util.regex;

/**
 * A set of code points: what one regex atom (a literal, a class, '.', \d, a
 * property) matches. Written from the Java SE specification of
 * java.util.regex.Pattern.
 *
 * Unicode properties use java.lang.Character, which in the J2C runtime is
 * exact for Latin-1 only, so they are too.
 */
abstract class CharPredicate {
	// Built-in sets, for Builtin
	static final int DIGIT = 0;
	static final int WORD = 1;
	static final int SPACE = 2;
	static final int HSPACE = 3;
	static final int VSPACE = 4;
	static final int DOT = 5;
	static final int DOT_UNIX = 6;
	static final int ANY = 7;
	static final int POSIX_LOWER = 8;
	static final int POSIX_UPPER = 9;
	static final int POSIX_ASCII = 10;
	static final int POSIX_ALPHA = 11;
	static final int POSIX_DIGIT = 12;
	static final int POSIX_ALNUM = 13;
	static final int POSIX_PUNCT = 14;
	static final int POSIX_GRAPH = 15;
	static final int POSIX_PRINT = 16;
	static final int POSIX_BLANK = 17;
	static final int POSIX_CNTRL = 18;
	static final int POSIX_XDIGIT = 19;
	static final int POSIX_SPACE = 20;
	static final int LETTER = 21;
	static final int UPPERCASE_LETTER = 22;
	static final int LOWERCASE_LETTER = 23;
	static final int DECIMAL_NUMBER = 24;
	static final int NUMBER = 25;
	static final int SEPARATOR = 26;
	static final int SPACE_SEPARATOR = 27;
	static final int ALPHABETIC = 28;
	static final int UPPERCASE = 29;
	static final int LOWERCASE = 30;
	static final int WHITE_SPACE = 31;
	static final int JAVA_LOWER = 32;
	static final int JAVA_UPPER = 33;
	static final int JAVA_WHITESPACE = 34;
	static final int JAVA_LETTER = 35;
	static final int JAVA_DIGIT = 36;
	static final int JAVA_LETTER_OR_DIGIT = 37;
	static final int JAVA_SPACE_CHAR = 38;
	static final int JAVA_ISO_CONTROL = 39;
	static final int JAVA_IDENTIFIER_START = 40;
	static final int JAVA_IDENTIFIER_PART = 41;

	abstract boolean is(int cp);

	/** Caches the answers for ASCII, which most input is */
	static CharPredicate cached(CharPredicate p) {
		if (p instanceof Cached) {
			return p;
		}
		return new Cached(p);
	}

	static CharPredicate union(CharPredicate a, CharPredicate b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return new Union(a, b);
	}

	static CharPredicate intersection(CharPredicate a, CharPredicate b) {
		if (a == null) {
			return b;
		}
		if (b == null) {
			return a;
		}
		return new Intersection(a, b);
	}

	/** A literal code point, with the case-insensitive flags of Pattern */
	static CharPredicate single(int c, int flags) {
		if ((flags & Pattern.CASE_INSENSITIVE) != 0) {
			if ((flags & Pattern.UNICODE_CASE) != 0) {
				int lower = Character.toLowerCase(Character.toUpperCase(c));
				if (lower != Character.toUpperCase(c)) {
					return new SingleUnicodeCase(lower);
				}
			} else if (isAsciiLetter(c)) {
				return new SingleAsciiCase(c | 0x20);
			}
		}
		return new Single(c);
	}

	static CharPredicate range(int lo, int hi, int flags) {
		int mode = 0;
		if ((flags & Pattern.CASE_INSENSITIVE) != 0) {
			mode = (flags & Pattern.UNICODE_CASE) != 0 ? 2 : 1;
		}
		return new Range(lo, hi, mode);
	}

	/** A property class: under CASE_INSENSITIVE it also matches the other case */
	static CharPredicate property(int kind, int flags) {
		CharPredicate p = new Builtin(kind);
		if ((flags & Pattern.CASE_INSENSITIVE) != 0 && (kind == POSIX_LOWER || kind == POSIX_UPPER
				|| kind == UPPERCASE_LETTER || kind == LOWERCASE_LETTER || kind == UPPERCASE || kind == LOWERCASE
				|| kind == JAVA_LOWER || kind == JAVA_UPPER)) {
			return new AnyCase(p);
		}
		return p;
	}

	static boolean isAsciiLetter(int c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
	}

	static boolean isAsciiDigit(int c) {
		return c >= '0' && c <= '9';
	}

	static boolean isWord(int c) {
		return isAsciiLetter(c) || isAsciiDigit(c) || c == '_';
	}

	/** A line terminator, as '.', '^' and '$' see it */
	static boolean isLineTerminator(int c, boolean unixLines) {
		if (unixLines) {
			return c == '\n';
		}
		return c == '\n' || c == '\r' || c == 0x85 || c == 0x2028 || c == 0x2029;
	}

	static boolean builtin(int kind, int c) {
		switch (kind) {
		case DIGIT:
		case POSIX_DIGIT:
			return isAsciiDigit(c);
		case WORD:
			return isWord(c);
		case SPACE:
		case POSIX_SPACE:
			return c == ' ' || (c >= '\t' && c <= '\r');
		case HSPACE:
			return c == ' ' || c == '\t' || c == 0xA0 || c == 0x1680 || c == 0x180e || (c >= 0x2000 && c <= 0x200a)
					|| c == 0x202f || c == 0x205f || c == 0x3000;
		case VSPACE:
			return (c >= '\n' && c <= '\r') || c == 0x85 || c == 0x2028 || c == 0x2029;
		case DOT:
			return !isLineTerminator(c, false);
		case DOT_UNIX:
			return c != '\n';
		case ANY:
			return true;
		case POSIX_LOWER:
			return c >= 'a' && c <= 'z';
		case POSIX_UPPER:
			return c >= 'A' && c <= 'Z';
		case POSIX_ASCII:
			return c >= 0 && c < 0x80;
		case POSIX_ALPHA:
			return isAsciiLetter(c);
		case POSIX_ALNUM:
			return isAsciiLetter(c) || isAsciiDigit(c);
		case POSIX_PUNCT:
			return (c >= 0x21 && c <= 0x2f) || (c >= 0x3a && c <= 0x40) || (c >= 0x5b && c <= 0x60)
					|| (c >= 0x7b && c <= 0x7e);
		case POSIX_GRAPH:
			return c >= 0x21 && c <= 0x7e;
		case POSIX_PRINT:
			return c >= 0x20 && c <= 0x7e;
		case POSIX_BLANK:
			return c == ' ' || c == '\t';
		case POSIX_CNTRL:
			return (c >= 0 && c < 0x20) || c == 0x7f;
		case POSIX_XDIGIT:
			return isAsciiDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
		case LETTER:
		case JAVA_LETTER:
			return Character.isLetter(c);
		case UPPERCASE_LETTER:
			return Character.isUpperCase(c);
		case LOWERCASE_LETTER:
			// The ordinal indicators are Other_Lowercase letters, not Ll
			return Character.isLowerCase(c) && c != 0xaa && c != 0xba;
		case DECIMAL_NUMBER:
		case JAVA_DIGIT:
			return Character.isDigit(c);
		case NUMBER:
			// Nd plus the Latin-1 No characters: superscripts and fractions
			return Character.isDigit(c) || c == 0xb2 || c == 0xb3 || c == 0xb9 || (c >= 0xbc && c <= 0xbe);
		case SEPARATOR:
		case JAVA_SPACE_CHAR:
			return Character.isSpaceChar(c);
		case SPACE_SEPARATOR:
			return Character.isSpaceChar(c) && c != 0x2028 && c != 0x2029;
		case ALPHABETIC:
			return Character.isAlphabetic(c);
		case UPPERCASE:
		case JAVA_UPPER:
			return Character.isUpperCase(c);
		case LOWERCASE:
		case JAVA_LOWER:
			return Character.isLowerCase(c);
		case WHITE_SPACE:
			return (c >= '\t' && c <= '\r') || c == ' ' || c == 0x85 || c == 0xa0 || c == 0x1680
					|| (c >= 0x2000 && c <= 0x200a) || c == 0x2028 || c == 0x2029 || c == 0x202f || c == 0x205f
					|| c == 0x3000;
		case JAVA_WHITESPACE:
			return Character.isWhitespace(c);
		case JAVA_LETTER_OR_DIGIT:
			return Character.isLetterOrDigit(c);
		case JAVA_ISO_CONTROL:
			return Character.isISOControl(c);
		case JAVA_IDENTIFIER_START:
			return Character.isJavaIdentifierStart(c);
		case JAVA_IDENTIFIER_PART:
			return Character.isJavaIdentifierPart(c);
		default:
			throw new IllegalStateException("character set " + kind);
		}
	}

	static final class Single extends CharPredicate {
		private final int c;

		Single(int c) {
			this.c = c;
		}

		boolean is(int cp) {
			return cp == c;
		}
	}

	/** An ASCII letter in either case; lower is the lower case form */
	static final class SingleAsciiCase extends CharPredicate {
		private final int lower;

		SingleAsciiCase(int lower) {
			this.lower = lower;
		}

		boolean is(int cp) {
			return cp == lower || cp == lower - 0x20;
		}
	}

	static final class SingleUnicodeCase extends CharPredicate {
		private final int lower;

		SingleUnicodeCase(int lower) {
			this.lower = lower;
		}

		boolean is(int cp) {
			return cp == lower || Character.toLowerCase(Character.toUpperCase(cp)) == lower;
		}
	}

	/** lo..hi; mode 1 also matches the other ASCII case, 2 the other Unicode case */
	static final class Range extends CharPredicate {
		private final int lo;
		private final int hi;
		private final int mode;

		Range(int lo, int hi, int mode) {
			this.lo = lo;
			this.hi = hi;
			this.mode = mode;
		}

		private boolean in(int cp) {
			return cp >= lo && cp <= hi;
		}

		boolean is(int cp) {
			if (in(cp)) {
				return true;
			}
			if (mode == 1) {
				return isAsciiLetter(cp) && in(cp ^ 0x20);
			}
			if (mode == 2) {
				return in(Character.toUpperCase(cp)) || in(Character.toLowerCase(cp));
			}
			return false;
		}
	}

	static final class Builtin extends CharPredicate {
		private final int kind;

		Builtin(int kind) {
			this.kind = kind;
		}

		boolean is(int cp) {
			return builtin(kind, cp);
		}
	}

	static final class AnyCase extends CharPredicate {
		private final CharPredicate p;

		AnyCase(CharPredicate p) {
			this.p = p;
		}

		boolean is(int cp) {
			return p.is(cp) || p.is(Character.toUpperCase(cp)) || p.is(Character.toLowerCase(cp));
		}
	}

	static final class Not extends CharPredicate {
		private final CharPredicate p;

		Not(CharPredicate p) {
			this.p = p;
		}

		boolean is(int cp) {
			return !p.is(cp);
		}
	}

	static final class Union extends CharPredicate {
		private final CharPredicate a;
		private final CharPredicate b;

		Union(CharPredicate a, CharPredicate b) {
			this.a = a;
			this.b = b;
		}

		boolean is(int cp) {
			return a.is(cp) || b.is(cp);
		}
	}

	static final class Intersection extends CharPredicate {
		private final CharPredicate a;
		private final CharPredicate b;

		Intersection(CharPredicate a, CharPredicate b) {
			this.a = a;
			this.b = b;
		}

		boolean is(int cp) {
			return a.is(cp) && b.is(cp);
		}
	}

	static final class Cached extends CharPredicate {
		private final CharPredicate p;
		private final boolean[] ascii = new boolean[128];

		Cached(CharPredicate p) {
			this.p = p;
			for (int c = 0; c < 128; c++) {
				ascii[c] = p.is(c);
			}
		}

		boolean is(int cp) {
			if (cp >= 0 && cp < 128) {
				return ascii[cp];
			}
			return p.is(cp);
		}
	}
}
