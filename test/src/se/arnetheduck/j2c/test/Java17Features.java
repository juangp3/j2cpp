package se.arnetheduck.j2c.test;

import java.io.StringReader;

public class Java17Features {
	public enum Shape {
		CIRCLE, SQUARE, TRIANGLE
	}

	public static String banner() {
		return """
				J2C
				  "text block"
				""";
	}

	public static int corners(Shape shape) {
		return switch (shape) {
		case CIRCLE -> 0;
		case SQUARE -> 4;
		default -> {
			int sides = 3;
			yield sides;
		}
		};
	}

	public static int weekday(String day) {
		return switch (day) {
		case "sat", "sun" -> 0;
		case "mon" -> 1;
		default -> 2;
		};
	}

	public static int legacyStringSwitch(String command) {
		switch (command) {
		case "stop":
			return 0;
		case "go":
		case "run":
			return 1;
		default:
			return -1;
		}
	}

	public static int firstChar(String text) throws Exception {
		try (StringReader reader = new StringReader(text)) {
			return reader.read();
		}
	}

	public static int firstCharOfExisting(StringReader reader) throws Exception {
		try (reader) {
			return reader.read();
		} catch (RuntimeException e) {
			return -1;
		}
	}
}
