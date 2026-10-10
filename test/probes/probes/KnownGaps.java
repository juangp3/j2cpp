// expect: rejected
package probes;

// Forms J2C reports instead of translating (see docs/PUBLISHING_PLAN.md)
public class KnownGaps {
	// A pattern variable outside a method body has no statement to precede
	static final boolean EMPTY = ((java.lang.Object) "") instanceof String s && s.isEmpty();

	// "this" in a lambda inside an anonymous class cannot be named
	static Runnable anonymousThis() {
		return new Runnable() {
			public void run() {
				Runnable inner = () -> System.out.println(this);
				inner.run();
			}
		};
	}
}
