package java.io;
public class FileOutputStream extends OutputStream {
	private final int fd;
	public FileOutputStream(int fd) {
		this.fd = fd;
	}
	public void write(byte[] b, int off, int len) {
		writeBytes(fd, b, off, len);
	}
	private static native void writeBytes(int fd, byte[] b, int off, int len);
}
