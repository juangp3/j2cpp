package java.util.regex;

/**
 * PatternSyntaxException, written from the Java SE specification. The
 * message is the description, " near index n" when the index is known, the
 * pattern, and a caret under the index when it is inside the pattern.
 */
public class PatternSyntaxException extends IllegalArgumentException {
	private final String desc;
	private final String pattern;
	private final int index;

	public PatternSyntaxException(String desc, String regex, int index) {
		this.desc = desc;
		this.pattern = regex;
		this.index = index;
	}

	public int getIndex() {
		return index;
	}

	public String getDescription() {
		return desc;
	}

	public String getPattern() {
		return pattern;
	}

	public String getMessage() {
		StringBuilder sb = new StringBuilder();
		sb.append(desc);
		if (index >= 0) {
			sb.append(" near index ").append(index);
		}
		sb.append(System.lineSeparator()).append(pattern);
		if (index >= 0 && pattern != null && index < pattern.length()) {
			sb.append(System.lineSeparator());
			for (int i = 0; i < index; i++) {
				sb.append(' ');
			}
			sb.append('^');
		}
		return sb.toString();
	}
}
