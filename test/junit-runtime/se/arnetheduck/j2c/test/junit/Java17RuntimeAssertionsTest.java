package se.arnetheduck.j2c.test.junit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

// JUnit 5 assertions on JDK objects, which need the J2C runtime (--runtime)
// for Java's equals and toString. The expected outcome and failure message of
// each test are in ../../../../../expected-results.json; real JUnit on the
// JVM produces the same (npm run junit:jvm).
class Java17RuntimeAssertionsTest {
	// A new String, so equality cannot come from interned literals
	static String joined(String first, String second) {
		return new StringBuilder(first).append(second).toString();
	}

	@Test
	void stringsUseEquals() {
		String ab = joined("a", "b");
		assertEquals("ab", ab);
		assertNotSame("ab", ab);
	}

	@Test
	void stringMismatchFails() {
		assertEquals("expected", joined("act", "ual"));
	}

	@Test
	void messagePrefixesFailure() {
		assertEquals("a", "b", "names");
	}

	@Test
	void blankMessageHasNoPrefix() {
		assertTrue(false, " ");
	}

	@Test
	void supplierMessage() {
		int answer = 42;
		assertEquals(1, 2, () -> "lazy " + answer);
	}

	// JUnit calls a message supplier only when the assertion fails
	@Test
	void supplierNotCalledOnSuccess() {
		assertTrue(true, () -> {
			throw new IllegalStateException("evaluated");
		});
	}

	@Test
	void boxedNumbersUseEquals() {
		assertEquals(Integer.valueOf(1000), Integer.valueOf(1000));
		assertNotEquals(Long.valueOf(1), Integer.valueOf(1));
	}

	@Test
	void boxedMismatchFails() {
		assertEquals(Integer.valueOf(1000), Integer.valueOf(1001));
	}

	@Test
	void listsUseEquals() {
		List<Integer> list = new ArrayList<>();
		list.add(1);
		list.add(2);
		assertEquals(List.of(1, 2), list);
		assertEquals(Arrays.asList(1, 2), list);
	}

	@Test
	void listMismatchFails() {
		assertEquals(List.of(1, 2), List.of(1, 3));
	}

	@Test
	void mapsUseEquals() {
		Map<String, Integer> map = new HashMap<>();
		map.put("a", 1);
		assertEquals(Map.of("a", 1), map);
	}

	@Test
	void mapMismatchFails() {
		Map<String, Integer> map = new HashMap<>();
		map.put("a", 2);
		assertEquals(Map.of("a", 1), map);
	}

	@Test
	void doubleShownAsJava() {
		assertEquals(0.3, 0.1 + 0.2);
	}

	@Test
	void charShownAsJava() {
		assertEquals('a', 'b');
	}

	@Test
	void notEqualsFails() {
		assertNotEquals("same", joined("sa", "me"), "differ");
	}

	@Test
	void nullFails() {
		assertNull(joined("not ", "null"));
	}

	@Test
	void doesNotThrowShowsMessage() {
		assertDoesNotThrow(() -> {
			throw new IllegalStateException("boom");
		});
	}

	@Test
	void stringArrayMismatchFails() {
		assertArrayEquals(new String[] { "a", "b" }, new String[] { "a", joined("c", "") });
	}

	@Test
	void exceptionMessageShown() {
		throw new IllegalStateException("thrown by the test");
	}

	@Test
	void failWithSupplier() {
		fail(() -> "supplied");
	}

	// Java code sees JUnit's message
	@Test
	void failureMessageIsVisibleToJava() {
		try {
			assertEquals(1, 2);
		} catch (AssertionError expected) {
			assertEquals("expected: <1> but was: <2>", expected.getMessage());
			return;
		}
		fail("AssertionError was not thrown");
	}
}
