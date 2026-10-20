package shop;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

// Migration report fixture for --tests: a timeout (J2C-TEST-TIMEOUT, with a
// quick fix), checked against test/junit-api. Used by
// vscode/scripts/test-cli.js.
public class MainTest {
	@Test
	@Timeout(5)
	void totals() {
		assertEquals(3, Main.total(Arrays.asList(1, 2)));
	}

	@Test
	void empty() {
		assertEquals(0, Main.total(Arrays.<Integer> asList()));
	}
}
