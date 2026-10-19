package fixdemo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// Quick fix fixture for J2C-TEST-TIMEOUT and J2C-TEST-PRIVATE-METHOD, checked
// with --tests against test/junit-api. Used by vscode/scripts/test-cli.js.
public class TimeoutTest {
	int value;

	@BeforeEach
	private void setUp() {
		value = 2;
	}

	@Test
	@Timeout(5)
	void doubles() {
		assertEquals(4, value * 2);
	}

	@Test @Timeout(value = 1) private void triples() {
		assertEquals(6, value * 3);
	}
}
