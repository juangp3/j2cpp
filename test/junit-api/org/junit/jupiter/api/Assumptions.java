// Minimal stand-in for the JUnit class of the same name, so the test fixtures
// compile without downloading JUnit. Signatures match JUnit; translated tests
// never call these bodies, because J2C translates assertion calls itself.
package org.junit.jupiter.api;

public final class Assumptions {
	private Assumptions() {
	}

	public static void assumeTrue(boolean assumption) { throw new UnsupportedOperationException(); }
	public static void assumeTrue(boolean assumption, String message) { throw new UnsupportedOperationException(); }
	public static void assumeFalse(boolean assumption) { throw new UnsupportedOperationException(); }
}
