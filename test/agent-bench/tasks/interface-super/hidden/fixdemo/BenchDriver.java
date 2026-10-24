package fixdemo;

public class BenchDriver {
	public static void main(String[] args) {
		InterfaceSuper named = new InterfaceSuper();
		System.out.println(named.describe("a"));
		System.out.println(named.describer().apply("b"));
	}
}
