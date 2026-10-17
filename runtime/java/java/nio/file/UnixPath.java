package java.nio.file;

import java.util.ArrayList;

/** The Path of Paths.get: normalized POSIX path text, as sun.nio.fs.UnixPath keeps it */
final class UnixPath implements Path {
	private final String path;
	private int[] offsets;
	private byte[] bytes;

	UnixPath(String normalized) {
		this.path = normalized;
	}

	static String normalizeAndCheck(String input) {
		int n = input.length();
		char prevChar = 0;
		for (int i = 0; i < n; i++) {
			char c = input.charAt(i);
			if (c == '\u0000') {
				throw new InvalidPathException(input, "Nul character not allowed");
			}
			prevChar = c;
		}
		StringBuilder sb = new StringBuilder(n);
		prevChar = 0;
		for (int i = 0; i < n; i++) {
			char c = input.charAt(i);
			if (c == '/' && prevChar == '/') {
				continue;
			}
			sb.append(c);
			prevChar = c;
		}
		if (sb.length() > 1 && sb.charAt(sb.length() - 1) == '/') {
			sb.setLength(sb.length() - 1);
		}
		return sb.toString();
	}

	private boolean isEmpty() {
		return path.isEmpty();
	}

	private int[] offsets() {
		if (offsets == null) {
			ArrayList<Integer> list = new ArrayList<Integer>();
			if (isEmpty()) {
				list.add(0);
			} else {
				int index = 0;
				while (index < path.length()) {
					char c = path.charAt(index++);
					if (c != '/') {
						list.add(index - 1);
						while (index < path.length() && path.charAt(index) != '/') {
							index++;
						}
					}
				}
			}
			int[] result = new int[list.size()];
			for (int i = 0; i < result.length; i++) {
				result[i] = list.get(i);
			}
			offsets = result;
		}
		return offsets;
	}

	private String nameText(int i) {
		int[] o = offsets();
		int begin = o[i];
		int end = (i == o.length - 1) ? path.length() : o[i + 1] - 1;
		return path.substring(begin, end);
	}

	private byte[] bytes() {
		if (bytes == null) {
			bytes = path.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		}
		return bytes;
	}

	public boolean isAbsolute() {
		return path.startsWith("/");
	}

	public Path getRoot() {
		return isAbsolute() ? new UnixPath("/") : null;
	}

	public Path getFileName() {
		int count = getNameCount();
		if (count == 0) {
			return null;
		}
		if (count == 1 && !isAbsolute()) {
			return this;
		}
		return new UnixPath(nameText(count - 1));
	}

	public Path getParent() {
		int count = getNameCount();
		if (count == 0) {
			return null;
		}
		int len = offsets()[count - 1] - 1;
		if (len <= 0) {
			return getRoot();
		}
		return new UnixPath(path.substring(0, len));
	}

	public int getNameCount() {
		return offsets().length;
	}

	public Path getName(int index) {
		if (index < 0 || index >= getNameCount()) {
			throw new IllegalArgumentException();
		}
		return new UnixPath(nameText(index));
	}

	public Path subpath(int beginIndex, int endIndex) {
		int count = getNameCount();
		if (beginIndex < 0 || beginIndex >= count || endIndex > count || beginIndex >= endIndex) {
			throw new IllegalArgumentException();
		}
		StringBuilder sb = new StringBuilder();
		for (int i = beginIndex; i < endIndex; i++) {
			if (i > beginIndex) {
				sb.append('/');
			}
			sb.append(nameText(i));
		}
		return new UnixPath(sb.toString());
	}

	private static UnixPath toUnixPath(Path obj) {
		if (obj == null) {
			throw new NullPointerException();
		}
		if (!(obj instanceof UnixPath)) {
			throw new ProviderMismatchException();
		}
		return (UnixPath) obj;
	}

	public boolean startsWith(Path other) {
		UnixPath that = toUnixPath(other);
		if (that.isAbsolute() != isAbsolute()) {
			return false;
		}
		if (that.isEmpty()) {
			return isEmpty();
		}
		int thatCount = that.getNameCount();
		if (thatCount > getNameCount()) {
			return false;
		}
		if (thatCount == 0) {
			return true;
		}
		for (int i = 0; i < thatCount; i++) {
			if (!nameText(i).equals(that.nameText(i))) {
				return false;
			}
		}
		return true;
	}

	public boolean endsWith(Path other) {
		UnixPath that = toUnixPath(other);
		if (that.isAbsolute()) {
			return this.equals(that);
		}
		if (that.isEmpty()) {
			return isEmpty();
		}
		int thisCount = getNameCount();
		int thatCount = that.getNameCount();
		if (thatCount > thisCount) {
			return false;
		}
		for (int i = 0; i < thatCount; i++) {
			if (!nameText(thisCount - thatCount + i).equals(that.nameText(i))) {
				return false;
			}
		}
		return true;
	}

	public Path normalize() {
		int count = getNameCount();
		if (count == 0 || isEmpty()) {
			return this;
		}
		ArrayList<String> names = new ArrayList<String>();
		boolean changed = false;
		for (int i = 0; i < count; i++) {
			String name = nameText(i);
			if (name.equals(".")) {
				changed = true;
				continue;
			}
			if (name.equals("..")) {
				if (!names.isEmpty() && !names.get(names.size() - 1).equals("..")) {
					names.remove(names.size() - 1);
					changed = true;
					continue;
				}
				if (isAbsolute()) {
					changed = true;
					continue;
				}
			}
			names.add(name);
		}
		if (!changed) {
			return this;
		}
		StringBuilder sb = new StringBuilder();
		if (isAbsolute()) {
			sb.append('/');
		}
		for (int i = 0; i < names.size(); i++) {
			if (i > 0) {
				sb.append('/');
			}
			sb.append(names.get(i));
		}
		return new UnixPath(sb.toString());
	}

	public Path resolve(Path obj) {
		UnixPath other = toUnixPath(obj);
		if (other.isAbsolute()) {
			return other;
		}
		if (other.isEmpty()) {
			return this;
		}
		if (isEmpty()) {
			return other;
		}
		if (path.equals("/")) {
			return new UnixPath("/" + other.path);
		}
		return new UnixPath(path + "/" + other.path);
	}

	public Path relativize(Path obj) {
		UnixPath other = toUnixPath(obj);
		if (other.equals(this)) {
			return new UnixPath("");
		}
		if (this.isAbsolute() != other.isAbsolute()) {
			throw new IllegalArgumentException("'other' is different type of Path");
		}
		if (this.isEmpty()) {
			return other;
		}
		int thisCount = getNameCount();
		int otherCount = other.isEmpty() ? 0 : other.getNameCount();
		int common = 0;
		while (common < thisCount && common < otherCount && nameText(common).equals(other.nameText(common))) {
			common++;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = common; i < thisCount; i++) {
			if (sb.length() > 0) {
				sb.append('/');
			}
			sb.append("..");
		}
		for (int i = common; i < otherCount; i++) {
			if (sb.length() > 0) {
				sb.append('/');
			}
			sb.append(other.nameText(i));
		}
		return new UnixPath(sb.toString());
	}

	public Path toAbsolutePath() {
		if (isAbsolute()) {
			return this;
		}
		String cwd = new String(java.io.NativeIo.cwd(), java.nio.charset.StandardCharsets.UTF_8);
		if (isEmpty()) {
			return new UnixPath(cwd);
		}
		return new UnixPath(cwd.equals("/") ? "/" + path : cwd + "/" + path);
	}

	public int compareTo(Path other) {
		byte[] a = bytes();
		byte[] b = ((UnixPath) other).bytes();
		int n = Math.min(a.length, b.length);
		for (int i = 0; i < n; i++) {
			int c1 = a[i] & 0xFF;
			int c2 = b[i] & 0xFF;
			if (c1 != c2) {
				return c1 - c2;
			}
		}
		return a.length - b.length;
	}

	public boolean equals(Object ob) {
		return (ob instanceof UnixPath) && compareTo((Path) ob) == 0;
	}

	public int hashCode() {
		int h = 0;
		byte[] b = bytes();
		for (int i = 0; i < b.length; i++) {
			h = 31 * h + (b[i] & 0xFF);
		}
		return h;
	}

	public String toString() {
		return path;
	}
}
