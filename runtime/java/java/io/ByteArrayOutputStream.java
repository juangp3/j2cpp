package java.io;

public class ByteArrayOutputStream extends OutputStream {
	protected byte[] buf;
	protected int count;

	public ByteArrayOutputStream() {
		this(32);
	}

	public ByteArrayOutputStream(int size) {
		if (size < 0) {
			throw new IllegalArgumentException("Negative initial size: " + size);
		}
		buf = new byte[size];
	}

	private void ensureCapacity(int minCapacity) {
		if (minCapacity > buf.length) {
			int newCapacity = Math.max(minCapacity, buf.length * 2);
			if (newCapacity < 0) {
				throw new OutOfMemoryError("Required array length too large");
			}
			byte[] larger = new byte[newCapacity];
			System.arraycopy(buf, 0, larger, 0, count);
			buf = larger;
		}
	}

	public void write(int b) {
		ensureCapacity(count + 1);
		buf[count] = (byte) b;
		count += 1;
	}

	public void write(byte[] b, int off, int len) {
		java.util.Objects.checkFromIndexSize(off, len, b.length);
		ensureCapacity(count + len);
		System.arraycopy(b, off, buf, count, len);
		count += len;
	}

	public void writeBytes(byte[] b) {
		write(b, 0, b.length);
	}

	public void writeTo(OutputStream out) throws IOException {
		out.write(buf, 0, count);
	}

	public void reset() {
		count = 0;
	}

	public byte[] toByteArray() {
		byte[] result = new byte[count];
		System.arraycopy(buf, 0, result, 0, count);
		return result;
	}

	public int size() {
		return count;
	}

	public String toString() {
		return new String(buf, 0, count);
	}

	public String toString(String charsetName) throws UnsupportedEncodingException {
		return new String(buf, 0, count, charsetName);
	}

	public String toString(java.nio.charset.Charset charset) {
		return new String(buf, 0, count, charset);
	}

	public void close() throws IOException {
	}
}
