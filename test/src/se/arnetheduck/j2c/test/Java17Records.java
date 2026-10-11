package se.arnetheduck.j2c.test;

record Java17User(int id, String name) {
}

record Java17Floating(float single, double wide) {
}

record Java17Array(int[] values) {
}

record Java17Validated(int age) {
	Java17Validated {
		if (age < 0) {
			throw new IllegalArgumentException();
		}
	}
}

record Java17Named(String name) {
	Java17Named(String name) {
		this.name = name.trim();
	}

	@Override
	public String toString() {
		return name;
	}
}

record Java17Empty() {
}

record Java17Overloaded(int value) {
	Java17Overloaded(long value) {
		this((int) value);
	}
}

public class Java17Records {
	public static Java17User createUser(int id, String name) {
		return new Java17User(id, name);
	}

	public static int componentValues(Java17User user) {
		return user.id() + user.name().length();
	}

	public static boolean sameUser(Java17User left, Java17User right) {
		return left.equals(right);
	}

	// Object and array components: equals and hashCode need their headers
	public record Tagged(java.util.List<String> tags, int[] counts) {
	}

	// Accessors returning a nested type are defined outside the class
	public sealed interface Expr permits Num, Add {
	}

	public record Num(int value) implements Expr {
	}

	public record Add(Expr left, Expr right) implements Expr {
	}

	public static int eval(Expr expr) {
		if (expr instanceof Num n) {
			return n.value();
		}
		if (expr instanceof Add a) {
			return eval(a.left()) + eval(a.right());
		}
		return 0;
	}

	public static int sum() {
		return eval(new Add(new Num(2), new Add(new Num(3), new Num(4))));
	}

	// Enum constants with bodies created with constructor arguments: the
	// anonymous constant classes forward to the matching private enum
	// constructor, including overloads and a null argument
	public static class Limits<T> {
		public int size;

		public Limits(int size) {
			this.size = size;
		}
	}

	public enum Tier {
		BASIC(new Limits<String>(4), new Limits<String>(1), 2),
		LIMITED(new Limits<String>(3), null, 5) {
			@Override
			public int bonus() {
				return 7;
			}
		},
		CUSTOM(new Limits<String>(6), new Limits<String>(2), 1, 30) {
			@Override
			public int bonus() {
				return extra + side();
			}
		};

		private final Limits<String> main;
		private final Limits<String> side;
		private final int copies;
		protected final int extra;

		Tier(Limits<String> main0, Limits<String> side0, int copies0,
				int extra0) {
			main = main0;
			side = side0;
			copies = copies0;
			extra = extra0;
		}

		Tier(Limits<String> main0, Limits<String> side0, int copies0) {
			this(main0, side0, copies0, 0);
		}

		public int side() {
			return side == null ? -1 : side.size;
		}

		public int bonus() {
			return main.size * copies;
		}
	}

	public static int tierScore() {
		// 8 + 7 * 10 + 32 * 100 - 1000 == 2278
		return Tier.BASIC.bonus() + Tier.LIMITED.bonus() * 10
				+ Tier.CUSTOM.bonus() * 100 + Tier.LIMITED.side() * 1000;
	}
}
