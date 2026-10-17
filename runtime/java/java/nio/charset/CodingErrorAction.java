package java.nio.charset;

/** What a decoder or encoder does with malformed input or unmappable characters */
public class CodingErrorAction {
	private final String name;

	private CodingErrorAction(String name) {
		this.name = name;
	}

	public static final CodingErrorAction IGNORE = new CodingErrorAction("IGNORE");

	public static final CodingErrorAction REPLACE = new CodingErrorAction("REPLACE");

	public static final CodingErrorAction REPORT = new CodingErrorAction("REPORT");

	public String toString() {
		return name;
	}
}
