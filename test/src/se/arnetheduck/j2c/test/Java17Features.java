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

	public static int boxedSwitchExpression(Integer k) {
		return switch (k) {
		case 1 -> 10;
		default -> 20;
		};
	}

	public static int colonEnumExpression(Shape shape) {
		return switch (shape) {
		case CIRCLE:
		case SQUARE:
			yield 1;
		default: {
			int sides = 3;
			yield sides;
		}
		};
	}

	public static int colonStringExpression(String text) {
		return switch (text) {
		case "a":
			yield 1;
		default:
			yield 2;
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

	// Updating a box makes a new box (found in semver4j)
	private Integer boxedCount = 1;

	public int boxedUpdates(Integer a, Long b, Character c) {
		a += 2;
		b *= 3;
		c++;
		++a;
		Integer old = a++;
		this.boxedCount += a;
		a /= 2;
		return a + old + boxedCount + (int) (long) b + c;
	}

	// A constructor parameter called name, like the implicit enum parameter
	// (found in commons-codec)
	public enum Named {
		FIRST("first"), SECOND("second");

		private final String name;

		Named(final String name) {
			this.name = name;
		}

		public String label() {
			return name;
		}
	}

	// A cast from Object to a primitive checks the box, then unboxes (found
	// in zxing). This package declares its own Object class.
	public static int unboxCast(java.lang.Object value) {
		return (int) value;
	}

	// A conditional mixing String and int has an intersection type, which
	// C++ cannot name (found in zxing)
	public static String conditionalMix(Integer value) {
		return "[" + (value == null ? "none" : value.intValue()) + "]";
	}
}
