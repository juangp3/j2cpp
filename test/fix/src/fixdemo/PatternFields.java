package fixdemo;

// Quick fix fixture for J2C-PATTERN-OUTSIDE-METHOD: instanceof patterns in
// field initializers. Used by vscode/scripts/test-cli.js.
public class PatternFields {
	static final Object VALUE = "text";
	static final boolean EMPTY = VALUE instanceof String s && s.isEmpty(); // constant

	final Object item;
	final int length = item() instanceof CharSequence text ? text.length() : -1;

	PatternFields(Object item) {
		this.item = item;
	}

	Object item() {
		return item;
	}

	interface Limits {
		Object MAX = 10;
		int MAX_VALUE = MAX instanceof Integer i ? i : 0;
	}
}
