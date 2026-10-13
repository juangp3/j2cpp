import java.util.Formatter;

/** String.format, String.formatted, printf and Formatter, compared with the JVM */
public class FormatDemo {
	static void show(String format, Object... args) {
		try {
			System.out.println(format + " -> [" + String.format(format, args) + "]");
		} catch (RuntimeException e) {
			System.out.println(format + " -> " + e.getClass().getName() + ": " + e.getMessage());
		}
	}

	public static void main(String[] args) {
		strings();
		integers();
		floats();
		general();
		errors();
		other();
	}

	static void strings() {
		show("plain text");
		show("%s and %s", "a", "b");
		show("%S", "mixed Case");
		show("[%10s] [%-10s] [%.2s] [%8.3s] [%-8.3s]", "right", "left", "truncate", "abcdef", "abcdef");
		show("%s %s %s", null, 12, 'c');
		show("%2$s %1$s %2$s", "one", "two");
		show("%s %<s %s %<S", "x", "y");
		show("%b %b %b %b %B", true, false, null, "text", true);
		show("%5b|%-6b|%.2b", true, false, true);
		show("%c %c %c %C", 'a', (int) 'B', (byte) 65, 'z');
		System.out.println(String.format("%c", 0x1F600).length());
		show("%3c|%-3c|", 'x', 'y');
		show("%h %H", "hi", "hi");
		show("%h", (Object) null);
		show("100%% %n%%");
		show("[%5%] [%-5%]");
		show("%s", new int[0].length);
		show("%-6s|%6s|", null, true);
	}

	static void integers() {
		show("%d %d %d %d", 0, -1, Integer.MAX_VALUE, Long.MIN_VALUE);
		show("%d %d", (byte) -5, (short) 300);
		show("[%5d] [%-5d] [%05d] [%+d] [%+d] [% d] [% d]", 42, 42, 42, 42, -42, 42, -42);
		show("[%,d] [%,d] [%,d] [%,d]", 1234567, -1234567, 999, Long.MAX_VALUE);
		show("[%(d] [%(d] [%(5d] [%(05d] [%+(d]", -42, 42, -7, -7, 5);
		show("[%,010d] [%0,10d] [%-,10d] [%,(12d]", 1234567, -1234567, 1234, -1234567);
		show("[%x] [%X] [%x] [%x] [%x]", 255, 255, -1, -1L, 0);
		show("[%#x] [%#X] [%08x] [%#08x] [%-8x|]", 255, 255, 255, 255, 255);
		show("[%o] [%#o] [%o] [%o]", 8, 8, -8, -8L);
		show("[%x] [%x] [%o] [%x]", (byte) -1, (short) -1, (byte) -1, (byte) 10);
		show("%d", (Object) null);
		show("%5d|%-5x|", null, null);
	}

	static void floats() {
		show("%f %f %f %f", 0.0, 1.0, -1.5, 3.14159265358979);
		show("%.2f %.2f %.2f %.2f", 0.125, 1.005, 2.675, 0.375);
		show("%.0f %.0f %.0f %.0f %.0f", 0.5, 1.5, 2.5, -0.5, 0.4);
		show("%.1f %.1f %.1f %.3f", 0.05, 0.25, 0.95, 1e-5);
		show("%f %f", 1e20, 1e-5);
		show("%.20f", 0.1);
		show("%f", Double.MAX_VALUE);
		show("%f", Double.MIN_VALUE);
		show("%f %.0f %.2f %e", -0.0, -0.0, -0.001, -0.0);
		show("%f %f %f", Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY);
		show("[%+f] [%(f] [%010f] [%+f] [% f] [%(f]", Double.NaN, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
				Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN);
		show("[%,.2f] [%,.2f] [%,f] [%,.0f]", 123456.789, -1234567.891, 1000.0, 999999.5);
		show("[%10.3f] [%-10.3f] [%010.3f] [%+.3f] [% .3f] [%(.3f] [%(010.2f]", 3.14159, 3.14159, -3.14159, 3.14159,
				3.14159, -3.14159, -3.14159);
		show("[%#.0f] [%.0f] [%#.0e]", 3.0, 3.0, 3.0);
		show("%e %e %e %e", 12345.678, 0.0, 1.0, -0.000123456);
		show("%.2e %.0e %.0e %E %.3E", 12345.678, 9.5, 1.5, 1e100, 1e-100);
		show("%e %e", 9.9999999e10, 1e-300);
		show("[%12.4e] [%-12.4e] [%012.4e] [%+e] [%(e]", 1234.5, 1234.5, -1234.5, 1.0, -1.0);
		show("%f %.3f %e", 1.1f, 0.1f, 3.4028235e38f);
		show("%.10f", 0.1f);
		show("%.15f %.17f %.3f", 0.1, 0.3, 1234567890123.4567);
		show("%.2f %.2f %.2f", 0.995, 9.995, 99.995);
		show("%.1f %.4f %.6f", 1e7, 123456789.0, 1e-7);
		show("%f %f", 123456789012345678.0, 1.0e16);
	}

	static void general() {
		show("%g %g %g %g", 0.0, 1.0, 123456.0, 1234567.0);
		show("%g %g %g %g", 0.0001, 0.00001, 0.000123456, 99999.95);
		show("%.3g %.3g %.3g %.0g %.1g", 1234.0, 0.00012345, 999.5, 5.0, 0.05);
		show("%G %G %g %g", 1e-10, Double.POSITIVE_INFINITY, Double.NaN, -0.0);
		show("[%12.3g] [%-12.3g] [%012.3g] [%+g] [%(g] [%,g]", 1234.0, 1234.0, -1234.0, 1.0, -2.0, 1234567.0);
		show("%g %g", 999999.5, 9999995.0);
	}

	static void errors() {
		show("%q", 1);
		show("%");
		show("abc %");
		show("%-");
		show("%5");
		show("%.f", 1.0);
		show("%s %s", "one");
		show("%2$s", "one");
		show("%<s", "one");
		show("%d", "text");
		show("%d", 1.5);
		show("%f", 1);
		show("%x", 'c');
		show("%c", "str");
		show("%c", 1.0);
		show("%c", -1);
		show("%b", 1);
		show("%#s", "x");
		show("%#d", 1);
		show("%,x", 1);
		show("%,e", 1.0);
		show("%#g", 1.0);
		show("%+s", "x");
		show("%0s", "x");
		show("%(x", 1);
		show("%+x", 1);
		show("% x", 1);
		show("%+ d", 1);
		show("%-05d", 1);
		show("%-d", 1);
		show("%0d", 1);
		show("%-s", "x");
		show("%.2d", 1);
		show("%.2c", 'c');
		show("%.2%");
		show("%5n");
		show("%-n");
		show("%--5d", 1);
		show("%0$s", "x");
		show("%#c", 'c');
		show("%,c", 'c');
		show("%#b", true);
		show("%-%");
		show("%#%");
		show("%#h", "x");
		show("%,s", "x");
	}

	static void other() {
		System.out.println("%d items cost %.2f".formatted(3, 9.5));
		System.out.printf("printf %s %05d%n", "works", 42);
		System.out.format("format %x%n", 48879).printf("chained %b%n", true);
		Formatter formatter = new Formatter();
		formatter.format("%s=%d", "a", 1).format(", %s=%d", "b", 2);
		System.out.println(formatter.toString());
		System.out.println(formatter);
		System.out.println(String.format("%s", (Object) new String[] { "array" }.length));
		System.out.println(String.format("%08.2f|%-8.2f|", -1.5, 1.5));
		System.out.println(String.format("%,d %,d", 0, -999));
		System.out.println(String.format("%s", 1.0f) + String.format("%s", 100.0) + String.format("%s", 1e21));
		System.out.println(String.format("%3$s %1$s %s %s", "a", "b", "c"));
		Object[] none = null;
		System.out.println(String.format("no args", none));
		show("[%a] [%A] [%015a] [%+a] [% a] [%-12a] [%a] [%a]", 1.0, -255.5, -1.5, 2.0, 2.0, 0.5, Double.MIN_VALUE,
				Double.MIN_NORMAL);
		show("[%a] [%a] [%a] [%010a] [%+a] [%a] [%a]", -0.0, Double.NEGATIVE_INFINITY, 0.1, Double.NaN,
				Double.POSITIVE_INFINITY, 0.1f, -1.0f);
		show("%s", "x", "extra");
		show("[%.3S] [%10.4B] [%X] [%-+(,10d] [%c]", null, true, null, -1234, (short) 66);
		show("%2$s %<s %s", "a", "b");
		show("%c", (byte) -3);
		show("%-+(,#5x", 1);
		show("%D", 1);
		show("%F", 1.0);
		show("%t", 1);
		try {
			System.out.printf("partial %d %d", 1, "x");
		} catch (java.util.IllegalFormatConversionException e) {
			System.out.println("| " + e.getMessage());
		}
		try {
			System.out.printf("nothing %d %q", 1, 2);
		} catch (java.util.UnknownFormatConversionException e) {
			System.out.println("| " + e.getMessage());
		}
		Formatter closed = new Formatter();
		closed.close();
		try {
			closed.format("x");
		} catch (java.util.FormatterClosedException e) {
			System.out.println("closed: " + e);
		}
		java.util.IllegalFormatException base = new java.util.UnknownFormatConversionException("z");
		System.out.println(base instanceof IllegalArgumentException);
		System.out.println(new java.util.IllegalFormatConversionException('d', String.class).getArgumentClass());
		double[] values = { 0.001, 0.01, 0.1, 1, 12.5, 999.999, 1e6, 1.25e7, 3e-3, 7.77e-7 };
		for (double v : values) {
			System.out.println(String.format("%s|%f|%.3f|%e|%.2e|%g|%.3g|%,.1f|%12.4f|%-12.2e|", v, v, v, v, v, v, v, v,
					v, v));
		}
		long[] longs = { 0, 1, -1, 255, -256, 65535, 1L << 40, Long.MAX_VALUE, Integer.MIN_VALUE };
		for (long n : longs) {
			System.out.println(String.format("%d|%,d|%x|%o|%X|%#x|%+d|%(d|%015d|%-8d|", n, n, n, n, n, n, n, n, n, n));
		}
	}
}
