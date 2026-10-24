package shapes;

import java.util.function.Supplier;

public class Shapes {
	public static class Shape {
		public String describe() {
			return "shape";
		}
	}

	public static Shape boxed(String label) {
		return new Shape() {
			@Override
			public String describe() {
				Supplier<String> base = () -> label;
				return "[" + base.get() + "]";
			}
		};
	}
}
