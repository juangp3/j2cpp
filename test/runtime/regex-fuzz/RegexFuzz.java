import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// R6: pseudo-random patterns over a small alphabet, matched against
// pseudo-random inputs; every match, group and error is printed, so the
// output is compared with the JVM's
public class RegexFuzz {
	static long seed = 12345;

	static int next(int bound) {
		seed = (seed * 6364136223846793005L + 1442695040888963407L);
		return (int) ((seed >>> 33) % bound);
	}

	static final String[] ATOMS = { "a", "b", "c", ".", "[ab]", "[^a]", "[a-b&&[^b]]", "\\w", "\\1", "^", "$", "\\b",
			"\\B", "x" };
	static final String[] QUANTIFIERS = { "", "", "", "*", "+", "?", "{1,2}", "{2}", "*?", "+?", "??", "{0,2}?", "*+",
			"++", "?+" };

	static String atom(int depth) {
		int choice = next(depth > 2 ? ATOMS.length : ATOMS.length + 6);
		if (choice < ATOMS.length) {
			return ATOMS[choice];
		}
		String inner = expr(depth + 1);
		switch (choice - ATOMS.length) {
		case 0:
		case 1:
			return "(" + inner + ")";
		case 2:
			return "(?:" + inner + ")";
		case 3:
			return (next(2) == 0 ? "(?=" : "(?!") + inner + ")";
		case 4:
			return (next(2) == 0 ? "(?<=" : "(?<!") + inner + ")";
		default:
			return "(?>" + inner + ")";
		}
	}

	static String sequence(int depth) {
		StringBuilder sb = new StringBuilder();
		int n = 1 + next(3);
		for (int i = 0; i < n; i++) {
			String a = atom(depth);
			sb.append(a);
			if (!a.equals("^") && !a.equals("$") && !a.equals("\\b") && !a.equals("\\B")) {
				sb.append(QUANTIFIERS[next(QUANTIFIERS.length)]);
			}
		}
		return sb.toString();
	}

	static String expr(int depth) {
		String s = sequence(depth);
		if (next(4) == 0) {
			s += "|" + sequence(depth);
		}
		return s;
	}

	static String input() {
		StringBuilder sb = new StringBuilder();
		int n = next(9);
		for (int i = 0; i < n; i++) {
			sb.append("abc x".charAt(next(5)));
		}
		return sb.toString();
	}

	public static void main(String[] args) {
		int matches = 0;
		for (int i = 0; i < 4000; i++) {
			String regex = expr(0);
			int flags = next(5) == 0 ? Pattern.CASE_INSENSITIVE : 0;
			String text = input();
			if (flags != 0) {
				text = text.toUpperCase();
			}
			StringBuilder sb = new StringBuilder();
			sb.append(i).append(' ').append(regex).append(flags != 0 ? " /i" : "").append(" on '").append(text).append("':");
			try {
				Matcher m = Pattern.compile(regex, flags).matcher(text);
				while (m.find()) {
					matches++;
					sb.append(" [").append(m.start()).append(',').append(m.end());
					for (int g = 1; g <= m.groupCount(); g++) {
						sb.append(' ').append(m.start(g)).append(',').append(m.end(g));
					}
					sb.append(']');
				}
				sb.append(" matches=").append(m.matches()).append(" lookingAt=").append(m.lookingAt());
				sb.append(" split=").append(String.join("|", text.split(regex, i % 3 - 1)));
				sb.append(" replaced=").append(text.replaceAll(regex, "<$0>"));
			} catch (PatternSyntaxException e) {
				sb.append(" error ").append(e.getDescription()).append(" at ").append(e.getIndex());
			}
			System.out.println(sb);
		}
		System.out.println(matches + " matches");
	}
}
