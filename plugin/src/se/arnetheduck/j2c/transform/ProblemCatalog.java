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
	public static final String LAMBDA_INTERFACE_SUPER = "J2C-LAMBDA-INTERFACE-SUPER";
	public static final String LAMBDA_INTERSECTION_TYPE_VARIABLE = "J2C-LAMBDA-INTERSECTION-TYPE-VARIABLE";
	public static final String LAMBDA_NON_DENOTABLE_TYPE = "J2C-LAMBDA-NON-DENOTABLE-TYPE";
	public static final String LAMBDA_UNRESOLVED = "J2C-LAMBDA-UNRESOLVED";
	public static final String LAMBDA_UNTRANSLATED = "J2C-LAMBDA-UNTRANSLATED";
	public static final String PATTERN_OUTSIDE_METHOD = "J2C-PATTERN-OUTSIDE-METHOD";
	public static final String SEALED_REJECTED = "J2C-SEALED-REJECTED";
	public static final String NON_SEALED_REJECTED = "J2C-NON-SEALED-REJECTED";
	public static final String TEST_MOCKING = "J2C-TEST-MOCKING";
	public static final String TEST_EXTENSION = "J2C-TEST-EXTENSION";
	public static final String TEST_UNSUPPORTED_KIND = "J2C-TEST-UNSUPPORTED-KIND";
	public static final String TEST_NESTED = "J2C-TEST-NESTED";
	public static final String TEST_TIMEOUT = "J2C-TEST-TIMEOUT";
	public static final String TEST_INSTANCE_LIFECYCLE = "J2C-TEST-INSTANCE-LIFECYCLE";
	public static final String TEST_PRIVATE_METHOD = "J2C-TEST-PRIVATE-METHOD";
	public static final String TEST_PARAMETERS = "J2C-TEST-PARAMETERS";
	public static final String TEST_UNSUPPORTED_ASSERTION = "J2C-TEST-UNSUPPORTED-ASSERTION";
	/** A warning: translation goes on without the dependency class path */
	public static final String PROJECT_CLASSPATH_UNRESOLVED = "J2C-PROJECT-CLASSPATH-UNRESOLVED";

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
		add(LAMBDA_INTERFACE_SUPER,
				"Lambda calls a default method through Iface.super",
				"Lambdas are translated into generated classes, and Java can name "
						+ "Iface.super.m() only in the class that implements Iface.",
				"Move the Iface.super.m() call into a private method of the enclosing class "
						+ "and call that method from the lambda.");
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
		add(TEST_MOCKING,
				"Mocking library in a translated test",
				"Mockito, PowerMock and EasyMock create classes at run time through "
						+ "reflection, which has no C++ equivalent.",
				"Replace the mock with a small hand-written fake class in Java (it "
						+ "translates like any class), or keep this test on the JVM.");
		add(TEST_EXTENSION,
				"JUnit runner, rule or extension",
				"@RunWith, @Rule, @ClassRule, @ExtendWith and @RegisterExtension change how "
						+ "JUnit runs tests, which the generated GoogleTest wrappers cannot reproduce.",
				"Move the setup the extension provides into @BeforeEach/@AfterEach methods, "
						+ "or keep this test on the JVM.");
		add(TEST_UNSUPPORTED_KIND,
				"Parameterized, repeated or dynamic test",
				"@ParameterizedTest, @RepeatedTest, @TestFactory and @TestTemplate generate "
						+ "test cases at run time; only plain @Test methods are translated.",
				"Write one @Test method per case (calling a shared helper), or keep this "
						+ "test on the JVM.");
		add(TEST_NESTED,
				"Nested test class",
				"@Nested test classes are inner classes whose tests run inside an instance "
						+ "of the enclosing test, which the wrappers do not support.",
				"Move the nested tests into a separate top-level or static nested test class.");
		add(TEST_TIMEOUT,
				"Test timeout",
				"Timeouts (@Timeout or @Test(timeout = ...)) need the test to run on a "
						+ "separate thread that can be abandoned, which the wrappers do not do.",
				"Remove the timeout, or enforce it with your C++ test runner's options.");
		add(TEST_INSTANCE_LIFECYCLE,
				"Non-default test instance lifecycle",
				"@TestInstance(PER_CLASS) shares one instance across tests; the wrappers "
						+ "create a new instance for every test, as JUnit does by default.",
				"Use the default lifecycle: make @BeforeAll/@AfterAll methods static and "
						+ "keep per-test state in fields set by @BeforeEach.");
		add(TEST_PRIVATE_METHOD,
				"Private test or lifecycle method",
				"JUnit does not run private test or lifecycle methods, and the generated "
						+ "wrappers cannot call them.",
				"Make the method package-private, protected or public.");
		add(TEST_UNSUPPORTED_ASSERTION,
				"Assertion form not supported in translated tests",
				"Translated tests support assertTrue/False, assertNull/NotNull, "
						+ "assertEquals/NotEquals (with or without delta), assertSame/NotSame, "
						+ "assertArrayEquals, fail, assertThrows with a class literal, "
						+ "assertDoesNotThrow and assumeTrue/False. Other forms (assertAll, "
						+ "assertTimeout, assertIterableEquals, lazy BooleanSupplier conditions, "
						+ "Hamcrest, AssertJ, Truth) are not translated yet.",
				"Rewrite the assertion with a supported form, for example assertTrue(...) "
						+ "with the condition written out, or keep this test on the JVM.");
		add(TEST_PARAMETERS,
				"Test method with parameters",
				"JUnit can inject parameters such as TestInfo through extensions; the "
						+ "wrappers call test and lifecycle methods without arguments.",
				"Remove the parameters, and obtain what they provided in another way.");
		add(PROJECT_CLASSPATH_UNRESOLVED,
				"Dependency class path not resolved (warning)",
				"J2C detected a Maven or Gradle build and uses its source roots, but could "
						+ "not get the dependency class path from the build tool: the tool "
						+ "(or the project's wrapper) is missing, failed, or was not run. This "
						+ "is a warning; types from dependencies then fail to resolve as "
						+ "J2C-COMPILE-ERROR problems.",
				"Make the build work (mvn dependency:build-classpath or gradle dependencies "
						+ "should succeed), install Maven or Gradle or add the project's wrapper, "
						+ "or pass the dependency JARs with --classpath. --no-build-tool skips "
						+ "running the build tool, and --project none turns detection off.");
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
