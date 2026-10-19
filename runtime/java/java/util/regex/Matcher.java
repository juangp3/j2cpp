package java.util.regex;

import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Matcher, written from the Java SE specification. It runs the instructions
 * Parser compiles with an explicit backtracking stack on the heap, so long
 * inputs do not overflow the C++ stack: a quantified single character
 * (a*, [a-z]+?, .{2,5}) loops without pushing a state per character, and
 * other quantified groups push a few entries per iteration. Only look-around
 * and atomic groups recurse, once per nesting level of the pattern.
 *
 * The matcher reads the input as UTF-16, like the JDK: '.' and classes
 * match a whole surrogate pair, and positions are char indexes.
 *
 * Groups keep the values the JDK leaves in them: a group captured in an
 * atomic group or look-around stays set when the match backtracks past it
 * (even into the next start position), and a repeated deterministic group
 * sets itself to its own last iteration after the rest matched.
 *
 * Limits: the input is copied to a String by reset, so later changes to a
 * StringBuilder are not seen; results() finds every match before the stream
 * starts; region bounds are always opaque and anchoring; there is no
 * hitEnd, requireEnd, usePattern or StringBuffer overloads.
 */
public final class Matcher implements MatchResult {
	// Backtracking stack entries: a kind and three ints
	/** RESTORE register oldValue */
	private static final int RESTORE = 0;
	/** BRANCH pc pos */
	private static final int BRANCH = 1;
	/** GIVE_BACK nextPc pos minPos: a greedy single-character loop gives back one more */
	private static final int GIVE_BACK = 2;
	/** TAKE_MORE repeatPc pos count: a reluctant single-character loop takes one more */
	private static final int TAKE_MORE = 3;
	/**
	 * OVERWRITE group start end: the group's value when the match succeeds,
	 * unless an older entry sets it too. The JDK's GroupCurly sets its group
	 * to its last iteration after the rest of the pattern matched, so its
	 * first invocation wins over later iterations of an enclosing loop.
	 */
	private static final int OVERWRITE = 4;

	// Accept modes
	private static final int ANYWHERE = 0;
	private static final int TO_END = 1;

	private Pattern parentPattern;
	private String text;
	private int from;
	private int to;
	/** The current match, or first == -1 */
	private int first = -1;
	private int last = 0;
	/** The end of the previous match, for \G */
	private int oldLast = -1;
	private int lastAppendPosition = 0;
	/** Group starts and ends of the current match, as group 2g and 2g + 1 */
	private int[] groups;

	// Matching state
	/** Groups, then group heads, then loop counts and loop starts */
	private int[] regs;
	private int headBase;
	private int loopBase;
	private int[] stack = new int[64];
	private int sp;
	private int acceptMode;
	private int resumePc;
	private int resumePos;

	Matcher(Pattern pattern, CharSequence input) {
		this.parentPattern = pattern;
		this.text = input.toString();
		int groupCount = pattern.groupCount;
		groups = new int[(groupCount + 1) * 2];
		headBase = groups.length;
		loopBase = headBase + groupCount + 1;
		regs = new int[loopBase + pattern.loopCount * 2];
		reset();
	}

	public Pattern pattern() {
		return parentPattern;
	}

	public MatchResult toMatchResult() {
		int[] copy = new int[groups.length];
		System.arraycopy(groups, 0, copy, 0, groups.length);
		return new Result(text, first, copy, parentPattern.groupCount);
	}

	public Matcher reset() {
		first = -1;
		last = 0;
		oldLast = -1;
		lastAppendPosition = 0;
		for (int i = 0; i < groups.length; i++) {
			groups[i] = -1;
		}
		from = 0;
		to = text.length();
		return this;
	}

	public Matcher reset(CharSequence input) {
		text = input.toString();
		return reset();
	}

	public Matcher region(int start, int end) {
		if (start < 0 || start > text.length()) {
			throw new IndexOutOfBoundsException("start");
		}
		if (end < 0 || end > text.length()) {
			throw new IndexOutOfBoundsException("end");
		}
		if (start > end) {
			throw new IndexOutOfBoundsException("start > end");
		}
		reset();
		from = start;
		to = end;
		return this;
	}

	public int regionStart() {
		return from;
	}

	public int regionEnd() {
		return to;
	}

	public boolean matches() {
		return match(TO_END);
	}

	public boolean lookingAt() {
		return match(ANYWHERE);
	}

	public boolean find() {
		int next = last;
		if (next == first) {
			// An empty match: move on
			next++;
		}
		if (next < from) {
			next = from;
		}
		if (next > to) {
			for (int i = 0; i < groups.length; i++) {
				groups[i] = -1;
			}
			return false;
		}
		return search(next);
	}

	public boolean find(int start) {
		if (start < 0 || start > text.length()) {
			throw new IndexOutOfBoundsException("Illegal start index");
		}
		reset();
		return search(start);
	}

	// MatchResult

	public int start() {
		if (first < 0) {
			throw new IllegalStateException("No match available");
		}
		return first;
	}

	public int start(int group) {
		if (first < 0) {
			throw new IllegalStateException("No match available");
		}
		if (group < 0 || group > groupCount()) {
			throw new IndexOutOfBoundsException("No group " + group);
		}
		return groups[group * 2];
	}

	public int start(String name) {
		return groups[groupIndex(name) * 2];
	}

	public int end() {
		if (first < 0) {
			throw new IllegalStateException("No match available");
		}
		return last;
	}

	public int end(int group) {
		if (first < 0) {
			throw new IllegalStateException("No match available");
		}
		if (group < 0 || group > groupCount()) {
			throw new IndexOutOfBoundsException("No group " + group);
		}
		return groups[group * 2 + 1];
	}

	public int end(String name) {
		return groups[groupIndex(name) * 2 + 1];
	}

	public String group() {
		return group(0);
	}

	public String group(int group) {
		if (first < 0) {
			throw new IllegalStateException("No match found");
		}
		if (group < 0 || group > groupCount()) {
			throw new IndexOutOfBoundsException("No group " + group);
		}
		if (groups[group * 2] == -1 || groups[group * 2 + 1] == -1) {
			return null;
		}
		return text.substring(groups[group * 2], groups[group * 2 + 1]);
	}

	public String group(String name) {
		int group = groupIndex(name);
		if (groups[group * 2] == -1 || groups[group * 2 + 1] == -1) {
			return null;
		}
		return text.substring(groups[group * 2], groups[group * 2 + 1]);
	}

	public int groupCount() {
		return parentPattern.groupCount;
	}

	private int groupIndex(String name) {
		Objects.requireNonNull(name, "Group name");
		if (first < 0) {
			throw new IllegalStateException("No match found");
		}
		Integer group = parentPattern.names.get(name);
		if (group == null) {
			throw new IllegalArgumentException("No group with name <" + name + ">");
		}
		return group.intValue();
	}

	// Replacing

	public static String quoteReplacement(String s) {
		if (s.indexOf('\\') < 0 && s.indexOf('$') < 0) {
			return s;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\\' || c == '$') {
				sb.append('\\');
			}
			sb.append(c);
		}
		return sb.toString();
	}

	public Matcher appendReplacement(StringBuilder sb, String replacement) {
		if (first < 0) {
			throw new IllegalStateException("No match available");
		}
		StringBuilder result = new StringBuilder();
		appendExpanded(replacement, result);
		sb.append(text, lastAppendPosition, first);
		sb.append(result);
		lastAppendPosition = last;
		return this;
	}

	public StringBuilder appendTail(StringBuilder sb) {
		sb.append(text, lastAppendPosition, text.length());
		return sb;
	}

	/** Appends replacement with $n, ${name} and \ escapes expanded */
	private void appendExpanded(String replacement, StringBuilder result) {
		int cursor = 0;
		int n = replacement.length();
		while (cursor < n) {
			char c = replacement.charAt(cursor);
			if (c == '\\') {
				cursor++;
				if (cursor == n) {
					throw new IllegalArgumentException("character to be escaped is missing");
				}
				result.append(replacement.charAt(cursor++));
			} else if (c == '$') {
				cursor++;
				if (cursor == n) {
					throw new IllegalArgumentException("Illegal group reference: group index is missing");
				}
				c = replacement.charAt(cursor);
				int ref;
				if (c == '{') {
					cursor++;
					StringBuilder name = new StringBuilder();
					while (cursor < n) {
						c = replacement.charAt(cursor);
						if (!CharPredicate.isAsciiLetter(c) && !CharPredicate.isAsciiDigit(c)) {
							break;
						}
						name.append(c);
						cursor++;
					}
					if (name.length() == 0) {
						throw new IllegalArgumentException("named capturing group has 0 length name");
					}
					if (c != '}') {
						throw new IllegalArgumentException("named capturing group is missing trailing '}'");
					}
					String groupName = name.toString();
					if (CharPredicate.isAsciiDigit(groupName.charAt(0))) {
						throw new IllegalArgumentException("capturing group name {" + groupName
								+ "} starts with digit character");
					}
					Integer group = parentPattern.names.get(groupName);
					if (group == null) {
						throw new IllegalArgumentException("No group with name {" + groupName + "}");
					}
					ref = group.intValue();
					cursor++;
				} else {
					ref = c - '0';
					if (ref < 0 || ref > 9) {
						throw new IllegalArgumentException("Illegal group reference");
					}
					cursor++;
					// More digits while they name an existing group
					while (cursor < n) {
						int digit = replacement.charAt(cursor) - '0';
						if (digit < 0 || digit > 9) {
							break;
						}
						int next = ref * 10 + digit;
						if (next > groupCount()) {
							break;
						}
						ref = next;
						cursor++;
					}
				}
				if (start(ref) != -1 && end(ref) != -1) {
					result.append(text, start(ref), end(ref));
				}
			} else {
				result.append(c);
				cursor++;
			}
		}
	}

	public String replaceAll(String replacement) {
		reset();
		boolean found = find();
		if (!found) {
			return text;
		}
		StringBuilder sb = new StringBuilder();
		do {
			appendReplacement(sb, replacement);
			found = find();
		} while (found);
		return appendTail(sb).toString();
	}

	public String replaceAll(Function<MatchResult, String> replacer) {
		Objects.requireNonNull(replacer);
		reset();
		boolean found = find();
		if (!found) {
			return text;
		}
		StringBuilder sb = new StringBuilder();
		do {
			appendReplacement(sb, replacer.apply(this));
			found = find();
		} while (found);
		return appendTail(sb).toString();
	}

	public String replaceFirst(String replacement) {
		if (replacement == null) {
			throw new NullPointerException("replacement");
		}
		reset();
		if (!find()) {
			return text;
		}
		StringBuilder sb = new StringBuilder();
		appendReplacement(sb, replacement);
		return appendTail(sb).toString();
	}

	public String replaceFirst(Function<MatchResult, String> replacer) {
		Objects.requireNonNull(replacer);
		reset();
		if (!find()) {
			return text;
		}
		StringBuilder sb = new StringBuilder();
		appendReplacement(sb, replacer.apply(this));
		return appendTail(sb).toString();
	}

	/** Finds every remaining match first, then streams them */
	public Stream<MatchResult> results() {
		ArrayList<MatchResult> list = new ArrayList<MatchResult>();
		while (find()) {
			list.add(toMatchResult());
		}
		return list.stream();
	}

	public String toString() {
		return "java.util.regex.Matcher[pattern=" + parentPattern.pattern() + " region=" + regionStart() + ","
				+ regionEnd() + " lastmatch=" + (first >= 0 && group() != null ? group() : "") + "]";
	}

	// Matching

	private boolean search(int start) {
		int startOldLast = oldLast < 0 ? from : oldLast;
		clearRegisters();
		boolean found = false;
		for (int i = start; i <= to; i++) {
			if (parentPattern.anchored && i > from) {
				break;
			}
			if (parentPattern.hasSupplementary && i > from && i < to && Character.isLowSurrogate(text.charAt(i))
					&& Character.isHighSurrogate(text.charAt(i - 1))) {
				continue;
			}
			if (attempt(i, ANYWHERE, startOldLast)) {
				found = true;
				break;
			}
		}
		if (!found) {
			first = -1;
		}
		oldLast = last;
		return found;
	}

	private boolean match(int mode) {
		clearRegisters();
		boolean found = attempt(from, mode, oldLast < 0 ? from : oldLast);
		if (!found) {
			first = -1;
		}
		oldLast = last;
		return found;
	}

	private int lastMatchEnd;

	private void clearRegisters() {
		for (int i = 0; i < regs.length; i++) {
			regs[i] = -1;
		}
	}

	/**
	 * Runs the program at start; on success sets first, last and groups.
	 * The registers are not cleared, because the JDK keeps groups captured
	 * in an atomic group or look-around of an earlier attempt.
	 */
	private boolean attempt(int start, int mode, int previousEnd) {
		sp = 0;
		acceptMode = mode;
		lastMatchEnd = previousEnd;
		int end = run(0, start, -1);
		sp = 0;
		if (end < 0) {
			for (int i = 0; i < groups.length; i++) {
				groups[i] = -1;
			}
			return false;
		}
		System.arraycopy(regs, 0, groups, 0, groups.length);
		groups[0] = start;
		groups[1] = end;
		first = start;
		last = end;
		return true;
	}

	private void push(int kind, int a, int b, int c) {
		if (sp + 4 > stack.length) {
			int[] bigger = new int[stack.length * 2];
			System.arraycopy(stack, 0, bigger, 0, sp);
			stack = bigger;
		}
		stack[sp] = kind;
		stack[sp + 1] = a;
		stack[sp + 2] = b;
		stack[sp + 3] = c;
		sp += 4;
	}

	/** Sets a register, recording the old value for backtracking */
	private void set(int reg, int value) {
		int old = regs[reg];
		if (old != value) {
			push(RESTORE, reg, old, 0);
			regs[reg] = value;
		}
	}

	/**
	 * Drops everything above base once an atomic group or look-around is
	 * decided. Like the JDK, groups it captured then stay captured even if
	 * the match backtracks past it, until the search ends.
	 */
	private void cut(int base) {
		sp = base;
	}

	/** Applies the OVERWRITE entries above base, the oldest last */
	private void overwrite(int base) {
		for (int i = sp - 4; i >= base; i -= 4) {
			if (stack[i] == OVERWRITE) {
				int g = stack[i + 1];
				regs[g * 2] = stack[i + 2];
				regs[g * 2 + 1] = stack[i + 3];
			}
		}
	}

	/** The code point at i, a surrogate pair only if it ends by to */
	private int codePointAt(int i) {
		char c = text.charAt(i);
		if (Character.isHighSurrogate(c) && i + 1 < to) {
			char d = text.charAt(i + 1);
			if (Character.isLowSurrogate(d)) {
				return Character.toCodePoint(c, d);
			}
		}
		return c;
	}

	private int codePointBefore(int i) {
		char c = text.charAt(i - 1);
		if (Character.isLowSurrogate(c) && i - 2 >= from) {
			char d = text.charAt(i - 2);
			if (Character.isHighSurrogate(d)) {
				return Character.toCodePoint(d, c);
			}
		}
		return c;
	}

	/**
	 * Pops choice points down to base; false when there are none left.
	 * Otherwise resumePc and resumePos say where to continue.
	 */
	private boolean backtrack(int base) {
		int[] code = parentPattern.code;
		while (sp > base) {
			sp -= 4;
			int kind = stack[sp];
			int a = stack[sp + 1];
			int b = stack[sp + 2];
			int c = stack[sp + 3];
			switch (kind) {
			case RESTORE:
				regs[a] = b;
				break;
			case OVERWRITE:
				break;
			case BRANCH:
				resumePc = a;
				resumePos = b;
				return true;
			case GIVE_BACK: {
				int pos = b - 1;
				if (pos > c && Character.isLowSurrogate(text.charAt(pos))
						&& Character.isHighSurrogate(text.charAt(pos - 1))) {
					pos--;
				}
				if (pos > c) {
					push(GIVE_BACK, a, pos, c);
				}
				resumePc = a;
				resumePos = pos;
				return true;
			}
			case TAKE_MORE: {
				if (b < to) {
					int cp = codePointAt(b);
					if (parentPattern.preds[code[a + 1]].is(cp)) {
						int pos = b + Character.charCount(cp);
						if (c + 1 < code[a + 3]) {
							push(TAKE_MORE, a, pos, c + 1);
						}
						resumePc = a + 5;
						resumePos = pos;
						return true;
					}
				}
				break;
			}
			default:
				throw new IllegalStateException("backtracking entry " + kind);
			}
		}
		return false;
	}

	/**
	 * Runs the program from pc at pos until ACCEPT, or SUCCEED for a
	 * look-around or atomic body (which must end at mustEnd when that is not
	 * -1). Returns the end position, or -1 if there is no match; choice points
	 * it leaves above the stack as it found it are the caller's.
	 */
	private int run(int pc, int pos, int mustEnd) {
		int[] code = parentPattern.code;
		CharPredicate[] preds = parentPattern.preds;
		int base = sp;
		while (true) {
			switch (code[pc]) {
			case Parser.OP_CHAR:
				if (pos < to) {
					int cp = codePointAt(pos);
					if (preds[code[pc + 1]].is(cp)) {
						pos += Character.charCount(cp);
						pc += 2;
						continue;
					}
				}
				break;
			case Parser.OP_REPEAT: {
				CharPredicate set = preds[code[pc + 1]];
				int min = code[pc + 2];
				int max = code[pc + 3];
				int mode = code[pc + 4];
				int count = 0;
				int p = pos;
				int minPos = pos;
				int limit = mode == Parser.LAZY ? min : max;
				while (count < limit && p < to) {
					int cp = codePointAt(p);
					if (!set.is(cp)) {
						break;
					}
					p += Character.charCount(cp);
					count++;
					if (count == min) {
						minPos = p;
					}
				}
				if (count < min) {
					break;
				}
				if (mode == Parser.GREEDY && p > minPos) {
					push(GIVE_BACK, pc + 5, p, minPos);
				} else if (mode == Parser.LAZY && count < max) {
					push(TAKE_MORE, pc, p, count);
				}
				pos = p;
				pc += 5;
				continue;
			}
			case Parser.OP_SPLIT:
				push(BRANCH, code[pc + 1], pos, 0);
				pc += 2;
				continue;
			case Parser.OP_JMP:
				pc = code[pc + 1];
				continue;
			case Parser.OP_GROUP_HEAD:
				set(headBase + code[pc + 1], pos);
				pc += 2;
				continue;
			case Parser.OP_GROUP_TAIL: {
				int g = code[pc + 1];
				set(g * 2, regs[headBase + g]);
				set(g * 2 + 1, pos);
				pc += 2;
				continue;
			}
			case Parser.OP_ASSERT:
				if (assertion(code[pc + 1], pos)) {
					pc += 2;
					continue;
				}
				break;
			case Parser.OP_BACKREF: {
				int end = backref(code[pc + 1], code[pc + 2], pos);
				if (end >= 0) {
					pos = end;
					pc += 3;
					continue;
				}
				break;
			}
			case Parser.OP_FAIL:
				break;
			case Parser.OP_LOOP_INIT:
				set(loopBase + code[pc + 1] * 2, 0);
				pc += 2;
				continue;
			case Parser.OP_LOOP_TEST: {
				int loop = code[pc + 1];
				int count = regs[loopBase + loop * 2];
				int enter = pc + 6;
				int exit = code[pc + 5];
				if (count < code[pc + 2]) {
					pc = enter;
				} else if (count < code[pc + 3]) {
					if (code[pc + 4] != 0) {
						push(BRANCH, exit, pos, 0);
						pc = enter;
					} else {
						push(BRANCH, enter, pos, 0);
						pc = exit;
					}
				} else {
					pc = exit;
				}
				continue;
			}
			case Parser.OP_LOOP_ENTER:
				set(loopBase + code[pc + 1] * 2 + 1, pos);
				pc += 2;
				continue;
			case Parser.OP_LOOP_END: {
				int loop = code[pc + 1];
				int count = regs[loopBase + loop * 2];
				int empty = code[pc + 3];
				if (pos == regs[loopBase + loop * 2 + 1]
						&& (empty == Parser.EMPTY_EXITS || count + 1 > code[code[pc + 2] + 2])) {
					if (empty == Parser.EMPTY_FAILS) {
						break;
					}
					pc += 4;
				} else {
					set(loopBase + loop * 2, count + 1);
					pc = code[pc + 2];
				}
				continue;
			}
			case Parser.OP_SUB: {
				int end = sub(code[pc + 1], pc + 5, pos, code[pc + 3], code[pc + 4]);
				if (end >= 0) {
					pos = end;
					pc = code[pc + 2];
					continue;
				}
				break;
			}
			case Parser.OP_SUCCEED:
				if (mustEnd < 0 || pos == mustEnd) {
					overwrite(base);
					return pos;
				}
				break;
			case Parser.OP_ACCEPT:
				if (acceptMode != TO_END || pos == to) {
					overwrite(base);
					return pos;
				}
				break;
			case Parser.OP_CURLY_EXIT: {
				int g = code[pc + 2];
				if (regs[loopBase + code[pc + 1] * 2] > code[pc + 3]) {
					push(OVERWRITE, g, regs[g * 2], regs[g * 2 + 1]);
				}
				pc += 4;
				continue;
			}
			default:
				throw new IllegalStateException("instruction " + code[pc]);
			}
			// The instruction failed
			if (!backtrack(base)) {
				return -1;
			}
			pc = resumePc;
			pos = resumePos;
		}
	}

	/** An atomic group or look-around at pos: the position after it, or -1 */
	private int sub(int kind, int body, int pos, int min, int max) {
		int base = sp;
		switch (kind) {
		case Parser.SUB_ATOMIC: {
			int end = run(body, pos, -1);
			if (end < 0) {
				return -1;
			}
			cut(base);
			return end;
		}
		case Parser.SUB_AHEAD:
			if (run(body, pos, -1) < 0) {
				return -1;
			}
			cut(base);
			return pos;
		case Parser.SUB_NOT_AHEAD:
			if (run(body, pos, -1) >= 0) {
				cut(base);
				return -1;
			}
			return pos;
		default: {
			// Look-behind: the body must end at pos, so try the starts it allows,
			// nearest first; max may have overflowed, as in the JDK
			int lowest = Math.max(pos - max, from);
			boolean found = false;
			for (int start = pos - min; start >= lowest; start--) {
				if (run(body, start, pos) >= 0) {
					found = true;
					break;
				}
			}
			if (kind == Parser.SUB_BEHIND) {
				if (!found) {
					return -1;
				}
				cut(base);
				return pos;
			}
			if (found) {
				cut(base);
				return -1;
			}
			return pos;
		}
		}
	}

	private boolean isWordAround(int i, boolean before) {
		int c;
		if (before) {
			if (i <= from) {
				return false;
			}
			c = codePointBefore(i);
		} else {
			if (i >= to) {
				return false;
			}
			c = codePointAt(i);
		}
		return c == '_' || Character.isLetterOrDigit(c);
	}

	private boolean assertion(int kind, int i) {
		switch (kind) {
		case Parser.BEGIN:
			return i == from;
		case Parser.CARET_MULTILINE:
		case Parser.CARET_MULTILINE_UNIX: {
			// Not at the end, even after a line terminator
			if (i == to) {
				return false;
			}
			if (i > from) {
				char c = text.charAt(i - 1);
				if (!CharPredicate.isLineTerminator(c, kind == Parser.CARET_MULTILINE_UNIX)) {
					return false;
				}
				// \r\n is one line terminator
				if (c == '\r' && text.charAt(i) == '\n' && kind == Parser.CARET_MULTILINE) {
					return false;
				}
			}
			return true;
		}
		case Parser.DOLLAR:
		case Parser.DOLLAR_MULTILINE: {
			boolean multiline = kind == Parser.DOLLAR_MULTILINE;
			if (!multiline) {
				// Only at the end or before a final line terminator
				if (i < to - 2) {
					return false;
				}
				if (i == to - 2 && (text.charAt(i) != '\r' || text.charAt(i + 1) != '\n')) {
					return false;
				}
			}
			if (i < to) {
				char c = text.charAt(i);
				if (c == '\n') {
					// Not between \r and \n
					return !(i > 0 && text.charAt(i - 1) == '\r');
				}
				return CharPredicate.isLineTerminator(c, false);
			}
			return true;
		}
		case Parser.DOLLAR_UNIX:
		case Parser.DOLLAR_MULTILINE_UNIX:
			if (i < to) {
				if (text.charAt(i) != '\n') {
					return false;
				}
				return kind == Parser.DOLLAR_MULTILINE_UNIX || i == to - 1;
			}
			return true;
		case Parser.END:
			return i == to;
		case Parser.WORD_BOUNDARY:
			return isWordAround(i, true) != isWordAround(i, false);
		case Parser.NOT_WORD_BOUNDARY:
			return isWordAround(i, true) == isWordAround(i, false);
		case Parser.LAST_MATCH:
			return i == lastMatchEnd;
		default:
			throw new IllegalStateException("assertion " + kind);
		}
	}

	/** Matches the text of group g at i: the position after it, or -1 */
	private int backref(int g, int caseMode, int i) {
		int start = regs[g * 2];
		int end = regs[g * 2 + 1];
		if (start < 0 || end < 0) {
			return -1;
		}
		int n = end - start;
		if (i + n > to) {
			return -1;
		}
		for (int k = 0; k < n; k++) {
			char c1 = text.charAt(start + k);
			char c2 = text.charAt(i + k);
			if (c1 != c2) {
				if (caseMode == 0) {
					return -1;
				}
				if (caseMode == 1) {
					if (!(CharPredicate.isAsciiLetter(c1) && (c1 ^ 0x20) == c2)) {
						return -1;
					}
				} else if (Character.toLowerCase(Character.toUpperCase((int) c1)) != Character
						.toLowerCase(Character.toUpperCase((int) c2))) {
					return -1;
				}
			}
		}
		return i + n;
	}

	/** A snapshot of a match, for toMatchResult and results */
	private static final class Result implements MatchResult {
		private final String text;
		private final int first;
		private final int[] groups;
		private final int groupCount;

		Result(String text, int first, int[] groups, int groupCount) {
			this.text = text;
			this.first = first;
			this.groups = groups;
			this.groupCount = groupCount;
		}

		private void check(int group) {
			if (first < 0) {
				throw new IllegalStateException("No match found");
			}
			if (group < 0 || group > groupCount) {
				throw new IndexOutOfBoundsException("No group " + group);
			}
		}

		public int start() {
			return start(0);
		}

		public int start(int group) {
			check(group);
			return groups[group * 2];
		}

		public int end() {
			return end(0);
		}

		public int end(int group) {
			check(group);
			return groups[group * 2 + 1];
		}

		public String group() {
			return group(0);
		}

		public String group(int group) {
			check(group);
			if (groups[group * 2] == -1 || groups[group * 2 + 1] == -1) {
				return null;
			}
			return text.substring(groups[group * 2], groups[group * 2 + 1]);
		}

		public int groupCount() {
			return groupCount;
		}
	}
}
