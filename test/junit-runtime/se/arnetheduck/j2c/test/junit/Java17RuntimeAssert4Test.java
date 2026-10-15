package se.arnetheduck.j2c.test.junit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

// JUnit 4 assertions on JDK objects, with JUnit 4's own failure messages:
// "expected:<a> but was:<b>", and two strings compacted around their
// difference, as ComparisonFailure does
public class Java17RuntimeAssert4Test {
	static String joined(String first, String second) {
		return new StringBuilder(first).append(second).toString();
	}

	@Test
	public void stringsUseEquals() {
		assertEquals("ab", joined("a", "b"));
		assertEquals(List.of(1, 2), List.of(1, 2));
	}

	@Test
	public void stringMismatchIsCompacted() {
		assertEquals("abcdef", joined("abX", "def"));
	}

	@Test
	public void stringMismatchWithMessage() {
		assertEquals("name", "a", "b");
	}

	@Test
	public void longContextIsShortened() {
		assertEquals("0123456789012345678901234x", "0123456789012345678901234y");
	}

	@Test
	public void longMismatchFails() {
		assertEquals(1, 2);
	}

	@Test
	public void listMismatchFails() {
		assertEquals(List.of(1), List.of(2));
	}

	@Test
	public void sameTextDifferentClass() {
		assertEquals(Integer.valueOf(1), Long.valueOf(1));
	}

	@Test
	public void nullFails() {
		assertNull("note", "x");
	}

	@Test
	public void notNullFailsWithoutMessage() {
		assertNotNull(null);
	}

	@Test
	public void notEqualsFails() {
		assertNotEquals(joined("sa", "me"), "same");
	}

	@Test
	public void notEqualsWithMessage() {
		assertNotEquals("check", 5L, 5L);
	}

	@Test
	public void sameFails() {
		assertSame("identity", "x", joined("x", ""));
	}

	@Test
	public void notSameFails() {
		assertNotSame("x", "x");
	}

	@Test
	public void arrayMismatchFails() {
		assertArrayEquals(new int[] { 1, 2 }, new int[] { 1, 3 });
	}

	@Test
	public void arrayLengthsDiffer() {
		assertArrayEquals("lengths", new int[] { 1 }, new int[] { 1, 2 });
	}

	@Test
	public void stringArrayIsCompacted() {
		assertArrayEquals(new String[] { "abc" }, new String[] { "abd" });
	}

	@Test
	public void doubleDeltaFails() {
		assertEquals(0.5, 0.25, 0.1);
	}

	@Test
	public void failWithoutMessage() {
		fail();
	}
}
