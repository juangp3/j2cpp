package simple;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Needs the test-scoped JUnit dependency */
class AppTest {
	@Test
	void hasOptions() {
		assertTrue(App.optionCount() > 0);
	}
}
