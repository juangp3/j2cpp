package se.arnetheduck.j2c.transform;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

/**
 * #line directives that make compilers and debuggers report Java files and
 * lines (j2c --line-directives).
 *
 * While translating, the writers wrap the C++ of each statement, method,
 * field, field initializer and enum constant in marks carrying its Java line
 * (see TransformWriter.preVisit2). Marks nest, and survive the buffers the
 * writers assemble files from. When a generated file is written, resolve
 * replaces them: every non-blank C++ line gets a Java line of the innermost
 * mark around its first code, so a C++ line maps to the Java statement it
 * came from. That is the statement's first line, or a later line of the same
 * statement when the C++ lines before it already count up to it (so the C++
 * of a statement spanning several Java lines needs no more directives, and
 * neither does a method's opening brace). Lines outside every mark
 * (includes, generated constructors, class literals and other boilerplate)
 * are mapped back to the C++ file and its real line numbers. A directive is
 * only written where the line numbering the compiler would assume is wrong.
 *
 * Paths in directives are relative to the output directory, where make runs
 * the compiler, so they work wherever the output is built (in WSL too), and
 * absolute only when the Java file is on another drive.
 */
public final class LineDirectives {
	/** CompilationUnit property: the quoted path of its Java file */
	static final String FILE_PROPERTY = "se.arnetheduck.j2c.lineFile";

	/** Starts and ends a mark; the writers never print these characters */
	private static final char MARK = '\u0001';
	private static final char MARK_END = '\u0002';
	private static final String UNMARK = "" + MARK + MARK_END;

	private LineDirectives() {
	}

	/** Whether the writers mark the C++ of node with its Java line */
	static boolean marks(ASTNode node) {
		if (node instanceof Statement || node instanceof MethodDeclaration
				|| node instanceof FieldDeclaration
				|| node instanceof Initializer
				|| node instanceof EnumConstantDeclaration) {
			return true;
		}
		// Field initializers are printed in generated init functions
		return node.getLocationInParent() == VariableDeclarationFragment.INITIALIZER_PROPERTY
				&& node.getParent().getParent() instanceof FieldDeclaration;
	}

	/** The mark starting node's C++, or null when its line is unknown */
	static String mark(ASTNode node) {
		ASTNode root = node.getRoot();
		if (!(root instanceof CompilationUnit)) {
			return null;
		}
		Object file = root.getProperty(FILE_PROPERTY);
		int position = position(node);
		if (!(file instanceof String) || position < 0) {
			return null;
		}
		CompilationUnit unit = (CompilationUnit) root;
		int line = unit.getLineNumber(position);
		int last = unit.getLineNumber(node.getStartPosition()
				+ Math.max(node.getLength() - 1, 0));
		return line < 1 ? null : MARK + Integer.toString(line) + " "
				+ Math.max(line, last) + " " + file + MARK_END;
	}

	/** The mark ending the C++ of the node marked last */
	static String unmark() {
		return UNMARK;
	}

	/** Where the node starts, without its Javadoc or annotations */
	private static int position(ASTNode node) {
		if (node instanceof MethodDeclaration) {
			return ((MethodDeclaration) node).getName().getStartPosition();
		}
		if (node instanceof EnumConstantDeclaration) {
			return ((EnumConstantDeclaration) node).getName().getStartPosition();
		}
		if (node instanceof FieldDeclaration) {
			List<?> fragments = ((FieldDeclaration) node).fragments();
			if (!fragments.isEmpty()) {
				return ((ASTNode) fragments.get(0)).getStartPosition();
			}
		}
		if (node instanceof Initializer) {
			return ((Initializer) node).getBody().getStartPosition();
		}
		return node.getStartPosition();
	}

	/**
	 * The path of file in directives: relative to base (the output
	 * directory), or absolute on another drive, with forward slashes
	 */
	static String path(File file, File base) {
		java.nio.file.Path target = file.getAbsoluteFile().toPath().normalize();
		String path;
		try {
			path = base.getAbsoluteFile().toPath().normalize().relativize(target)
					.toString();
		} catch (IllegalArgumentException e) {
			path = target.toString();
		}
		return path.replace(File.separatorChar, '/');
	}

	/** path as the contents of a C string literal */
	static String quote(String path) {
		return path.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	/** A mark: the node's file, first line and last line */
	private static final class Location {
		final String file;
		final int line;
		final int last;

		Location(String file, int line, int last) {
			this.file = file;
			this.line = line;
			this.last = last;
		}

		/**
		 * Whether a C++ line the compiler would put at file:presumed may stay
		 * there: on the node's first line, or a later line of the node, which
		 * keeps directives few where C++ lines follow the Java ones
		 */
		boolean accepts(String presumedFile, int presumed) {
			return presumedFile.equals(file) && presumed >= line
					&& presumed <= last;
		}
	}

	/**
	 * Replaces the marks in a generated file's text with #line directives;
	 * cppPath is the file's own quoted path, used after boilerplate. Text
	 * without marks is returned unchanged.
	 */
	public static String resolve(String text, String cppPath) {
		if (text.indexOf(MARK) < 0) {
			return text;
		}

		StringBuilder result = new StringBuilder(text.length() + 4096);
		List<Location> marks = new ArrayList<Location>();
		// The file and line the compiler assumes for the next line
		String presumedFile = cppPath;
		int presumedLine = 1;
		// The real line number of the next line written
		int physical = 1;
		boolean inComment = false;
		boolean continued = false;

		int start = 0;
		while (start < text.length()) {
			int newline = text.indexOf('\n', start);
			int end = newline < 0 ? text.length() : newline + 1;

			StringBuilder line = new StringBuilder(end - start);
			boolean code = false;
			Location wanted = null;
			Location firstMark = null;
			for (int i = start; i < end;) {
				char c = text.charAt(i);
				if (c == MARK) {
					int close = text.indexOf(MARK_END, i);
					String body = text.substring(i + 1, close);
					if (body.isEmpty()) {
						marks.remove(marks.size() - 1);
					} else {
						int space = body.indexOf(' ');
						int second = body.indexOf(' ', space + 1);
						Location location = new Location(body.substring(second + 1),
								Integer.parseInt(body.substring(0, space)),
								Integer.parseInt(body.substring(space + 1, second)));
						marks.add(location);
						if (firstMark == null) {
							firstMark = location;
						}
					}
					i = close + 1;
					continue;
				}
				if (!code && !Character.isWhitespace(c)) {
					code = true;
					wanted = marks.isEmpty() ? null : marks.get(marks.size() - 1);
					firstMark = null;
				}
				line.append(c);
				++i;
			}

			String content = line.toString();
			String lineBreak = content.endsWith("\r\n") ? "\r\n" : "\n";
			// Directives inside comments or continued lines would not apply
			if (code && !inComment && !continued) {
				// Code after boilerplate on the same line, such as an
				// initializer after its field's name, takes the code's line
				if (wanted == null) {
					wanted = firstMark;
				}
				if (wanted == null) {
					if (!presumedFile.equals(cppPath) || presumedLine != physical) {
						result.append("#line ").append(physical + 1).append(" \"")
								.append(cppPath).append('"').append(lineBreak);
						++physical;
						presumedFile = cppPath;
						presumedLine = physical;
					}
				} else if (!wanted.accepts(presumedFile, presumedLine)) {
					result.append("#line ").append(wanted.line).append(" \"")
							.append(wanted.file).append('"').append(lineBreak);
					++physical;
					presumedFile = wanted.file;
					presumedLine = wanted.line;
				}
			}

			result.append(content);
			if (newline >= 0) {
				++physical;
				++presumedLine;
			}
			inComment = endsInComment(content, inComment);
			String trimmed = content.endsWith("\r\n") ? content.substring(0,
					content.length() - 2) : content.endsWith("\n") ? content
					.substring(0, content.length() - 1) : content;
			continued = trimmed.endsWith("\\");
			start = end;
		}
		return result.toString();
	}

	/** Whether a block comment is open after line, given whether one was */
	private static boolean endsInComment(String line, boolean inComment) {
		for (int i = 0; i < line.length(); ++i) {
			char c = line.charAt(i);
			char next = i + 1 < line.length() ? line.charAt(i + 1) : 0;
			if (inComment) {
				if (c == '*' && next == '/') {
					inComment = false;
					++i;
				}
			} else if (c == '/' && next == '/') {
				return false;
			} else if (c == '/' && next == '*') {
				inComment = true;
				++i;
			} else if (c == '"' || c == '\'') {
				// String and character literals end on their line
				for (++i; i < line.length() && line.charAt(i) != c; ++i) {
					if (line.charAt(i) == '\\') {
						++i;
					}
				}
			}
		}
		return inComment;
	}
}
