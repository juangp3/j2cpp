package text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class Wrap {
	public static List<String> lines(String text, int width) {
		List<String> lines = new ArrayList<String>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			if (line.length() > 0 && line.length() + 1 + word.length() > width) {
				lines.add(line.toString());
				line.setLength(0);
			}
			if (line.length() > 0) {
				line.append(" ");
			}
			line.append(word);
		}
		if (line.length() > 0) {
			lines.add(line.toString());
		}
		return lines;
	}

	public static Function<String, List<String>> wrapper(int width) {
		return text -> lines(text, width);
	}

	public static String describe(Object value) {
		if (value instanceof String s && !s.isEmpty()) {
			return "text of " + s.length();
		}
		if (value == null) {
			return "nothing";
		}
		return value instanceof Integer i && i > 0 ? "positive " + i : "other";
	}
}
