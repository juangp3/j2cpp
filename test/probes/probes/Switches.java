// expect: compiles
package probes;
public class Switches {
	enum Day { MON, TUE, SAT }
	static int colonYield(int k) { return switch (k) { case 1: yield 10; case 2: case 3: yield 20; default: { int x = k * 2; yield x; } }; }
	static String nestedSwitch(Day d, int k) { return switch (d) { case SAT -> "weekend"; default -> switch (k) { case 0 -> "zero"; default -> "n"; }; }; }
	static int stringStatementArrow(String s) { int r = 0; switch (s) { case "a" -> r = 1; case "b", "c" -> { r = 2; } default -> r = 3; } return r; }
	static int enumStatementBreak(Day d) { int r = 0; switch (d) { case MON: r = 1; break; case TUE: r = 2; default: r += 5; } return r; }
	static int boxed(Integer k) { switch (k) { case 1: return 1; default: return 0; } }
	static int chars(char c) { return switch (c) { case 'a', 'e' -> 1; default -> 0; }; }
}
