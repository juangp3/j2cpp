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
}
