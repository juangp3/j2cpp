// Minimal stand-in for the JUnit class of the same name, so the test fixtures
// compile without downloading JUnit. Signatures match JUnit; translated tests
// never call these bodies, because J2C translates assertion calls itself.
package org.junit.jupiter.api.function;

@FunctionalInterface
public interface Executable {
	void execute() throws Throwable;
}
