package se.arnetheduck.j2c.test.junit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

// JUnit 5 assertions translated without a Java runtime; the expected outcome
// of each test is in ../../../../../expected-results.json
class Java17AssertionsTest {
	static final class Point {
		final int x;
		final int y;

		Point(int x, int y) {
			this.x = x;
			this.y = y;
		}

		@Override
		public boolean equals(java.lang.Object other) {
			return other instanceof Point p && p.x == x && p.y == y;
		}

		@Override
		public int hashCode() {
			return 31 * x + y;
		}
	}

	@Test
	void primitivesPass() {
		assertEquals(4, 2 + 2);
		assertEquals(5L, 5);
		assertEquals('a', 'a');
		assertTrue(true, "message");
		assertFalse(false);
		assertNotEquals(1, 2);
	}

	@Test
	void primitiveMismatchFails() {
		assertEquals(4, 2 + 3);
	}

	@Test
	void doublesWithinDelta() {
		assertEquals(0.3, 0.1 + 0.2, 1e-9);
	}

	// JUnit compares doubles by bits: NaN equals NaN, 0.0 differs from -0.0
	@Test
	void doubleSpecialValues() {
		assertEquals(0.0 / 0.0, 0.0 / 0.0);
		assertNotEquals(0.0, -0.0);
	}

	@Test
	void nullsAndIdentity() {
		assertNull(null);
		assertNotNull(this);
		assertSame(this, this);
		assertNotSame(new Point(1, 2), new Point(1, 2));
	}

	@Test
	void objectsUseEquals() {
		assertEquals(new Point(1, 2), new Point(1, 2));
	}

	@Test
	void objectMismatchFails() {
		assertEquals(new Point(1, 2), new Point(2, 2));
	}

	@Test
	void arraysEqual() {
		assertArrayEquals(new int[] { 1, 2 }, new int[] { 1, 2 });
		assertArrayEquals(new double[] { 0.3 }, new double[] { 0.1 + 0.2 }, 1e-9);
		assertArrayEquals(new Point[] { new Point(1, 1) }, new Point[] { new Point(1, 1) });
	}

	@Test
	void arrayMismatchFails() {
		assertArrayEquals(new int[] { 1, 2 }, new int[] { 1, 3 });
	}

	@Test
	void assertThrowsReturnsException() {
		IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
			throw new IllegalArgumentException();
		});
		assertNotNull(thrown);
	}

	@Test
	void assertThrowsWrongTypeFails() {
		assertThrows(IllegalArgumentException.class, () -> {
			throw new IllegalStateException();
		});
	}

	@Test
	void assertThrowsNothingFails() {
		assertThrows(IllegalArgumentException.class, () -> {
		});
	}

	@Test
	void doesNotThrow() {
		assertDoesNotThrow(() -> {
		});
	}

	@Test
	void failFails() {
		fail("explicit failure");
	}

	@Test
	void assumptionSkips() {
		assumeTrue(false);
		fail("not reached");
	}

	// Assertion failures are Java AssertionErrors, so Java code can catch them
	@Test
	void assertionErrorIsCatchable() {
		try {
			assertTrue(false);
		} catch (AssertionError expected) {
			return;
		}
		fail("AssertionError was not thrown");
	}
}
