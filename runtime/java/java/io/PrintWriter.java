package java.io;

import java.nio.charset.Charset;

/** Prints to a Writer; IOExceptions only set the error flag, as in Java SE */
public class PrintWriter extends Writer {
	protected Writer out;
	private final boolean autoFlush;
	private boolean trouble = false;
	private PrintStream psOut = null;

	public PrintWriter(Writer out) {
		this(out, false);
	}

	public PrintWriter(Writer out, boolean autoFlush) {
		super(out);
		this.out = out;
		this.autoFlush = autoFlush;
	}

	public PrintWriter(OutputStream out) {
		this(out, false);
	}

	public PrintWriter(OutputStream out, boolean autoFlush) {
		this(out, autoFlush, Charset.defaultCharset());
	}

	public PrintWriter(OutputStream out, boolean autoFlush, Charset charset) {
		this(new BufferedWriter(new OutputStreamWriter(out, charset)), autoFlush);
		if (out instanceof PrintStream) {
			psOut = (PrintStream) out;
		}
	}

	public PrintWriter(String fileName) throws FileNotFoundException {
		this(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName))), false);
	}

	public PrintWriter(String fileName, String csn) throws FileNotFoundException, UnsupportedEncodingException {
		this(toCharset(csn), new File(fileName));
	}

	public PrintWriter(String fileName, Charset charset) throws IOException {
		this(java.util.Objects.requireNonNull(charset, "charset"), new File(fileName));
	}

	public PrintWriter(File file) throws FileNotFoundException {
		this(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file))), false);
	}

	public PrintWriter(File file, String csn) throws FileNotFoundException, UnsupportedEncodingException {
		this(toCharset(csn), file);
	}

	public PrintWriter(File file, Charset charset) throws IOException {
		this(java.util.Objects.requireNonNull(charset, "charset"), file);
	}

	private PrintWriter(Charset charset, File file) throws FileNotFoundException {
		this(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), charset)), false);
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

	public void flush() {
		try {
			ensureOpen();
			out.flush();
		} catch (IOException x) {
			trouble = true;
		}
	}

	public void close() {
		try {
			if (out != null) {
				out.close();
				out = null;
			}
		} catch (IOException x) {
			trouble = true;
		}
	}

	public boolean checkError() {
		if (out != null) {
			flush();
		}
		if (out instanceof PrintWriter) {
			return ((PrintWriter) out).checkError();
		} else if (psOut != null) {
			return psOut.checkError();
		}
		return trouble;
	}

	protected void setError() {
		trouble = true;
	}

	protected void clearError() {
		trouble = false;
	}

	public void write(int c) {
		try {
			ensureOpen();
			out.write(c);
		} catch (IOException x) {
			trouble = true;
		}
	}

	public void write(char[] buf, int off, int len) {
		try {
			ensureOpen();
			out.write(buf, off, len);
		} catch (IOException x) {
			trouble = true;
		}
	}

	public void write(char[] buf) {
		write(buf, 0, buf.length);
	}

	public void write(String s, int off, int len) {
		try {
			ensureOpen();
			out.write(s, off, len);
		} catch (IOException x) {
			trouble = true;
		}
	}

	public void write(String s) {
		write(s, 0, s.length());
	}

	private void newLine() {
		try {
			ensureOpen();
			out.write(System.lineSeparator());
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
		write(c);
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

	public void println(boolean x) {
		print(x);
		println();
	}

	public void println(char x) {
		print(x);
		println();
	}

	public void println(int x) {
		print(x);
		println();
	}

	public void println(long x) {
		print(x);
		println();
	}

	public void println(float x) {
		print(x);
		println();
	}

	public void println(double x) {
		print(x);
		println();
	}

	public void println(char[] x) {
		print(x);
		println();
	}

	public void println(String x) {
		print(x);
		println();
	}

	public void println(Object x) {
		String s = String.valueOf(x);
		print(s);
		println();
	}

	public PrintWriter printf(String format, Object... args) {
		return format(format, args);
	}

	/** Like the JVM, writes the text formatted before a failing specifier */
	public PrintWriter format(String format, Object... args) {
		try {
			ensureOpen();
			java.util.Formatter formatter = new java.util.Formatter();
			try {
				formatter.format(format, args);
			} finally {
				out.write(formatter.toString());
			}
			if (autoFlush) {
				out.flush();
			}
		} catch (IOException x) {
			trouble = true;
		}
		return this;
	}

	public PrintWriter append(CharSequence csq) {
		write(String.valueOf(csq));
		return this;
	}

	public PrintWriter append(CharSequence csq, int start, int end) {
		if (csq == null) {
			csq = "null";
		}
		return append(csq.subSequence(start, end));
	}

	public PrintWriter append(char c) {
		write(c);
		return this;
	}
}
