package se.arnetheduck.j2c.test;

public class Java17ControlFlow {
	public static int patternLength(java.lang.Object value) {
		if (value instanceof String text) {
			return text.length();
		}
		return 0;
	}

	public static int switchValue(int value) {
		return switch (value) {
		case 0 -> 1;
		case 1, 2 -> 3;
		default -> 4;
		};
	}
}
