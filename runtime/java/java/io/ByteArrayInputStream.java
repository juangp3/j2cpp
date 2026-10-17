package java.io;

public class ByteArrayInputStream extends InputStream {
	protected byte[] buf;
	protected int pos;
	protected int mark;
	protected int count;

	public ByteArrayInputStream(byte[] buf) {
		this.buf = buf;
		this.pos = 0;
		this.count = buf.length;
	}

	public ByteArrayInputStream(byte[] buf, int offset, int length) {
		this.buf = buf;
		this.pos = offset;
		this.count = Math.min(offset + length, buf.length);
		this.mark = offset;
	}

	public int read() {
		return (pos < count) ? (buf[pos++] & 0xFF) : -1;
	}

	public int read(byte[] b, int off, int len) {
		java.util.Objects.checkFromIndexSize(off, len, b.length);
		if (pos >= count) {
			return -1;
		}
		int avail = count - pos;
		if (len > avail) {
			len = avail;
		}
		if (len <= 0) {
			return 0;
		}
		System.arraycopy(buf, pos, b, off, len);
		pos += len;
		return len;
	}

	public byte[] readAllBytes() {
		int n = count - pos;
		byte[] result = new byte[Math.max(n, 0)];
		if (n > 0) {
			System.arraycopy(buf, pos, result, 0, n);
		}
		pos = count;
		return result;
	}

	public int readNBytes(byte[] b, int off, int len) {
		int n = read(b, off, len);
		return n == -1 ? 0 : n;
	}

	public long transferTo(OutputStream out) throws IOException {
		int len = count - pos;
		out.write(buf, pos, len);
		pos = count;
		return len;
	}

	public long skip(long n) {
		long k = count - pos;
		if (n < k) {
			k = n < 0 ? 0 : n;
		}
		pos += (int) k;
		return k;
	}

	public int available() {
		return count - pos;
	}

	public boolean markSupported() {
		return true;
	}

	public void mark(int readAheadLimit) {
		mark = pos;
	}

	public void reset() {
		pos = mark;
	}

	public void close() throws IOException {
	}
}
