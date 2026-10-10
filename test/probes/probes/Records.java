// expect: compiles
package probes;
import java.util.List;
public class Records {
	interface Shape { double area(); }
	record Circle(double r) implements Shape {
		Circle { if (r < 0) throw new IllegalArgumentException("r"); }
		static Circle unit() { return new Circle(1); }
		public double area() { return Math.PI * r * r; }
	}
	record Pair<A, B>(A first, B second) {}
	record Box(List<String> items, int[] counts) {}
	static double total(List<Shape> shapes) { double t = 0; for (Shape s : shapes) t += s.area(); return t; }
	static Pair<String, Integer> pair() { return new Pair<>("a", 1); }
}
