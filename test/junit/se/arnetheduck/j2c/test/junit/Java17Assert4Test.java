package se.arnetheduck.j2c.test.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Assume;
import org.junit.Test;

// JUnit 4 assertions take an optional message first
public class Java17Assert4Test {
	@Test
	public void messageFirstPasses() {
		assertEquals("lengths", 3L, 3L);
		assertTrue("always", true);
		assertEquals(1.0, 1.0 + 1e-12, 1e-9);
	}

	@Test
	public void messageFirstFails() {
		assertTrue("condition", false);
	}

	// JUnit 4's assertEquals(double, double) without a delta always fails
	@Test
	@SuppressWarnings("deprecation")
	public void doubleWithoutDeltaFails() {
		assertEquals(1.0, 1.0);
	}

	@Test
	public void assumptionSkips() {
		Assume.assumeTrue(false);
		throw new IllegalStateException();
	}
}
