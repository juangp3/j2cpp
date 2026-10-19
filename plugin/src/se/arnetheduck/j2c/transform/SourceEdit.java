package se.arnetheduck.j2c.transform;

import java.io.File;

/**
 * A replacement of a range of a Java source file. Lines and columns are
 * 1-based, like problem locations; columns count UTF-16 characters, a tab
 * being one. The end is exclusive, and an insertion has start equal to end.
 */
public final class SourceEdit {
	public final File file;
	public final int startLine;
	public final int startColumn;
	public final int endLine;
	public final int endColumn;
	public final String newText;

	public SourceEdit(File file, int startLine, int startColumn, int endLine,
			int endColumn, String newText) {
		this.file = file;
		this.startLine = startLine;
		this.startColumn = startColumn;
		this.endLine = endLine;
		this.endColumn = endColumn;
		this.newText = newText;
	}

	/** Replaces the characters from start to end (exclusive) of text */
	static SourceEdit of(File file, SourceText text, int start, int end,
			String newText) {
		return new SourceEdit(file, text.line(start), text.column(start),
				text.line(end), text.column(end), newText);
	}

	/** Orders edits by start, then by end */
	public int compareTo(SourceEdit other) {
		int start = compare(startLine, startColumn, other.startLine,
				other.startColumn);
		return start != 0 ? start : compare(endLine, endColumn, other.endLine,
				other.endColumn);
	}

	/** Whether the two edits touch the same text, so cannot both be applied */
	public boolean overlaps(SourceEdit other) {
		if (!file.getAbsoluteFile().equals(other.file.getAbsoluteFile())) {
			return false;
		}
		boolean empty = isInsertion();
		boolean otherEmpty = other.isInsertion();
		if (empty && otherEmpty) {
			// Two insertions at one place would have no defined order
			return compare(startLine, startColumn, other.startLine,
					other.startColumn) == 0;
		}
		if (empty || otherEmpty) {
			SourceEdit insertion = empty ? this : other;
			SourceEdit replacement = empty ? other : this;
			// Inside the replaced text, not at either end
			return compare(insertion.startLine, insertion.startColumn,
					replacement.startLine, replacement.startColumn) > 0
					&& compare(insertion.startLine, insertion.startColumn,
							replacement.endLine, replacement.endColumn) < 0;
		}
		return compare(startLine, startColumn, other.endLine, other.endColumn) < 0
				&& compare(other.startLine, other.startColumn, endLine,
						endColumn) < 0;
	}

	public boolean isInsertion() {
		return startLine == endLine && startColumn == endColumn;
	}

	private static int compare(int line, int column, int otherLine,
			int otherColumn) {
		return line != otherLine ? Integer.compare(line, otherLine) : Integer
				.compare(column, otherColumn);
	}

	@Override
	public boolean equals(Object object) {
		if (!(object instanceof SourceEdit)) {
			return false;
		}
		SourceEdit other = (SourceEdit) object;
		return file.getAbsoluteFile().equals(other.file.getAbsoluteFile())
				&& startLine == other.startLine
				&& startColumn == other.startColumn && endLine == other.endLine
				&& endColumn == other.endColumn
				&& newText.equals(other.newText);
	}

	@Override
	public int hashCode() {
		return ((startLine * 31 + startColumn) * 31 + endLine) * 31 + endColumn
				+ newText.hashCode();
	}
}
