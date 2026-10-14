// G43: array accesses are checked, as in Java
public class Bounds {
	interface Access {
		void run();
	}

	static int counter;

	static void attempt(String label, Access access) {
		try {
			access.run();
			System.out.println(label + ": no exception");
		} catch (ArrayIndexOutOfBoundsException e) {
			System.out.println(label + ": " + e);
		} catch (NegativeArraySizeException e) {
			System.out.println(label + ": " + e);
		}
	}

	static int index(int i) {
		counter++;
		return i;
	}

	public static void main(String[] args) {
		int[] ints = { 1, 2, 3 };
		long[] longs = new long[2];
		double[] doubles = new double[1];
		char[] chars = "ab".toCharArray();
		boolean[] flags = new boolean[4];
		byte[] bytes = new byte[0];
		String[] strings = { "x", "y" };
		Object[] objects = new Object[3];
		Integer[] boxes = { 7, 8 };
		int[][] grid = new int[2][3];
		String[][] names = new String[2][];

		attempt("read", () -> System.out.println(ints[5]));
		attempt("read last", () -> System.out.println(ints[2]));
		attempt("write", () -> ints[3] = 9);
		attempt("compound", () -> ints[4] += 1);
		attempt("increment", () -> ints[-2]++);
		attempt("negative", () -> System.out.println(ints[-1]));
		attempt("min int", () -> System.out.println(ints[Integer.MIN_VALUE]));
		attempt("long", () -> longs[2] = 1L);
		attempt("double", () -> System.out.println(doubles[1]));
		attempt("char", () -> System.out.println(chars[2]));
		attempt("boolean", () -> flags[4] = true);
		attempt("empty", () -> System.out.println(bytes[0]));
		attempt("string read", () -> System.out.println(strings[2]));
		attempt("string write", () -> strings[-1] = "z");
		attempt("string append", () -> strings[2] += "!");
		attempt("object read", () -> System.out.println(objects[3]));
		attempt("object write", () -> objects[10] = "o");
		attempt("box increment", () -> boxes[2]++);
		attempt("store before type check", () -> {
			Object[] covariant = strings;
			covariant[5] = Integer.valueOf(1);
		});
		attempt("grid row", () -> System.out.println(grid[2][0]));
		attempt("grid column", () -> grid[1][3] = 4);
		attempt("grid compound", () -> grid[0][-1] *= 2);
		attempt("names row", () -> names[2] = new String[1]);
		attempt("index evaluated once", () -> ints[index(7)] += 1);
		attempt("negative size", () -> System.out.println(new int[-1].length));
		attempt("negative object size", () -> System.out.println(new String[-3].length));
		attempt("negative inner size", () -> System.out.println(new int[2][-4].length));
		attempt("negative below zero", () -> System.out.println(new int[0][-5].length));
		attempt("negative outer size", () -> System.out.println(new int[-6][-7].length));
		attempt("zero size", () -> System.out.println(new int[0].length + new String[0][3].length));

		try {
			ints[3] = 0;
		} catch (IndexOutOfBoundsException e) {
			System.out.println("as IndexOutOfBoundsException: " + e.getMessage());
		}
		try {
			strings[-7] = null;
		} catch (RuntimeException e) {
			System.out.println("as RuntimeException: " + e.getMessage());
		}

		int sum = 0;
		for (int i = 0; i < ints.length; i++) {
			sum += ints[i];
		}
		System.out.println("sum " + sum + ", counter " + counter + ", ints " + ints[0] + ints[1] + ints[2]
				+ ", strings " + strings[0] + strings[1] + ", boxes " + boxes[0] + boxes[1]);
	}
}
