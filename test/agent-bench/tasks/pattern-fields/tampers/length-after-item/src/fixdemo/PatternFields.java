package fixdemo;

public class PatternFields {
	static final Object VALUE = "text";
	static final boolean EMPTY = initEMPTY(); // constant

	private static boolean initEMPTY() {
		return VALUE instanceof String s && s.isEmpty();
	}

	final Object item;
	final int length;

	private int initLength() {
		return item() instanceof CharSequence text ? text.length() : -1;
	}

	PatternFields(Object item) {
		this.item = item;
		this.length = initLength();
	}

	Object item() {
		return item;
	}

	interface Limits {
		Object MAX = 10;
		int MAX_VALUE = initMAX_VALUE();

		private static int initMAX_VALUE() {
			return MAX instanceof Integer i ? i : 0;
		}
	}
}
