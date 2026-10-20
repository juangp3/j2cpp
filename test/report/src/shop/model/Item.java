package shop.model;

// Migration report fixture: an instanceof pattern in a field initializer
// (J2C-PATTERN-OUTSIDE-METHOD, with a quick fix) and a compile error
// (J2C-COMPILE-ERROR, without one). Used by vscode/scripts/test-cli.js.
public class Item {
	static final Object NAME = "item";
	static final int LENGTH = NAME instanceof String s ? s.length() : 0;

	final String name;

	Item(String name) {
		this.name = name;
	}

	int price() {
		return lookup(name);
	}

	String label() {
		return name.toUpperCase();
	}
}
