// Minimal stand-in for the JUnit class of the same name, so the test fixtures
// compile without downloading JUnit. Signatures match JUnit; translated tests
// never call these bodies, because J2C translates assertion calls itself.
package org.junit;

public class Assume {
	public static void assumeTrue(boolean b) { throw new UnsupportedOperationException(); }
}
