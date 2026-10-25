package calc;

public class Calc {
	public static int gcd(int a, int b) {
		if (a == 0) {
			return 0;
		}
		a = Math.abs(a);
		b = Math.abs(b);
		while (b != 0) {
			int r = a % b;
			a = b;
			b = r;
		}
		return a;
	}

	public static int parsePositive(String text) {
		int value = Integer.parseInt(text.trim());
		if (value <= 0) {
			throw new IllegalArgumentException("not positive: " + value);
		}
		return value;
	}
}
