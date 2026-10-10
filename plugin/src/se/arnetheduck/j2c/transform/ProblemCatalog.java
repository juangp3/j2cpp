package se.arnetheduck.j2c.transform;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every problem J2C reports, by stable ID, with what it means and how to fix
 * it. IDs are part of the CLI's JSON output and the MCP tools, so they must
 * not change once released.
 */
public final class ProblemCatalog {
	public static final String COMPILE_ERROR = "J2C-COMPILE-ERROR";
	public static final String LAMBDA_ANONYMOUS_THIS = "J2C-LAMBDA-ANONYMOUS-THIS";
	public static final String LAMBDA_INTERSECTION_TYPE_VARIABLE = "J2C-LAMBDA-INTERSECTION-TYPE-VARIABLE";
	public static final String LAMBDA_NON_DENOTABLE_TYPE = "J2C-LAMBDA-NON-DENOTABLE-TYPE";
	public static final String LAMBDA_UNRESOLVED = "J2C-LAMBDA-UNRESOLVED";
	public static final String LAMBDA_UNTRANSLATED = "J2C-LAMBDA-UNTRANSLATED";
	public static final String PATTERN_OUTSIDE_METHOD = "J2C-PATTERN-OUTSIDE-METHOD";
	public static final String SEALED_REJECTED = "J2C-SEALED-REJECTED";
	public static final String NON_SEALED_REJECTED = "J2C-NON-SEALED-REJECTED";

	public static final class Entry {
		public final String id;
		public final String title;
		public final String explanation;
		public final String hint;

		Entry(String id, String title, String explanation, String hint) {
			this.id = id;
			this.title = title;
			this.explanation = explanation;
			this.hint = hint;
		}
	}

	private static final Map<String, Entry> ENTRIES = new LinkedHashMap<String, Entry>();

	static {
		add(COMPILE_ERROR,
				"Java compile error",
				"J2C translates only Java that compiles. The message is the Java compiler's own.",
				"Fix the reported Java error. If it names a missing type or package, add its "
						+ "source root with --sourcepath or its JAR with --classpath.");
		add(LAMBDA_ANONYMOUS_THIS,
				"Lambda uses this inside an anonymous class",
				"Lambdas are translated into generated classes that refer to the enclosing "
						+ "object by its class name, and an anonymous class has no name.",
				"Assign this to a local variable before the lambda (Runnable self = this;) and "
						+ "use the variable, or turn the anonymous class into a named nested or local class.");
		add(LAMBDA_INTERSECTION_TYPE_VARIABLE,
				"Intersection cast mentions a type variable",
				"An intersection-cast lambda implements a generated member interface, which "
						+ "cannot see the type variables of the enclosing method.",
				"Cast to a single functional interface, or use concrete types in the intersection.");
		add(LAMBDA_NON_DENOTABLE_TYPE,
				"Lambda involves a type that cannot be written in Java",
				"The generated class must name every parameter and return type, and inferred "
						+ "types such as intersections or anonymous classes have no Java syntax.",
				"Give the lambda parameters explicit types, or assign the lambda to a variable "
						+ "declared with an explicit functional interface type.");
		add(LAMBDA_UNRESOLVED,
				"Lambda target type could not be resolved",
				"The functional interface a lambda or method reference implements could not be "
						+ "found, which usually accompanies a compile error.",
				"Fix any compile errors first, and make sure the interface's source or JAR is on "
						+ "--sourcepath or --classpath.");
		add(LAMBDA_UNTRANSLATED,
				"Lambda could not be translated",
				"J2C could not rewrite this lambda or method reference for an unexpected reason.",
				"Please report it as a J2C bug, with the code that triggers it.");
		add(PATTERN_OUTSIDE_METHOD,
				"instanceof pattern outside a method body",
				"Pattern variables are declared just before the statement that contains them, "
						+ "and a field initializer has no statement.",
				"Move the initializer into a constructor, an initializer block, or a helper method.");
		add(SEALED_REJECTED,
				"Sealed type rejected",
				"--sealed-types reject was given and this type is sealed.",
				"Use --sealed-types advisory (the default) to translate sealed types as ordinary "
						+ "C++ inheritance with comments listing the permitted subtypes.");
		add(NON_SEALED_REJECTED,
				"Non-sealed type rejected",
				"--sealed-types reject was given and this type is declared non-sealed.",
				"Use --sealed-types advisory (the default) to translate it as ordinary C++ inheritance.");
	}

	private static void add(String id, String title, String explanation,
			String hint) {
		ENTRIES.put(id, new Entry(id, title, explanation, hint));
	}

	private ProblemCatalog() {
	}

	/** The entry for id, or null if there is none */
	public static Entry get(String id) {
		return ENTRIES.get(id);
	}

	public static Collection<Entry> all() {
		return Collections.unmodifiableCollection(ENTRIES.values());
	}
}
