package java.io;

public class BufferedWriter extends Writer {
	private Writer out;
	private char[] cb;
	private int nChars;
	private int nextChar;

	public BufferedWriter(Writer out) {
		this(out, 8192);
	}

	public BufferedWriter(Writer out, int sz) {
		super(out);
		if (sz <= 0) {
			throw new IllegalArgumentException("Buffer size <= 0");
		}
		this.out = out;
		cb = new char[sz];
		nChars = sz;
		nextChar = 0;
	}

	private void ensureOpen() throws IOException {
		if (out == null) {
			throw new IOException("Stream closed");
		}
	}

	void flushBuffer() throws IOException {
		ensureOpen();
		if (nextChar == 0) {
			return;
		}
		out.write(cb, 0, nextChar);
		nextChar = 0;
	}

	public void write(int c) throws IOException {
		ensureOpen();
		if (nextChar >= nChars) {
			flushBuffer();
		}
		cb[nextChar++] = (char) c;
	}

	public void write(char[] cbuf, int off, int len) throws IOException {
		ensureOpen();
		java.util.Objects.checkFromIndexSize(off, len, cbuf.length);
		if (len == 0) {
			return;
		}
		if (len >= nChars) {
			flushBuffer();
			out.write(cbuf, off, len);
			return;
		}
		int b = off;
		int t = off + len;
		while (b < t) {
			int d = Math.min(nChars - nextChar, t - b);
			System.arraycopy(cbuf, b, cb, nextChar, d);
			b += d;
			nextChar += d;
			if (nextChar >= nChars) {
				flushBuffer();
			}
		}
	}

	public void write(String s, int off, int len) throws IOException {
		ensureOpen();
		int b = off;
		int t = off + len;
		while (b < t) {
			int d = Math.min(nChars - nextChar, t - b);
			s.getChars(b, b + d, cb, nextChar);
			b += d;
			nextChar += d;
			if (nextChar >= nChars) {
				flushBuffer();
			}
		}
	}

	public void newLine() throws IOException {
		write(System.lineSeparator());
	}

	public void flush() throws IOException {
		flushBuffer();
		out.flush();
	}

	public void close() throws IOException {
		if (out == null) {
			return;
		}
		Writer w = out;
		try {
			flushBuffer();
		} finally {
			out = null;
			cb = null;
			w.close();
		}
	}
}
