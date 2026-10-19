package java.util.regex;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Parses a regular expression into a tree of nodes and compiles the tree into
 * the instructions Matcher runs. Written from the Java SE specification of
 * java.util.regex.Pattern; syntax errors carry the JDK's descriptions and
 * indexes.
 *
 * Unsupported, and failing loudly with UnsupportedOperationException: CANON_EQ,
 * UNICODE_CHARACTER_CLASS and (?U), \X, \N{...}, \b{g}, Unicode blocks and
 * scripts, and the property names CharPredicate does not know.
 */
final class Parser {
	// Node kinds
	static final int EMPTY = 0;
	static final int CHAR = 1;
	static final int SEQ = 2;
	static final int ALT = 3;
	static final int GROUP = 4;
	static final int REPEAT = 5;
	static final int ATOMIC = 6;
	static final int LOOK = 7;
	static final int ASSERT = 8;
	static final int BACKREF = 9;

	// Quantifier modes
	static final int GREEDY = 0;
	static final int LAZY = 1;
	static final int POSSESSIVE = 2;

	// Look-around kinds, for LOOK nodes and OP_SUB
	static final int SUB_ATOMIC = 0;
	static final int SUB_AHEAD = 1;
	static final int SUB_NOT_AHEAD = 2;
	static final int SUB_BEHIND = 3;
	static final int SUB_NOT_BEHIND = 4;

	// Assertions
	static final int BEGIN = 0;
	static final int CARET_MULTILINE = 1;
	static final int CARET_MULTILINE_UNIX = 2;
	static final int DOLLAR = 3;
	static final int DOLLAR_UNIX = 4;
	static final int DOLLAR_MULTILINE = 5;
	static final int DOLLAR_MULTILINE_UNIX = 6;
	static final int END = 7;
	static final int WORD_BOUNDARY = 8;
	static final int NOT_WORD_BOUNDARY = 9;
	static final int LAST_MATCH = 10;

	// Instructions: an opcode followed by its operands
	/** CHAR pred */
	static final int OP_CHAR = 0;
	/** REPEAT pred min max mode */
	static final int OP_REPEAT = 1;
	/** SPLIT target: try the next instruction, then target */
	static final int OP_SPLIT = 2;
	/** JMP target */
	static final int OP_JMP = 3;
	/** GROUP_HEAD group */
	static final int OP_GROUP_HEAD = 4;
	/** GROUP_TAIL group */
	static final int OP_GROUP_TAIL = 5;
	/** ASSERT kind */
	static final int OP_ASSERT = 6;
	/** BACKREF group caseMode */
	static final int OP_BACKREF = 7;
	static final int OP_FAIL = 8;
	/** LOOP_INIT loop */
	static final int OP_LOOP_INIT = 9;
	/** LOOP_TEST loop min max greedy exit; LOOP_ENTER follows it */
	static final int OP_LOOP_TEST = 10;
	/** LOOP_ENTER loop: records where an iteration starts; the body follows it */
	static final int OP_LOOP_ENTER = 11;
	/** LOOP_END loop test empty; the exit follows it (empty is an EMPTY_ mode) */
	static final int OP_LOOP_END = 12;
	/** SUB kind next min max; the body starts after it and ends with SUCCEED */
	static final int OP_SUB = 13;
	static final int OP_SUCCEED = 14;
	static final int OP_ACCEPT = 15;
	/**
	 * CURLY_EXIT loop group min: leaving a greedy loop of a deterministic
	 * capturing group after more than min iterations
	 */
	static final int OP_CURLY_EXIT = 16;

	// What an iteration that matched nothing does, as in the JDK's loop nodes
	/** Loop (a group that is not deterministic): it ends the loop */
	static final int EMPTY_EXITS = 0;
	/**
	 * GroupCurly (a deterministic group) and reluctant Curly: under the
	 * minimum, it counts; then it fails
	 */
	static final int EMPTY_FAILS = 1;
	/** Greedy Curly (anything else, and possessive groups): it counts under the minimum, then ends the loop */
	static final int EMPTY_COUNTS = 2;

	/** Java's MAX_REPS: the bound of * and + */
	static final int INFINITY = Integer.MAX_VALUE;

	static final class Node {
		final int kind;
		ArrayList<Node> children;
		Node child;
		CharPredicate set;
		int a;
		int b;
		int c;
		/** For REPEAT: the quantifier is ? or {0,1}, in any mode */
		boolean question;
		/** For REPEAT: the quantifier is * or + (greedy) */
		boolean star;
		/** Whether the node is deterministic: 0 not known yet, 1 yes, -1 no */
		int deterministic;

		Node(int kind) {
			this.kind = kind;
		}
	}

	/** (?:X): a GROUP without a number, since the JDK treats groups apart from their content */
	private static Node nonCapturing(Node child) {
		Node node = new Node(GROUP);
		node.child = child;
		return node;
	}

	private final String pattern;
	/** The pattern as code points, after \Q...\E quoting is removed */
	private int[] p;
	private int length;
	private int cursor;
	private int flags;
	private int groupCount;
	private int loopCount;
	private boolean hasSupplementary;
	final HashMap<String, Integer> names = new HashMap<String, Integer>();

	// The compiled program
	private int[] code = new int[64];
	private int size;
	private final ArrayList<CharPredicate> preds = new ArrayList<CharPredicate>();

	Parser(String pattern, int flags) {
		this.pattern = pattern;
		this.flags = flags;
	}

	/** Parses and compiles into the fields of target */
	void compile(Pattern target) {
		Node root;
		if ((flags & Pattern.LITERAL) != 0) {
			root = new Node(SEQ);
			root.children = new ArrayList<Node>();
			int i = 0;
			while (i < pattern.length()) {
				int cp = pattern.codePointAt(i);
				i += Character.charCount(cp);
				if (cp >= 0x10000) {
					hasSupplementary = true;
				}
				root.children.add(charNode(CharPredicate.single(cp, flags)));
			}
		} else {
			p = removeQuoting(pattern);
			length = p.length;
			root = expr();
			if (cursor < length) {
				// Only an unmatched ')' stops expr() early
				throw error("Unmatched closing ')'", cursor - 1);
			}
		}
		emit(root);
		emit(OP_ACCEPT);
		int[] program = new int[size];
		System.arraycopy(code, 0, program, 0, size);
		target.code = program;
		target.preds = preds.toArray(new CharPredicate[preds.size()]);
		target.groupCount = groupCount;
		target.loopCount = loopCount;
		target.names = names;
		target.hasSupplementary = hasSupplementary;
		target.anchored = startsWithBegin(root);
	}

	private static boolean startsWithBegin(Node node) {
		if (node.kind == ASSERT) {
			return node.a == BEGIN;
		}
		if (node.kind == SEQ && !node.children.isEmpty()) {
			return startsWithBegin(node.children.get(0));
		}
		if (node.kind == GROUP || node.kind == ATOMIC) {
			return startsWithBegin(node.child);
		}
		return false;
	}

	/** Code points of the pattern with \Q...\E replaced by escaped characters */
	private static int[] removeQuoting(String s) {
		int[] all = new int[s.length()];
		int n = 0;
		for (int i = 0; i < s.length();) {
			int cp = s.codePointAt(i);
			all[n++] = cp;
			i += Character.charCount(cp);
		}
		int[] cps = new int[n];
		System.arraycopy(all, 0, cps, 0, n);
		boolean quoting = false;
		for (int i = 0; i + 1 < n; i++) {
			if (cps[i] == '\\') {
				if (cps[i + 1] == 'Q') {
					quoting = true;
					break;
				}
				i++;
			}
		}
		if (!quoting) {
			return cps;
		}
		int[] out = new int[n * 2];
		int j = 0;
		int i = 0;
		boolean inQuote = false;
		while (i < n) {
			int c = cps[i];
			if (inQuote) {
				if (c == '\\' && i + 1 < n && cps[i + 1] == 'E') {
					inQuote = false;
					i += 2;
					continue;
				}
				if (c < 0x80 && !CharPredicate.isAsciiLetter(c) && !CharPredicate.isAsciiDigit(c)) {
					out[j++] = '\\';
				}
				out[j++] = c;
				i++;
			} else if (c == '\\' && i + 1 < n) {
				if (cps[i + 1] == 'Q') {
					inQuote = true;
					i += 2;
				} else {
					out[j++] = c;
					out[j++] = cps[i + 1];
					i += 2;
				}
			} else {
				out[j++] = c;
				i++;
			}
		}
		int[] result = new int[j];
		System.arraycopy(out, 0, result, 0, j);
		return result;
	}

	private PatternSyntaxException error(String description, int index) {
		return new PatternSyntaxException(description, pattern, index);
	}

	private static UnsupportedOperationException unsupported(String what) {
		return new UnsupportedOperationException("java.util.regex in the J2C runtime does not support " + what);
	}

	private boolean has(int flag) {
		return (flags & flag) != 0;
	}

	/** The code point at i, or -1 past the end */
	private int at(int i) {
		return i < length ? p[i] : -1;
	}

	/** The next code point, skipping white space and comments under COMMENTS */
	private int peek() {
		if (has(Pattern.COMMENTS)) {
			skipComments();
		}
		return at(cursor);
	}

	private void skipComments() {
		while (cursor < length) {
			int c = p[cursor];
			if (c == ' ' || (c >= '\t' && c <= '\r')) {
				cursor++;
			} else if (c == '#') {
				while (cursor < length && !CharPredicate.isLineTerminator(p[cursor], has(Pattern.UNIX_LINES))) {
					cursor++;
				}
			} else {
				return;
			}
		}
	}

	private Node charNode(CharPredicate set) {
		Node node = new Node(CHAR);
		node.set = set;
		return node;
	}

	private Node literal(int c) {
		if (c >= 0x10000) {
			hasSupplementary = true;
		}
		return charNode(CharPredicate.single(c, flags));
	}

	private static Node assertion(int kind) {
		Node node = new Node(ASSERT);
		node.a = kind;
		return node;
	}

	// Parsing

	/** Alternatives separated by '|', up to ')' or the end */
	private Node expr() {
		Node first = sequence();
		if (peek() != '|') {
			return first;
		}
		Node alt = new Node(ALT);
		alt.children = new ArrayList<Node>();
		alt.children.add(first);
		while (peek() == '|') {
			cursor++;
			alt.children.add(sequence());
		}
		return alt;
	}

	private Node sequence() {
		Node seq = new Node(SEQ);
		seq.children = new ArrayList<Node>();
		while (true) {
			int ch = peek();
			Node atom;
			switch (ch) {
			case -1:
			case '|':
			case ')':
				return seq.children.size() == 1 ? seq.children.get(0) : seq;
			case '(':
				atom = group();
				if (atom == null) {
					// (?i) changes the flags of what follows
					continue;
				}
				break;
			case '[':
				cursor++;
				atom = charNode(CharPredicate.cached(classBody()));
				break;
			case '\\':
				cursor++;
				atom = escapeAtom();
				break;
			case '^':
				cursor++;
				if (has(Pattern.MULTILINE)) {
					atom = assertion(has(Pattern.UNIX_LINES) ? CARET_MULTILINE_UNIX : CARET_MULTILINE);
				} else {
					atom = assertion(BEGIN);
				}
				break;
			case '$':
				cursor++;
				if (has(Pattern.MULTILINE)) {
					atom = assertion(has(Pattern.UNIX_LINES) ? DOLLAR_MULTILINE_UNIX : DOLLAR_MULTILINE);
				} else {
					atom = assertion(has(Pattern.UNIX_LINES) ? DOLLAR_UNIX : DOLLAR);
				}
				break;
			case '.':
				cursor++;
				int kind = has(Pattern.DOTALL) ? CharPredicate.ANY
						: has(Pattern.UNIX_LINES) ? CharPredicate.DOT_UNIX : CharPredicate.DOT;
				atom = charNode(new CharPredicate.Builtin(kind));
				break;
			case '*':
			case '+':
			case '?':
				throw error("Dangling meta character '" + (char) ch + "'", cursor);
			case '{':
				// The JDK quantifies an empty atom, so a{1}{2} compiles and { alone is an error
				atom = new Node(EMPTY);
				break;
			default:
				cursor++;
				atom = literal(ch);
				break;
			}
			seq.children.add(closure(atom));
		}
	}

	/** A group from '(' to ')'; null for a flag change like (?i) */
	private Node group() {
		int savedFlags = flags;
		cursor++;
		Node node;
		if (at(cursor) == '?') {
			cursor++;
			int ch = at(cursor++);
			switch (ch) {
			case ':':
				node = nonCapturing(expr());
				break;
			case '=':
			case '!':
				node = new Node(LOOK);
				node.a = ch == '=' ? SUB_AHEAD : SUB_NOT_AHEAD;
				node.child = expr();
				break;
			case '>':
				node = new Node(ATOMIC);
				node.child = expr();
				break;
			case '<':
				ch = at(cursor);
				if (ch == '=' || ch == '!') {
					cursor++;
					node = new Node(LOOK);
					node.a = ch == '=' ? SUB_BEHIND : SUB_NOT_BEHIND;
					node.child = expr();
					Info info = new Info();
					study(chain(node.child), 0, info);
					if (!info.valid) {
						throw error("Look-behind group does not have an obvious maximum length", cursor - 1);
					}
					node.b = info.min;
					node.c = info.max;
				} else {
					String name = groupName();
					if (names.containsKey(name)) {
						throw error("Named capturing group <" + name + "> is already defined", cursor - 1);
					}
					node = new Node(GROUP);
					node.a = ++groupCount;
					names.put(name, Integer.valueOf(node.a));
					node.child = expr();
				}
				break;
			default:
				cursor--;
				addFlags();
				ch = at(cursor++);
				if (ch == ')') {
					return null;
				}
				if (ch != ':') {
					throw error("Unknown inline modifier", ch == -1 ? length : cursor - 1);
				}
				node = nonCapturing(expr());
				break;
			}
		} else {
			node = new Node(GROUP);
			node.a = ++groupCount;
			node.child = expr();
		}
		if (peek() != ')') {
			throw error("Unclosed group", length);
		}
		cursor++;
		flags = savedFlags;
		return node;
	}

	/** The flags of (?idmsux-idmsux) or (?idmsux-idmsux:X) */
	private void addFlags() {
		boolean remove = false;
		while (true) {
			int ch = at(cursor);
			int flag;
			switch (ch) {
			case 'i':
				flag = Pattern.CASE_INSENSITIVE;
				break;
			case 'm':
				flag = Pattern.MULTILINE;
				break;
			case 's':
				flag = Pattern.DOTALL;
				break;
			case 'd':
				flag = Pattern.UNIX_LINES;
				break;
			case 'u':
				flag = Pattern.UNICODE_CASE;
				break;
			case 'x':
				flag = Pattern.COMMENTS;
				break;
			case 'c':
				throw unsupported("CANON_EQ (?c)");
			case 'U':
				throw unsupported("UNICODE_CHARACTER_CLASS (?U)");
			case '-':
				if (remove) {
					return;
				}
				remove = true;
				cursor++;
				continue;
			default:
				return;
			}
			if (remove) {
				flags &= ~flag;
			} else {
				flags |= flag;
			}
			cursor++;
		}
	}

	/** A group name and its closing '>' */
	private String groupName() {
		StringBuilder sb = new StringBuilder();
		int ch = at(cursor);
		if (!CharPredicate.isAsciiLetter(ch)) {
			throw error("capturing group name does not start with a Latin letter", cursor);
		}
		while (CharPredicate.isAsciiLetter(ch) || CharPredicate.isAsciiDigit(ch)) {
			sb.append((char) ch);
			ch = at(++cursor);
		}
		if (ch != '>') {
			throw error("named capturing group is missing trailing '>'", cursor);
		}
		cursor++;
		return sb.toString();
	}

	/** A quantifier after atom, if there is one */
	private Node closure(Node atom) {
		int ch = peek();
		int min;
		int max;
		switch (ch) {
		case '?':
			cursor++;
			min = 0;
			max = 1;
			break;
		case '*':
			cursor++;
			min = 0;
			max = INFINITY;
			break;
		case '+':
			cursor++;
			min = 1;
			max = INFINITY;
			break;
		case '{':
			cursor++;
			if (!CharPredicate.isAsciiDigit(at(cursor))) {
				throw error("Illegal repetition", cursor);
			}
			min = number();
			max = min;
			if (at(cursor) == ',') {
				cursor++;
				max = at(cursor) == '}' ? INFINITY : number();
			}
			if (at(cursor) != '}') {
				throw error("Unclosed counted closure", cursor);
			}
			if (max < min) {
				throw error("Illegal repetition range", cursor);
			}
			cursor++;
			break;
		default:
			return atom;
		}
		// The JDK treats {0,1} like ?
		boolean question = min == 0 && max == 1;
		boolean star = ch == '*' || ch == '+';
		int mode = GREEDY;
		ch = peek();
		if (ch == '?') {
			cursor++;
			mode = LAZY;
		} else if (ch == '+') {
			cursor++;
			mode = POSSESSIVE;
		}
		Node node = new Node(REPEAT);
		node.child = atom;
		node.a = min;
		node.b = max;
		node.c = mode;
		node.question = question;
		node.star = star && mode == GREEDY;
		return node;
	}

	private int number() {
		long value = 0;
		while (CharPredicate.isAsciiDigit(at(cursor))) {
			value = value * 10 + (at(cursor) - '0');
			if (value > Integer.MAX_VALUE) {
				throw error("Illegal repetition range", cursor);
			}
			cursor++;
		}
		return (int) value;
	}

	/** After a backslash outside a class */
	private Node escapeAtom() {
		int ch = at(cursor);
		switch (ch) {
		case -1:
			throw error("Unexpected internal error", length);
		case '1':
		case '2':
		case '3':
		case '4':
		case '5':
		case '6':
		case '7':
		case '8':
		case '9': {
			cursor++;
			int ref = ch - '0';
			// More digits while they name a group that exists so far
			while (CharPredicate.isAsciiDigit(at(cursor))) {
				int next = ref * 10 + (at(cursor) - '0');
				if (next > groupCount) {
					break;
				}
				ref = next;
				cursor++;
			}
			return backref(ref);
		}
		case 'k': {
			cursor++;
			if (at(cursor) != '<') {
				throw error("\\k is not followed by '<' for named capturing group", cursor);
			}
			cursor++;
			String name = groupName();
			Integer group = names.get(name);
			if (group == null) {
				throw error("named capturing group <" + name + "> does not exist", cursor - 1);
			}
			return backref(group.intValue());
		}
		case 'A':
			cursor++;
			return assertion(BEGIN);
		case 'b':
			cursor++;
			if (at(cursor) == '{') {
				throw unsupported("\\b{g}");
			}
			return assertion(WORD_BOUNDARY);
		case 'B':
			cursor++;
			return assertion(NOT_WORD_BOUNDARY);
		case 'G':
			cursor++;
			return assertion(LAST_MATCH);
		case 'Z':
			cursor++;
			return assertion(has(Pattern.UNIX_LINES) ? DOLLAR_UNIX : DOLLAR);
		case 'z':
			cursor++;
			return assertion(END);
		case 'R': {
			cursor++;
			// \r\n or one vertical white space character, as the specification spells it
			Node crlf = new Node(SEQ);
			crlf.children = new ArrayList<Node>();
			crlf.children.add(charNode(new CharPredicate.Single('\r')));
			crlf.children.add(charNode(new CharPredicate.Single('\n')));
			Node alt = new Node(ALT);
			alt.children = new ArrayList<Node>();
			alt.children.add(crlf);
			alt.children.add(charNode(new CharPredicate.Builtin(CharPredicate.VSPACE)));
			return alt;
		}
		case 'X':
			throw unsupported("\\X");
		case 'N':
			throw unsupported("\\N{...}");
		default:
			Object e = escape(false);
			if (e instanceof CharPredicate) {
				return charNode(CharPredicate.cached((CharPredicate) e));
			}
			return literal(((Integer) e).intValue());
		}
	}

	private Node backref(int group) {
		Node node = new Node(BACKREF);
		node.a = group;
		node.b = !has(Pattern.CASE_INSENSITIVE) ? 0 : has(Pattern.UNICODE_CASE) ? 2 : 1;
		return node;
	}

	/**
	 * The escapes valid both in and out of classes, at cursor (after the
	 * backslash): an Integer code point or a CharPredicate.
	 */
	private Object escape(boolean inClass) {
		int ch = at(cursor++);
		switch (ch) {
		case -1:
			throw error("Unexpected internal error", length);
		case '0':
			return Integer.valueOf(octal());
		case 't':
			return Integer.valueOf('\t');
		case 'n':
			return Integer.valueOf('\n');
		case 'r':
			return Integer.valueOf('\r');
		case 'f':
			return Integer.valueOf('\f');
		case 'a':
			return Integer.valueOf(7);
		case 'e':
			return Integer.valueOf(27);
		case 'c': {
			int c = at(cursor);
			if (c == -1) {
				throw error("Illegal control escape sequence", cursor - 1);
			}
			cursor++;
			return Integer.valueOf(c ^ 64);
		}
		case 'x':
			return Integer.valueOf(hex());
		case 'u':
			return Integer.valueOf(unicode());
		case 'd':
			return new CharPredicate.Builtin(CharPredicate.DIGIT);
		case 'D':
			return new CharPredicate.Not(new CharPredicate.Builtin(CharPredicate.DIGIT));
		case 's':
			return new CharPredicate.Builtin(CharPredicate.SPACE);
		case 'S':
			return new CharPredicate.Not(new CharPredicate.Builtin(CharPredicate.SPACE));
		case 'w':
			return new CharPredicate.Builtin(CharPredicate.WORD);
		case 'W':
			return new CharPredicate.Not(new CharPredicate.Builtin(CharPredicate.WORD));
		case 'h':
			return new CharPredicate.Builtin(CharPredicate.HSPACE);
		case 'H':
			return new CharPredicate.Not(new CharPredicate.Builtin(CharPredicate.HSPACE));
		case 'v':
			return new CharPredicate.Builtin(CharPredicate.VSPACE);
		case 'V':
			return new CharPredicate.Not(new CharPredicate.Builtin(CharPredicate.VSPACE));
		case 'p':
		case 'P':
			return property(ch == 'P');
		default:
			if (CharPredicate.isAsciiLetter(ch) || CharPredicate.isAsciiDigit(ch)) {
				throw error("Illegal/unsupported escape sequence", cursor - 1);
			}
			return Integer.valueOf(ch);
		}
	}

	/** \0n, \0nn or \0mnn (m at most 3), after the 0 */
	private int octal() {
		int n = at(cursor++);
		if (n >= '0' && n <= '7') {
			int m = at(cursor);
			if (m >= '0' && m <= '7') {
				cursor++;
				int o = at(cursor);
				if (o >= '0' && o <= '7' && n <= '3') {
					cursor++;
					return (n - '0') * 64 + (m - '0') * 8 + (o - '0');
				}
				return (n - '0') * 8 + (m - '0');
			}
			return n - '0';
		}
		throw error("Illegal octal escape sequence", n == -1 ? length : cursor - 1);
	}

	private static int hexValue(int c) {
		if (c >= '0' && c <= '9') {
			return c - '0';
		}
		if (c >= 'a' && c <= 'f') {
			return c - 'a' + 10;
		}
		if (c >= 'A' && c <= 'F') {
			return c - 'A' + 10;
		}
		return -1;
	}

	/** \xhh or \x{h...h}, after the x */
	private int hex() {
		int n = at(cursor++);
		if (hexValue(n) >= 0) {
			int m = at(cursor++);
			if (hexValue(m) >= 0) {
				return hexValue(n) * 16 + hexValue(m);
			}
			throw error("Illegal hexadecimal escape sequence", m == -1 ? length : cursor - 1);
		}
		if (n == '{' && hexValue(at(cursor)) >= 0) {
			long value = 0;
			while (hexValue(at(cursor)) >= 0) {
				value = value * 16 + hexValue(at(cursor++));
				if (value > Character.MAX_CODE_POINT) {
					throw error("Hexadecimal codepoint is too big", cursor - 1);
				}
			}
			if (at(cursor++) != '}') {
				throw error("Unclosed hexadecimal escape sequence", cursor - 1);
			}
			return (int) value;
		}
		throw error("Illegal hexadecimal escape sequence", n == -1 ? length : cursor - 1);
	}

	/** The four hex digits of a u escape; two escapes of a surrogate pair are one code point */
	private int unicode() {
		int c = fourHex();
		if (Character.isHighSurrogate((char) c) && at(cursor) == '\\' && at(cursor + 1) == 'u') {
			int saved = cursor;
			cursor += 2;
			int low = fourHex();
			if (Character.isLowSurrogate((char) low)) {
				return Character.toCodePoint((char) c, (char) low);
			}
			cursor = saved;
		}
		return c;
	}

	private int fourHex() {
		int value = 0;
		for (int i = 0; i < 4; i++) {
			int c = at(cursor++);
			if (hexValue(c) < 0) {
				throw error("Illegal Unicode escape sequence", c == -1 ? length : cursor - 1);
			}
			value = value * 16 + hexValue(c);
		}
		return value;
	}

	/** \p{name}, \pL, \P{name}, after the p */
	private CharPredicate property(boolean negate) {
		int ch = at(cursor);
		String name;
		if (ch == '{') {
			int close = cursor + 1;
			while (close < length && p[close] != '}') {
				close++;
			}
			if (close >= length) {
				throw error("Unclosed character family", length);
			}
			StringBuilder sb = new StringBuilder();
			for (int i = cursor + 1; i < close; i++) {
				sb.appendCodePoint(p[i]);
			}
			name = sb.toString();
			cursor = close + 1;
		} else if (ch == -1) {
			throw error("Illegal character family", length);
		} else {
			name = new StringBuilder().appendCodePoint(ch).toString();
			cursor++;
		}
		if (name.startsWith("^")) {
			name = name.substring(1);
			negate = !negate;
		}
		int kind = propertyKind(name);
		if (kind < 0) {
			throw unsupported("the character property \\p{" + name + "}");
		}
		CharPredicate set = CharPredicate.property(kind, flags);
		return negate ? new CharPredicate.Not(set) : set;
	}

	private static int propertyKind(String name) {
		String n = name;
		if (n.startsWith("Is")) {
			n = n.substring(2);
			switch (n) {
			case "Alphabetic":
			case "ALPHABETIC":
			case "alphabetic":
				return CharPredicate.ALPHABETIC;
			case "Letter":
			case "LETTER":
			case "letter":
				return CharPredicate.LETTER;
			case "Digit":
			case "DIGIT":
			case "digit":
				return CharPredicate.DECIMAL_NUMBER;
			case "Uppercase":
			case "UPPERCASE":
			case "uppercase":
				return CharPredicate.UPPERCASE;
			case "Lowercase":
			case "LOWERCASE":
			case "lowercase":
				return CharPredicate.LOWERCASE;
			case "White_Space":
			case "WHITE_SPACE":
			case "WhiteSpace":
			case "white_space":
				return CharPredicate.WHITE_SPACE;
			default:
				break;
			}
		}
		switch (n) {
		case "Lower":
			return CharPredicate.POSIX_LOWER;
		case "Upper":
			return CharPredicate.POSIX_UPPER;
		case "ASCII":
			return CharPredicate.POSIX_ASCII;
		case "Alpha":
			return CharPredicate.POSIX_ALPHA;
		case "Digit":
			return CharPredicate.POSIX_DIGIT;
		case "Alnum":
			return CharPredicate.POSIX_ALNUM;
		case "Punct":
			return CharPredicate.POSIX_PUNCT;
		case "Graph":
			return CharPredicate.POSIX_GRAPH;
		case "Print":
			return CharPredicate.POSIX_PRINT;
		case "Blank":
			return CharPredicate.POSIX_BLANK;
		case "Cntrl":
			return CharPredicate.POSIX_CNTRL;
		case "XDigit":
			return CharPredicate.POSIX_XDIGIT;
		case "Space":
			return CharPredicate.POSIX_SPACE;
		case "L":
			return CharPredicate.LETTER;
		case "Lu":
			return CharPredicate.UPPERCASE_LETTER;
		case "Ll":
			return CharPredicate.LOWERCASE_LETTER;
		case "Nd":
			return CharPredicate.DECIMAL_NUMBER;
		case "N":
			return CharPredicate.NUMBER;
		case "Z":
			return CharPredicate.SEPARATOR;
		case "Zs":
			return CharPredicate.SPACE_SEPARATOR;
		case "javaLowerCase":
			return CharPredicate.JAVA_LOWER;
		case "javaUpperCase":
			return CharPredicate.JAVA_UPPER;
		case "javaWhitespace":
			return CharPredicate.JAVA_WHITESPACE;
		case "javaLetter":
			return CharPredicate.JAVA_LETTER;
		case "javaDigit":
			return CharPredicate.JAVA_DIGIT;
		case "javaLetterOrDigit":
			return CharPredicate.JAVA_LETTER_OR_DIGIT;
		case "javaSpaceChar":
			return CharPredicate.JAVA_SPACE_CHAR;
		case "javaISOControl":
			return CharPredicate.JAVA_ISO_CONTROL;
		case "javaJavaIdentifierStart":
			return CharPredicate.JAVA_IDENTIFIER_START;
		case "javaJavaIdentifierPart":
			return CharPredicate.JAVA_IDENTIFIER_PART;
		case "javaAlphabetic":
			return CharPredicate.ALPHABETIC;
		default:
			return -1;
		}
	}

	/**
	 * A class after its '[': items, ranges and nested classes, unions of them
	 * separated by && for intersection, all negated by a leading ^.
	 */
	private CharPredicate classBody() {
		boolean negate = false;
		if (at(cursor) == '^') {
			cursor++;
			negate = true;
		}
		CharPredicate result = null;
		CharPredicate operand = null;
		boolean intersect = false;
		while (true) {
			int ch = peek();
			if (ch == -1) {
				throw error("Unclosed character class", length - 1);
			}
			if (ch == ']' && (operand != null || result != null || intersect)) {
				cursor++;
				break;
			}
			if (ch == '&' && at(cursor + 1) == '&') {
				cursor += 2;
				result = intersect ? CharPredicate.intersection(result, operand) : operand;
				operand = null;
				intersect = true;
				continue;
			}
			if (ch == '[') {
				cursor++;
				operand = CharPredicate.union(operand, classBody());
				continue;
			}
			operand = CharPredicate.union(operand, classItem());
		}
		if (intersect) {
			result = CharPredicate.intersection(result, operand);
		} else {
			result = operand;
		}
		if (result == null) {
			// [a&&] or [&&]: nothing on either side
			result = new CharPredicate.Not(new CharPredicate.Builtin(CharPredicate.ANY));
		}
		return negate ? new CharPredicate.Not(result) : result;
	}

	/** A character, an escape or a range in a class */
	private CharPredicate classItem() {
		int c;
		int ch = at(cursor);
		if (ch == '\\') {
			cursor++;
			int letter = at(cursor);
			if (CharPredicate.isAsciiDigit(letter) && letter != '0') {
				throw error("Illegal/unsupported escape sequence", cursor);
			}
			if (letter == 'b' || letter == 'B' || letter == 'A' || letter == 'G' || letter == 'Z' || letter == 'z'
					|| letter == 'R' || letter == 'X' || letter == 'k' || letter == 'N') {
				throw error("Illegal/unsupported escape sequence", cursor);
			}
			Object e = escape(true);
			if (e instanceof CharPredicate) {
				return (CharPredicate) e;
			}
			c = ((Integer) e).intValue();
		} else {
			cursor++;
			c = ch;
		}
		if (c >= 0x10000) {
			hasSupplementary = true;
		}
		if (peek() == '-') {
			int after = at(cursor + 1);
			if (after != '[' && after != ']') {
				cursor++;
				int end;
				if (after == -1) {
					throw error("Illegal character range", length);
				}
				if (after == '\\') {
					cursor++;
					Object e = escape(true);
					if (e instanceof CharPredicate) {
						throw error("Illegal character range", cursor - 1);
					}
					end = ((Integer) e).intValue();
				} else {
					cursor++;
					end = after;
				}
				if (end < c) {
					throw error("Illegal character range", cursor - 1);
				}
				if (end >= 0x10000) {
					hasSupplementary = true;
				}
				return CharPredicate.range(c, end, flags);
			}
		}
		return CharPredicate.single(c, flags);
	}

	/**
	 * What the JDK's analysis of a pattern finds: the fewest and most chars
	 * it matches, whether the most is known, and whether it is deterministic
	 * (no alternation or variable quantifier). Look-behind uses min and max;
	 * deterministic groups repeat like the JDK's GroupCurly. The JDK computes
	 * max in int arithmetic, overflow included, checks for overflow after
	 * counted quantifiers but not after * and + of a character, and
	 * look-behind then tries the starts the overflowed values allow, so this
	 * does too.
	 */
	static final class Info {
		int min;
		int max;
		boolean valid = true;
		boolean deterministic = true;

		void reset() {
			min = 0;
			max = 0;
			valid = true;
			deterministic = true;
		}
	}

	/** The nodes a node matches in sequence, groups without quantifiers opened up */
	private static ArrayList<Node> chain(Node node) {
		ArrayList<Node> chain = new ArrayList<Node>();
		addChain(node, chain);
		return chain;
	}

	private static void addChain(Node node, ArrayList<Node> chain) {
		if (node.kind == SEQ) {
			for (int i = 0; i < node.children.size(); i++) {
				addChain(node.children.get(i), chain);
			}
		} else if (node.kind == GROUP) {
			addChain(node.child, chain);
		} else {
			chain.add(node);
		}
	}

	/**
	 * Studies chain from index into info. An alternation studies what follows
	 * it from scratch and adds what came before at the end, as the JDK does,
	 * which matters to the overflow checks of later quantifiers.
	 */
	private static void study(ArrayList<Node> chain, int index, Info info) {
		for (int i = index; i < chain.size(); i++) {
			Node node = chain.get(i);
			switch (node.kind) {
			case EMPTY:
			case ASSERT:
			case LOOK:
				break;
			case CHAR:
				info.min++;
				info.max++;
				break;
			case BACKREF:
				info.valid = false;
				break;
			case ATOMIC:
				study(chain(node.child), 0, info);
				break;
			case ALT:
				branch(node.children, chain, i + 1, info);
				return;
			case REPEAT: {
				Node atom = node.child;
				boolean group = atom.kind == GROUP;
				if (node.question && group && node.c != POSSESSIVE) {
					// (X)? is an alternation of X and nothing
					ArrayList<Node> alternatives = new ArrayList<Node>();
					alternatives.add(atom);
					alternatives.add(null);
					branch(alternatives, chain, i + 1, info);
					return;
				}
				if (node.question) {
					int min = info.min;
					study(chain(atom), 0, info);
					info.min = min;
					info.deterministic = false;
				} else if (atom.kind == CHAR && node.star) {
					// The JDK's greedy * and + of a character skip the overflow check
					info.min += node.a;
					if (info.valid) {
						info.max += INFINITY;
					}
					info.deterministic = false;
				} else if (group && node.c != POSSESSIVE && !deterministic(atom)) {
					info.valid = false;
					info.deterministic = false;
				} else {
					repeat(atom, node.a, node.b, info);
				}
				break;
			}
			default:
				throw new IllegalStateException("node " + node.kind);
			}
		}
	}

	static boolean deterministic(Node atom) {
		if (atom.deterministic == 0) {
			Info info = new Info();
			study(chain(atom), 0, info);
			atom.deterministic = info.deterministic ? 1 : -1;
		}
		return atom.deterministic > 0;
	}

	private static void branch(ArrayList<Node> alternatives, ArrayList<Node> chain, int next, Info info) {
		int minBefore = info.min;
		int maxBefore = info.max;
		boolean valid = info.valid;
		int min = Integer.MAX_VALUE;
		int max = -1;
		for (int i = 0; i < alternatives.size(); i++) {
			info.reset();
			Node alternative = alternatives.get(i);
			if (alternative != null) {
				study(chain(alternative), 0, info);
			}
			min = Math.min(min, info.min);
			max = Math.max(max, info.max);
			valid &= info.valid;
		}
		minBefore += min;
		maxBefore += max;
		info.reset();
		study(chain, next, info);
		info.min += minBefore;
		info.max += maxBefore;
		info.valid &= valid;
		info.deterministic = false;
	}

	private static void repeat(Node atom, int cmin, int cmax, Info info) {
		int minBefore = info.min;
		int maxBefore = info.max;
		boolean valid = info.valid;
		boolean deterministic = info.deterministic;
		info.reset();
		study(chain(atom), 0, info);
		int min = info.min * cmin + minBefore;
		if (min < minBefore) {
			min = 0xFFFFFFF;
		}
		info.min = min;
		if (valid && info.valid) {
			int max = info.max * cmax;
			info.max = maxBefore + max;
			if (info.max < maxBefore) {
				info.valid = false;
			}
		} else {
			info.valid = false;
		}
		info.deterministic = info.deterministic && cmin == cmax ? deterministic : false;
	}

	// Compiling

	private void emit(int value) {
		if (size == code.length) {
			int[] bigger = new int[size * 2];
			System.arraycopy(code, 0, bigger, 0, size);
			code = bigger;
		}
		code[size++] = value;
	}

	private int predicate(CharPredicate set) {
		preds.add(set);
		return preds.size() - 1;
	}

	private void emit(Node node) {
		switch (node.kind) {
		case EMPTY:
			break;
		case CHAR:
			emit(OP_CHAR);
			emit(predicate(node.set));
			break;
		case SEQ:
			for (int i = 0; i < node.children.size(); i++) {
				emit(node.children.get(i));
			}
			break;
		case ALT: {
			int[] jumps = new int[node.children.size()];
			int last = node.children.size() - 1;
			for (int i = 0; i < last; i++) {
				emit(OP_SPLIT);
				int split = size;
				emit(0);
				emit(node.children.get(i));
				emit(OP_JMP);
				jumps[i] = size;
				emit(0);
				code[split] = size;
			}
			emit(node.children.get(last));
			for (int i = 0; i < last; i++) {
				code[jumps[i]] = size;
			}
			break;
		}
		case GROUP:
			if (node.a == 0) {
				emit(node.child);
				break;
			}
			emit(OP_GROUP_HEAD);
			emit(node.a);
			emit(node.child);
			emit(OP_GROUP_TAIL);
			emit(node.a);
			break;
		case ASSERT:
			emit(OP_ASSERT);
			emit(node.a);
			break;
		case BACKREF:
			// A group that never exists never matches; groupCount is final here
			if (node.a > groupCount) {
				emit(OP_FAIL);
			} else {
				emit(OP_BACKREF);
				emit(node.a);
				emit(node.b);
			}
			break;
		case ATOMIC:
			emitSub(SUB_ATOMIC, node.child, 0, 0);
			break;
		case LOOK:
			emitSub(node.a, node.child, node.b, node.c);
			break;
		case REPEAT:
			emitRepeat(node);
			break;
		default:
			throw new IllegalStateException("node " + node.kind);
		}
	}

	private void emitSub(int kind, Node body, int min, int max) {
		emit(OP_SUB);
		emit(kind);
		int next = size;
		emit(0);
		emit(min);
		emit(max);
		emit(body);
		emit(OP_SUCCEED);
		code[next] = size;
	}

	private void emitRepeat(Node node) {
		int min = node.a;
		int max = node.b;
		int mode = node.c;
		if (max == 0) {
			return;
		}
		Node atom = node.child;
		Node single = atom;
		if (single.kind == GROUP && single.a == 0) {
			single = single.child;
		}
		if (single.kind == CHAR) {
			emit(OP_REPEAT);
			emit(predicate(single.set));
			emit(min);
			emit(max);
			emit(mode);
			return;
		}
		if (min == 1 && max == 1) {
			emit(atom);
			return;
		}
		if (mode == POSSESSIVE) {
			// The JDK matches each iteration once, and keeps them all
			Node iteration = new Node(ATOMIC);
			iteration.child = atom;
			Node greedy = new Node(REPEAT);
			greedy.child = iteration;
			greedy.a = min;
			greedy.b = max;
			greedy.c = GREEDY;
			emitSub(SUB_ATOMIC, greedy, 0, 0);
			return;
		}
		boolean group = atom.kind == GROUP;
		int empty;
		if (node.question) {
			empty = EMPTY_EXITS;
		} else if (group) {
			empty = deterministic(atom) ? EMPTY_FAILS : EMPTY_EXITS;
		} else {
			empty = mode == LAZY ? EMPTY_FAILS : EMPTY_COUNTS;
		}
		int loop = loopCount++;
		emit(OP_LOOP_INIT);
		emit(loop);
		int test = size;
		emit(OP_LOOP_TEST);
		emit(loop);
		emit(min);
		emit(max);
		emit(mode == GREEDY ? 1 : 0);
		int exit = size;
		emit(0);
		emit(OP_LOOP_ENTER);
		emit(loop);
		emit(atom);
		emit(OP_LOOP_END);
		emit(loop);
		emit(test);
		emit(empty);
		code[exit] = size;
		if (empty == EMPTY_FAILS && group && atom.a > 0 && mode == GREEDY) {
			// The JDK's GroupCurly sets its group to its last iteration again
			// once the rest of the pattern has matched
			emit(OP_CURLY_EXIT);
			emit(loop);
			emit(atom.a);
			emit(min);
		}
	}
}
