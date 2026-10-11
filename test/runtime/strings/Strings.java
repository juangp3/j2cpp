// R2: string concatenation, StringBuilder, boxed types, Math and exceptions
public class Strings {
	static int parse(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			System.out.println("bad number: " + e.getMessage());
			return -1;
		}
	}

	public static void main(String[] args) {
		String name = "J2C";
		int count = 3;
		long big = 9_000_000_000L;
		char initial = name.charAt(0);
		boolean ok = true;
		System.out.println("Hello " + name + ", count=" + count + ", big=" + big + ", initial=" + initial + ", ok=" + ok);
		System.out.println("doubles: " + 0.1 + " " + 1.5 + " " + 100.0 + " " + 1.0E10 + " " + 1.0E-5 + " " + (1.0 / 3) + " " + -0.0);
		System.out.println("floats: " + 0.1f + " " + 3.25f + " " + 1.0E10f);
		System.out.println("special: " + Double.NaN + " " + (1.0 / 0) + " " + Integer.MIN_VALUE + " " + Long.MAX_VALUE);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 5; i++) {
			sb.append(i).append(',');
		}
		sb.setLength(sb.length() - 1);
		System.out.println(sb.reverse().toString() + " length " + sb.length());
		String text = "the quick brown fox";
		System.out.println(text.indexOf("quick") + " " + text.substring(4, 9) + " " + text.startsWith("the") + " " + text.endsWith("dog"));
		System.out.println("compare: " + "apple".compareTo("banana") + " equal: " + "a".equals(new String("a")) + " literal identity: " + ("lit" == "lit"));
		System.out.println("hash: " + "hello".hashCode() + " " + Integer.toHexString(255) + " " + Integer.toBinaryString(10) + " " + Long.toString(-123456789L));
		Integer boxed = 127;
		Integer same = 127;
		Integer larger = 1000;
		System.out.println("cache: " + (boxed == same) + " " + boxed.equals(same) + " " + larger.compareTo(boxed));
		System.out.println("parse: " + parse("42") + " " + parse("-7") + " " + parse("x9"));
		System.out.println("math: " + Math.max(3, 9) + " " + Math.abs(-2.5) + " " + Math.sqrt(2.0) + " " + Math.pow(2, 10) + " " + Math.floorMod(-7, 3) + " " + Math.round(2.5));
		System.out.println("double parse: " + Double.parseDouble("3.5") + " " + Double.compare(1.0, 2.0));
		int[] source = { 1, 2, 3, 4, 5 };
		int[] target = new int[5];
		System.arraycopy(source, 1, target, 0, 3);
		System.out.println("arraycopy: " + target[0] + target[1] + target[2] + target[3]);
		try {
			Object o = "text";
			Integer wrong = (Integer) o;
			System.out.println("unreachable " + wrong);
		} catch (ClassCastException e) {
			System.out.println("caught ClassCastException");
		}
		try {
			int zero = args.length;
			System.out.println(10 / zero);
		} catch (ArithmeticException e) {
			System.out.println("caught " + e);
		}
		try {
			String missing = null;
			System.out.println(missing.length());
		} catch (NullPointerException e) {
			System.out.println("caught NullPointerException");
		}
		throw new IllegalStateException("uncaught at the end", new RuntimeException("the cause"));
	}
}
