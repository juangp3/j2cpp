package java.lang;

public class IndexOutOfBoundsException extends RuntimeException {
	public IndexOutOfBoundsException() {
	}

	public IndexOutOfBoundsException(String message) {
		super(message);
	}

	public IndexOutOfBoundsException(int index) {
		super("Index out of range: " + index);
	}

	public IndexOutOfBoundsException(long index) {
		super("Index out of range: " + index);
	}
}
