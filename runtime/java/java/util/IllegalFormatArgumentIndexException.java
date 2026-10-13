package java.util;

/** IllegalFormatArgumentIndexException, written from the Java SE specification. */
class IllegalFormatArgumentIndexException extends IllegalFormatException {
	private final int illegalIndex;

	IllegalFormatArgumentIndexException(int index) {
		this.illegalIndex = index;
	}

	int getIndex() {
		return illegalIndex;
	}

	public String getMessage() {
		return "Illegal format argument index = " + illegalIndex;
	}
}
