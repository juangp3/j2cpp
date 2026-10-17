package java.io;

/** Reads a file descriptor with read(2); no FileChannel */
public class FileInputStream extends InputStream {
	private final FileDescriptor fd;
	private boolean closed;

	public FileInputStream(String name) throws FileNotFoundException {
		this(name != null ? new File(name) : null);
	}

	public FileInputStream(File file) throws FileNotFoundException {
		String name = file != null ? file.getPath() : null;
		if (name == null) {
			throw new NullPointerException();
		}
		int result = NativeIo.open(NativeIo.path(name), NativeIo.READ | NativeIo.REJECT_DIRECTORY);
		if (result < 0) {
			throw new FileNotFoundException(name + " (" + NativeIo.errorText(-result) + ")");
		}
		this.fd = new FileDescriptor(result);
	}

	public FileInputStream(FileDescriptor fdObj) {
		if (fdObj == null) {
			throw new NullPointerException();
		}
		this.fd = fdObj;
	}

	private int descriptor() throws IOException {
		if (closed || fd.fd == -1) {
			throw new IOException("Stream Closed");
		}
		return fd.fd;
	}

	public int read() throws IOException {
		byte[] b = new byte[1];
		int n = read(b, 0, 1);
		return n <= 0 ? -1 : b[0] & 0xFF;
	}

	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	public int read(byte[] b, int off, int len) throws IOException {
		if (off < 0 || len < 0 || len > b.length - off) {
			throw new IndexOutOfBoundsException();
		}
		if (len == 0) {
			return 0;
		}
		int n = NativeIo.read(descriptor(), b, off, len);
		if (n < 0) {
			throw new IOException(NativeIo.errorText(-n));
		}
		return n == 0 ? -1 : n;
	}

	public long skip(long n) throws IOException {
		long moved = NativeIo.skip(descriptor(), n);
		if (moved < 0) {
			throw new IOException(NativeIo.errorText((int) -moved));
		}
		return moved;
	}

	public int available() throws IOException {
		int n = NativeIo.available(descriptor());
		if (n < 0) {
			throw new IOException(NativeIo.errorText(-n));
		}
		return n;
	}

	public void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		if (fd.fd != -1) {
			int result = NativeIo.close(fd.fd);
			fd.fd = -1;
			if (result != 0) {
				throw new IOException(NativeIo.errorText(result));
			}
		}
	}

	public final FileDescriptor getFD() throws IOException {
		return fd;
	}
}
