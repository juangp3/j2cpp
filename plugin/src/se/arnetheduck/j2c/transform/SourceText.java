package se.arnetheduck.j2c.transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Java source text with its line starts, converting between offsets and the
 * 1-based lines and columns of problems and edits. Lines end at \n, \r\n or
 * \r, as in the Java compiler.
 */
public final class SourceText {
	private final String text;
	private final int[] lineStarts;

	public SourceText(String text) {
		this.text = text;
		List<Integer> starts = new ArrayList<Integer>();
		starts.add(0);
		for (int i = 0; i < text.length(); ++i) {
			char c = text.charAt(i);
			if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
				++i;
			}
			if (c == '\r' || c == '\n') {
				starts.add(i + 1);
			}
		}
		lineStarts = new int[starts.size()];
		for (int i = 0; i < lineStarts.length; ++i) {
			lineStarts[i] = starts.get(i);
		}
	}

	public String text() {
		return text;
	}

	/** 1-based line of offset */
	public int line(int offset) {
		int low = 0;
		int high = lineStarts.length - 1;
		while (low < high) {
			int middle = (low + high + 1) >>> 1;
			if (lineStarts[middle] <= offset) {
				low = middle;
			} else {
				high = middle - 1;
			}
		}
		return low + 1;
	}

	/** 1-based column of offset */
	public int column(int offset) {
		return offset - lineStarts[line(offset) - 1] + 1;
	}

	/** The offset of a 1-based line and column, or -1 if there is none */
	public int offset(int line, int column) {
		if (line < 1 || line > lineStarts.length || column < 1) {
			return -1;
		}
		int offset = lineStarts[line - 1] + column - 1;
		int lineEnd = line < lineStarts.length ? lineStarts[line] : text
				.length();
		return offset <= lineEnd ? offset : -1;
	}

	/** Offset of the first character of the line containing offset */
	public int lineStart(int offset) {
		return lineStarts[line(offset) - 1];
	}

	/** Offset of the line break ending the line containing offset */
	public int lineEnd(int offset) {
		int end = offset;
		while (end < text.length() && text.charAt(end) != '\n'
				&& text.charAt(end) != '\r') {
			++end;
		}
		return end;
	}

	/** Offset just after the line break ending the line containing offset */
	public int nextLineStart(int offset) {
		int line = line(offset);
		return line < lineStarts.length ? lineStarts[line] : text.length();
	}

	/** The whitespace before offset on its line, or null if there is code */
	public String indentation(int offset) {
		String prefix = text.substring(lineStart(offset), offset);
		return prefix.trim().isEmpty() ? prefix : null;
	}

	/** The line break the file uses most, \n when it has none */
	public String lineSeparator() {
		int crlf = 0;
		int lf = 0;
		int cr = 0;
		for (int i = 0; i < text.length(); ++i) {
			char c = text.charAt(i);
			if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
				++crlf;
				++i;
			} else if (c == '\n') {
				++lf;
			} else if (c == '\r') {
				++cr;
			}
		}
		if (crlf > lf && crlf >= cr) {
			return "\r\n";
		}
		return cr > lf ? "\r" : "\n";
	}

	/**
	 * One more level of indentation than indentation: a tab when it uses
	 * tabs or is empty, else four spaces.
	 */
	public static String indentUnit(String indentation) {
		return indentation.isEmpty() || indentation.indexOf('\t') >= 0 ? "\t"
				: "    ";
	}

	/**
	 * Applies non-overlapping edits of this text. Returns null when an edit
	 * lies outside the text.
	 */
	public String apply(List<SourceEdit> edits) {
		List<SourceEdit> sorted = new ArrayList<SourceEdit>(edits);
		// Last first, so earlier offsets stay valid; at one start the
		// replacement goes before the insertion, which so ends up first
		Collections.sort(sorted, new Comparator<SourceEdit>() {
			@Override
			public int compare(SourceEdit a, SourceEdit b) {
				return b.compareTo(a);
			}
		});
		StringBuilder result = new StringBuilder(text);
		for (SourceEdit edit : sorted) {
			int start = offset(edit.startLine, edit.startColumn);
			int end = offset(edit.endLine, edit.endColumn);
			if (start < 0 || end < start) {
				return null;
			}
			result.replace(start, end, edit.newText);
		}
		return result.toString();
	}
}
