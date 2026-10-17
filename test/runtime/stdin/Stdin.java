import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.InputMismatchException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.stream.Collectors;

// Scanner over System.in (input.txt) and over strings (R6)
public class Stdin {
	public static void main(String[] args) throws IOException {
		Scanner in = new Scanner(System.in);
		int n = in.nextInt();
		int sum = 0;
		for (int i = 0; i < n; i++) {
			sum += in.nextInt();
		}
		System.out.printf("sum=%d average=%.2f%n", sum, (double) sum / n);
		double a = in.nextDouble();
		double b = in.nextDouble();
		System.out.println(a + b);

		// nextLine after nextDouble returns the rest of that line
		System.out.println("[" + in.nextLine() + "]");
		System.out.println("[" + in.nextLine() + "]");
		System.out.println("[" + in.nextLine() + "]");

		for (int i = 0; i < 6; i++) {
			if (in.hasNextInt()) {
				System.out.println("int " + in.nextInt());
			} else if (in.hasNextLong()) {
				System.out.println("long " + in.nextLong());
			} else {
				try {
					in.nextInt();
				} catch (InputMismatchException e) {
					System.out.println("mismatch " + e.getMessage() + ", skipping " + in.next());
				}
			}
		}
		System.out.println(in.nextBoolean() + " " + in.nextBoolean() + " " + in.hasNextBoolean() + " " + in.next());
		System.out.println("[" + in.nextLine() + "]");
		String unicode = in.nextLine();
		System.out.println(unicode + " " + unicode.length() + " " + unicode.codePoints().count());
		System.out.println(in.nextInt() + in.nextInt());

		// The scanner read all of the input ahead, so a reader made now sees none of it
		BufferedReader late = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("late reader: " + late.readLine());

		int lineNumber = 0;
		in.nextLine();
		while (in.hasNextLine()) {
			System.out.println(++lineNumber + ": [" + in.nextLine() + "]");
		}
		System.out.println(in.hasNext() + " " + in.hasNextLine() + " " + in.hasNextInt());
		try {
			in.nextLine();
		} catch (NoSuchElementException e) {
			System.out.println(e.getClass().getName() + ": " + e.getMessage());
		}
		try {
			in.next();
		} catch (NoSuchElementException e) {
			System.out.println(e.getClass().getName() + ": " + e.getMessage());
		}
		try {
			in.nextInt();
		} catch (NoSuchElementException e) {
			System.out.println(e.getClass().getName() + ": " + e.getMessage());
		}
		in.close();
		try {
			in.hasNext();
		} catch (IllegalStateException e) {
			System.out.println(e.getMessage());
		}

		strings();

		// Uncaught, as a program that reads a word where it expects a number
		new Scanner("twelve").nextInt();
	}

	static void strings() {
		Scanner numbers = new Scanner("1e3 .5 5. 1,234.5 NaN -Infinity ∞ 0x1.8p1 1d 1,23 +.5e-3 --1 0x1p3 1,234,567 12,34 0,123");
		while (numbers.hasNext()) {
			String token = numbers.next();
			Scanner one = new Scanner(token);
			System.out.println(token + " double=" + (one.hasNextDouble() ? String.valueOf(one.nextDouble()) : "no")
					+ " int=" + (new Scanner(token).hasNextInt() ? String.valueOf(new Scanner(token).nextInt()) : "no"));
		}
		Scanner ints = new Scanner("2147483647 2147483648 -2147483648 １２３ ff FF 102 -0 +0");
		System.out.println(ints.nextInt() + " " + ints.hasNextInt() + " " + ints.nextLong() + " " + ints.nextInt() + " "
				+ ints.nextInt() + " " + ints.nextInt(16) + " " + ints.hasNextInt(2) + " " + ints.nextInt(16) + " "
				+ ints.nextInt(3) + " " + ints.nextInt() + " " + ints.nextInt());
		try {
			new Scanner("2147483648").nextInt();
		} catch (InputMismatchException e) {
			System.out.println("InputMismatchException " + e.getMessage());
		}

		Scanner csv = new Scanner("a,b,,c, d,").useDelimiter(",");
		StringBuilder fields = new StringBuilder();
		while (csv.hasNext()) {
			fields.append('[').append(csv.next()).append(']');
		}
		System.out.println(fields);
		Scanner lines = new Scanner("x;y\nz;w").useDelimiter(";");
		System.out.println(lines.next() + "|" + lines.next() + "|" + lines.next());
		Scanner reset = new Scanner("p:q r").useDelimiter(":");
		System.out.println(reset.next() + "|" + reset.reset().next() + "|" + reset.next());
		System.out.println(new Scanner(" alpha  beta\tgamma\n\ndelta ").tokens().collect(Collectors.toList()));

		Scanner mixed = new Scanner("  7 rest of line\nnext\r\nafter crlf\rafter cr after ls");
		System.out.println(mixed.nextInt() + "[" + mixed.nextLine() + "][" + mixed.nextLine() + "][" + mixed.nextLine()
				+ "][" + mixed.nextLine() + "][" + mixed.nextLine() + "]" + mixed.hasNextLine());
		Scanner failed = new Scanner("   word 5");
		try {
			failed.nextInt();
		} catch (InputMismatchException e) {
			System.out.println("mismatch, line is now [" + failed.nextLine() + "]");
		}
		Scanner blank = new Scanner("   \n  ");
		System.out.println(blank.hasNext() + " " + blank.hasNextLine() + " [" + blank.nextLine() + "]");
		try {
			blank.next();
		} catch (NoSuchElementException e) {
			// The failed next() consumed the trailing blanks
			System.out.println(e.getClass().getName() + " then " + blank.hasNextLine());
		}
		Scanner empty = new Scanner("");
		System.out.println(empty.hasNext() + " " + empty.hasNextLine());
		Scanner bools = new Scanner("TRUE false yes");
		System.out.println(bools.nextBoolean() + " " + bools.nextBoolean() + " " + bools.hasNextBoolean());
		try {
			bools.nextBoolean();
		} catch (InputMismatchException e) {
			System.out.println("InputMismatchException " + e.getMessage());
		}
		try {
			new Scanner(System.in, "no-such-charset");
		} catch (IllegalArgumentException e) {
			System.out.println(e.getMessage());
		}
		Scanner iterated = new Scanner("one two");
		iterated.forEachRemaining(s -> System.out.println("token " + s));
	}
}
