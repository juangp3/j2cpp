package java.io;

/**
 * J2C: the POSIX calls under the runtime's files and streams; not part of
 * Java SE. Paths are NUL-terminated UTF-8 (see path), failures come back as
 * negative errno values, and the callers throw Java's exceptions.
 */
public final class NativeIo {
	private NativeIo() {
	}

	public static final int READ = 1;
	public static final int WRITE = 2;
	public static final int APPEND = 4;
	public static final int CREATE = 8;
	public static final int CREATE_NEW = 16;
	public static final int TRUNCATE = 32;
	/** Fail with EISDIR when the path is a directory, as FileInputStream does */
	public static final int REJECT_DIRECTORY = 64;

	/** This platform's errno values */
	public static final int NO_SUCH_FILE = errnoValue(0);
	public static final int ACCESS_DENIED = errnoValue(1);
	public static final int ALREADY_EXISTS = errnoValue(2);
	public static final int NOT_A_DIRECTORY = errnoValue(3);
	public static final int IS_A_DIRECTORY = errnoValue(4);
	public static final int NOT_EMPTY = errnoValue(5);

	private static native int errnoValue(int index);

	public static final int MISSING = 0;
	public static final int REGULAR = 1;
	public static final int DIRECTORY = 2;
	public static final int OTHER = 3;

	/** The bytes of a path for the natives: UTF-8, ending with a NUL */
	public static byte[] path(String path) {
		byte[] utf8 = path.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		byte[] result = new byte[utf8.length + 1];
		System.arraycopy(utf8, 0, result, 0, utf8.length);
		return result;
	}

	/** A file descriptor, or -errno */
	public static native int open(byte[] path, int flags);

	/** Bytes read (0 at the end of the file), or -errno; len is positive */
	public static native int read(int fd, byte[] b, int off, int len);

	/** 0, or errno */
	public static native int write(int fd, byte[] b, int off, int len);

	/** 0, or errno */
	public static native int close(int fd);

	/** Bytes that can be read without blocking, or -errno */
	public static native int available(int fd);

	/** Moves the position n bytes forward and returns how far it moved, or -errno */
	public static native long skip(int fd, long n);

	/** MISSING, REGULAR, DIRECTORY or OTHER (following links), or -errno for other failures */
	public static native int kind(byte[] path);

	/** The size in bytes, or -errno */
	public static native long size(byte[] path);

	/** Milliseconds since the epoch of the last modification, or -errno */
	public static native long lastModified(byte[] path);

	/** Removes a file or empty directory: 0, or errno */
	public static native int remove(byte[] path);

	/** 0, or errno */
	public static native int mkdir(byte[] path);

	/** 0, or errno */
	public static native int rename(byte[] from, byte[] to);

	/** access(2) with R_OK (4), W_OK (2) or X_OK (1): 0, or errno */
	public static native int access(byte[] path, int mode);

	/** The names in a directory (not . and ..), each ending with a NUL, or null */
	public static native byte[] list(byte[] path);

	/** The working directory as UTF-8 */
	public static native byte[] cwd();

	/** strerror's text for an errno value */
	public static native String errorText(int error);

	/** A stream reading an open descriptor, for java.nio.file.Files */
	public static InputStream inputStream(int fd) {
		return new FileInputStream(new FileDescriptor(fd));
	}

	/** A stream writing an open descriptor, for java.nio.file.Files */
	public static OutputStream outputStream(int fd) {
		return new FileOutputStream(new FileDescriptor(fd));
	}

	/** The names list returns */
	public static String[] names(byte[] list) {
		int count = 0;
		for (int i = 0; i < list.length; i++) {
			if (list[i] == 0) {
				count++;
			}
		}
		String[] names = new String[count];
		int start = 0;
		int n = 0;
		for (int i = 0; i < list.length; i++) {
			if (list[i] == 0) {
				names[n++] = new String(list, start, i - start, java.nio.charset.StandardCharsets.UTF_8);
				start = i + 1;
			}
		}
		return names;
	}
}
