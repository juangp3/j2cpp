package multi.app;

import multi.core.Flags;

/** In the overridden source directory; uses the sibling module and its dependency */
public class Main {
	public static int count() {
		return Flags.all().getOptions().size();
	}
}
