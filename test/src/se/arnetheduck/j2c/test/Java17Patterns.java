package se.arnetheduck.j2c.test;

// instanceof patterns in every position; see Java17PatternsSmoke.cpp
public class Java17Patterns {
	public static class Shape {
	}

	public static class Circle extends Shape {
		public final int r;

		public Circle(int r) {
			this.r = r;
		}
	}

	public static class Square extends Shape {
		public final int side;

		public Square(int side) {
			this.side = side;
		}
	}

	public static class Counter {
		public final int value;

		public Counter(int value) {
			this.value = value;
		}

		public Counter next() {
			return value > 0 ? new Counter(value - 1) : null;
		}
	}

	public static int and(Shape s) {
		return s instanceof Circle c && c.r > 2 ? c.r : 0;
	}

	// The pattern variable stays in scope after the if
	public static int negated(Shape s) {
		if (!(s instanceof Square q)) {
			return -1;
		}
		return q.side;
	}

	public static int loop(Counter start) {
		int n = 0;
		java.lang.Object o = start;
		while (o instanceof Counter c && c.value > 0) {
			n++;
			o = c.next();
		}
		return n;
	}

	public static int ternary(Shape s) {
		return s instanceof Circle c ? c.r : s instanceof Square q ? q.side * 10 : -1;
	}

	// Two sibling statements bind the same name
	public static int siblings(Shape a, Shape b) {
		int r = 0;
		if (a instanceof Circle c) {
			r += c.r;
		}
		if (b instanceof Circle c) {
			r += c.r * 100;
		}
		return r;
	}

	public static int elseIf(Shape s) {
		if (s == null)
			return -2;
		else if (s instanceof Square q)
			return q.side;
		else
			return 0;
	}

	public static int unbraced(Shape s, boolean flag) {
		if (flag)
			return s instanceof Circle c ? c.r : 0;
		return -1;
	}

	public static int inSwitch(int k, Shape s) {
		switch (k) {
		case 1:
			if (!(s instanceof Circle c)) {
				return -1;
			}
			return c.r;
		default:
			return 0;
		}
	}

	public static int nullOperand() {
		Shape s = null;
		return s instanceof Circle c ? c.r : 7;
	}
}
