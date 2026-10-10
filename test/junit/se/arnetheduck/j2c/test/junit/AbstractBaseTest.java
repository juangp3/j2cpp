package se.arnetheduck.j2c.test.junit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Abstract: never run itself, but its tests and lifecycle methods are inherited
abstract class AbstractBaseTest {
	int steps;

	@BeforeEach
	void baseSetUp() {
		steps = 1;
	}

	@Test
	void inheritedTest() {
		if (steps != 2) {
			throw new IllegalStateException();
		}
	}

	@Test
	void overriddenTest() {
		throw new IllegalStateException();
	}
}
