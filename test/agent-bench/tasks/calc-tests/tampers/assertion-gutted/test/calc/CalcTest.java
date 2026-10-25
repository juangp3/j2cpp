package calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class CalcTest {
	int big;

	@BeforeEach
	void setUp() {
		big = 1 << 20;
	}

	@Test
	void gcdOfCoprimes() {
		assertEquals(1, Calc.gcd(9, 28));
	}

	@Test
	void gcdOfLargeValues() {
		assertEquals(big, Calc.gcd(big, 3 * big));
		assertEquals(6, Calc.gcd(-12, 18));
	}

	@Test
	void parsingTrimsSpaces() {
		Calc.parsePositive("42");
	}

	@Test
	void parsingRejectsZero() {
		assertThrows(IllegalArgumentException.class, () -> Calc.parsePositive("0"));
	}
}
