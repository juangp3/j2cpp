package java.io;

public abstract class Writer implements Appendable, Closeable, Flushable {
	private char[] writeBuffer;
	private static final int WRITE_BUFFER_SIZE = 1024;

	protected Object lock;

	protected Writer() {
		this.lock = this;
	}

	protected Writer(Object lock) {
		if (lock == null) {
			throw new NullPointerException();
		}
		this.lock = lock;
	}

	public static Writer nullWriter() {
		return new Writer() {
			private boolean closed;

			private void ensureOpen() throws IOException {
				if (closed) {
					throw new IOException("Stream closed");
				}
			}

			public void write(char[] cbuf, int off, int len) throws IOException {
				java.util.Objects.checkFromIndexSize(off, len, cbuf.length);
				ensureOpen();
			}

			public void flush() throws IOException {
				ensureOpen();
			}

			public void close() {
				closed = true;
			}
		};
	}

	public void write(int c) throws IOException {
		if (writeBuffer == null) {
			writeBuffer = new char[WRITE_BUFFER_SIZE];
		}
		writeBuffer[0] = (char) c;
		write(writeBuffer, 0, 1);
	}

	public void write(char[] cbuf) throws IOException {
		write(cbuf, 0, cbuf.length);
	}

	public abstract void write(char[] cbuf, int off, int len) throws IOException;

	public void write(String str) throws IOException {
		write(str, 0, str.length());
	}

	public void write(String str, int off, int len) throws IOException {
		char[] cbuf;
		if (len <= WRITE_BUFFER_SIZE) {
			if (writeBuffer == null) {
				writeBuffer = new char[WRITE_BUFFER_SIZE];
			}
			cbuf = writeBuffer;
		} else {
			cbuf = new char[len];
		}
		str.getChars(off, (off + len), cbuf, 0);
		write(cbuf, 0, len);
	}

	public Writer append(CharSequence csq) throws IOException {
		write(String.valueOf(csq));
		return this;
	}

	public Writer append(CharSequence csq, int start, int end) throws IOException {
		if (csq == null) {
			csq = "null";
		}
		return append(csq.subSequence(start, end));
	}

	public Writer append(char c) throws IOException {
		write(c);
		return this;
	}

	public abstract void flush() throws IOException;

	public abstract void close() throws IOException;
}
