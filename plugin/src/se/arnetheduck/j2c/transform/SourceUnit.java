package se.arnetheduck.j2c.transform;

import java.io.File;

public class SourceUnit implements Comparable<SourceUnit> {
	private final File file;

	public SourceUnit(File file) {
		this.file = file.getAbsoluteFile();
	}

	public File file() {
		return file;
	}

	@Override
	public int compareTo(SourceUnit other) {
		return file.getPath().compareTo(other.file.getPath());
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof SourceUnit
				&& file.equals(((SourceUnit) other).file);
	}

	@Override
	public int hashCode() {
		return file.hashCode();
	}
}
