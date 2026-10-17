package java.nio.charset;

/** The charsets this runtime supports; the UTF-16 and UTF-32 forms are not among them */
public final class StandardCharsets {
	private StandardCharsets() {
	}

	public static final Charset US_ASCII = Charset.US_ASCII;

	public static final Charset ISO_8859_1 = Charset.ISO_8859_1;

	public static final Charset UTF_8 = Charset.UTF_8;
}
