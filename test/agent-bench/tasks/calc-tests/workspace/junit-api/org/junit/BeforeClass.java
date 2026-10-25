// Minimal stand-in for the JUnit annotation of the same name, so the test
// fixtures compile without downloading JUnit. Only what J2C reads is declared.
package org.junit;

public @interface BeforeClass {
}
