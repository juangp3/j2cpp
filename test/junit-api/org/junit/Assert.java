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
	public static void assertNotEquals(Object unexpected, Object actual) { throw new UnsupportedOperationException(); }
	public static void assertNotEquals(String message, long unexpected, long actual) { throw new UnsupportedOperationException(); }
	public static void assertArrayEquals(int[] expecteds, int[] actuals) { throw new UnsupportedOperationException(); }
	public static void assertArrayEquals(String message, int[] expecteds, int[] actuals) { throw new UnsupportedOperationException(); }
	public static void assertArrayEquals(Object[] expecteds, Object[] actuals) { throw new UnsupportedOperationException(); }
	public static void assertNull(Object object) { throw new UnsupportedOperationException(); }
	public static void assertNull(String message, Object object) { throw new UnsupportedOperationException(); }
	public static void assertNotNull(Object object) { throw new UnsupportedOperationException(); }
	public static void assertSame(String message, Object expected, Object actual) { throw new UnsupportedOperationException(); }
	public static void assertNotSame(Object unexpected, Object actual) { throw new UnsupportedOperationException(); }
	public static void fail() { throw new UnsupportedOperationException(); }
	public static void fail(String message) { throw new UnsupportedOperationException(); }
}
