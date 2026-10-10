package se.arnetheduck.j2c.test.junit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Superclass @BeforeEach methods run first; an override replaces its original
class DerivedTest extends AbstractBaseTest {
	@BeforeEach
	void derivedSetUp() {
		if (steps != 1) {
			throw new IllegalStateException();
		}
		steps = 2;
	}

	@Test
	@Override
	void overriddenTest() {
		if (steps != 2) {
			throw new IllegalStateException();
		}
	}
}
