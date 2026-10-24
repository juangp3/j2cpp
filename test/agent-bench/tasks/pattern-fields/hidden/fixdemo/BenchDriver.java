package fixdemo;

public class BenchDriver {
	public static void main(String[] args) {
		System.out.println(PatternFields.EMPTY);
		System.out.println(new PatternFields("abc").length);
		System.out.println(new PatternFields(5).length);
		System.out.println(PatternFields.Limits.MAX_VALUE);
	}
}
