// R2: the String and StringBuilder API (searching, case, stripping, split,
// join, builder editing) with edge cases and exception messages
public class Text {
	// Prints non-ASCII characters as \\uXXXX so the output does not depend on
	// the console encoding
	static String show(String s) {
		if (s == null) {
			return "null";
		}
		StringBuilder sb = new StringBuilder("\"");
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= ' ' && c < 127) {
				sb.append(c);
			} else {
				String hex = Integer.toHexString(c);
				sb.append("\\u");
				for (int j = hex.length(); j < 4; j++) {
					sb.append('0');
				}
				sb.append(hex);
			}
		}
		return sb.append('"').toString();
	}

	static String show(String[] parts) {
		StringBuilder sb = new StringBuilder();
		sb.append(parts.length).append(" [");
		for (int i = 0; i < parts.length; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(show(parts[i]));
		}
		return sb.append(']').toString();
	}

	static void print(String label, Object value) {
		System.out.println(label + ": " + value);
	}

	static void caught(String label, RuntimeException e) {
		System.out.println(label + ": " + e);
	}

	static void cases() {
		String upper = "\u00c9COLE \u00d8RE \u00de\u00c0";
		String lower = "\u00e9cole \u00f8re \u00fe\u00e0";
		print("equalsIgnoreCase", "Hello".equalsIgnoreCase("hELLO") + " " + "a".equalsIgnoreCase("ab") + " "
				+ "x".equalsIgnoreCase(null) + " " + "".equalsIgnoreCase("") + " " + upper.equalsIgnoreCase(lower) + " "
				+ "\u00ff".equalsIgnoreCase("\u0178") + " " + "\u00b5".equalsIgnoreCase("\u039c") + " "
				+ "\u00d7".equalsIgnoreCase("\u00f7"));
		print("compareToIgnoreCase", "apple".compareToIgnoreCase("APPLE") + " " + "a".compareToIgnoreCase("B") + " "
				+ "Zed".compareToIgnoreCase("abc") + " " + "ab".compareToIgnoreCase("ABC") + " " + "".compareToIgnoreCase("x")
				+ " " + "_".compareToIgnoreCase("a") + " " + "\u00b5".compareToIgnoreCase("a") + " "
				+ "\u00ff".compareToIgnoreCase("\u0178") + " " + "\u00e9".compareToIgnoreCase("\u00c8"));
		print("toUpperCase", show("hello, World 42".toUpperCase()) + " " + show(lower.toUpperCase()) + " "
				+ show("stra\u00dfe".toUpperCase()) + " " + show("\u00ff\u00b5".toUpperCase()) + " " + show("".toUpperCase()));
		print("toLowerCase", show("HELLO, World 42".toLowerCase()) + " " + show(upper.toLowerCase()) + " "
				+ show("\u00d7\u00f7\u00df".toLowerCase()) + " " + show("\u0178".toLowerCase()));
		String same = "already lower";
		print("case identity", (same.toLowerCase() == same) + " " + ("ABC".toUpperCase() == "ABC") + " "
				+ (same.toUpperCase() == same));
	}

	static void searching() {
		String s = "abcabc";
		print("indexOf char", s.indexOf('b') + " " + s.indexOf('b', 2) + " " + s.indexOf('b', -5) + " " + s.indexOf('b', 99)
				+ " " + s.indexOf('z') + " " + "".indexOf('a'));
		print("lastIndexOf char", s.lastIndexOf('b') + " " + s.lastIndexOf('b', 3) + " " + s.lastIndexOf('b', 99) + " "
				+ s.lastIndexOf('b', 0) + " " + s.lastIndexOf('b', -1) + " " + s.lastIndexOf('z'));
		print("indexOf String", s.indexOf("bc") + " " + s.indexOf("bc", 2) + " " + s.indexOf("bc", 5) + " " + s.indexOf("")
				+ " " + s.indexOf("", 3) + " " + s.indexOf("", 99) + " " + s.indexOf("", -3) + " " + s.indexOf("abcabcd") + " "
				+ "".indexOf(""));
		print("lastIndexOf String", s.lastIndexOf("bc") + " " + s.lastIndexOf("bc", 3) + " " + s.lastIndexOf("bc", 0) + " "
				+ s.lastIndexOf("") + " " + s.lastIndexOf("", 2) + " " + s.lastIndexOf("", -1) + " " + s.lastIndexOf("abc", 99)
				+ " " + s.lastIndexOf("x"));
		// A hex digit ("b") right after a surrogate escape (G44)
		String pair = "a\ud83d\ude00b";
		print("supplementary", pair.indexOf(0x1f600) + " " + pair.lastIndexOf(0x1f600) + " " + pair.indexOf(0x1f601) + " "
				+ pair.indexOf(0x110000) + " " + pair.indexOf(-1) + " " + pair.codePointAt(1) + " " + pair.codePointAt(2) + " "
				+ pair.codePointAt(0) + " " + "\ud83d".codePointAt(0));
		print("contains", s.contains("ca") + " " + s.contains("") + " " + s.contains("cb") + " "
				+ s.contains(new StringBuilder("bca")));
		print("regionMatches", s.regionMatches(3, "abc", 0, 3) + " " + s.regionMatches(4, "abc", 0, 3) + " "
				+ s.regionMatches(-1, "abc", 0, 1) + " " + s.regionMatches(0, "abc", -1, 1) + " " + s.regionMatches(0, "x", 0, 0)
				+ " " + s.regionMatches(0, "x", 0, -4) + " " + s.regionMatches(9, "x", 0, -4) + " "
				+ s.regionMatches(true, 0, "ABC", 0, 3) + " " + s.regionMatches(false, 0, "ABC", 0, 3) + " "
				+ "\u00c9t\u00e9".regionMatches(true, 0, "\u00e9T\u00c9", 0, 3) + " " + s.regionMatches(true, 5, "C", 0, 2));
		print("startsWith offset", s.startsWith("ca", 2) + " " + s.startsWith("", 6) + " " + s.startsWith("", 7) + " "
				+ s.startsWith("a", -1));
		print("contentEquals", s.contentEquals("abcabc") + " " + s.contentEquals(new StringBuilder("abcabc")) + " "
				+ s.contentEquals(new StringBuilder("abcab")) + " " + "".contentEquals(new StringBuilder()));
		try {
			s.codePointAt(6);
		} catch (StringIndexOutOfBoundsException e) {
			caught("codePointAt 6", e);
		}
		try {
			s.codePointAt(-1);
		} catch (StringIndexOutOfBoundsException e) {
			caught("codePointAt -1", e);
		}
	}

	static void transforming() {
		print("replace char", "banana".replace('a', 'o') + " " + "banana".replace('x', 'o') + " " + "".replace('a', 'b'));
		String b = "banana";
		print("replace identity", (b.replace('x', 'y') == b) + " " + (b.replace('a', 'a') == b));
		print("replace seq", "banana".replace("an", "AN") + " " + "aaaa".replace("aa", "b") + " " + "abc".replace("", "-")
				+ " " + "".replace("", "-") + " " + "abc".replace("x", "y") + " " + "abc".replace("b", "") + " "
				+ "abc".replace(new StringBuilder("c"), new StringBuilder("C!")));
		print("strip", show(" \t hi there \n ".strip()) + " " + show(" \t hi ".stripLeading()) + " "
				+ show(" hi \r\n".stripTrailing()) + " " + show("   ".strip()) + " " + show("".strip()) + " "
				+ show("\u00a0x\u00a0".strip()) + " " + show("\u001fx\u001c".strip()) + " " + show("\u0085x".strip()));
		print("trim vs strip", show("\u0001x\u0001".trim()) + " " + show("\u0001x\u0001".strip()));
		print("isBlank", "".isBlank() + " " + " \t\n".isBlank() + " " + " x ".isBlank() + " " + "\u00a0".isBlank());
		print("repeat", show("ab".repeat(3)) + " " + show("ab".repeat(0)) + " " + show("".repeat(5)) + " "
				+ show("x".repeat(1)));
		try {
			"x".repeat(-2);
		} catch (IllegalArgumentException e) {
			caught("repeat -2", e);
		}
		print("join", show(String.join(", ", "a", "b", "c")) + " " + show(String.join("-")) + " "
				+ show(String.join("", "x", "y")) + " " + show(String.join("/", "only")) + " "
				+ show(String.join(new StringBuilder("+"), new StringBuilder("p"), "q")));
		String[] parts = { "a", null, "c" };
		print("join null", show(String.join(":", parts)));
		char[] chars = { 'h', 'e', 'l', 'l', 'o' };
		print("valueOf range", show(String.valueOf(chars, 1, 3)) + " " + show(String.copyValueOf(chars)) + " "
				+ show(String.copyValueOf(chars, 2, 0)) + " " + show(String.copyValueOf(chars, 0, 5)));
		try {
			String.valueOf(chars, 3, 4);
		} catch (StringIndexOutOfBoundsException e) {
			caught("valueOf 3 4", e);
		}
		try {
			String.copyValueOf(chars, -1, 2);
		} catch (StringIndexOutOfBoundsException e) {
			caught("copyValueOf -1 2", e);
		}
	}

	static void splitting() {
		print("split comma", show("a,b,,c,,".split(",")));
		print("split limit 0", show("a,b,,c,,".split(",", 0)));
		print("split limit -1", show("a,b,,c,,".split(",", -1)));
		print("split limit 1", show("a,b,,c,,".split(",", 1)));
		print("split limit 2", show("a,b,,c,,".split(",", 2)));
		print("split limit 5", show("a,b,,c,,".split(",", 5)));
		print("split limit 9", show("a,b,,c,,".split(",", 9)));
		print("split leading", show(",a,b".split(",")));
		print("split none", show("abc".split(",")));
		print("split empty input", show("".split(",")));
		print("split only sep", show(",,,".split(",")));
		print("split only sep -1", show(",,,".split(",", -1)));
		print("split escaped dot", show("1.2.3".split("\\.")));
		print("split escaped pipe", show("x|y||".split("\\|", -1)));
		print("split space", show("the quick  brown".split(" ")));
		print("split literal word", show("oneANDtwoANDthree".split("AND")));
		print("split literal word limit", show("oneANDtwoANDthreeAND".split("AND", -1)));
		print("split comma space", show("a, b, c".split(", ")));
		print("split escaped multi", show("a\\.b..c".split("\\.\\.")));
		print("split overlapping", show("aaaaa".split("aa")));
		print("split empty regex", show("abc".split("")));
		print("split empty regex -1", show("abc".split("", -1)));
		print("split empty regex 2", show("abc".split("", 2)));
		print("split empty regex on empty", show("".split("")));
		print("split hash and bracket", show("a#b]c}d".split("#")) + " " + show("a]b".split("]")) + " "
				+ show("a}b".split("}")) + " " + show("a-b".split("-")));
	}

	static void builder() {
		StringBuilder sb = new StringBuilder("hello");
		print("append seq range", sb.append("_world_", 1, 6).toString() + " " + new StringBuilder().append((CharSequence) null, 1, 3)
				+ " " + new StringBuilder().append(new StringBuilder("abcdef"), 2, 4));
		char[] chars = { 'x', 'y', 'z' };
		print("append chars range", new StringBuilder("<").append(chars, 1, 2).append('>').toString() + " "
				+ new StringBuilder().append(chars, 0, 0).length());
		try {
			new StringBuilder().append("abc", 2, 1);
		} catch (IndexOutOfBoundsException e) {
			caught("append seq 2 1", e);
		}
		try {
			new StringBuilder().append("abc", 0, 4);
		} catch (IndexOutOfBoundsException e) {
			caught("append seq 0 4", e);
		}
		try {
			new StringBuilder().append(chars, 2, 2);
		} catch (IndexOutOfBoundsException e) {
			caught("append chars 2 2", e);
		}
		try {
			new StringBuilder().append(chars, -1, 1);
		} catch (IndexOutOfBoundsException e) {
			caught("append chars -1 1", e);
		}
		StringBuilder ins = new StringBuilder("[]");
		ins.insert(1, 42).insert(1, 7L).insert(1, true).insert(1, chars).insert(1, (Object) "obj").insert(1, (Object) null)
				.insert(0, 'c').insert(ins.length(), "end").insert(2, (String) null).insert(1, 1.5).insert(1, 2.5f)
				.insert(1, (CharSequence) new StringBuilder("cs")).insert(1, chars, 1, 2).insert(1, "abcdef", 2, 4);
		print("insert", ins);
		try {
			ins.insert(-1, 5);
		} catch (StringIndexOutOfBoundsException e) {
			caught("insert -1", e);
		}
		try {
			ins.insert(ins.length() + 1, "x");
		} catch (StringIndexOutOfBoundsException e) {
			caught("insert past end", e);
		}
		try {
			ins.insert(0, chars, 2, 5);
		} catch (StringIndexOutOfBoundsException e) {
			caught("insert chars range", e);
		}
		try {
			ins.insert(0, "abc", 2, 1);
		} catch (IndexOutOfBoundsException e) {
			caught("insert seq range", e);
		}
		StringBuilder del = new StringBuilder("0123456789");
		print("delete", del.delete(2, 4) + " " + del.delete(5, 99) + " " + del.delete(1, 1) + " " + del.delete(0, 0));
		try {
			del.delete(3, 2);
		} catch (StringIndexOutOfBoundsException e) {
			caught("delete 3 2", e);
		}
		try {
			del.delete(-1, 2);
		} catch (StringIndexOutOfBoundsException e) {
			caught("delete -1 2", e);
		}
		try {
			del.delete(9, 99);
		} catch (StringIndexOutOfBoundsException e) {
			caught("delete 9 99", e);
		}
		StringBuilder rep = new StringBuilder("0123456789");
		print("replace", rep.replace(2, 4, "ab") + " " + rep.replace(0, 1, "") + " " + rep.replace(5, 99, "XYZ") + " "
				+ rep.replace(0, 0, "<") + " " + rep.replace(rep.length(), rep.length(), ">"));
		try {
			rep.replace(4, 3, "x");
		} catch (StringIndexOutOfBoundsException e) {
			caught("replace 4 3", e);
		}
		try {
			rep.replace(99, 100, "x");
		} catch (StringIndexOutOfBoundsException e) {
			caught("replace 99 100", e);
		}
		StringBuilder find = new StringBuilder("abcabc");
		print("builder search", find.indexOf("bc") + " " + find.indexOf("bc", 2) + " " + find.indexOf("x", 0) + " "
				+ find.lastIndexOf("bc") + " " + find.lastIndexOf("bc", 3) + " " + find.lastIndexOf("") + " "
				+ find.indexOf("", 99) + " " + find.lastIndexOf("abc", -1));
		try {
			find.charAt(6);
		} catch (IndexOutOfBoundsException e) {
			caught("charAt 6", e);
		}
		try {
			find.charAt(-1);
		} catch (IndexOutOfBoundsException e) {
			caught("charAt -1", e);
		}
		try {
			find.setCharAt(6, 'x');
		} catch (IndexOutOfBoundsException e) {
			caught("setCharAt 6", e);
		}
		try {
			find.deleteCharAt(6);
		} catch (IndexOutOfBoundsException e) {
			caught("deleteCharAt 6", e);
		}
		find.setCharAt(0, 'A');
		print("setCharAt", find);
		StringBuilder cap = new StringBuilder();
		String caps = cap.capacity() + "";
		cap.append("12345678901234567");
		caps += " " + cap.capacity();
		cap.ensureCapacity(10);
		caps += " " + cap.capacity();
		cap.ensureCapacity(50);
		caps += " " + cap.capacity();
		cap.ensureCapacity(-5);
		caps += " " + cap.capacity();
		cap.ensureCapacity(120);
		caps += " " + cap.capacity();
		cap.trimToSize();
		caps += " " + cap.capacity() + " " + new StringBuilder("abc").capacity() + " " + new StringBuilder(3).capacity();
		StringBuilder empty = new StringBuilder(0);
		empty.trimToSize();
		empty.append('x');
		caps += " " + empty.capacity();
		StringBuilder grow = new StringBuilder(4);
		grow.setLength(5);
		caps += " " + grow.capacity();
		print("capacity", caps);
		print("compareTo", new StringBuilder("abc").compareTo(new StringBuilder("abd")) + " "
				+ new StringBuilder("abc").compareTo(new StringBuilder("ab")) + " "
				+ new StringBuilder("").compareTo(new StringBuilder("")) + " "
				+ new StringBuilder("b").compareTo(new StringBuilder("a")) + " " + CharSequence.compare("ab", new StringBuilder("ab"))
				+ " " + CharSequence.compare("a", "abc"));
		StringBuilder cp = new StringBuilder();
		cp.appendCodePoint('A').appendCodePoint(0x1f600).appendCodePoint(0xe9);
		print("appendCodePoint", show(cp.toString()) + " " + cp.length());
	}

	public static void main(String[] args) {
		cases();
		searching();
		transforming();
		splitting();
		builder();
	}
}
