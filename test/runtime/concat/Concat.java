// G49: operands before the first String in a concatenation are added as
// numbers, left to right with Java's promotions; the rest are appended
public class Concat {
	public static void main(String[] args) {
		int i = 1, j = 2;
		long l = 3000000000L;
		char c = 'x';
		short s = 4;
		byte b = 5;
		float f = 0.25f;
		Integer boxed = 40;
		System.out.println(i + j + " ints");
		System.out.println(1.5 + i + " mixed");
		System.out.println(c + i + " char");
		System.out.println(c + c + " chars");
		System.out.println("a" + i + j);
		System.out.println(i + j + "b" + i + j);
		System.out.println(i + l + " long");
		System.out.println(Integer.MAX_VALUE + i + l + " wraps first");
		System.out.println(s + b + " short byte");
		System.out.println(f + i + j + " float");
		System.out.println(boxed + i + " boxed");
		System.out.println(c + " alone");
		String t = "t";
		t += i + j;
		System.out.println(t);
		t = i + j + t;
		System.out.println(t);
		Object o = null;
		System.out.println(o + " null");
		System.out.println(1 + 2 + "" + 1 + 2);
	}
}
