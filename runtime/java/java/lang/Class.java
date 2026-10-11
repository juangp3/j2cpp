package java.lang;

/** Simplified Class: a name only; no reflection. */
public final class Class<T> implements java.io.Serializable {
	private final String name;

	private Class(String name) {
		this.name = name;
	}

	/** For the class_ hook, which caches one instance per name */
	static Class<?> j2cForName(String name) {
		return new Class<Object>(name);
	}

	public String getName() {
		return name;
	}

	public String getSimpleName() {
		int dot = Math.max(name.lastIndexOf('.'), name.lastIndexOf('$'));
		return name.substring(dot + 1);
	}

	public String toString() {
		return "class " + name;
	}
}
