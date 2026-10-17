package java.io;

public abstract class InputStream implements Closeable {
	private static final int DEFAULT_BUFFER_SIZE = 8192;

	public InputStream() {
	}

	public static InputStream nullInputStream() {
		return new InputStream() {
			private boolean closed;

			private void ensureOpen() throws IOException {
				if (closed) {
					throw new IOException("Stream closed");
				}
			}

			public int available() throws IOException {
				ensureOpen();
				return 0;
			}

			public int read() throws IOException {
				ensureOpen();
				return -1;
			}

			public int read(byte[] b, int off, int len) throws IOException {
				java.util.Objects.checkFromIndexSize(off, len, b.length);
				if (len == 0) {
					return 0;
				}
				ensureOpen();
				return -1;
			}

			public void close() {
				closed = true;
			}
		};
	}

	public abstract int read() throws IOException;

	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	public int read(byte[] b, int off, int len) throws IOException {
		java.util.Objects.checkFromIndexSize(off, len, b.length);
		if (len == 0) {
			return 0;
		}
		int c = read();
		if (c == -1) {
			return -1;
		}
		b[off] = (byte) c;
		int i = 1;
		try {
			for (; i < len; i++) {
				c = read();
				if (c == -1) {
					break;
				}
				b[off + i] = (byte) c;
			}
		} catch (IOException e) {
			// Returns the bytes read before the failure, as the JDK does
		}
		return i;
	}

	public byte[] readAllBytes() throws IOException {
		return readNBytes(Integer.MAX_VALUE);
	}

	public byte[] readNBytes(int len) throws IOException {
		if (len < 0) {
			throw new IllegalArgumentException("len < 0");
		}
		byte[] result = new byte[Math.min(len, DEFAULT_BUFFER_SIZE)];
		int total = 0;
		while (total < len) {
			if (total == result.length) {
				int grown = (int) Math.min((long) len, Math.max(2L * result.length, DEFAULT_BUFFER_SIZE));
				if (grown <= total) {
					throw new OutOfMemoryError("Required array size too large");
				}
				byte[] larger = new byte[grown];
				System.arraycopy(result, 0, larger, 0, total);
				result = larger;
			}
			int n = read(result, total, Math.min(result.length, len) - total);
			if (n < 0) {
				break;
			}
			total += n;
		}
		if (total == result.length) {
			return result;
		}
		byte[] exact = new byte[total];
		System.arraycopy(result, 0, exact, 0, total);
		return exact;
	}

	public int readNBytes(byte[] b, int off, int len) throws IOException {
		java.util.Objects.checkFromIndexSize(off, len, b.length);
		int n = 0;
		while (n < len) {
			int count = read(b, off + n, len - n);
			if (count < 0) {
				break;
			}
			n += count;
		}
		return n;
	}

	public long skip(long n) throws IOException {
		long remaining = n;
		if (n <= 0) {
			return 0;
		}
		int size = (int) Math.min(2048, remaining);
		byte[] skipBuffer = new byte[size];
		while (remaining > 0) {
			int nr = read(skipBuffer, 0, (int) Math.min(size, remaining));
			if (nr < 0) {
				break;
			}
			remaining -= nr;
		}
		return n - remaining;
	}

	public void skipNBytes(long n) throws IOException {
		while (n > 0) {
			long ns = skip(n);
			if (ns > 0 && ns <= n) {
				n -= ns;
			} else if (ns == 0) {
				if (read() == -1) {
					throw new EOFException();
				}
				n--;
			} else {
				throw new IOException("Unable to skip exactly");
			}
		}
	}

	public int available() throws IOException {
		return 0;
	}

	public void close() throws IOException {
	}

	public void mark(int readlimit) {
	}

	public void reset() throws IOException {
		throw new IOException("mark/reset not supported");
	}

	public boolean markSupported() {
		return false;
	}

	public long transferTo(OutputStream out) throws IOException {
		java.util.Objects.requireNonNull(out, "out");
		long transferred = 0;
		byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
		int read;
		while ((read = this.read(buffer, 0, DEFAULT_BUFFER_SIZE)) >= 0) {
			out.write(buffer, 0, read);
			transferred += read;
		}
		return transferred;
	}
}
