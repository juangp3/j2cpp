// expect: compiles
package probes;
public class Interfaces {
	interface Greeter {
		String name();
		default String greet() { return "hi " + helper(); }
		private String helper() { return name(); }
		static Greeter of(String n) { return () -> n; }
	}
	interface Loud extends Greeter { default String greet() { return Greeter.super.greet() + "!"; } }
	static class Person implements Loud { public String name() { return "p"; } }
	static String run() { return new Person().greet() + Greeter.of("x").greet(); }
}
