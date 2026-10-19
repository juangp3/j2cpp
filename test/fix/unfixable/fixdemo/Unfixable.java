package fixdemo;

// Problems that J2C reports without a quick fix, because no mechanical
// rewrite keeps their meaning. Used by vscode/scripts/test-cli.js.
public class Unfixable {
	Object make() {
		return new Object() {
			// A field initializer has no statement to declare a local before
			Runnable field = () -> System.out.println(this);

			@Override
			public String toString() {
				// super.toString() cannot be called through a variable
				java.util.function.Supplier<String> base = () -> super.toString();
				return base.get();
			}
		};
	}
}
