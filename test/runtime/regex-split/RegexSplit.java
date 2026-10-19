import java.util.regex.Pattern;
import java.util.stream.Collectors;

// R6: String.split and Pattern.split with regular expressions: limits,
// trailing empty strings and zero-width matches
public class RegexSplit {
	static String show(String[] parts) {
		StringBuilder sb = new StringBuilder();
		sb.append(parts.length).append(" [");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append('"').append(parts[i]).append('"');
		}
		return sb.append(']').toString();
	}

	static void split(String s, String regex) {
		System.out.println(String.format("%-14s %-10s %s", "\"" + s + "\"", regex, show(s.split(regex))));
	}

	static void split(String s, String regex, int limit) {
		System.out.println(String.format("%-14s %-10s %3d %s", "\"" + s + "\"", regex, limit, show(s.split(regex, limit))));
	}

	public static void main(String[] args) {
		// Literal patterns (the fast path) and the same splits through the matcher
		String[] inputs = { "a,b,,", ",a", "", ",", ",,", "a", "a,b", ",a,,b,", "abc" };
		for (String s : inputs) {
			split(s, ",");
			split(s, "[,]");
			split(s, ",", -1);
			split(s, "[,]", -1);
			split(s, "[,]", 2);
		}
		// Zero-width patterns
		split("abc", "");
		split("abc", "", -1);
		split("abc", "", 2);
		split("", "");
		split("abc", "(?=b)");
		split("abc", "(?=a)");
		split("abc", "(?!)");
		split("aXbXc", "(?<=X)");
		split("camelCaseString", "(?=\\p{Upper})");
		split("CamelCase", "(?=[A-Z])");
		split("abc", "x*");
		split("abc", "x*", -1);
		split("abc", "\\b");
		split("ab cd", "\\b");
		split("a1b2c3", "(?<=\\d)");
		split("a1b2c3", "(?<=\\d)", -1);
		// Regular expressions
		split("a1b22c333d", "\\d+");
		split("a1b22c333", "\\d+");
		split("a1b22c333", "\\d+", -1);
		split("1a2", "\\d");
		split("  leading and trailing  ", "\\s+");
		split("  leading and trailing  ", "\\s+", -1);
		split("one, two;three  four", "[,;\\s]+");
		split("key=value=more", "=", 2);
		split("a.b.c", "\\.", 0);
		split("a.b.c", ".");
		split("a.b.c", ".", -1);
		split("a|b|c", "\\|");
		split("a|b|c", "|");
		split("aaa", "a");
		split("aaa", "a", -1);
		split("aaa", "a", 2);
		split("baaab", "a*");
		split("baaab", "a*?");
		split("x1y22z", "(\\d)+");
		split("tab\tseparated\tvalues", "\\t");
		split("lines\nand\r\nmore", "\\R");
		split("ABCabc", "(?i)b");
		for (int limit = -2; limit <= 4; limit++) {
			split("a:b:c:", ":", limit);
			split("a:b:c:", "[:]", limit);
		}
		Pattern p = Pattern.compile("\\s*,\\s*");
		System.out.println(show(p.split("a , b,c ,, d")) + " " + show(p.split("a , b,c ,, d", 3)));
		System.out.println(p.splitAsStream(" x , y ,").collect(Collectors.toList()));
		System.out.println(Pattern.compile("").splitAsStream("abc").collect(Collectors.toList()));
	}
}
