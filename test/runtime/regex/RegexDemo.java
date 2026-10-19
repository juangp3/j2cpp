import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

// R6: java.util.regex, matching, groups, look-around, flags and replacement
public class RegexDemo {
	static String show(String s) {
		if (s == null) {
			return "null";
		}
		StringBuilder sb = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\n') {
				sb.append("\\n");
			} else if (c == '\r') {
				sb.append("\\r");
			} else if (c == '\t') {
				sb.append("\\t");
			} else if (c < 0x20 || c > 0x7e) {
				sb.append("\\u").append(Integer.toHexString(c));
			} else {
				sb.append(c);
			}
		}
		return sb.append('"').toString();
	}

	/** Every match of regex in s, with its groups */
	static void find(String regex, String s) {
		find(regex, 0, s);
	}

	static void find(String regex, int flags, String s) {
		StringBuilder sb = new StringBuilder();
		try {
			Matcher m = Pattern.compile(regex, flags).matcher(s);
			while (m.find()) {
				sb.append(" [").append(m.start()).append(',').append(m.end()).append(' ').append(show(m.group()));
				for (int g = 1; g <= m.groupCount(); g++) {
					sb.append(" g").append(g).append('=').append(show(m.group(g)));
				}
				sb.append(']');
			}
		} catch (RuntimeException e) {
			sb.append(" threw ").append(e.getClass().getName()).append(": ").append(e.getMessage());
		}
		System.out.println("find " + show(regex) + (flags != 0 ? "/" + flags : "") + " in " + show(s) + ":" + sb);
	}

	static void matches(String regex, String... inputs) {
		StringBuilder sb = new StringBuilder();
		for (String s : inputs) {
			sb.append(' ').append(show(s)).append('=').append(s.matches(regex));
		}
		System.out.println("matches " + show(regex) + ":" + sb);
	}

	static void bad(String regex) {
		try {
			Pattern.compile(regex);
			System.out.println("compiled " + show(regex));
		} catch (PatternSyntaxException e) {
			System.out.println("PatternSyntaxException desc=" + e.getDescription() + " index=" + e.getIndex() + " pattern="
					+ show(e.getPattern()));
			System.out.println(e.getMessage());
		}
	}

	static void replace(String regex, String s, String replacement) {
		String result;
		try {
			result = show(s.replaceAll(regex, replacement)) + " first " + show(s.replaceFirst(regex, replacement));
		} catch (RuntimeException e) {
			result = "threw " + e.getClass().getName() + ": " + e.getMessage();
		}
		System.out.println("replace " + show(regex) + " in " + show(s) + " with " + show(replacement) + ": " + result);
	}

	static void basics() {
		find("abc", "xabcyabc");
		find("a.c", "abc a\nc a\rc axc");
		find("a.c", Pattern.DOTALL, "abc a\nc");
		find("(?s)a.c", "a\nc");
		find("colou?r", "color colour colouur");
		find("\\d+", "a1b22c333");
		find("\\D+", "a1b22c333");
		find("\\w+", "hello, world_42!");
		find("\\W", "a-b c");
		find("\\s+", "a \t\nb\u000bc");
		find("\\S+", " ab  cd ");
		find("[aeiou]", "education");
		find("[^aeiou]+", "education");
		find("[a-cx-z]+", "abcdwxyz");
		find("[a-z&&[^aeiou]]+", "education");
		find("[a-z&&def]", "abcdefg");
		find("[^a[b]]", "abc");
		find("[^a-z&&[aeiou]]", "ab1");
		find("[]a]", "a]b");
		find("[^]]", "]x]");
		find("[a-]", "a-b");
		find("[\\d-z]", "1-z");
		find("[\\w&&[^\\d]]+", "ab12cd");
		find("\\p{Alpha}+", "abc123def");
		find("\\p{Digit}+", "abc123def");
		find("\\p{Upper}\\p{Lower}+", "Hello World");
		find("\\p{Punct}+", "a!?b.,c");
		find("\\p{Alnum}+", "ab-12_c");
		find("\\p{Space}", "a b\tc");
		find("\\p{XDigit}+", "0x1fAz");
		find("\\P{Alpha}+", "ab12cd");
		find("\\p{L}+", "caf\u00e9 na\u00efve 123");
		find("\\p{Lu}", "ABc\u00c9\u00e9");
		find("\\p{Ll}+", "ABc\u00c9\u00e9\u00aa");
		find("\\pL+", "ab1");
		find("\\p{IsAlphabetic}+", "ab1");
		find("\\p{javaLowerCase}+", "abCD");
		find("\\h+", "a \tb");
		find("\\v", "a\nb");
		find("\\R", "a\r\nb\nc\rd");
		find("\\t\\n\\x41\\u0042\\0103\\cA", "\t\nABC\u0001");
		find("\\x{1F600}", "a\ud83d\ude00b");
		find("\\.\\*\\+\\?\\(\\)\\[\\]\\{\\}\\|\\^\\$\\\\", ".*+?()[]{}|^$\\");
		find("\\Qa.b*c\\E+", "a.b*cc a.b*c");
		find("a\\Q", "a");
		find("x{2}", "xxxxx");
		find("x{2,}", "x xx xxxxx");
		find("x{2,3}", "xxxxxxx");
		find("x{0}", "ab");
		find("", "ab");
		find("a|b|", "abc");
		find("ab|a", "ab");
		find("a{1}{2}", "aaa");
		find("]}", "]}");
	}

	static void quantifiers() {
		find("<.+>", "<a><b>");
		find("<.+?>", "<a><b>");
		find("<.++>", "<a><b>");
		find("<[^>]*+>", "<a><b>");
		find("a*", "baaab");
		find("a*?", "baa");
		find("a+?", "aaa");
		find("a??b", "ab b");
		find("a?+a", "aa a");
		find("(ab)+", "ababab abab");
		find("(ab)+?", "ababab");
		find("(ab){2}", "ababab");
		find("(ab){1,2}?c", "ababc");
		find("(?:ab)*+ab", "ababab");
		find("(?:a|ab)(?:c|bcd)(?:d*)", "abcd");
		find("(a|ab)(c|bcd)(d*)", "abcd");
		find("(a*)*b", "aaab");
		find("(a*)+", "b");
		find("(a?){3}", "aa");
		find("(a|b)*c", "abbac");
		find("(x+x+)+y", "xxxxxxxxxxy");
		find("^(\\w+)\\s*=\\s*(.*?)\\s*$", "key = some value  ");
		find("(\\d{1,3})(?=(\\d{3})+$)", "1234567");
		find(".*foo", "xfooxxxxxxfoo");
		find(".*?foo", "xfooxxxxxxfoo");
		find(".*+foo", "xfooxxxxxxfoo");
		find("(?>a|ab)c", "abc ac");
		find("a.{2,4}?b", "a12b345b");
		find("[a-c]{2,3}+c", "abcc abc");
		// Exponential backtracking that still ends: 2^18 ways to fail
		find("(a+)+b", "aaaaaaaaaaaaaaaaaa");
		find("(a|aa)+$", "aaaaaaaaaaaaaaaaaaaab");
		find("^(\\d+|\\w+)*!$", "abc123def456!");
		find("(?:(a)|b)*", "abba");
		find("(a)*", "aaa");
		find("(a)*?b", "aab");
		find("(?:x(a)?)+", "xax");
		find("(?<=^|,)[^,]*", "a,,b");
		find("\\b(\\w+)(?:\\s+\\1\\b)+", "this is is is a test test");
	}

	static void groups() {
		find("(\\w+)@(\\w+)\\.com", "joe@example.com, ann@test.com");
		find("(a)|(b)", "ab");
		find("(a(b)?)+", "aba");
		find("((a)|b)+", "ab");
		find("(?<year>\\d{4})-(?<month>\\d\\d)", "on 2024-05 and 1999-12");
		Matcher m = Pattern.compile("(?<year>\\d{4})-(?<month>\\d\\d)(-(?<day>\\d\\d))?").matcher("2024-05");
		System.out.println("named matches " + m.matches() + " year=" + m.group("year") + " month=" + m.group("month") + " day="
				+ m.group("day") + " start=" + m.start("month") + " end=" + m.end("month") + " count=" + m.groupCount()
				+ " start(3)=" + m.start(3) + " end(3)=" + m.end(3));
		try {
			m.group("nope");
		} catch (IllegalArgumentException e) {
			System.out.println("group(nope): " + e.getMessage());
		}
		try {
			m.group(9);
		} catch (IndexOutOfBoundsException e) {
			System.out.println("group(9): " + e.getMessage());
		}
		Matcher none = Pattern.compile("x").matcher("abc");
		try {
			none.group();
		} catch (IllegalStateException e) {
			System.out.println("group() before match: " + e.getMessage());
		}
		try {
			none.start();
		} catch (IllegalStateException e) {
			System.out.println("start() before match: " + e.getMessage());
		}
		System.out.println("find fails " + none.find() + ", " + none);
		find("(\\w)\\1", "hello bookkeeper");
		find("(\\w+) \\1", "the the cat cat dog");
		find("(?<c>[a-z])\\k<c>", "aabbcd");
		find("(a)\\2", "aa");
		find("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)\\10", "abcdefghijj");
		find("(a)\\11", "aa1");
		find("(?i)(a)\\1", "aA Aa");
		find("(\\2two|(one))+", "oneonetwo");
		find("(a\\1?){3}", "aaaaaa");
	}

	/** Where the JDK's groups keep values a plain backtracking matcher would not */
	static void jdkGroups() {
		// A quantified fixed-length group sets itself to its own last
		// iteration once the rest has matched, over later outer iterations
		find("(([^a]){1,2})+", "xycd");
		find("(?:(b){1,2}){2}", "bbbb");
		find("(?:x(b){1,2})+", "xbxbb");
		find("(?:x(b)*)+", "xbxbb");
		find("(?:x(b)+?)+y", "xbbxby");
		find("(?:x(bc)*)+", "xbcbcxbc");
		find("(?:(b){0,2})+", "bbbbb");
		// An optional empty iteration of a fixed-length group captures nothing
		find("(^)*", "ab");
		find("(^){0,2}", "b");
		find("(^){0,1}", "b");
		find("(a?){0,1}?b", "b");
		// Groups captured in atomic groups and look-arounds stay set when the
		// match backtracks past them, even into the next start position
		find("(?>(a))b|ac", "ac");
		find("(?=(a))ab|ac", "ac");
		find("(?!(a)b)a.", "ac");
		find("\\1?+(?>\\b(\\b)?+a?+)?\\B", " c bx");
		find("(?:(?>($$|$c?[^a]+)?+)??){0,2}?\\1{2}", "");
		// Look-behind lengths are computed with int overflow, as in the JDK
		find("(?<=a+b+)c", "abc");
		find("(?<=ba*)c", "baac");
		find("(?<=b(?:x|y)(?:a)*)c", "bxac");
		find("(?<=a+a+a+)c", "aaaac");
		bad("(?<=(?:ab)+)c");
		bad("(?<=b(?:a)*)c");
		bad("(?<=a+x{2})c");
		bad("(?<=(?:a|b)*)c");
		bad("(?<=ba*?)c");
		bad("(?<=b*(?>b){0,2})c");
		bad("(?<=(?:a{1,2}){1,2})c");
		bad("(?<=(?:a+){1})c");
		find("(?<=(?:ab){1,2})c", "ababc");
		find("(?<=b*(?>b){0,1})c", "bbc");
		find("(?<=(?:x(?:a){2})*)c", "xaac");
	}

	static void lookaround() {
		find("foo(?=bar)", "foobar foobaz");
		find("foo(?!bar)", "foobar foobaz");
		find("(?<=\\$)\\d+", "cost $42 or 17");
		find("(?<!\\$)\\b\\d+", "cost $42 or 17");
		find("(?<=a|bc)d", "ad bcd cd");
		find("(?<=a{2,3})b", "ab aab aaab");
		find("(?<=a+)b", "b ab");
		find("(?<=(\\d))x", "1x ax");
		find("(?=(\\w+))\\w", "ab");
		find("\\b\\w", "hello big world");
		find("\\B\\w", "hi there");
		find("\\bcat\\b", "cat concat cat's");
		find("(?<=\\G..)", "abcdefg");
		find("\\Gab", "ababxab");
		find("q(?!u)", "quit qatar");
		find("(?=.*\\d)(?=.*[a-z]).{6,}", "abc123");
		find("^(?!.*(.).*\\1)[a-z]+$", "abcdef");
		find("^(?!.*(.).*\\1)[a-z]+$", "abcdea");
	}

	static void anchorsAndFlags() {
		find("^\\w+", "one\ntwo\nthree");
		find("^\\w+", Pattern.MULTILINE, "one\ntwo\r\nthree");
		find("\\w+$", "one\ntwo\n");
		find("\\w+$", Pattern.MULTILINE, "one\ntwo\r\nthree");
		find("$", "a\nb\n");
		find("(?m)$", "a\nb\n");
		find("(?m)^", "a\nb\n");
		find("(?m)^", "");
		find("$", "a\r\n");
		find("\\Z", "ab\n");
		find("\\z", "ab\n");
		find("\\Aab", "abab");
		find("(?d)^.", Pattern.MULTILINE, "a\rb\nc");
		find("hello", Pattern.CASE_INSENSITIVE, "Hello HELLO hello");
		find("(?i)[a-c]+", "xAbCx");
		find("(?i)[^a]", "aAb");
		find("(?i)\\p{Lower}", "aB");
		find("a(?i)b", "aB AB");
		find("a(?i:b)c", "aBc aBC");
		find("(a(?i)b)c", "aBc aBC");
		find("(?i)caf\u00c9", "CAF\u00e9 caf\u00c9");
		find("(?iu)caf\u00c9", "CAF\u00e9 caf\u00c9");
		find("(?-i)a", Pattern.CASE_INSENSITIVE, "aA");
		find("a b c # comment", Pattern.COMMENTS, "abc a b c");
		find("(?x) [a b] + \\ ", "ab ba ");
		find("a.b", Pattern.LITERAL, "axb a.b");
		find("A.B", Pattern.LITERAL | Pattern.CASE_INSENSITIVE, "a.b");
		Pattern p = Pattern.compile("a+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
		System.out.println("pattern " + p.pattern() + " toString " + p + " flags " + p.flags());
		System.out.println("Pattern.matches " + Pattern.matches("a*b", "aaab") + " " + Pattern.matches("a*b", "aaabc"));
	}

	static void matcherApi() {
		Matcher m = Pattern.compile("\\d+").matcher("a12b345c6");
		System.out.println("matches " + m.matches() + " lookingAt " + m.lookingAt());
		m = Pattern.compile("\\w\\d").matcher("a12b345c6");
		System.out.println("lookingAt " + m.lookingAt() + " " + m.group() + " matches " + m.matches());
		m = Pattern.compile("\\d+").matcher("a12b345c6");
		System.out.println("find(3) " + m.find(3) + " " + m.group() + " next " + m.find() + " " + m.group());
		try {
			m.find(99);
		} catch (IndexOutOfBoundsException e) {
			System.out.println("find(99): " + e.getMessage());
		}
		m.reset();
		System.out.println("after reset " + m.find() + " " + m.group() + " " + m);
		m.reset("x7y");
		System.out.println("reset input " + m.find() + " " + m.group() + " " + m.start() + " " + m.end());
		m = Pattern.compile("b+").matcher("abbbc");
		m.region(1, 3);
		System.out.println("region " + m.find() + " " + m.group() + " " + m.regionStart() + "-" + m.regionEnd()
				+ " matches " + m.matches());
		m = Pattern.compile("(\\w)(\\d)").matcher("a1 b2 c3");
		List<String> found = m.results().map(r -> r.group(2) + r.group(1) + "@" + r.start()).collect(Collectors.toList());
		System.out.println("results " + found);
		m.reset();
		m.find();
		MatchResult r = m.toMatchResult();
		m.find();
		System.out.println("toMatchResult " + r.group() + " " + r.start(1) + " " + r.end(2) + " " + r.groupCount() + " now "
				+ m.group());
		System.out.println("count " + Pattern.compile("o").matcher("foo boo").results().count());
		Pattern words = Pattern.compile("\\w+");
		System.out.println("asPredicate " + words.asPredicate().test("!!a!") + " " + words.asPredicate().test("!!!")
				+ " asMatchPredicate " + words.asMatchPredicate().test("ab") + " " + words.asMatchPredicate().test("a b"));
		System.out.println("splitAsStream " + Pattern.compile(",").splitAsStream("a,b,,c,,").collect(Collectors.toList()));
		System.out.println("quote " + Pattern.quote("a.b") + " " + Pattern.quote("x\\Ey") + " "
				+ "a.b x\\Ey".matches(Pattern.quote("a.b") + " " + Pattern.quote("x\\Ey")) + " "
				+ "axb".matches(Pattern.quote("a.b")));
		System.out.println("quoteReplacement " + Matcher.quoteReplacement("$1\\x") + " "
				+ "abc".replaceAll("b", Matcher.quoteReplacement("$1\\x")));
	}

	static void replacing() {
		replace("(\\w+)@(\\w+)", "joe@site ann@host", "$2 at $1");
		replace("(?<user>\\w+)@(?<host>\\w+)", "joe@site", "${host}/${user}");
		replace("a", "banana", "\\$");
		replace("a", "banana", "[$0]");
		replace("(a)", "banana", "$10");
		replace("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)", "abcdefghij", "$10-$11");
		replace("x*", "abc", "-");
		replace("", "abc", "-");
		replace("b*", "abbbc", "X");
		replace("\\s+", "  lots   of\tspace  ", " ");
		replace("(\\d+)", "a1b22", "<$1>");
		replace("(a)|b", "ab", "[$1]");
		replace("a", "aaa", "\\\\");
		replace("a", "abc", "$");
		replace("a", "abc", "\\");
		replace("a", "abc", "$x");
		replace("a", "abc", "$2");
		replace("a", "abc", "${}");
		replace("(?<n>a)", "abc", "${n");
		replace("(?<n>a)", "abc", "${m}");
		replace("(?<n>a)", "abc", "${1n}");
		replace("[", "abc", "x");
		System.out.println("lambda " + Pattern.compile("\\d+").matcher("a1b22c333")
				.replaceAll(mr -> String.valueOf(mr.group().length())));
		System.out.println("lambda first " + Pattern.compile("[aeiou]").matcher("regex").replaceFirst(mr -> mr.group().toUpperCase()));
		Matcher m = Pattern.compile("cat").matcher("one cat two cats in the yard");
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			m.appendReplacement(sb, "dog");
		}
		m.appendTail(sb);
		System.out.println("append " + sb);
		System.out.println("String.replace " + "a.b.c".replace(".", "$") + " " + "aaa".replace("aa", "b") + " "
				+ "abc".replace("", "-") + " " + "x+y".replace("+", "\\"));
	}

	static void stringMethods() {
		matches("\\d+", "123", "12a", "");
		matches("[a-z]+@[a-z]+\\.[a-z]{2,}", "me@host.org", "me@host.o", "Me@host.org");
		matches("(?i)hello", "HeLLo", "hello!");
		matches(".*", "", "anything", "two\nlines");
		matches("a|ab", "ab");
		matches("(a|ab)(c|bcd)", "abcd");
		matches("\\p{Punct}*", "!?", "a");
	}

	static void syntaxErrors() {
		bad("(");
		bad("[a");
		bad("*a");
		bad("a{2,1}");
		bad(")");
		bad("a)");
		bad("a**");
		bad("+");
		bad("a{");
		bad("a{1");
		bad("a{1,");
		bad("a{x}");
		bad("[");
		bad("[]");
		bad("[^]");
		bad("[b-a]");
		bad("[a-\\w]");
		bad("\\");
		bad("(?<n>a)\\k<m>");
		bad("(?<1n>a)");
		bad("(?<n");
		bad("(?<n>a)(?<n>b)");
		bad("(?<a_b>x)");
		bad("\\k");
		bad("(?");
		bad("(?z)");
		bad("\\x");
		bad("\\xg");
		bad("\\u12");
		bad("\\c");
		bad("\\q");
		bad("a|*");
		bad("(*)");
		bad("x{99999999999}");
		bad("[a&&");
		bad("\\0");
		bad("\\09");
		bad("a++*");
		bad("a{,3}");
		bad("(?<n>");
		bad("(?<=a\\1)");
		bad("a{1}*");
		bad("\\1");
		bad("[\\w-a]");
		try {
			"abc".split("(");
		} catch (PatternSyntaxException e) {
			System.out.println("split: " + e.getDescription());
		}
		try {
			"abc".matches("[");
		} catch (PatternSyntaxException e) {
			System.out.println("matches: " + e.getDescription());
		}
		try {
			Pattern.compile("a", 0x10000);
		} catch (IllegalArgumentException e) {
			System.out.println("flag: " + e.getMessage());
		}
	}

	static void supplementary() {
		String face = new String(Character.toChars(0x1F600));
		String s = "a" + face + "b";
		find(".", s);
		find("[^a]", s);
		find("a.b", s);
		find(face + "+", face + face + "x");
		find("\\x{1F600}", s);
		System.out.println("matches " + s.matches("a.b") + " " + s.matches("a..b") + " " + s.replaceAll(".", "_"));
		System.out.println("split empty " + String.join("|", s.split("")).length() + " " + s.split("").length);
	}

	static void longInput() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 100000; i++) {
			sb.append('a');
		}
		String as = sb.toString();
		System.out.println("long a*b " + Pattern.matches("a*b", as + "b") + " " + Pattern.matches("a*b", as));
		System.out.println("long a*?b " + Pattern.matches("a*?b", as + "b"));
		System.out.println("long .*a " + Pattern.compile(".*a").matcher(as).matches());
		System.out.println("long [ab]+ " + Pattern.compile("[ab]+").matcher(as + "c").lookingAt());
		System.out.println("long \\w+ end " + Pattern.compile("\\w+$").matcher(as).find());
		StringBuilder pairs = new StringBuilder();
		for (int i = 0; i < 50000; i++) {
			pairs.append("ab");
		}
		Matcher m = Pattern.compile("(ab)*").matcher(pairs);
		System.out.println("long (ab)* " + m.matches() + " " + m.start(1) + " " + m.end(1));
		System.out.println("long (?:ab)+c " + Pattern.matches("(?:ab)+c", pairs + "c"));
		StringBuilder words = new StringBuilder();
		for (int i = 0; i < 20000; i++) {
			words.append("w").append(i % 10).append(i % 3 == 0 ? "  " : " ");
		}
		String[] parts = words.toString().split("\\s+");
		System.out.println("long split " + parts.length + " " + parts[0] + " " + parts[parts.length - 1]);
		String replaced = as.replaceAll("a", "bc");
		System.out.println("long replaceAll " + replaced.length() + " " + replaced.substring(0, 6));
		System.out.println("long find count " + Pattern.compile("a{3}").matcher(as).results().count());
	}

	public static void main(String[] args) {
		basics();
		quantifiers();
		groups();
		jdkGroups();
		lookaround();
		anchorsAndFlags();
		matcherApi();
		replacing();
		stringMethods();
		syntaxErrors();
		supplementary();
		longInput();
	}
}
