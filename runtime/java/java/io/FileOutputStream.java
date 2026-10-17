package java.io;

/** Writes a file descriptor with write(2); no FileChannel */
public class FileOutputStream extends OutputStream {
	private final FileDescriptor fd;
	private boolean closed;

	public FileOutputStream(String name) throws FileNotFoundException {
		this(name != null ? new File(name) : null, false);
	}

	public FileOutputStream(String name, boolean append) throws FileNotFoundException {
		this(name != null ? new File(name) : null, append);
	}

	public FileOutputStream(File file) throws FileNotFoundException {
		this(file, false);
	}

	public FileOutputStream(File file, boolean append) throws FileNotFoundException {
		String name = file != null ? file.getPath() : null;
		if (name == null) {
			throw new NullPointerException();
		}
		int flags = NativeIo.WRITE | NativeIo.CREATE | (append ? NativeIo.APPEND : NativeIo.TRUNCATE);
		int result = NativeIo.open(NativeIo.path(name), flags);
		if (result < 0) {
			throw new FileNotFoundException(name + " (" + NativeIo.errorText(-result) + ")");
		}
		this.fd = new FileDescriptor(result);
	}

	public FileOutputStream(FileDescriptor fdObj) {
		if (fdObj == null) {
			throw new NullPointerException();
		}
		this.fd = fdObj;
	}

	public void write(int b) throws IOException {
		write(new byte[] { (byte) b }, 0, 1);
	}

	public void write(byte[] b) throws IOException {
		write(b, 0, b.length);
	}

	public void write(byte[] b, int off, int len) throws IOException {
		if (off < 0 || len < 0 || len > b.length - off) {
			throw new IndexOutOfBoundsException();
		}
		if (closed || fd.fd == -1) {
			throw new IOException("Stream Closed");
		}
		if (len == 0) {
			return;
		}
		int result = NativeIo.write(fd.fd, b, off, len);
		if (result != 0) {
			throw new IOException(NativeIo.errorText(result));
		}
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
