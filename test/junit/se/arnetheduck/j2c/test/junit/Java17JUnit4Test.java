package se.arnetheduck.j2c.test.junit;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

// JUnit 4 lifecycle and expected exceptions
public class Java17JUnit4Test {
	static boolean classSetUp;
	int value;

	@BeforeClass
	public static void setUpClass() {
		classSetUp = true;
	}

	@Before
	public void setUp() {
		value = 7;
	}

	@After
	public void tearDown() {
		value = 0;
	}

	@Test
	public void lifecycleRan() {
		if (!classSetUp || value != 7) {
			throw new IllegalStateException();
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void expectedThrown() {
		throw new IllegalArgumentException();
	}

	// A subclass of the expected exception also passes
	@Test(expected = RuntimeException.class)
	public void expectedSubclassThrown() {
		throw new IllegalArgumentException();
	}

	@Test(expected = IllegalArgumentException.class)
	public void expectedMissing() {
	}

	@Test(expected = IllegalArgumentException.class)
	public void wrongException() {
		throw new IllegalStateException();
	}

	@Ignore
	@Test
	public void ignored() {
		throw new IllegalStateException();
	}
}
