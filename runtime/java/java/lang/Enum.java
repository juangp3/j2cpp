package java.lang;

/**
 * Enum, written from the Java SE specification. J2C generates values() and
 * valueOf(String) for each enum class. Limits: no reflection, so
 * getDeclaringClass() and Enum.valueOf(Class, String) are not provided.
 */
public abstract class Enum<E extends Enum<E>> implements Comparable<E>, java.io.Serializable {
	private final String name;
	private final int ordinal;

	protected Enum(String name, int ordinal) {
		this.name = name;
		this.ordinal = ordinal;
	}

	public final String name() {
		return name;
	}

	public final int ordinal() {
		return ordinal;
	}

	public String toString() {
		return name;
	}

	public final boolean equals(Object other) {
		return this == other;
	}

	public final int hashCode() {
		return super.hashCode();
	}

	protected final Object clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}

	public final int compareTo(E o) {
		Enum<?> other = o;
		return ordinal - other.ordinal;
	}
}
