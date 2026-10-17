package java.io;

public abstract class OutputStream implements Closeable, Flushable {
	public OutputStream() {
	}

	public static OutputStream nullOutputStream() {
		return new OutputStream() {
			private boolean closed;

			public void write(int b) throws IOException {
				if (closed) {
					throw new IOException("Stream closed");
				}
			}

			public void write(byte[] b, int off, int len) throws IOException {
				java.util.Objects.checkFromIndexSize(off, len, b.length);
				if (closed) {
					throw new IOException("Stream closed");
				}
			}

			public void close() {
				closed = true;
			}
		};
	}

	public abstract void write(int b) throws IOException;

	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	public void write(byte[] b, int off, int len) throws IOException {
		java.util.Objects.checkFromIndexSize(off, len, b.length);
		for (int i = 0; i < len; i++) {
			write(b[off + i]);
		}
	}

	public void flush() throws IOException {
	}

	public void close() throws IOException {
	}
}
