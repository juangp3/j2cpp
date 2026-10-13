package java.util;

/**
 * Formatter, written from the Java SE specification: the format string syntax
 * behind String.format, String.formatted and PrintStream.printf.
 *
 * Supported conversions: b B h H s S c C d o x X e E f g G a A % n, with
 * argument indexes (%2$s), relative indexes (%<s), the flags - # + space 0 ,
 * and (, width and precision. Numbers are formatted like Locale.US: '.' as the
 * decimal separator and ',' between groups of three digits. Floating-point
 * conversions round half up from the digits of Double.toString, as the JDK
 * does.
 *
 * Limits: there is no Locale, Appendable or Formattable support, BigInteger
 * and BigDecimal arguments are rejected like any other wrong type, and the
 * date and time conversions (%t, %T) and %a with a precision throw
 * UnsupportedOperationException. Double.toString prints the shortest digits
 * that round-trip, so the few values for which JDK 17's digits differ also
 * format differently.
 */
public final class Formatter implements java.io.Closeable {
	private static final int LEFT = 1;
	private static final int ALTERNATE = 2;
	private static final int PLUS = 4;
	private static final int SPACE = 8;
	private static final int ZERO = 16;
	private static final int GROUP = 32;
	private static final int PARENTHESES = 64;
	private static final int PREVIOUS = 128;

	/** The flag characters, in the order Java lists them in messages */
	private static final String FLAG_CHARS = "-#+ 0,(<";

	private final StringBuilder out;
	private boolean closed;

	public Formatter() {
		out = new StringBuilder();
	}

	public Formatter format(String format, Object... args) {
		ensureOpen();
		format(out, format, args);
		return this;
	}

	public String toString() {
		ensureOpen();
		return out.toString();
	}

	public void flush() {
		ensureOpen();
	}

	public void close() {
		closed = true;
	}

	private void ensureOpen() {
		if (closed) {
			throw new FormatterClosedException();
		}
	}

	/** One format specifier: %[index$][flags][width][.precision]conversion */
	private static final class Spec {
		/** 1-based argument index, or 0 for the next ordinary argument */
		int index;
		int flags;
		int width = -1;
		int precision = -1;
		/** The conversion in lower case; upper is set for B, H, S, C, X, E, G, A */
		char conversion;
		boolean upper;
		/** The second character of a date conversion (%tY) */
		char dateConversion;

		boolean has(int flag) {
			return (flags & flag) != 0;
		}

		String conversionText() {
			char c = upper ? Character.toUpperCase(conversion) : conversion;
			return dateConversion != 0 ? "" + c + dateConversion : String.valueOf(c);
		}

		public String toString() {
			StringBuilder sb = new StringBuilder("%");
			if (index > 0 && !has(PREVIOUS)) {
				sb.append(index).append('$');
			}
			sb.append(flagText(flags));
			if (width != -1) {
				sb.append(width);
			}
			if (precision != -1) {
				sb.append('.').append(precision);
			}
			return sb.append(conversionText()).toString();
		}
	}

	private static String flagText(int flags) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < FLAG_CHARS.length(); i++) {
			if ((flags & (1 << i)) != 0) {
				sb.append(FLAG_CHARS.charAt(i));
			}
		}
		return sb.toString();
	}

	/** Parses the whole format first, so syntax errors come before any output */
	static void format(StringBuilder out, String format, Object[] args) {
		ArrayList<Object> parts = parse(format);
		int ordinary = 0;
		int last = -1;
		for (int p = 0; p < parts.size(); p++) {
			Object part = parts.get(p);
			if (part instanceof String) {
				out.append((String) part);
				continue;
			}
			Spec spec = (Spec) part;
			if (spec.conversion == '%') {
				justify(out, spec, "%");
				continue;
			}
			if (spec.conversion == 'n') {
				out.append('\n');
				continue;
			}
			int argument;
			if (spec.has(PREVIOUS)) {
				argument = last;
				if (argument < 0) {
					throw new MissingFormatArgumentException(spec.toString());
				}
			} else if (spec.index > 0) {
				argument = spec.index - 1;
			} else {
				argument = ordinary++;
			}
			if (args != null && argument >= args.length) {
				throw new MissingFormatArgumentException(spec.toString());
			}
			last = argument;
			print(out, spec, args == null ? null : args[argument]);
		}
	}

	private static boolean isDigit(char c) {
		return c >= '0' && c <= '9';
	}

	private static boolean isLetter(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
	}

	private static ArrayList<Object> parse(String format) {
		ArrayList<Object> parts = new ArrayList<Object>();
		int length = format.length();
		int i = 0;
		while (i < length) {
			int percent = format.indexOf('%', i);
			if (percent < 0) {
				parts.add(format.substring(i));
				break;
			}
			if (percent > i) {
				parts.add(format.substring(i, percent));
			}
			Spec spec = new Spec();
			i = parseSpec(format, percent, spec);
			check(spec);
			parts.add(spec);
		}
		return parts;
	}

	/** Fills spec from the specifier at start; returns the index after it */
	private static int parseSpec(String format, int start, Spec spec) {
		int length = format.length();
		int p = start + 1;
		int q = p;
		while (q < length && isDigit(format.charAt(q))) {
			q++;
		}
		if (q > p && q < length && format.charAt(q) == '$') {
			spec.index = parseNumber(format, p, q);
			if (spec.index <= 0) {
				throw new IllegalFormatArgumentIndexException(spec.index);
			}
			p = q + 1;
		}
		while (p < length) {
			int flag = FLAG_CHARS.indexOf(format.charAt(p));
			if (flag < 0) {
				break;
			}
			if ((spec.flags & (1 << flag)) != 0) {
				throw new DuplicateFormatFlagsException(String.valueOf(format.charAt(p)));
			}
			spec.flags |= 1 << flag;
			p++;
		}
		q = p;
		while (q < length && isDigit(format.charAt(q))) {
			q++;
		}
		if (q > p) {
			spec.width = parseNumber(format, p, q);
			if (spec.width < 0) {
				throw new IllegalFormatWidthException(spec.width);
			}
			p = q;
		}
		if (p < length && format.charAt(p) == '.') {
			q = p + 1;
			while (q < length && isDigit(format.charAt(q))) {
				q++;
			}
			if (q == p + 1) {
				throw unknownAt(format, start);
			}
			spec.precision = parseNumber(format, p + 1, q);
			if (spec.precision < 0) {
				throw new IllegalFormatPrecisionException(spec.precision);
			}
			p = q;
		}
		if (p >= length) {
			throw unknownAt(format, start);
		}
		char c = format.charAt(p);
		if (c == 't' || c == 'T') {
			if (p + 1 >= length || !isLetter(format.charAt(p + 1))) {
				throw new UnknownFormatConversionException(String.valueOf(c));
			}
			spec.upper = c == 'T';
			spec.conversion = 't';
			spec.dateConversion = format.charAt(p + 1);
			return p + 2;
		}
		if (!isLetter(c) && c != '%') {
			throw unknownAt(format, start);
		}
		if ("bhscdoxefgan%".indexOf(c) >= 0) {
			spec.conversion = c;
		} else if ("BHSCXEGA".indexOf(c) >= 0) {
			spec.conversion = Character.toLowerCase(c);
			spec.upper = true;
		} else {
			throw new UnknownFormatConversionException(String.valueOf(c));
		}
		return p + 1;
	}

	/** A specifier that does not parse names the character after the '%' */
	private static UnknownFormatConversionException unknownAt(String format, int start) {
		return new UnknownFormatConversionException(
				start + 1 < format.length() ? String.valueOf(format.charAt(start + 1)) : "%");
	}

	/** The decimal number in format[from, to), or Integer.MIN_VALUE on overflow */
	private static int parseNumber(String format, int from, int to) {
		long value = 0;
		for (int i = from; i < to; i++) {
			value = value * 10 + (format.charAt(i) - '0');
			if (value > Integer.MAX_VALUE) {
				return Integer.MIN_VALUE;
			}
		}
		return (int) value;
	}

	/** The checks that depend only on the specifier, made while parsing */
	private static void check(Spec spec) {
		switch (spec.conversion) {
		case 'b':
		case 'h':
		case 's':
			if (spec.conversion != 's' && spec.has(ALTERNATE)) {
				throw mismatch(ALTERNATE, spec);
			}
			if (spec.width == -1 && spec.has(LEFT)) {
				throw new MissingFormatWidthException(spec.toString());
			}
			checkBadFlags(spec, PLUS | SPACE | ZERO | GROUP | PARENTHESES);
			break;
		case 'c':
			if (spec.precision != -1) {
				throw new IllegalFormatPrecisionException(spec.precision);
			}
			checkBadFlags(spec, ALTERNATE | PLUS | SPACE | ZERO | GROUP | PARENTHESES);
			if (spec.width == -1 && spec.has(LEFT)) {
				throw new MissingFormatWidthException(spec.toString());
			}
			break;
		case 'd':
		case 'o':
		case 'x':
			checkNumeric(spec);
			if (spec.precision != -1) {
				throw new IllegalFormatPrecisionException(spec.precision);
			}
			checkBadFlags(spec, spec.conversion == 'd' ? ALTERNATE : GROUP);
			break;
		case 'e':
		case 'f':
		case 'g':
		case 'a':
			checkNumeric(spec);
			if (spec.conversion == 'e') {
				checkBadFlags(spec, GROUP);
			} else if (spec.conversion == 'g') {
				checkBadFlags(spec, ALTERNATE);
			} else if (spec.conversion == 'a') {
				checkBadFlags(spec, PARENTHESES | GROUP);
			}
			break;
		case '%':
			if (spec.precision != -1) {
				throw new IllegalFormatPrecisionException(spec.precision);
			}
			if ((spec.flags & ~LEFT) != 0) {
				throw new IllegalFormatFlagsException(flagText(spec.flags));
			}
			if (spec.width == -1 && spec.has(LEFT)) {
				throw new MissingFormatWidthException(spec.toString());
			}
			break;
		case 'n':
			if (spec.precision != -1) {
				throw new IllegalFormatPrecisionException(spec.precision);
			}
			if (spec.width != -1) {
				throw new IllegalFormatWidthException(spec.width);
			}
			if (spec.flags != 0) {
				throw new IllegalFormatFlagsException(flagText(spec.flags));
			}
			break;
		default:
			throw new UnsupportedOperationException(
					"J2C runtime: date and time conversions are not supported: " + spec);
		}
	}

	private static void checkNumeric(Spec spec) {
		if (spec.width == -1 && (spec.has(LEFT) || spec.has(ZERO))) {
			throw new MissingFormatWidthException(spec.toString());
		}
		if ((spec.has(PLUS) && spec.has(SPACE)) || (spec.has(LEFT) && spec.has(ZERO))) {
			throw new IllegalFormatFlagsException(flagText(spec.flags));
		}
	}

	/** Throws for the first of the given flags (in Java's order) the spec has */
	private static void checkBadFlags(Spec spec, int bad) {
		int present = spec.flags & bad;
		if (present == 0) {
			return;
		}
		int[] order = { ALTERNATE, PLUS, SPACE, ZERO, GROUP, PARENTHESES };
		for (int i = 0; i < order.length; i++) {
			if ((present & order[i]) != 0) {
				throw mismatch(order[i], spec);
			}
		}
	}

	private static FormatFlagsConversionMismatchException mismatch(int flag, Spec spec) {
		return new FormatFlagsConversionMismatchException(flagText(flag), spec.conversion);
	}

	private static void print(StringBuilder out, Spec spec, Object arg) {
		char c = spec.conversion;
		if (c == 'b') {
			String text = arg == null ? "false" : arg instanceof Boolean ? arg.toString() : "true";
			printText(out, spec, text);
			return;
		}
		if (arg == null) {
			printText(out, spec, "null");
			return;
		}
		switch (c) {
		case 'h':
			printText(out, spec, Integer.toHexString(arg.hashCode()));
			return;
		case 's':
			if (spec.has(ALTERNATE)) {
				throw mismatch(ALTERNATE, spec);
			}
			printText(out, spec, arg.toString());
			return;
		case 'c':
			printCharacter(out, spec, arg);
			return;
		case 'd':
		case 'o':
		case 'x':
			printInteger(out, spec, arg);
			return;
		default:
			printFloating(out, spec, arg);
		}
	}

	/** Text with the precision as its maximum length, in upper case for %S */
	private static void printText(StringBuilder out, Spec spec, String text) {
		if (spec.precision != -1 && spec.precision < text.length()) {
			text = text.substring(0, spec.precision);
		}
		justify(out, spec, spec.upper ? text.toUpperCase() : text);
	}

	private static void justify(StringBuilder out, Spec spec, String text) {
		int padding = spec.width - text.length();
		if (padding > 0 && !spec.has(LEFT)) {
			appendRepeated(out, ' ', padding);
		}
		out.append(text);
		if (padding > 0 && spec.has(LEFT)) {
			appendRepeated(out, ' ', padding);
		}
	}

	private static void appendRepeated(StringBuilder out, char c, int count) {
		for (int i = 0; i < count; i++) {
			out.append(c);
		}
	}

	private static IllegalFormatConversionException wrongType(Spec spec, Object arg) {
		return new IllegalFormatConversionException(spec.conversion, arg.getClass());
	}

	private static void printCharacter(StringBuilder out, Spec spec, Object arg) {
		String text;
		if (arg instanceof Character) {
			text = String.valueOf(((Character) arg).charValue());
		} else {
			int codePoint;
			if (arg instanceof Byte) {
				codePoint = ((Byte) arg).byteValue();
			} else if (arg instanceof Short) {
				codePoint = ((Short) arg).shortValue();
			} else if (arg instanceof Integer) {
				codePoint = ((Integer) arg).intValue();
			} else {
				throw wrongType(spec, arg);
			}
			if (!Character.isValidCodePoint(codePoint)) {
				throw new IllegalFormatCodePointException(codePoint);
			}
			text = new String(Character.toChars(codePoint));
		}
		printText(out, spec, text);
	}

	private static void printInteger(StringBuilder out, Spec spec, Object arg) {
		long value;
		int bits;
		if (arg instanceof Integer) {
			value = ((Integer) arg).intValue();
			bits = 32;
		} else if (arg instanceof Long) {
			value = ((Long) arg).longValue();
			bits = 64;
		} else if (arg instanceof Short) {
			value = ((Short) arg).shortValue();
			bits = 16;
		} else if (arg instanceof Byte) {
			value = ((Byte) arg).byteValue();
			bits = 8;
		} else {
			throw wrongType(spec, arg);
		}
		if (spec.conversion == 'd') {
			boolean negative = value < 0;
			String digits = Long.toString(value);
			if (negative) {
				digits = digits.substring(1);
			}
			printNumber(out, spec, negative, spec.has(GROUP) ? group(digits) : digits);
			return;
		}
		checkBadFlags(spec, PARENTHESES | SPACE | PLUS);
		if (value < 0 && bits < 64) {
			value += 1L << bits;
		}
		String digits = spec.conversion == 'x' ? Long.toHexString(value) : Long.toOctalString(value);
		String prefix = !spec.has(ALTERNATE) ? "" : spec.conversion == 'x' ? "0x" : "0";
		StringBuilder sb = new StringBuilder(prefix);
		if (spec.has(ZERO)) {
			appendRepeated(sb, '0', spec.width - prefix.length() - digits.length());
		}
		sb.append(digits);
		String text = sb.toString();
		justify(out, spec, spec.upper ? text.toUpperCase() : text);
	}

	/** Adds the sign or parentheses, zero padding, case and justification */
	private static void printNumber(StringBuilder out, Spec spec, boolean negative, String magnitude) {
		String prefix;
		String suffix = "";
		if (negative) {
			prefix = spec.has(PARENTHESES) ? "(" : "-";
			if (spec.has(PARENTHESES)) {
				suffix = ")";
			}
		} else {
			prefix = spec.has(PLUS) ? "+" : spec.has(SPACE) ? " " : "";
		}
		StringBuilder sb = new StringBuilder(prefix);
		if (spec.has(ZERO)) {
			appendRepeated(sb, '0', spec.width - prefix.length() - suffix.length() - magnitude.length());
		}
		sb.append(magnitude).append(suffix);
		String text = sb.toString();
		justify(out, spec, spec.upper ? text.toUpperCase() : text);
	}

	/** Digits with ',' between groups of three */
	private static String group(String digits) {
		StringBuilder sb = new StringBuilder();
		int length = digits.length();
		for (int i = 0; i < length; i++) {
			if (i > 0 && (length - i) % 3 == 0) {
				sb.append(',');
			}
			sb.append(digits.charAt(i));
		}
		return sb.toString();
	}

	private static void printFloating(StringBuilder out, Spec spec, Object arg) {
		double value;
		boolean isFloat = false;
		if (arg instanceof Double) {
			value = ((Double) arg).doubleValue();
		} else if (arg instanceof Float) {
			value = ((Float) arg).floatValue();
			isFloat = true;
		} else {
			throw wrongType(spec, arg);
		}
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			String text;
			if (Double.isNaN(value)) {
				text = "NaN";
			} else if (value < 0) {
				text = spec.has(PARENTHESES) ? "(Infinity)" : "-Infinity";
			} else {
				text = spec.has(PLUS) ? "+Infinity" : spec.has(SPACE) ? " Infinity" : "Infinity";
			}
			justify(out, spec, spec.upper ? text.toUpperCase() : text);
			return;
		}
		boolean negative = Double.compare(value, 0.0) < 0;
		double magnitude = Math.abs(value);
		if (spec.conversion == 'a') {
			printHex(out, spec, negative, isFloat ? Float.toHexString((float) magnitude) : Double.toHexString(magnitude));
			return;
		}
		Decimal d = new Decimal(magnitude);
		String text;
		if (spec.conversion == 'f') {
			int precision = spec.precision == -1 ? 6 : spec.precision;
			d.round(d.exponent + precision);
			text = d.fixed(precision, spec.has(GROUP), spec.has(ALTERNATE));
		} else if (spec.conversion == 'e') {
			int precision = spec.precision == -1 ? 6 : spec.precision;
			d.round(precision + 1);
			text = d.scientific(precision, spec.has(ALTERNATE));
		} else {
			int precision = spec.precision == -1 ? 6 : spec.precision == 0 ? 1 : spec.precision;
			if (d.digits.length() == 0) {
				text = d.fixed(precision - 1, spec.has(GROUP), false);
			} else {
				d.round(precision);
				int exponent = d.exponent - 1;
				if (exponent >= -4 && exponent < precision) {
					text = d.fixed(precision - 1 - exponent, spec.has(GROUP), false);
				} else {
					text = d.scientific(precision - 1, false);
				}
			}
		}
		printNumber(out, spec, negative, text);
	}

	/** %a: Double.toHexString's form; the precision is not supported */
	private static void printHex(StringBuilder out, Spec spec, boolean negative, String hex) {
		if (spec.precision != -1) {
			throw new UnsupportedOperationException("J2C runtime: %a with a precision is not supported: " + spec);
		}
		String prefix = negative ? "-" : spec.has(PLUS) ? "+" : spec.has(SPACE) ? " " : "";
		StringBuilder sb = new StringBuilder(prefix).append("0x");
		String rest = hex.substring(2);
		if (spec.has(ZERO)) {
			appendRepeated(sb, '0', spec.width - prefix.length() - 2 - rest.length());
		}
		String text = sb.append(rest).toString();
		justify(out, spec, spec.upper ? text.toUpperCase() : text);
	}

	/**
	 * A non-negative value as decimal digits: 0.digits times 10^exponent,
	 * starting from Double.toString's digits, without leading or trailing
	 * zeros (no digits for zero).
	 */
	private static final class Decimal {
		String digits;
		int exponent;

		Decimal(double magnitude) {
			String text = Double.toString(magnitude);
			int e = text.indexOf('E');
			int power = 0;
			if (e >= 0) {
				power = Integer.parseInt(text.substring(e + 1));
				text = text.substring(0, e);
			}
			int point = text.indexOf('.');
			StringBuilder sb = new StringBuilder();
			sb.append(text, 0, point).append(text, point + 1, text.length());
			int pointPosition = point + power;
			int start = 0;
			while (start < sb.length() && sb.charAt(start) == '0') {
				start++;
				pointPosition--;
			}
			int end = sb.length();
			while (end > start && sb.charAt(end - 1) == '0') {
				end--;
			}
			digits = sb.substring(start, end);
			exponent = digits.length() == 0 ? 0 : pointPosition;
		}

		/** Keeps the first count digits, rounding half up on the next one */
		void round(int count) {
			if (count >= digits.length()) {
				return;
			}
			boolean up = count >= 0 && digits.charAt(count) >= '5';
			char[] kept = digits.substring(0, Math.max(count, 0)).toCharArray();
			if (up) {
				int i = kept.length - 1;
				while (i >= 0 && kept[i] == '9') {
					kept[i] = '0';
					i--;
				}
				if (i >= 0) {
					kept[i]++;
					digits = new String(kept);
				} else {
					digits = "1" + new String(kept);
					exponent++;
				}
			} else {
				digits = new String(kept);
			}
			int end = digits.length();
			while (end > 0 && digits.charAt(end - 1) == '0') {
				end--;
			}
			digits = digits.substring(0, end);
			if (digits.length() == 0) {
				exponent = 0;
			}
		}

		/** The digit at position i (0 is the first), or '0' outside them */
		char digit(int i) {
			return i >= 0 && i < digits.length() ? digits.charAt(i) : '0';
		}

		String fixed(int precision, boolean group, boolean alternate) {
			StringBuilder whole = new StringBuilder();
			for (int i = 0; i < exponent; i++) {
				whole.append(digit(i));
			}
			if (whole.length() == 0) {
				whole.append('0');
			}
			StringBuilder sb = new StringBuilder(group ? Formatter.group(whole.toString()) : whole.toString());
			if (precision > 0 || alternate) {
				sb.append('.');
			}
			for (int i = 0; i < precision; i++) {
				sb.append(digit(exponent + i));
			}
			return sb.toString();
		}

		String scientific(int precision, boolean alternate) {
			StringBuilder sb = new StringBuilder();
			sb.append(digit(0));
			if (precision > 0 || alternate) {
				sb.append('.');
			}
			for (int i = 1; i <= precision; i++) {
				sb.append(digit(i));
			}
			int e = digits.length() == 0 ? 0 : exponent - 1;
			sb.append(e < 0 ? "e-" : "e+");
			int abs = Math.abs(e);
			if (abs < 10) {
				sb.append('0');
			}
			return sb.append(abs).toString();
		}
	}
}
