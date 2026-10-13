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
		return name.charAt(0) == '[' ? elementSimpleName(name.substring(1)) + "[]"
				: simpleName(name);
	}

	/** Binary names: "p.Outer$Inner", anonymous "p.Outer$1", local "p.Outer$1Local" */
	private static String simpleName(String binaryName) {
		int dot = Math.max(binaryName.lastIndexOf('.'), binaryName.lastIndexOf('$'));
		int start = dot + 1;
		while (start < binaryName.length() && Character.isDigit(binaryName.charAt(start))) {
			start++;
		}
		return binaryName.substring(start);
	}

	/** An array's element descriptor: "[I", "Lp.Outer$Inner;", "I" */
	private static String elementSimpleName(String descriptor) {
		switch (descriptor.charAt(0)) {
		case '[':
			return elementSimpleName(descriptor.substring(1)) + "[]";
		case 'L':
			return simpleName(descriptor.substring(1, descriptor.length() - 1));
		case 'Z':
			return "boolean";
		case 'B':
			return "byte";
		case 'C':
			return "char";
		case 'S':
			return "short";
		case 'I':
			return "int";
		case 'J':
			return "long";
		case 'F':
			return "float";
		default:
			return "double";
		}
	}

	public String toString() {
		return "class " + name;
	}
}
