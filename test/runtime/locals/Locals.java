import java.util.function.Function;
import java.util.function.Supplier;

// G34: a local class created from another class (an anonymous class, a
// lambda, another local class) or extended by one gets its captured
// variables and outer instance from that class
public class Locals {
	private final String tag;
	private int field = 7;

	Locals(String tag) {
		this.tag = tag;
	}

	interface Named {
		String name();
	}

	String run(final int base, final String suffix) {
		class Counter implements Named {
			final int start;

			Counter(int start) {
				this.start = start;
			}

			public String name() {
				return tag + ":" + (base + start) + suffix;
			}
		}
		class Pair implements Named {
			public String name() {
				// A local class created from another local class
				return new Counter(1).name() + "&" + new Counter(2).name();
			}
		}
		// From an anonymous class
		Named anon = new Named() {
			public String name() {
				return new Counter(10).name();
			}
		};
		// From a lambda
		Supplier<Named> lambda = () -> new Counter(20);
		// From an anonymous class inside an anonymous class
		Named nested = new Named() {
			public String name() {
				Supplier<String> s = new Supplier<String>() {
					public String get() {
						return new Pair().name();
					}
				};
				return s.get();
			}
		};
		return new Counter(0).name() + " " + new Pair().name() + " " + anon.name() + " "
				+ lambda.get().name() + " " + nested.name();
	}

	String extend(final int base) {
		final String name = "n";
		class Counter {
			int value() {
				return base + field;
			}
		}
		// Subclasses pass the captured variables to the local superclass
		class Sub extends Counter {
			@Override
			int value() {
				return super.value() * 2 + name.length();
			}
		}
		class Box<T> {
			T item;

			Box(T item) {
				this.item = item;
			}

			String show() {
				return name + item + base;
			}
		}
		Counter anonSub = new Counter() {
			@Override
			int value() {
				return super.value() + 1000;
			}
		};
		Function<String, String> f = s -> new Box<String>(s).show();
		Supplier<Supplier<Integer>> twice = () -> () -> new Sub().value();
		Supplier<String> inLambda = () -> {
			class InLambda {
				String get() {
					return name + base;
				}
			}
			Supplier<String> inner = () -> new InLambda().get();
			return inner.get();
		};
		return new Sub().value() + " " + anonSub.value() + " " + f.apply("x") + " "
				+ twice.get().get() + " " + inLambda.get();
	}

	static String staticRun(final int x) {
		class Holder {
			int get() {
				return x * 2;
			}
		}
		Supplier<Integer> s = () -> new Holder().get();
		Runnable r = new Runnable() {
			public void run() {
				System.out.println("holder " + new Holder().get());
			}
		};
		r.run();
		return "static " + s.get();
	}

	public static void main(String[] args) {
		Locals locals = new Locals("t");
		System.out.println(locals.run(100, "!"));
		System.out.println(locals.extend(3));
		System.out.println(staticRun(21));
	}
}
