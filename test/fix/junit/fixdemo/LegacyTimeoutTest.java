package fixdemo;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// Quick fix fixture for J2C-TEST-TIMEOUT in JUnit 4's @Test(timeout = ...),
// checked with --tests against test/junit-api. Used by
// vscode/scripts/test-cli.js.
public class LegacyTimeoutTest {
	@Test(timeout = 1000)
	public void adds() {
		assertEquals(3, 1 + 2);
	}

	@Test(expected = ArithmeticException.class, timeout = 500)
	public void divides() {
		assertEquals(0, 1 / (1 - 1));
	}
}
