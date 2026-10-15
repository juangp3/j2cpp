// Minimal stand-in for the JUnit class of the same name, so the test fixtures
// compile without downloading JUnit. Signatures match JUnit; translated tests
// never call these bodies, because J2C translates assertion calls itself.
package org.junit.jupiter.api;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import org.junit.jupiter.api.function.Executable;

public final class Assertions {
	private Assertions() {
	}

	private static RuntimeException standIn() {
		return new UnsupportedOperationException("JUnit stand-in");
	}

	public static void assertTrue(boolean condition) { throw standIn(); }
	public static void assertTrue(boolean condition, String message) { throw standIn(); }
	public static void assertTrue(boolean condition, Supplier<String> messageSupplier) { throw standIn(); }
	public static void assertTrue(BooleanSupplier condition) { throw standIn(); }
	public static void assertFalse(boolean condition) { throw standIn(); }
	public static void assertFalse(boolean condition, String message) { throw standIn(); }
	public static void assertNull(Object actual) { throw standIn(); }
	public static void assertNull(Object actual, String message) { throw standIn(); }
	public static void assertNotNull(Object actual) { throw standIn(); }
	public static void assertNotNull(Object actual, String message) { throw standIn(); }
	public static void assertEquals(int expected, int actual) { throw standIn(); }
	public static void assertEquals(int expected, int actual, String message) { throw standIn(); }
	public static void assertEquals(int expected, int actual, Supplier<String> messageSupplier) { throw standIn(); }
	public static void assertEquals(Integer expected, Integer actual) { throw standIn(); }
	public static void assertEquals(long expected, long actual) { throw standIn(); }
	public static void assertEquals(char expected, char actual) { throw standIn(); }
	public static void assertEquals(double expected, double actual) { throw standIn(); }
	public static void assertEquals(double expected, double actual, double delta) { throw standIn(); }
	public static void assertEquals(double expected, double actual, String message) { throw standIn(); }
	public static void assertEquals(Object expected, Object actual) { throw standIn(); }
	public static void assertEquals(Object expected, Object actual, String message) { throw standIn(); }
	public static void assertNotEquals(int unexpected, int actual) { throw standIn(); }
	public static void assertNotEquals(double unexpected, double actual) { throw standIn(); }
	public static void assertNotEquals(Object unexpected, Object actual) { throw standIn(); }
	public static void assertNotEquals(Object unexpected, Object actual, String message) { throw standIn(); }
	public static void assertSame(Object expected, Object actual) { throw standIn(); }
	public static void assertNotSame(Object unexpected, Object actual) { throw standIn(); }
	public static void assertArrayEquals(int[] expected, int[] actual) { throw standIn(); }
	public static void assertArrayEquals(double[] expected, double[] actual, double delta) { throw standIn(); }
	public static void assertArrayEquals(Object[] expected, Object[] actual) { throw standIn(); }
	public static <V> V fail() { throw standIn(); }
	public static <V> V fail(String message) { throw standIn(); }
	public static <V> V fail(Supplier<String> messageSupplier) { throw standIn(); }
	public static <T extends Throwable> T assertThrows(Class<T> expectedType, Executable executable) { throw standIn(); }
	public static void assertDoesNotThrow(Executable executable) { throw standIn(); }
	public static void assertAll(Executable... executables) { throw standIn(); }
}
