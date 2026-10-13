package java.lang.invoke;

/**
 * Declared only so the Java compiler resolves what lambdas and records refer
 * to; J2C translates lambdas to classes and generates record members itself.
 */
public class MethodHandles {
	private MethodHandles() {
	}

	public static final class Lookup {
		private Lookup() {
		}
	}
}
