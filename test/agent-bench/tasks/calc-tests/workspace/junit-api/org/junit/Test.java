// Minimal stand-in for the JUnit annotation of the same name, so the test
// fixtures compile without downloading JUnit. Only what J2C reads is declared.
package org.junit;

public @interface Test {
	Class<? extends Throwable> expected() default None.class;

	long timeout() default 0L;

	class None extends Throwable {
		private static final long serialVersionUID = 1L;

		private None() {
		}
	}
}
