package java.io;

import java.nio.charset.Charset;

/**
 * Prints text through an OutputStreamWriter over this stream, so the bytes
 * of each print reach the underlying stream at once, as in the JDK.
 * IOExceptions only set the error flag. As in the JDK, each print, println
 * and printf holds the stream's monitor, so lines from threads do not mix.
 */
public class PrintStream extends FilterOutputStream implements Appendable, Closeable {
	private final boolean autoFlush;
	private boolean trouble = false;
	private OutputStreamWriter charOut;
	private boolean closing = false;

	public PrintStream(OutputStream out) {
		this(out, false);
	}

	public PrintStream(OutputStream out, boolean autoFlush) {
		this(autoFlush, requireNonNull(out), Charset.defaultCharset());
	}

	public PrintStream(OutputStream out, boolean autoFlush, String encoding) throws UnsupportedEncodingException {
		this(autoFlush, requireNonNull(out), toCharset(encoding));
	}

	public PrintStream(OutputStream out, boolean autoFlush, Charset charset) {
		this(autoFlush, requireNonNull(out), java.util.Objects.requireNonNull(charset, "charset"));
	}

	public PrintStream(String fileName) throws FileNotFoundException {
		this(false, new FileOutputStream(fileName), Charset.defaultCharset());
	}

	public PrintStream(String fileName, String csn) throws FileNotFoundException, UnsupportedEncodingException {
		this(false, toCharset(csn), new FileOutputStream(fileName));
	}

	public PrintStream(String fileName, Charset charset) throws IOException {
		this(false, java.util.Objects.requireNonNull(charset, "charset"), new FileOutputStream(fileName));
	}

	public PrintStream(File file) throws FileNotFoundException {
		this(false, new FileOutputStream(file), Charset.defaultCharset());
	}

	public PrintStream(File file, String csn) throws FileNotFoundException, UnsupportedEncodingException {
		this(false, toCharset(csn), new FileOutputStream(file));
	}

	public PrintStream(File file, Charset charset) throws IOException {
		this(false, java.util.Objects.requireNonNull(charset, "charset"), new FileOutputStream(file));
	}

	private PrintStream(boolean autoFlush, Charset charset, OutputStream out) {
		this(autoFlush, out, charset);
	}

	private PrintStream(boolean autoFlush, OutputStream out, Charset charset) {
		super(out);
		this.autoFlush = autoFlush;
		this.charOut = new OutputStreamWriter(this, charset);
	}

	private static OutputStream requireNonNull(OutputStream out) {
		if (out == null) {
			throw new NullPointerException("Null output stream");
		}
		return out;
	}

	private static Charset toCharset(String csn) throws UnsupportedEncodingException {
		java.util.Objects.requireNonNull(csn, "charsetName");
		try {
			return Charset.forName(csn);
		} catch (IllegalArgumentException e) {
			throw new UnsupportedEncodingException(csn);
		}
	}

	private void ensureOpen() throws IOException {
		if (out == null) {
			throw new IOException("Stream closed");
		}
	}

	public synchronized void flush() {
		try {
			ensureOpen();
			out.flush();
		} catch (IOException x) {
			trouble = true;
		}
	}

	public synchronized void close() {
		if (!closing) {
			closing = true;
			try {
				charOut.close();
				out.close();
			} catch (IOException x) {
				trouble = true;
			}
			charOut = null;
			out = null;
		}
	}

	public boolean checkError() {
		if (out != null) {
			flush();
		}
		if (out instanceof PrintStream) {
			return ((PrintStream) out).checkError();
		}
		return trouble;
	}

	protected void setError() {
		trouble = true;
	}

	protected void clearError() {
		trouble = false;
	}

	public synchronized void write(int b) {
		try {
			ensureOpen();
			out.write(b);
			if ((b == '\n') && autoFlush) {
				out.flush();
			}
		} catch (IOException x) {
			trouble = true;
		}
	}

	public synchronized void write(byte[] buf, int off, int len) {
		try {
			ensureOpen();
			out.write(buf, off, len);
			if (autoFlush) {
				out.flush();
			}
		} catch (IOException x) {
			trouble = true;
		}
	}

	public void write(byte[] buf) throws IOException {
		this.write(buf, 0, buf.length);
	}

	public void writeBytes(byte[] buf) {
		this.write(buf, 0, buf.length);
	}

	private synchronized void write(char[] buf) {
		try {
			ensureOpen();
			charOut.write(buf);
			charOut.flushBuffer();
			if (autoFlush) {
				for (int i = 0; i < buf.length; i++) {
					if (buf[i] == '\n') {
						out.flush();
						break;
					}
				}
			}
		} catch (IOException x) {
			trouble = true;
		}
	}

	private synchronized void write(String s) {
		try {
			ensureOpen();
			charOut.write(s);
			charOut.flushBuffer();
			if (autoFlush && (s.indexOf('\n') >= 0)) {
				out.flush();
			}
		} catch (IOException x) {
			trouble = true;
		}
	}

	private synchronized void newLine() {
		try {
			ensureOpen();
			charOut.write(System.lineSeparator());
			charOut.flushBuffer();
			if (autoFlush) {
				out.flush();
			}
		} catch (IOException x) {
			trouble = true;
		}
	}

	public void print(boolean b) {
		write(String.valueOf(b));
	}

	public void print(char c) {
		write(String.valueOf(c));
	}

	public void print(int i) {
		write(String.valueOf(i));
	}

	public void print(long l) {
		write(String.valueOf(l));
	}

	public void print(float f) {
		write(String.valueOf(f));
	}

	public void print(double d) {
		write(String.valueOf(d));
	}

	public void print(char[] s) {
		write(s);
	}

	public void print(String s) {
		write(String.valueOf(s));
	}

	public void print(Object obj) {
		write(String.valueOf(obj));
	}

	public void println() {
		newLine();
	}

	// As in the JDK, a PrintStream writes the text and the line end together;
	// a subclass gets print and then the line end, so its overrides see both

	private boolean exact() {
		return getClass() == PrintStream.class;
	}

	private synchronized void writeln(String s) {
		try {
			ensureOpen();
			charOut.write(s);
			charOut.write(System.lineSeparator());
			charOut.flushBuffer();
			if (autoFlush) {
				out.flush();
			}
		} catch (IOException x) {
			trouble = true;
		}
	}

	public void println(boolean x) {
		if (exact()) {
			writeln(String.valueOf(x));
		} else {
			print(x);
			newLine();
		}
	}

	public void println(char x) {
		if (exact()) {
			writeln(String.valueOf(x));
		} else {
			print(x);
			newLine();
		}
	}

	public void println(int x) {
		if (exact()) {
			writeln(String.valueOf(x));
		} else {
			print(x);
			newLine();
		}
	}

	public void println(long x) {
		if (exact()) {
			writeln(String.valueOf(x));
		} else {
			print(x);
			newLine();
		}
	}

	public void println(float x) {
		if (exact()) {
			writeln(String.valueOf(x));
		} else {
			print(x);
			newLine();
		}
	}

	public void println(double x) {
		if (exact()) {
			writeln(String.valueOf(x));
		} else {
			print(x);
			newLine();
		}
	}

	public void println(char[] x) {
		if (exact()) {
			writeln(new String(x));
		} else {
			print(x);
			newLine();
		}
	}

	public void println(String x) {
		if (exact()) {
			writeln(String.valueOf(x));
		} else {
			print(x);
			newLine();
		}
	}

	public void println(Object x) {
		String s = String.valueOf(x);
		if (exact()) {
			writeln(s);
		} else {
			print(s);
			newLine();
		}
	}

	public PrintStream printf(String format, Object... args) {
		return format(format, args);
	}

	/** Like the JVM, prints the text formatted before a failing specifier */
	public synchronized PrintStream format(String format, Object... args) {
		java.util.Formatter formatter = new java.util.Formatter();
		try {
			formatter.format(format, args);
		} finally {
			print(formatter.toString());
		}
		return this;
	}

	public PrintStream append(CharSequence csq) {
		print(String.valueOf(csq));
		return this;
	}

	public PrintStream append(CharSequence csq, int start, int end) {
		if (csq == null) {
			csq = "null";
		}
		return append(csq.subSequence(start, end));
	}

	public PrintStream append(char c) {
		print(c);
		return this;
	}
}
