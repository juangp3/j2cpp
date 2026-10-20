package shop.ui;

import java.util.ArrayList;
import java.util.List;

// Migration report fixture: one lambda that uses this inside an anonymous
// class (J2C-LAMBDA-ANONYMOUS-THIS, with a quick fix) blocks show() but not
// clear(). Used by vscode/scripts/test-cli.js.
public class Panel {
	interface Listener {
		void fire();
	}

	final List<Object> seen = new ArrayList<Object>();

	Listener show() {
		return new Listener() {
			public void fire() {
				Runnable remember = () -> seen.add(this);
				remember.run();
			}
		};
	}

	void clear() {
		seen.clear();
	}

	static class Label {
		String text = "label";

		String text() {
			return text.trim();
		}
	}
}
