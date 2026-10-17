package java.util;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Scanner without regular expressions: tokens are separated by
 * Character.isWhitespace characters, as with the default delimiter, or by a
 * literal string given to useDelimiter. Numbers follow the US locale
 * (grouping with ',', decimal '.'), as the JDK's patterns accept them for
 * Locale.US. Methods taking a Pattern or a regular expression (hasNext(String),
 * findInLine, skip, delimiter()), locales and the Readable and channel
 * constructors are absent; useDelimiter(String) throws
 * UnsupportedOperationException for a pattern that is not literal text.
 */
public final class Scanner implements Iterator<String>, Closeable {
	private static final int BUFFER_SIZE = 1024;

	private Reader source;
	private char[] buf = new char[BUFFER_SIZE];
	private int position;
	private int limit;
	private boolean sourceClosed;
	private boolean closed;
	private IOException lastException;
	/** null for whitespace, otherwise the literal delimiter */
	private String delimiter;
	private int defaultRadix = 10;

	private int tokenStart;
	private int tokenEnd;

	public Scanner(InputStream source) {
		this(new InputStreamReader(Objects.requireNonNull(source, "source")));
	}

	public Scanner(InputStream source, String charsetName) {
		this(source, toCharset(charsetName));
	}

	public Scanner(InputStream source, Charset charset) {
		this(new InputStreamReader(Objects.requireNonNull(source, "source"),
				Objects.requireNonNull(charset, "charset")));
	}

	public Scanner(File source) throws FileNotFoundException {
		this(source, Charset.defaultCharset());
	}

	public Scanner(File source, String charsetName) throws FileNotFoundException {
		this(source, toCharset(charsetName));
	}

	public Scanner(File source, Charset charset) throws FileNotFoundException {
		// As the JDK reads a File through a channel, malformed input ends the input
		this(new InputStreamReader(new FileInputStream(Objects.requireNonNull(source, "source")),
				Objects.requireNonNull(charset, "charset").newDecoder()));
	}

	public Scanner(java.nio.file.Path source) throws IOException {
		this(source, Charset.defaultCharset());
	}

	public Scanner(java.nio.file.Path source, String charsetName) throws IOException {
		this(source, toCharset(charsetName));
	}

	public Scanner(java.nio.file.Path source, Charset charset) throws IOException {
		this(new InputStreamReader(java.nio.file.Files.newInputStream(source),
				Objects.requireNonNull(charset, "charset")));
	}

	public Scanner(String source) {
		this(new StringReader(source));
	}

	private Scanner(Reader source) {
		this.source = source;
	}

	private static Charset toCharset(String csn) {
		Objects.requireNonNull(csn, "charsetName");
		try {
			return Charset.forName(csn);
		} catch (IllegalArgumentException e) {
			// UnsupportedCharsetException and IllegalCharsetNameException
			throw new IllegalArgumentException(e);
		}
	}

	private void ensureOpen() {
		if (closed) {
			throw new IllegalStateException("Scanner closed");
		}
	}

	private void readInput() {
		if (limit == buf.length) {
			if (position > 0) {
				System.arraycopy(buf, position, buf, 0, limit - position);
				limit -= position;
				position = 0;
			} else {
				char[] larger = new char[buf.length * 2];
				System.arraycopy(buf, 0, larger, 0, limit);
				buf = larger;
			}
		}
		int n;
		try {
			n = source.read(buf, limit, buf.length - limit);
		} catch (IOException ioe) {
			lastException = ioe;
			n = -1;
		}
		if (n == -1) {
			sourceClosed = true;
		} else if (n > 0) {
			limit += n;
		}
	}

	/** Whether the character at position + rel exists, reading input as needed */
	private boolean ensure(int rel) {
		while (position + rel >= limit) {
			if (sourceClosed) {
				return false;
			}
			readInput();
		}
		return true;
	}

	private boolean literalAt(int rel) {
		for (int k = 0; k < delimiter.length(); k++) {
			if (!ensure(rel + k) || buf[position + rel + k] != delimiter.charAt(k)) {
				return false;
			}
		}
		return true;
	}

	private boolean isDelimiterAt(int rel) {
		if (delimiter == null) {
			return Character.isWhitespace(buf[position + rel]);
		}
		return literalAt(rel);
	}

	/** The length of the delimiter at the position */
	private int delimiterLength() {
		if (delimiter != null) {
			return literalAt(0) ? delimiter.length() : 0;
		}
		int i = 0;
		while (ensure(i) && Character.isWhitespace(buf[position + i])) {
			i++;
		}
		return i;
	}

	/** The next token, without moving; null at the end. Sets tokenStart and tokenEnd. */
	private String scanToken() {
		int start = delimiterLength();
		tokenStart = start;
		tokenEnd = start;
		if (!ensure(start)) {
			return null;
		}
		int i = start;
		while (ensure(i) && !isDelimiterAt(i)) {
			i++;
		}
		tokenEnd = i;
		return new String(buf, position + start, i - start);
	}

	public void close() {
		if (closed) {
			return;
		}
		if (source instanceof Closeable) {
			try {
				((Closeable) source).close();
			} catch (IOException ioe) {
				lastException = ioe;
			}
		}
		sourceClosed = true;
		source = null;
		closed = true;
	}

	public IOException ioException() {
		return lastException;
	}

	/** Supports literal text only: characters, backslash-escaped punctuation, \t, \n and \r */
	public Scanner useDelimiter(String pattern) {
		StringBuilder literal = new StringBuilder();
		for (int i = 0; i < pattern.length(); i++) {
			char c = pattern.charAt(i);
			if (c == '\\' && i + 1 < pattern.length()) {
				char next = pattern.charAt(++i);
				if (next == 't') {
					literal.append('\t');
				} else if (next == 'n') {
					literal.append('\n');
				} else if (next == 'r') {
					literal.append('\r');
				} else if (!Character.isLetterOrDigit(next)) {
					literal.append(next);
				} else {
					throw unsupportedDelimiter(pattern);
				}
			} else if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
				throw unsupportedDelimiter(pattern);
			} else {
				literal.append(c);
			}
		}
		if (literal.length() == 0) {
			throw unsupportedDelimiter(pattern);
		}
		delimiter = literal.toString();
		return this;
	}

	private static UnsupportedOperationException unsupportedDelimiter(String pattern) {
		return new UnsupportedOperationException(
				"Scanner.useDelimiter takes only literal text in the J2C runtime, not \"" + pattern + "\"");
	}

	public int radix() {
		return defaultRadix;
	}

	public Scanner useRadix(int radix) {
		if ((radix < Character.MIN_RADIX) || (radix > Character.MAX_RADIX)) {
			throw new IllegalArgumentException("radix:" + radix);
		}
		defaultRadix = radix;
		return this;
	}

	public Scanner reset() {
		delimiter = null;
		defaultRadix = 10;
		return this;
	}

	public boolean hasNext() {
		ensureOpen();
		return scanToken() != null;
	}

	public String next() {
		ensureOpen();
		String token = scanToken();
		position += tokenStart;
		if (token == null) {
			throw new NoSuchElementException();
		}
		position += tokenEnd - tokenStart;
		return token;
	}

	public void remove() {
		throw new UnsupportedOperationException();
	}

	public boolean hasNextLine() {
		ensureOpen();
		return ensure(0);
	}

	public String nextLine() {
		ensureOpen();
		int i = 0;
		for (;;) {
			if (!ensure(i)) {
				if (i == 0) {
					throw new NoSuchElementException("No line found");
				}
				String line = new String(buf, position, i);
				position += i;
				return line;
			}
			char c = buf[position + i];
			if (c == '\n' || c == '\r' || c == '\u0085' || c == ' ' || c == ' ') {
				String line = new String(buf, position, i);
				int separator = 1;
				if (c == '\r' && ensure(i + 1) && buf[position + i + 1] == '\n') {
					separator = 2;
				}
				position += i + separator;
				return line;
			}
			i++;
		}
	}

	/** The next token's typed value, or why there is none */
	private abstract static class Parser<T> {
		abstract boolean matches(String token);

		abstract T parse(String token);
	}

	private <T> boolean hasNextOf(Parser<T> parser) {
		ensureOpen();
		String token = scanToken();
		if (token == null || !parser.matches(token)) {
			return false;
		}
		try {
			parser.parse(token);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private <T> T nextOf(Parser<T> parser) {
		ensureOpen();
		String token = scanToken();
		// The delimiters are skipped even when no token follows or it does not match
		position += tokenStart;
		if (token == null) {
			throw new NoSuchElementException();
		}
		if (!parser.matches(token)) {
			throw new InputMismatchException();
		}
		T value;
		try {
			value = parser.parse(token);
		} catch (NumberFormatException e) {
			throw new InputMismatchException(e.getMessage());
		}
		position += tokenEnd - tokenStart;
		return value;
	}

	private final class IntegerParser<T> extends Parser<T> {
		private final int radix;
		private final boolean isLong;

		IntegerParser(int radix, boolean isLong) {
			this.radix = radix;
			this.isLong = isLong;
		}

		boolean matches(String token) {
			return matchesInteger(token, radix);
		}

		@SuppressWarnings("unchecked")
		T parse(String token) {
			String s = withoutGroupSeparators(token);
			if (isLong) {
				return (T) Long.valueOf(Long.parseLong(s, radix));
			}
			return (T) Integer.valueOf(Integer.parseInt(s, radix));
		}
	}

	private final class FloatParser<T> extends Parser<T> {
		private final boolean isFloat;

		FloatParser(boolean isFloat) {
			this.isFloat = isFloat;
		}

		boolean matches(String token) {
			return matchesFloat(token);
		}

		@SuppressWarnings("unchecked")
		T parse(String token) {
			String s = processFloatToken(token);
			if (isFloat) {
				return (T) Float.valueOf(Float.parseFloat(s));
			}
			return (T) Double.valueOf(Double.parseDouble(s));
		}
	}

	private static final class BooleanParser extends Parser<Boolean> {
		boolean matches(String token) {
			return token.equalsIgnoreCase("true") || token.equalsIgnoreCase("false");
		}

		Boolean parse(String token) {
			return Boolean.valueOf(Boolean.parseBoolean(token));
		}
	}

	private static void checkRadix(int radix) {
		if ((radix < Character.MIN_RADIX) || (radix > Character.MAX_RADIX)) {
			throw new IllegalArgumentException("radix:" + radix);
		}
	}

	public boolean hasNextInt() {
		return hasNextInt(defaultRadix);
	}

	public boolean hasNextInt(int radix) {
		checkRadix(radix);
		return hasNextOf(new IntegerParser<Integer>(radix, false));
	}

	public int nextInt() {
		return nextInt(defaultRadix);
	}

	public int nextInt(int radix) {
		checkRadix(radix);
		return nextOf(new IntegerParser<Integer>(radix, false)).intValue();
	}

	public boolean hasNextLong() {
		return hasNextLong(defaultRadix);
	}

	public boolean hasNextLong(int radix) {
		checkRadix(radix);
		return hasNextOf(new IntegerParser<Long>(radix, true));
	}

	public long nextLong() {
		return nextLong(defaultRadix);
	}

	public long nextLong(int radix) {
		checkRadix(radix);
		return nextOf(new IntegerParser<Long>(radix, true)).longValue();
	}

	public boolean hasNextDouble() {
		return hasNextOf(new FloatParser<Double>(false));
	}

	public double nextDouble() {
		return nextOf(new FloatParser<Double>(false)).doubleValue();
	}

	public boolean hasNextFloat() {
		return hasNextOf(new FloatParser<Float>(true));
	}

	public float nextFloat() {
		return nextOf(new FloatParser<Float>(true)).floatValue();
	}

	public boolean hasNextBoolean() {
		return hasNextOf(new BooleanParser());
	}

	public boolean nextBoolean() {
		return nextOf(new BooleanParser()).booleanValue();
	}

	public Stream<String> tokens() {
		ensureOpen();
		final Scanner self = this;
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(this,
				Spliterator.ORDERED | Spliterator.NONNULL), false).onClose(new Runnable() {
					public void run() {
						self.close();
					}
				});
	}

	// The JDK's patterns for Locale.US, matched by hand:
	// integer: [-+]? (digit+ | non0Digit digit? digit? (',' digit digit digit)+)
	// float: [-+]? (numeral | numeral '.' digit* | '.' digit+) ([eE][-+]? digit+)?
	// | [-+]? 0[xX] hex* '.' hex+ ([pP][-+]? [0-9]+)? | [-+]? (NaN | Infinity | infinity sign)

	private static boolean isRadixDigit(char c, int radix) {
		return Character.isDigit(c) || (c < 128 && Character.digit(c, radix) >= 0);
	}

	/** Whether s[from, to) is a numeral: radix digits, or US grouping such as 1,234,567 */
	private static boolean isNumeral(String s, int from, int to, int radix) {
		if (from >= to) {
			return false;
		}
		boolean plain = true;
		for (int i = from; i < to; i++) {
			if (!isRadixDigit(s.charAt(i), radix)) {
				plain = false;
				break;
			}
		}
		if (plain) {
			return true;
		}
		// A nonzero digit and up to two more digits, then groups of a ',' and three digits
		char first = s.charAt(from);
		if (!Character.isDigit(first) || Character.digit(first, 10) == 0) {
			return false;
		}
		int i = from + 1;
		int lead = 1;
		while (i < to && lead < 3 && isRadixDigit(s.charAt(i), radix)) {
			i++;
			lead++;
		}
		int groups = 0;
		while (i < to) {
			if (to - i < 4 || s.charAt(i) != ',' || !isRadixDigit(s.charAt(i + 1), radix)
					|| !isRadixDigit(s.charAt(i + 2), radix) || !isRadixDigit(s.charAt(i + 3), radix)) {
				return false;
			}
			i += 4;
			groups++;
		}
		return groups > 0;
	}

	private static int signEnd(String s) {
		return s.length() > 0 && (s.charAt(0) == '-' || s.charAt(0) == '+') ? 1 : 0;
	}

	static boolean matchesInteger(String s, int radix) {
		return isNumeral(s, signEnd(s), s.length(), radix);
	}

	private static boolean isHex(char c) {
		return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
	}

	private static boolean allDigits(String s, int from, int to) {
		for (int i = from; i < to; i++) {
			if (!Character.isDigit(s.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static boolean matchesHexFloat(String s, int start) {
		int n = s.length();
		if (n - start < 3 || s.charAt(start) != '0' || (s.charAt(start + 1) != 'x' && s.charAt(start + 1) != 'X')) {
			return false;
		}
		int i = start + 2;
		while (i < n && isHex(s.charAt(i))) {
			i++;
		}
		if (i >= n || s.charAt(i) != '.') {
			return false;
		}
		i++;
		int fraction = i;
		while (i < n && isHex(s.charAt(i))) {
			i++;
		}
		if (i == fraction) {
			return false;
		}
		if (i == n) {
			return true;
		}
		if (s.charAt(i) != 'p' && s.charAt(i) != 'P') {
			return false;
		}
		i++;
		if (i < n && (s.charAt(i) == '-' || s.charAt(i) == '+')) {
			i++;
		}
		int digits = i;
		while (i < n && s.charAt(i) >= '0' && s.charAt(i) <= '9') {
			i++;
		}
		return i > digits && i == n;
	}

	static boolean matchesFloat(String s) {
		int n = s.length();
		int start = signEnd(s);
		String rest = s.substring(start);
		if (rest.equals("NaN") || rest.equals("Infinity") || rest.equals("∞")) {
			return true;
		}
		if (matchesHexFloat(s, start)) {
			return true;
		}
		int exponent = n;
		for (int i = start; i < n; i++) {
			if (s.charAt(i) == 'e' || s.charAt(i) == 'E') {
				exponent = i;
				break;
			}
		}
		if (exponent < n) {
			int digits = exponent + 1;
			if (digits < n && (s.charAt(digits) == '-' || s.charAt(digits) == '+')) {
				digits++;
			}
			if (digits == n || !allDigits(s, digits, n)) {
				return false;
			}
		}
		int point = s.indexOf('.', start);
		if (point < 0 || point > exponent) {
			return isNumeral(s, start, exponent, 10);
		}
		if (point == start) {
			return exponent > point + 1 && allDigits(s, point + 1, exponent);
		}
		return isNumeral(s, start, point, 10) && allDigits(s, point + 1, exponent);
	}

	private static String withoutGroupSeparators(String token) {
		if (token.indexOf(',') < 0) {
			return token;
		}
		StringBuilder sb = new StringBuilder(token.length());
		for (int i = 0; i < token.length(); i++) {
			if (token.charAt(i) != ',') {
				sb.append(token.charAt(i));
			}
		}
		return sb.toString();
	}

	private static String processFloatToken(String token) {
		// The JDK compares the token with the quoted pattern text "\Q∞\E", so
		// the infinity sign stays and parseDouble rejects it
		String result = withoutGroupSeparators(token);
		StringBuilder sb = new StringBuilder(result.length());
		for (int i = 0; i < result.length(); i++) {
			char c = result.charAt(i);
			if (Character.isDigit(c) && (c < '0' || c > '9')) {
				sb.append((char) ('0' + Character.digit(c, 10)));
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
}
