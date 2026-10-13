// G50: an interface default method overriding another default
public class Defaults {
	interface A {
		default String m() {
			return "A";
		}
	}

	interface B extends A {
		@Override
		default String m() {
			return "B";
		}
	}

	interface Other {
		default String m() {
			return "Other";
		}
	}

	// Two parents' defaults, resolved in the interface
	interface Child extends A, Other {
		@Override
		default String m() {
			return A.super.m() + "+" + Other.super.m();
		}
	}

	static class C implements B {
	}

	static class G implements A {
		@Override
		public String m() {
			return "G";
		}
	}

	// A class method wins over a default
	static class F extends G implements B {
	}

	static class G2 implements A {
	}

	// The more specific default wins
	static class H extends G2 implements B {
	}

	static class K implements Child {
	}

	static class L extends C implements A {
	}

	public static void main(String[] args) {
		System.out.println(new C().m());
		System.out.println(new F().m());
		System.out.println(new H().m());
		System.out.println(new K().m());
		System.out.println(new L().m());
		A a = new H();
		System.out.println(a.m());
		B b = new F();
		System.out.println(b.m());
	}
}
