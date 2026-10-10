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

	public static int switchRule(int value) {
		int result = 0;
		switch (value) {
		case 0 -> result = 10;
		case 1, 2 -> {
			result = 20;
		}
		default -> result = 30;
		}
		return result + 1;
	}

	public enum Light {
		RED, AMBER, GREEN
	}

	public static int enumSwitchRule(Light light) {
		int result = 0;
		switch (light) {
		case RED -> result = 1;
		case AMBER, GREEN -> result = 2;
		}
		return result + 10;
	}
}
