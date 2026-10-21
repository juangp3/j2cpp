package se.arnetheduck.j2c.transform;

import java.io.File;

/**
 * A GoogleTest test generated from a JUnit test method (--tests): its
 * GoogleTest name and the Java method it runs, so results can be reported
 * at the Java source.
 */
public final class GeneratedTest {
	/** The GoogleTest test suite (the fixture), such as a_b_FooTest */
	public final String suite;
	/** The GoogleTest test name, the Java method's name */
	public final String name;
	/** The qualified name of the test class run */
	public final String javaClass;
	public final String method;
	/**
	 * The qualified name of the class declaring the method, a superclass of
	 * javaClass for an inherited test
	 */
	public final String declaringClass;
	/** The Java file declaring the method, or null when it has no source */
	public final File file;
	/** The line of the method's name, or 0 when unknown */
	public final int line;
	/** Null when enabled, else the reason given (possibly empty) */
	public final String disabledReason;
	/** The wrapper's path relative to the output directory */
	public final String wrapper;

	GeneratedTest(String suite, String name, String javaClass, String method,
			String declaringClass, File file, int line, String disabledReason,
			String wrapper) {
		this.suite = suite;
		this.name = name;
		this.javaClass = javaClass;
		this.method = method;
		this.declaringClass = declaringClass;
		this.file = file;
		this.line = line;
		this.disabledReason = disabledReason;
		this.wrapper = wrapper;
	}

	/** The --gtest_filter name of the test, Suite.name */
	public String gtestName() {
		return suite + "." + name;
	}
}
