package java.lang;

public class Object {
	public Object() {
	}

	public final native Class<?> getClass();

	public boolean equals(Object obj) {
		return this == obj;
	}

	public native int hashCode();

	protected native Object clone() throws CloneNotSupportedException;

	public String toString() {
		return getClass().getName() + "@" + Integer.toHexString(hashCode());
	}
}
