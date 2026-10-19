package se.arnetheduck.j2c.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified diffs ("diff -u", "git diff") of a file before and after edits,
 * with three lines of context. Lines keep their own line breaks, so the
 * diff applies with patch or git apply to files with any line ending.
 */
final class UnifiedDiff {
	private static final int CONTEXT = 3;
	/** Beyond this many line pairs the changed middle is shown replaced */
	private static final long MAX_TABLE = 4000000L;

	private static final int EQUAL = 0;
	private static final int DELETE = 1;
	private static final int INSERT = 2;

	private UnifiedDiff() {
	}

	/** The diff of one file, empty when before equals after */
	static String diff(String path, String before, String after) {
		if (before.equals(after)) {
			return "";
		}
		List<String> a = lines(before);
		List<String> b = lines(after);
		List<int[]> ops = operations(a, b);

		StringBuilder diff = new StringBuilder();
		diff.append("--- a/").append(path).append('\n');
		diff.append("+++ b/").append(path).append('\n');
		int i = 0;
		while (i < ops.size()) {
			if (ops.get(i)[0] == EQUAL) {
				++i;
				continue;
			}
			// A hunk: changes no more than 2 * CONTEXT equal lines apart
			int first = Math.max(0, i - CONTEXT);
			int last = i;
			int j = i;
			while (j < ops.size()) {
				if (ops.get(j)[0] != EQUAL) {
					last = j;
					++j;
					continue;
				}
				int k = j;
				while (k < ops.size() && ops.get(k)[0] == EQUAL) {
					++k;
				}
				if (k == ops.size() || k - j > 2 * CONTEXT) {
					break;
				}
				j = k;
			}
			int end = Math.min(ops.size(), last + 1 + CONTEXT);
			hunk(diff, ops.subList(first, end), a, b);
			i = end;
		}
		return diff.toString();
	}

	private static void hunk(StringBuilder diff, List<int[]> ops, List<String> a,
			List<String> b) {
		int aStart = -1;
		int bStart = -1;
		int aLength = 0;
		int bLength = 0;
		for (int[] op : ops) {
			if (op[0] != INSERT) {
				if (aStart < 0) {
					aStart = op[1];
				}
				++aLength;
			}
			if (op[0] != DELETE) {
				if (bStart < 0) {
					bStart = op[2];
				}
				++bLength;
			}
		}
		// An empty side is numbered by the line before it
		int[] firstOp = ops.get(0);
		aStart = aLength == 0 ? firstOp[1] : aStart + 1;
		bStart = bLength == 0 ? firstOp[2] : bStart + 1;
		diff.append("@@ -").append(aStart).append(',').append(aLength)
				.append(" +").append(bStart).append(',').append(bLength)
				.append(" @@\n");
		for (int[] op : ops) {
			String line = op[0] == INSERT ? b.get(op[2]) : a.get(op[1]);
			diff.append(op[0] == EQUAL ? ' ' : op[0] == DELETE ? '-' : '+')
					.append(line);
			if (!line.endsWith("\n") && !line.endsWith("\r")) {
				diff.append("\n\\ No newline at end of file\n");
			}
		}
	}

	/** {kind, index in a, index in b}, in order */
	private static List<int[]> operations(List<String> a, List<String> b) {
		int prefix = 0;
		while (prefix < a.size() && prefix < b.size()
				&& a.get(prefix).equals(b.get(prefix))) {
			++prefix;
		}
		int suffix = 0;
		while (suffix < a.size() - prefix && suffix < b.size() - prefix
				&& a.get(a.size() - 1 - suffix).equals(
						b.get(b.size() - 1 - suffix))) {
			++suffix;
		}

		List<int[]> ops = new ArrayList<int[]>();
		for (int i = 0; i < prefix; ++i) {
			ops.add(new int[] { EQUAL, i, i });
		}
		int n = a.size() - prefix - suffix;
		int m = b.size() - prefix - suffix;
		if ((long) n * m <= MAX_TABLE) {
			// Longest common subsequence of the changed middle
			int[][] table = new int[n + 1][m + 1];
			for (int i = n - 1; i >= 0; --i) {
				for (int j = m - 1; j >= 0; --j) {
					table[i][j] = a.get(prefix + i).equals(b.get(prefix + j)) ? table[i + 1][j + 1] + 1
							: Math.max(table[i + 1][j], table[i][j + 1]);
				}
			}
			int i = 0;
			int j = 0;
			while (i < n || j < m) {
				if (i < n && j < m
						&& a.get(prefix + i).equals(b.get(prefix + j))) {
					ops.add(new int[] { EQUAL, prefix + i++, prefix + j++ });
				} else if (i < n && (j == m || table[i + 1][j] >= table[i][j + 1])) {
					// Removed lines before added ones, as diff prints them
					ops.add(new int[] { DELETE, prefix + i++, prefix + j });
				} else {
					ops.add(new int[] { INSERT, prefix + i, prefix + j++ });
				}
			}
		} else {
			for (int i = 0; i < n; ++i) {
				ops.add(new int[] { DELETE, prefix + i, prefix });
			}
			for (int j = 0; j < m; ++j) {
				ops.add(new int[] { INSERT, prefix + n, prefix + j });
			}
		}
		for (int i = 0; i < suffix; ++i) {
			ops.add(new int[] { EQUAL, a.size() - suffix + i,
					b.size() - suffix + i });
		}
		return ops;
	}

	/** The lines of text, each with its line break */
	private static List<String> lines(String text) {
		List<String> lines = new ArrayList<String>();
		int start = 0;
		for (int i = 0; i < text.length(); ++i) {
			char c = text.charAt(i);
			if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
				++i;
			}
			if (c == '\r' || c == '\n') {
				lines.add(text.substring(start, i + 1));
				start = i + 1;
			}
		}
		if (start < text.length()) {
			lines.add(text.substring(start));
		}
		return lines;
	}
}
