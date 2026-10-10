// expect: compiles
package probes;
import java.util.*;
public class Misc {
	class Inner { static int count = 0; }
	static int localClasses() { record P(int x) {} enum E { A } interface I { int f(); } I i = () -> 3; return new P(1).x() + E.A.ordinal() + i.f(); }
	static String concat(Object o, int i, char c) { return "x" + o + i + c + 1.5 + true; }
	static int divide(int a, int b) { return a / b; }
	static int castNaN(double d) { return (int) d; }
	static long shift(long x, int s) { return x << s; }
	static int arrays() { int[][] grid = new int[3][4]; int[] xs = {1, 2}; return grid[2][3] + xs[1] + grid.length; }
	static String sync(Object lock) { synchronized (lock) { return "ok"; } }
	static Object anonDiamond() { return new ArrayList<>() {}; }
	static int multiCatch(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException | NullPointerException e) { return -1; } }
	static int labeled() { int n = 0; outer: for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) { if (j == 2) continue outer; if (i == 2) break outer; n++; } return n; }
	static Iterator<Integer> iterator() { return new Iterator<>() { int i; public boolean hasNext() { return i < 3; } public Integer next() { return i++; } }; }
	static String textBlockFormatted(String n) { return """
		Hello %s
		""".formatted(n); }
	@SuppressWarnings("unchecked") static <T> T[] genericArray(int n) { return (T[]) new Object[n]; }
}
