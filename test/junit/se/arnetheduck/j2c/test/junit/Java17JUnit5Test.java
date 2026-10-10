package se.arnetheduck.j2c.test.junit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

// JUnit 5 lifecycle; expected outcomes are in ../../../../../expected-results.json
class Java17JUnit5Test {
	static int suiteStarts;
	static int instances;
	int value;

	Java17JUnit5Test() {
		instances++;
	}

	@BeforeAll
	static void startSuite() {
		suiteStarts++;
	}

	@AfterAll
	static void finishSuite() {
		suiteStarts--;
	}

	@BeforeEach
	void setUp() {
		value = 40;
	}

	@AfterEach
	void tearDown() {
		value = -1;
	}

	static void check(boolean condition) {
		if (!condition) {
			throw new IllegalStateException();
		}
	}

	@Test
	void beforeEachRan() {
		check(value == 40);
	}

	@Test
	void beforeAllRanOnce() {
		check(suiteStarts == 1);
	}

	// Every test gets a fresh instance, as in JUnit
	@Test
	void freshInstancePerTest() {
		value++;
		check(value == 41);
	}

	@Test
	void failingCheck() {
		check(value == 0);
	}

	@Test
	void unexpectedException() {
		throw new UnsupportedOperationException();
	}

	@Disabled("not ready")
	@Test
	void disabledWithReason() {
		check(false);
	}
}
