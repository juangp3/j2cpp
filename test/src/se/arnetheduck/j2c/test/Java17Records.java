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
}
