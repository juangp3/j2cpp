package se.arnetheduck.j2c.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON for the CLI's --format json output and the MCP protocol.
 * Objects are LinkedHashMaps (keeping key order), arrays are Lists, numbers
 * are Long when integral and Double otherwise.
 */
final class Json {
	private final String text;
	private int position;

	private Json(String text) {
		this.text = text;
	}

	/** Parses one JSON value; throws IllegalArgumentException if invalid */
	static Object parse(String text) {
		Json json = new Json(text);
		json.skipWhitespace();
		Object value = json.value();
		json.skipWhitespace();
		if (json.position != text.length()) {
			throw json.error("Unexpected trailing characters");
		}
		return value;
	}

	static String write(Object value) {
		StringBuilder out = new StringBuilder();
		write(value, out);
		return out.toString();
	}

	private static void write(Object value, StringBuilder out) {
		if (value == null) {
			out.append("null");
		} else if (value instanceof String) {
			writeString((String) value, out);
		} else if (value instanceof Boolean || value instanceof Long
				|| value instanceof Integer) {
			out.append(value);
		} else if (value instanceof Number) {
			double number = ((Number) value).doubleValue();
			out.append(Double.isFinite(number) ? String.valueOf(number) : "null");
		} else if (value instanceof Map) {
			out.append('{');
			String separator = "";
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
				out.append(separator);
				separator = ",";
				writeString(String.valueOf(entry.getKey()), out);
				out.append(':');
				write(entry.getValue(), out);
			}
			out.append('}');
		} else if (value instanceof Iterable) {
			out.append('[');
			String separator = "";
			for (Object item : (Iterable<?>) value) {
				out.append(separator);
				separator = ",";
				write(item, out);
			}
			out.append(']');
		} else {
			writeString(value.toString(), out);
		}
	}

	private static void writeString(String value, StringBuilder out) {
		out.append('"');
		for (int i = 0; i < value.length(); ++i) {
			char c = value.charAt(i);
			switch (c) {
			case '"':
				out.append("\\\"");
				break;
			case '\\':
				out.append("\\\\");
				break;
			case '\n':
				out.append("\\n");
				break;
			case '\r':
				out.append("\\r");
				break;
			case '\t':
				out.append("\\t");
				break;
			default:
				if (c < 0x20) {
					out.append(String.format("\\u%04x", (int) c));
				} else {
					out.append(c);
				}
			}
		}
		out.append('"');
	}

	private Object value() {
		if (position >= text.length()) {
			throw error("Unexpected end of input");
		}
		char c = text.charAt(position);
		switch (c) {
		case '{':
			return object();
		case '[':
			return array();
		case '"':
			return string();
		case 't':
			return literal("true", Boolean.TRUE);
		case 'f':
			return literal("false", Boolean.FALSE);
		case 'n':
			return literal("null", null);
		default:
			if (c == '-' || (c >= '0' && c <= '9')) {
				return number();
			}
			throw error("Unexpected character '" + c + "'");
		}
	}

	private Map<String, Object> object() {
		Map<String, Object> object = new LinkedHashMap<String, Object>();
		position++;
		skipWhitespace();
		if (peek('}')) {
			position++;
			return object;
		}
		while (true) {
			skipWhitespace();
			if (!peek('"')) {
				throw error("Expected a string key");
			}
			String key = string();
			skipWhitespace();
			expect(':');
			skipWhitespace();
			object.put(key, value());
			skipWhitespace();
			if (peek(',')) {
				position++;
			} else {
				expect('}');
				return object;
			}
		}
	}

	private List<Object> array() {
		List<Object> array = new ArrayList<Object>();
		position++;
		skipWhitespace();
		if (peek(']')) {
			position++;
			return array;
		}
		while (true) {
			skipWhitespace();
			array.add(value());
			skipWhitespace();
			if (peek(',')) {
				position++;
			} else {
				expect(']');
				return array;
			}
		}
	}

	private String string() {
		position++; // opening quote
		StringBuilder out = new StringBuilder();
		while (position < text.length()) {
			char c = text.charAt(position++);
			if (c == '"') {
				return out.toString();
			}
			if (c != '\\') {
				out.append(c);
				continue;
			}
			if (position >= text.length()) {
				break;
			}
			char escape = text.charAt(position++);
			switch (escape) {
			case '"':
			case '\\':
			case '/':
				out.append(escape);
				break;
			case 'b':
				out.append('\b');
				break;
			case 'f':
				out.append('\f');
				break;
			case 'n':
				out.append('\n');
				break;
			case 'r':
				out.append('\r');
				break;
			case 't':
				out.append('\t');
				break;
			case 'u':
				if (position + 4 > text.length()) {
					throw error("Incomplete unicode escape");
				}
				try {
					out.append((char) Integer.parseInt(
							text.substring(position, position + 4), 16));
				} catch (NumberFormatException e) {
					throw error("Invalid unicode escape");
				}
				position += 4;
				break;
			default:
				throw error("Invalid escape '\\" + escape + "'");
			}
		}
		throw error("Unterminated string");
	}

	private Object number() {
		int start = position;
		if (peek('-')) {
			position++;
		}
		boolean integral = true;
		while (position < text.length()) {
			char c = text.charAt(position);
			if (c >= '0' && c <= '9') {
				position++;
			} else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
				integral = false;
				position++;
			} else {
				break;
			}
		}
		String number = text.substring(start, position);
		try {
			return integral ? (Object) Long.valueOf(number) : Double.valueOf(number);
		} catch (NumberFormatException e) {
			throw error("Invalid number " + number);
		}
	}

	private Object literal(String word, Object value) {
		if (!text.startsWith(word, position)) {
			throw error("Unexpected token");
		}
		position += word.length();
		return value;
	}

	private void skipWhitespace() {
		while (position < text.length()
				&& Character.isWhitespace(text.charAt(position))) {
			position++;
		}
	}

	private boolean peek(char c) {
		return position < text.length() && text.charAt(position) == c;
	}

	private void expect(char c) {
		if (!peek(c)) {
			throw error("Expected '" + c + "'");
		}
		position++;
	}

	private IllegalArgumentException error(String message) {
		return new IllegalArgumentException(message + " at offset " + position);
	}
}
