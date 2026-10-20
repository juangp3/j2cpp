package builddemo;

/**
 * Built by test-cli.js with j2c build, after it breaks the C++ of the
 * statement on line 12, which must then be reported at that line
 */
public class Counter {
	private int count = 40;

	public int next() {
		int step = 2;
		count += step;
		return count;
	}

	public static int twice(int value) {
		return value * 2;
	}
}
