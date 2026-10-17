package java.nio.file;

import java.util.Iterator;

/**
 * Simplified Path: POSIX path text, with no FileSystem, URI, real-path or
 * watch support (those methods are absent).
 */
public interface Path extends Comparable<Path>, Iterable<Path> {
	public static Path of(String first, String... more) {
		return Paths.get(first, more);
	}

	boolean isAbsolute();

	Path getRoot();

	Path getFileName();

	Path getParent();

	int getNameCount();

	Path getName(int index);

	Path subpath(int beginIndex, int endIndex);

	boolean startsWith(Path other);

	default boolean startsWith(String other) {
		return startsWith(Path.of(other));
	}

	boolean endsWith(Path other);

	default boolean endsWith(String other) {
		return endsWith(Path.of(other));
	}

	Path normalize();

	Path resolve(Path other);

	default Path resolve(String other) {
		return resolve(Path.of(other));
	}

	default Path resolveSibling(Path other) {
		if (other == null) {
			throw new NullPointerException();
		}
		Path parent = getParent();
		return (parent == null) ? other : parent.resolve(other);
	}

	default Path resolveSibling(String other) {
		return resolveSibling(Path.of(other));
	}

	Path relativize(Path other);

	Path toAbsolutePath();

	default java.io.File toFile() {
		return new java.io.File(toString());
	}

	default Iterator<Path> iterator() {
		final Path self = this;
		return new Iterator<Path>() {
			private int i = 0;

			public boolean hasNext() {
				return (i < self.getNameCount());
			}

			public Path next() {
				if (i < self.getNameCount()) {
					Path result = self.getName(i);
					i++;
					return result;
				}
				throw new java.util.NoSuchElementException();
			}
		};
	}

	int compareTo(Path other);

	boolean equals(Object other);

	int hashCode();

	String toString();
}
