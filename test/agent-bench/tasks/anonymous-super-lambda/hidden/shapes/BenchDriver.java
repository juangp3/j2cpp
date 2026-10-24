package shapes;

public class BenchDriver {
	public static void main(String[] args) {
		Shapes.Shape box = Shapes.boxed("a");
		System.out.println(box.describe());
		System.out.println(Shapes.boxed("b c").describe());
		System.out.println(new Shapes.Shape().describe());
		System.out.println(box.getClass() != Shapes.Shape.class);
	}
}
