package java.lang;
/** Simplified String: UTF-16 code units, as in J2ObjC-style patched classes. */
public final class String implements java.io.Serializable {
	private final char[] value;
	public String(char[] value) {
		this.value = value;
	}
	public int length() {
		return value.length;
	}
	public char charAt(int index) {
		return value[index];
	}
	public String toString() {
		return this;
	}
}
