package se.arnetheduck.j2c.transform;

import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.IBinding;

/**
 * Orders bindings by key, so generated output (such as the order of
 * includes) is stable. JDT puts the compilation unit's path into the keys of
 * some source types (Ljava/util//src/java/util/HashMap~HashMap&lt;TK;TV;&gt;;
 * on Linux, with a drive and backslashes on Windows),
 * which would make the order depend on where the sources are (a runtime's
 * directory, LambdaDesugarer's temporary directory): those paths are compared
 * as the unit's file name alone, and only break ties.
 */
public final class BindingComparator implements Comparator<IBinding> {
	/** An absolute unit path before ~, after L or a package's / */
	private static final Pattern UNIT_PATH = Pattern
			.compile("(L|/)(?:[A-Za-z]:[\\\\/]|/)[^~;<>]*[\\\\/]([^\\\\/~;<>]+~)");

	@Override
	public int compare(IBinding o1, IBinding o2) {
		String key1 = o1.getKey();
		String key2 = o2.getKey();
		int result = withoutPaths(key1).compareTo(withoutPaths(key2));
		return result != 0 ? result : key1.compareTo(key2);
	}

	static String withoutPaths(String key) {
		if (key.indexOf('~') < 0) {
			return key;
		}
		Matcher matcher = UNIT_PATH.matcher(key);
		return matcher.find() ? matcher.replaceAll("$1$2") : key;
	}
}
