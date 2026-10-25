package calc;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LegacyCalcTest {
	@Test
	public void gcdWithZero() {
		assertEquals(5, Calc.gcd(0, 5));
		assertEquals(5, Calc.gcd(5, 0));
	}

	@Test(expected = IllegalArgumentException.class)
	public void rejectsNegative() {
		Calc.parsePositive("-3");
	}
}
