// expect: compiles
package probes;
public class Patterns {
	static int and(Object o) { return o instanceof String s && s.length() > 2 ? s.length() : 0; }
	static int negated(Object o) { if (!(o instanceof String s)) { return -1; } return s.length(); }
	static int loop(Object o) { int n = 0; while (o instanceof Integer i && i > 0) { n++; o = i - 1; } return n; }
	static String ternary(Object o) { return o instanceof Integer i ? "int" + i : "other"; }
}
