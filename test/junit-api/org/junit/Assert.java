// Minimal stand-in for the JUnit class of the same name, so the test fixtures
// compile without downloading JUnit. Signatures match JUnit; translated tests
// never call these bodies, because J2C translates assertion calls itself.
package org.junit;

public class Assert {
	protected Assert() {
	}

	public static void assertTrue(boolean condition) { throw new UnsupportedOperationException(); }
	public static void assertTrue(String message, boolean condition) { throw new UnsupportedOperationException(); }
	public static void assertEquals(long expected, long actual) { throw new UnsupportedOperationException(); }
	public static void assertEquals(String message, long expected, long actual) { throw new UnsupportedOperationException(); }
	@Deprecated
	public static void assertEquals(double expected, double actual) { throw new UnsupportedOperationException(); }
	public static void assertEquals(double expected, double actual, double delta) { throw new UnsupportedOperationException(); }
	public static void assertEquals(Object expected, Object actual) { throw new UnsupportedOperationException(); }
	public static void assertEquals(String message, Object expected, Object actual) { throw new UnsupportedOperationException(); }
	public static void assertNull(Object object) { throw new UnsupportedOperationException(); }
	public static void fail() { throw new UnsupportedOperationException(); }
	public static void fail(String message) { throw new UnsupportedOperationException(); }
}
