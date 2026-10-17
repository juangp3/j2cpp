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

	// A rethrow keeps the dynamic type (G46)
	public static void rethrow(Exception e) throws Exception {
		try {
			throw e;
		} catch (IllegalStateException s) {
			throw new IllegalArgumentException(s);
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

	// Names of C library macros from <cerrno> are renamed (G61, found while
	// writing the runtime's file natives)
	public static final int ENOENT = 2;

	public static int errnoNames(int errno) {
		int EINTR = errno + ENOENT;
		return EINTR;
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

	// JDK classes overriding interface methods with covariant returns that
	// include each other, such as LocalDate.atTime and
	// LocalDateTime.toLocalDate (found in commons-lang)
	public static String covariantTime() {
		java.time.LocalDateTime noon = java.time.LocalDate.now().atTime(
				java.time.LocalTime.NOON);
		java.time.LocalDate today = java.time.LocalDateTime.now().toLocalDate();
		java.time.LocalDateTime local = java.time.ZonedDateTime.now()
				.toLocalDateTime();
		return noon.toString() + today + local;
	}

	// Iface.super.m() calls that interface's default method (G33), also when
	// it is generic, inherited through another interface, or a JDK interface
	public interface Greeter {
		default String greet(String name) {
			return "hello " + name;
		}
	}

	public interface Polite extends Greeter {
	}

	public interface Echo<T> {
		default T echo(T value) {
			return value;
		}
	}

	public static class Greeting implements Polite, Echo<String>,
			java.util.Comparator<String> {
		@Override
		public String greet(String name) {
			return Polite.super.greet(name) + "!";
		}

		@Override
		public String echo(String value) {
			return Echo.super.echo(value) + value;
		}

		@Override
		public int compare(String a, String b) {
			return a.length() - b.length();
		}

		@Override
		public java.util.Comparator<String> reversed() {
			return java.util.Comparator.super.reversed();
		}
	}
}
