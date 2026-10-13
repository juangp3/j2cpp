// G33: Iface.super.m() calls the named interface's default method
public class Supers {
	interface Greeter {
		default String greet(String name) {
			return "hello " + name;
		}
	}

	interface Loud extends Greeter {
	}

	interface Box<T> {
		default T pick(T a, T b) {
			return a;
		}

		default int size() {
			return 1;
		}
	}

	interface Other {
		default String greet(String name) {
			return "other " + name;
		}
	}

	static class Both implements Greeter, Other {
		@Override
		public String greet(String name) {
			return Greeter.super.greet(name) + "/" + Other.super.greet(name);
		}
	}

	static class ViaLoud implements Loud {
		@Override
		public String greet(String name) {
			return Loud.super.greet(name) + "!";
		}
	}

	static class Boxed implements Box<String> {
		@Override
		public String pick(String a, String b) {
			return Box.super.pick(b, a) + Box.super.size();
		}

		@Override
		public int size() {
			return Box.super.size() + 1;
		}
	}

	static class Base {
		String who() {
			return "base";
		}
	}

	class Outer extends Base {
		@Override
		String who() {
			return "outer";
		}

		class In {
			String ask() {
				return Outer.super.who() + " " + Outer.this.who();
			}
		}
	}

	public static void main(String[] args) {
		System.out.println(new Both().greet("a"));
		System.out.println(new ViaLoud().greet("b"));
		Boxed boxed = new Boxed();
		System.out.println(boxed.pick("x", "y"));
		System.out.println(boxed.size());
		Box<String> box = boxed;
		System.out.println(box.pick("p", "q"));
		System.out.println(new Supers().new Outer().new In().ask());
		Runnable r = new Runnable() {
			@Override
			public void run() {
				System.out.println(new Both().greet("d"));
			}
		};
		r.run();
	}
}
