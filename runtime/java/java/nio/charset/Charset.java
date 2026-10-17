package java.nio.charset;

/**
 * Simplified Charset: UTF-8, ISO-8859-1 and US-ASCII only, with their
 * standard aliases. forName throws UnsupportedCharsetException for every
 * other name, including the UTF-16 and UTF-32 forms the JVM supports. There
 * is no ByteBuffer, so encode, decode and the ByteBuffer methods of the
 * coders are absent; the runtime's readers, writers and String use the
 * coders' j2c methods.
 */
public class Charset implements Comparable<Charset> {
	static final int UTF8 = 0;
	static final int LATIN1 = 1;
	static final int ASCII = 2;

	static final Charset UTF_8 = new Charset("UTF-8", UTF8, new String[] { "UTF8", "unicode-1-1-utf-8" });

	static final Charset ISO_8859_1 = new Charset("ISO-8859-1", LATIN1, new String[] { "iso-ir-100",
			"ISO_8859-1", "latin1", "l1", "IBM819", "cp819", "csISOLatin1", "819", "IBM-819", "ISO8859_1",
			"ISO_8859-1:1987", "ISO_8859_1", "8859_1", "ISO8859-1" });

	static final Charset US_ASCII = new Charset("US-ASCII", ASCII, new String[] { "iso-ir-6",
			"ANSI_X3.4-1986", "ISO_646.irv:1991", "ASCII", "ISO646-US", "us", "IBM367", "cp367", "csASCII",
			"default", "646", "iso_646.irv:1983", "ANSI_X3.4-1968", "ascii7" });

	private final String name;
	final int kind;
	private final String[] aliases;

	private Charset(String name, int kind, String[] aliases) {
		this.name = name;
		this.kind = kind;
		this.aliases = aliases;
	}

	public static Charset forName(String charsetName) {
		Charset charset = lookup(charsetName);
		if (charset == null) {
			throw new UnsupportedCharsetException(charsetName);
		}
		return charset;
	}

	public static Charset forName(String charsetName, Charset fallback) {
		Charset charset = lookup(charsetName);
		return charset != null ? charset : fallback;
	}

	public static boolean isSupported(String charsetName) {
		return lookup(charsetName) != null;
	}

	public static Charset defaultCharset() {
		return UTF_8;
	}

	private static Charset lookup(String charsetName) {
		if (charsetName == null) {
			throw new IllegalArgumentException("Null charset name");
		}
		checkName(charsetName);
		Charset[] all = new Charset[] { UTF_8, ISO_8859_1, US_ASCII };
		for (int i = 0; i < all.length; i++) {
			if (all[i].name.equalsIgnoreCase(charsetName)) {
				return all[i];
			}
			for (int j = 0; j < all[i].aliases.length; j++) {
				if (all[i].aliases[j].equalsIgnoreCase(charsetName)) {
					return all[i];
				}
			}
		}
		return null;
	}

	private static void checkName(String s) {
		int n = s.length();
		if (n == 0) {
			throw new IllegalCharsetNameException(s);
		}
		for (int i = 0; i < n; i++) {
			char c = s.charAt(i);
			if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
				continue;
			}
			if (i != 0 && (c == '-' || c == '+' || c == ':' || c == '_' || c == '.')) {
				continue;
			}
			throw new IllegalCharsetNameException(s);
		}
	}

	public final String name() {
		return name;
	}

	public String displayName() {
		return name;
	}

	public final java.util.Set<String> aliases() {
		java.util.Set<String> set = new java.util.HashSet<String>();
		for (int i = 0; i < aliases.length; i++) {
			set.add(aliases[i]);
		}
		return java.util.Collections.unmodifiableSet(set);
	}

	public final boolean isRegistered() {
		return true;
	}

	public boolean canEncode() {
		return true;
	}

	public boolean contains(Charset cs) {
		if (kind == UTF8) {
			return true;
		}
		if (kind == LATIN1) {
			return cs.kind != UTF8;
		}
		return cs.kind == ASCII;
	}

	public CharsetDecoder newDecoder() {
		return new CharsetDecoder(this);
	}

	public CharsetEncoder newEncoder() {
		return new CharsetEncoder(this);
	}

	public final int compareTo(Charset that) {
		return name().compareToIgnoreCase(that.name());
	}

	public final int hashCode() {
		return name().hashCode();
	}

	public final boolean equals(Object ob) {
		if (this == ob) {
			return true;
		}
		if (!(ob instanceof Charset)) {
			return false;
		}
		return name.equals(((Charset) ob).name());
	}

	public final String toString() {
		return name;
	}

	/** J2C: the name InputStreamReader.getEncoding and OutputStreamWriter.getEncoding report */
	public String j2cHistoricalName() {
		if (kind == UTF8) {
			return "UTF8";
		}
		return kind == LATIN1 ? "ISO8859_1" : "ASCII";
	}

	/** J2C: decodes bytes with replacement, as new String(bytes, charset) does */
	public char[] j2cDecodeAll(byte[] bytes, int offset, int length) {
		CharsetDecoder decoder = newDecoder();
		decoder.onMalformedInput(CodingErrorAction.REPLACE);
		decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
		char[] chars = new char[length];
		try {
			decoder.j2cDecode(bytes, offset, offset + length, chars, 0, length, true);
		} catch (CharacterCodingException e) {
			throw new InternalError(e);
		}
		return trim(chars, decoder.j2cOutPosition());
	}

	/** J2C: encodes characters with replacement, as String.getBytes(charset) does */
	public byte[] j2cEncodeAll(char[] chars, int offset, int length) {
		CharsetEncoder encoder = newEncoder();
		encoder.onMalformedInput(CodingErrorAction.REPLACE);
		encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
		byte[] bytes = new byte[length * 3];
		try {
			encoder.j2cEncode(chars, offset, offset + length, bytes, 0, bytes.length, true);
		} catch (CharacterCodingException e) {
			throw new InternalError(e);
		}
		int n = encoder.j2cOutPosition();
		byte[] result = new byte[n];
		System.arraycopy(bytes, 0, result, 0, n);
		return result;
	}

	private static char[] trim(char[] chars, int n) {
		if (n == chars.length) {
			return chars;
		}
		char[] result = new char[n];
		System.arraycopy(chars, 0, result, 0, n);
		return result;
	}
}
