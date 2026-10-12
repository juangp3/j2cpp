package java.lang;

public class ClassNotFoundException extends ReflectiveOperationException {
	public ClassNotFoundException() {
		super((String) null, (Throwable) null);
	}

	public ClassNotFoundException(String s) {
		super(s, (Throwable) null);
	}

	public ClassNotFoundException(String s, Throwable ex) {
		super(s, ex);
	}

	/** The exception raised while loading the class: the cause */
	public Throwable getException() {
		return getCause();
	}
}
