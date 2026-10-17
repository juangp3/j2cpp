package java.io;

public class StringReader extends Reader {
	private String str;
	private final int length;
	private int next = 0;
	private int mark = 0;

	public StringReader(String s) {
		this.length = s.length();
		this.str = s;
	}

	private void ensureOpen() throws IOException {
		if (str == null) {
			throw new IOException("Stream closed");
		}
	}

	public int read() throws IOException {
		ensureOpen();
		if (next >= length) {
			return -1;
		}
		return str.charAt(next++);
	}

	public int read(char[] cbuf, int off, int len) throws IOException {
		ensureOpen();
		java.util.Objects.checkFromIndexSize(off, len, cbuf.length);
		if (len == 0) {
			return 0;
		}
		if (next >= length) {
			return -1;
		}
		int n = Math.min(length - next, len);
		str.getChars(next, next + n, cbuf, off);
		next += n;
		return n;
	}

	public long skip(long n) throws IOException {
		ensureOpen();
		if (next >= length) {
			return 0;
		}
		long r = Math.min(length - next, n);
		r = Math.max(-next, r);
		next += (int) r;
		return r;
	}

	public boolean ready() throws IOException {
		ensureOpen();
		return true;
	}

	public boolean markSupported() {
		return true;
	}

	public void mark(int readAheadLimit) throws IOException {
		if (readAheadLimit < 0) {
			throw new IllegalArgumentException("Read-ahead limit < 0");
		}
		ensureOpen();
		mark = next;
	}

	public void reset() throws IOException {
		ensureOpen();
		next = mark;
	}

	public void close() {
		str = null;
	}
}
