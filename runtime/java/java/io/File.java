package java.io;

/**
 * A POSIX path name. getCanonicalPath resolves "." and ".." in the text
 * but does not follow symbolic links. Temporary files, permissions setters,
 * URIs and disk space are not supported (absent).
 */
public class File implements Serializable, Comparable<File> {
	public static final char separatorChar = '/';
	public static final String separator = "/";
	public static final char pathSeparatorChar = ':';
	public static final String pathSeparator = ":";

	private final String path;

	public File(String pathname) {
		if (pathname == null) {
			throw new NullPointerException();
		}
		this.path = normalize(pathname);
	}

	public File(String parent, String child) {
		if (child == null) {
			throw new NullPointerException();
		}
		if (parent != null) {
			if (parent.isEmpty()) {
				this.path = resolve("/", normalize(child));
			} else {
				this.path = resolve(normalize(parent), normalize(child));
			}
		} else {
			this.path = normalize(child);
		}
	}

	public File(File parent, String child) {
		if (child == null) {
			throw new NullPointerException();
		}
		if (parent != null) {
			if (parent.path.isEmpty()) {
				this.path = resolve("/", normalize(child));
			} else {
				this.path = resolve(parent.path, normalize(child));
			}
		} else {
			this.path = normalize(child);
		}
	}

	/** Removes repeated and trailing slashes, as UnixFileSystem.normalize does */
	private static String normalize(String pathname) {
		StringBuilder sb = new StringBuilder(pathname.length());
		char previous = 0;
		for (int i = 0; i < pathname.length(); i++) {
			char c = pathname.charAt(i);
			if (c == '/' && previous == '/') {
				continue;
			}
			sb.append(c);
			previous = c;
		}
		if (sb.length() > 1 && sb.charAt(sb.length() - 1) == '/') {
			sb.setLength(sb.length() - 1);
		}
		return sb.toString();
	}

	private static String resolve(String parent, String child) {
		if (child.isEmpty()) {
			return parent;
		}
		if (child.charAt(0) == '/') {
			if (parent.equals("/")) {
				return child;
			}
			return parent + child;
		}
		if (parent.equals("/")) {
			return parent + child;
		}
		return parent + '/' + child;
	}

	private byte[] bytes() {
		return NativeIo.path(path);
	}

	public String getName() {
		int index = path.lastIndexOf(separatorChar);
		int prefixLength = path.startsWith("/") ? 1 : 0;
		if (index < prefixLength) {
			return path.substring(prefixLength);
		}
		return path.substring(index + 1);
	}

	public String getParent() {
		int index = path.lastIndexOf(separatorChar);
		int prefixLength = path.startsWith("/") ? 1 : 0;
		if (index < prefixLength) {
			if ((prefixLength > 0) && (path.length() > prefixLength)) {
				return path.substring(0, prefixLength);
			}
			return null;
		}
		return path.substring(0, index);
	}

	public File getParentFile() {
		String p = getParent();
		if (p == null) {
			return null;
		}
		return new File(p);
	}

	public String getPath() {
		return path;
	}

	public boolean isAbsolute() {
		return path.startsWith("/");
	}

	public String getAbsolutePath() {
		if (isAbsolute()) {
			return path;
		}
		String cwd = new String(NativeIo.cwd(), java.nio.charset.StandardCharsets.UTF_8);
		return resolve(cwd, path);
	}

	public File getAbsoluteFile() {
		return new File(getAbsolutePath());
	}

	public String getCanonicalPath() throws IOException {
		String absolute = getAbsolutePath();
		java.util.ArrayList<String> names = new java.util.ArrayList<String>();
		int start = 1;
		while (start <= absolute.length()) {
			int end = absolute.indexOf('/', start);
			if (end < 0) {
				end = absolute.length();
			}
			String name = absolute.substring(start, end);
			if (name.equals("..")) {
				if (!names.isEmpty()) {
					names.remove(names.size() - 1);
				}
			} else if (!name.isEmpty() && !name.equals(".")) {
				names.add(name);
			}
			start = end + 1;
		}
		if (names.isEmpty()) {
			return "/";
		}
		StringBuilder sb = new StringBuilder();
		for (String name : names) {
			sb.append('/').append(name);
		}
		return sb.toString();
	}

	public File getCanonicalFile() throws IOException {
		return new File(getCanonicalPath());
	}

	public java.nio.file.Path toPath() {
		return java.nio.file.Paths.get(path);
	}

	public boolean canRead() {
		return NativeIo.access(bytes(), 4) == 0;
	}

	public boolean canWrite() {
		return NativeIo.access(bytes(), 2) == 0;
	}

	public boolean canExecute() {
		return NativeIo.access(bytes(), 1) == 0;
	}

	public boolean exists() {
		return NativeIo.kind(bytes()) > 0;
	}

	public boolean isDirectory() {
		return NativeIo.kind(bytes()) == NativeIo.DIRECTORY;
	}

	public boolean isFile() {
		return NativeIo.kind(bytes()) == NativeIo.REGULAR;
	}

	public boolean isHidden() {
		return getName().startsWith(".");
	}

	public long lastModified() {
		long result = NativeIo.lastModified(bytes());
		return result < 0 ? 0L : result;
	}

	public long length() {
		long result = NativeIo.size(bytes());
		return result < 0 ? 0L : result;
	}

	public boolean createNewFile() throws IOException {
		int fd = NativeIo.open(bytes(), NativeIo.WRITE | NativeIo.CREATE_NEW);
		if (fd < 0) {
			if (-fd == NativeIo.ALREADY_EXISTS) {
				return false;
			}
			throw new IOException(NativeIo.errorText(-fd));
		}
		NativeIo.close(fd);
		return true;
	}

	public boolean delete() {
		return NativeIo.remove(bytes()) == 0;
	}

	public String[] list() {
		byte[] names = NativeIo.list(bytes());
		return names == null ? null : NativeIo.names(names);
	}

	public File[] listFiles() {
		String[] ss = list();
		if (ss == null) {
			return null;
		}
		File[] fs = new File[ss.length];
		for (int i = 0; i < ss.length; i++) {
			fs[i] = new File(this, ss[i]);
		}
		return fs;
	}

	public boolean mkdir() {
		return NativeIo.mkdir(bytes()) == 0;
	}

	public boolean mkdirs() {
		if (exists()) {
			return false;
		}
		if (mkdir()) {
			return true;
		}
		File canonFile;
		try {
			canonFile = getCanonicalFile();
		} catch (IOException e) {
			return false;
		}
		File parent = canonFile.getParentFile();
		return (parent != null && (parent.mkdirs() || parent.exists()) && canonFile.mkdir());
	}

	public boolean renameTo(File dest) {
		if (dest == null) {
			throw new NullPointerException();
		}
		return NativeIo.rename(bytes(), dest.bytes()) == 0;
	}

	public int compareTo(File pathname) {
		return path.compareTo(pathname.path);
	}

	public boolean equals(Object obj) {
		if (obj instanceof File) {
			return compareTo((File) obj) == 0;
		}
		return false;
	}

	public int hashCode() {
		return path.hashCode() ^ 1234321;
	}

	public String toString() {
		return path;
	}
}
