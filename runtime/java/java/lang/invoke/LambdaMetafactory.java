package java.lang.invoke;

/**
 * Declared only so the Java compiler resolves a lambda that J2C reports
 * instead of translating (such as one that uses this of an anonymous class);
 * without it, the report also has a compile error naming this class.
 */
public final class LambdaMetafactory {
	private LambdaMetafactory() {
	}
}
