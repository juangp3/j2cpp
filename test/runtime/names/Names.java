// G47: Class.getName() returns binary names (Outer$Inner, Outer$1,
// Outer$1Local, [LOuter$Inner;), as does the uncaught-exception printout
public class Names {
	static class Inner {
		class Deeper {
		}
	}

	interface Shape {
	}

	static class Oops extends RuntimeException {
		Oops(String message) {
			super(message);
		}
	}

	static void show(Object o) {
		Class<?> c = o.getClass();
		System.out.println(c.getName() + " " + c.getSimpleName() + " " + c);
	}

	static Object local() {
		class Local {
		}
		return new Local();
	}

	static Object otherLocal() {
		class Local {
		}
		return new Local();
	}

	public static void main(String[] args) {
		show(new Names());
		show(new Inner());
		show(new Inner().new Deeper());
		show(new Object() {
		});
		show(new Shape() {
		});
		show(local());
		show(otherLocal());
		show(new Inner[0]);
		show(new Inner[0][0]);
		show(new Inner.Deeper[1]);
		show(new int[0]);
		show(new int[0][0]);
		show(new long[0]);
		show(new boolean[0]);
		show(new char[0]);
		show(new byte[0]);
		show(new short[0]);
		show(new float[0]);
		show(new double[0]);
		show(new String[0]);
		show(new Object[0]);
		show(new Object[0][0]);
		show("text");
		System.out.println(Inner.class.getName());
		System.out.println(Inner.Deeper.class.getName());
		System.out.println(new Inner().getClass() == Inner.class);
		System.out.println(new Inner[2].getClass() == new Inner[3].getClass());
		try {
			throw new Oops("caught");
		} catch (Oops e) {
			System.out.println(e);
		}
		throw new Oops("uncaught");
	}
}
