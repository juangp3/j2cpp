package java.lang;

/**
 * A stack frame. J2C records no frames, so Throwable.getStackTrace() is
 * empty unless a program stores frames with setStackTrace().
 */
public final class StackTraceElement implements java.io.Serializable {
	private final String declaringClass;
	private final String methodName;
	private final String fileName;
	private final int lineNumber;

	public StackTraceElement(String declaringClass, String methodName, String fileName, int lineNumber) {
		if (declaringClass == null) {
			throw new NullPointerException("Declaring class is null");
		}
		if (methodName == null) {
			throw new NullPointerException("Method name is null");
		}
		this.declaringClass = declaringClass;
		this.methodName = methodName;
		this.fileName = fileName;
		this.lineNumber = lineNumber;
	}

	public String getClassName() {
		return declaringClass;
	}

	public String getMethodName() {
		return methodName;
	}

	public String getFileName() {
		return fileName;
	}

	public int getLineNumber() {
		return lineNumber;
	}

	public boolean isNativeMethod() {
		return lineNumber == -2;
	}

	public String toString() {
		String where;
		if (lineNumber == -2) {
			where = "Native Method";
		} else if (fileName == null) {
			where = "Unknown Source";
		} else if (lineNumber >= 0) {
			where = fileName + ":" + lineNumber;
		} else {
			where = fileName;
		}
		return declaringClass + "." + methodName + "(" + where + ")";
	}

	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (!(obj instanceof StackTraceElement)) {
			return false;
		}
		StackTraceElement e = (StackTraceElement) obj;
		return declaringClass.equals(e.declaringClass) && methodName.equals(e.methodName)
				&& lineNumber == e.lineNumber
				&& (fileName == null ? e.fileName == null : fileName.equals(e.fileName));
	}

	public int hashCode() {
		int h = 31 * declaringClass.hashCode() + methodName.hashCode();
		h = 31 * h + (fileName == null ? 0 : fileName.hashCode());
		return 31 * h + lineNumber;
	}
}
