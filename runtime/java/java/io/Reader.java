package java.io;

/** Simplified Reader: not Readable, since there is no CharBuffer */
public abstract class Reader implements Closeable {
	private static final int TRANSFER_BUFFER_SIZE = 8192;
	private static final int MAX_SKIP_BUFFER_SIZE = 8192;

	protected Object lock;

	protected Reader() {
		this.lock = this;
	}

	protected Reader(Object lock) {
		if (lock == null) {
			throw new NullPointerException();
		}
		this.lock = lock;
	}

	public static Reader nullReader() {
		return new Reader() {
			private boolean closed;

			private void ensureOpen() throws IOException {
				if (closed) {
					throw new IOException("Stream closed");
				}
			}

			public int read() throws IOException {
				ensureOpen();
				return -1;
			}

			public int read(char[] cbuf, int off, int len) throws IOException {
				java.util.Objects.checkFromIndexSize(off, len, cbuf.length);
				ensureOpen();
				return len == 0 ? 0 : -1;
			}

			public boolean ready() throws IOException {
				ensureOpen();
				return false;
			}

			public void close() {
				closed = true;
			}
		};
	}

	public int read() throws IOException {
		char[] cb = new char[1];
		if (read(cb, 0, 1) == -1) {
			return -1;
		}
		return cb[0];
	}

	public int read(char[] cbuf) throws IOException {
		return read(cbuf, 0, cbuf.length);
	}

	public abstract int read(char[] cbuf, int off, int len) throws IOException;

	public long skip(long n) throws IOException {
		if (n < 0L) {
			throw new IllegalArgumentException("skip value is negative");
		}
		int nn = (int) Math.min(n, MAX_SKIP_BUFFER_SIZE);
		char[] skipBuffer = new char[nn];
		long r = n;
		while (r > 0) {
			int nc = read(skipBuffer, 0, (int) Math.min(r, nn));
			if (nc == -1) {
				break;
			}
			r -= nc;
		}
		return n - r;
	}

	public boolean ready() throws IOException {
		return false;
	}

	public boolean markSupported() {
		return false;
	}

	public void mark(int readAheadLimit) throws IOException {
		throw new IOException("mark() not supported");
	}

	public void reset() throws IOException {
		throw new IOException("reset() not supported");
	}

	public abstract void close() throws IOException;

	public long transferTo(Writer out) throws IOException {
		java.util.Objects.requireNonNull(out, "out");
		long transferred = 0;
		char[] buffer = new char[TRANSFER_BUFFER_SIZE];
		int nRead;
		while ((nRead = read(buffer, 0, TRANSFER_BUFFER_SIZE)) >= 0) {
			out.write(buffer, 0, nRead);
			transferred += nRead;
		}
		return transferred;
	}
}
