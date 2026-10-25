package text;

public class BenchDriver {
	public static void main(String[] args) {
		System.out.println(Wrap.lines("the quick brown fox jumps over the lazy dog", 10));
		System.out.println(Wrap.wrapper(4).apply("a bb ccc dddd eeeee"));
		System.out.println(Wrap.describe("abc") + ", " + Wrap.describe(null) + ", " + Wrap.describe(3) + ", " + Wrap.describe(-3));
	}
}
