package java.util;

/** StringJoiner, written from the Java SE specification. */
public final class StringJoiner {
	private final String prefix;
	private final String delimiter;
	private final String suffix;
	/** The prefix and the elements so far, or null before the first add */
	private StringBuilder value;
	private String emptyValue;

	public StringJoiner(CharSequence delimiter) {
		this(delimiter, "", "");
	}

	public StringJoiner(CharSequence delimiter, CharSequence prefix, CharSequence suffix) {
		Objects.requireNonNull(prefix, "The prefix must not be null");
		Objects.requireNonNull(delimiter, "The delimiter must not be null");
		Objects.requireNonNull(suffix, "The suffix must not be null");
		this.prefix = prefix.toString();
		this.delimiter = delimiter.toString();
		this.suffix = suffix.toString();
	}

	public StringJoiner setEmptyValue(CharSequence emptyValue) {
		this.emptyValue = Objects.requireNonNull(emptyValue, "The empty value must not be null").toString();
		return this;
	}

	public String toString() {
		if (value == null) {
			return emptyValue != null ? emptyValue : prefix + suffix;
		}
		return value.toString() + suffix;
	}

	public StringJoiner add(CharSequence newElement) {
		String text = String.valueOf(newElement);
		if (value == null) {
			value = new StringBuilder(prefix);
		} else {
			value.append(delimiter);
		}
		value.append(text);
		return this;
	}

	public StringJoiner merge(StringJoiner other) {
		Objects.requireNonNull(other);
		if (other.value == null) {
			return this;
		}
		String content = other.value.substring(other.prefix.length());
		if (value == null) {
			value = new StringBuilder(prefix);
		} else {
			value.append(delimiter);
		}
		value.append(content);
		return this;
	}

	public int length() {
		if (value == null) {
			return emptyValue != null ? emptyValue.length() : prefix.length() + suffix.length();
		}
		return value.length() + suffix.length();
	}
}
