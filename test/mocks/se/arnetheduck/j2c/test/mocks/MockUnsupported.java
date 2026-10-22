package se.arnetheduck.j2c.test.mocks;

/**
 * Interfaces that --mocks reports as J2C-MOCK-UNSUPPORTED (checked by
 * vscode/scripts/test-cli.js); the others are mocked.
 */
public class MockUnsupported {
	/** Sixteen parameters: more than gMock's MOCK_METHOD takes */
	public interface Wide {
		void call(int a0, int a1, int a2, int a3, int a4, int a5, int a6,
				int a7, int a8, int a9, int a10, int a11, int a12, int a13,
				int a14, int a15);
	}

	/** Fifteen parameters are mocked */
	public interface Fifteen {
		void call(int a0, int a1, int a2, int a3, int a4, int a5, int a6,
				int a7, int a8, int a9, int a10, int a11, int a12, int a13,
				int a14);
	}

	public interface Numbered<T extends Number> {
		T value();
	}

	@SuppressWarnings("rawtypes")
	public interface Compared {
		Comparable value();
	}

	/**
	 * value() returns Integer in Java, but erases to Number and Comparable,
	 * and no C++ return type overrides both
	 */
	public interface Both extends Numbered<Integer>, Compared {
	}

	/** Declaring value() with Integer gives the C++ override its type */
	public interface Declared extends Numbered<Integer>, Compared {
		@Override
		Integer value();
	}
}
