package java.nio.charset;

/**
 * Simplified CharsetEncoder, without ByteBuffer: the runtime's writers and
 * String call j2cEncode on arrays. An unpaired surrogate is malformed input
 * and a character the charset lacks is unmappable; both are replaced by '?'
 * unless the action says otherwise.
 */
public class CharsetEncoder {
	private final Charset charset;
	private CodingErrorAction malformedInputAction = CodingErrorAction.REPORT;
	private CodingErrorAction unmappableCharacterAction = CodingErrorAction.REPORT;
	private int inPosition;
	private int outPosition;

	CharsetEncoder(Charset charset) {
		this.charset = charset;
	}

	public final Charset charset() {
		return charset;
	}

	public final byte[] replacement() {
		return new byte[] { (byte) '?' };
	}

	public CodingErrorAction malformedInputAction() {
		return malformedInputAction;
	}

	public CodingErrorAction unmappableCharacterAction() {
		return unmappableCharacterAction;
	}

	public final CharsetEncoder onMalformedInput(CodingErrorAction newAction) {
		if (newAction == null) {
			throw new IllegalArgumentException("Null action");
		}
		malformedInputAction = newAction;
		return this;
	}

	public final CharsetEncoder onUnmappableCharacter(CodingErrorAction newAction) {
		if (newAction == null) {
			throw new IllegalArgumentException("Null action");
		}
		unmappableCharacterAction = newAction;
		return this;
	}

	public final float averageBytesPerChar() {
		return charset.kind == Charset.UTF8 ? 1.1f : 1.0f;
	}

	public final float maxBytesPerChar() {
		return charset.kind == Charset.UTF8 ? 3.0f : 1.0f;
	}

	public boolean canEncode(char c) {
		if (c >= '\uD800' && c <= '\uDFFF') {
			return false;
		}
		return charset.kind == Charset.UTF8 || c < (charset.kind == Charset.LATIN1 ? 0x100 : 0x80);
	}

	public final CharsetEncoder reset() {
		return this;
	}

	/** J2C: the input position the last j2cEncode stopped at */
	public int j2cInPosition() {
		return inPosition;
	}

	/** J2C: the output position the last j2cEncode stopped at */
	public int j2cOutPosition() {
		return outPosition;
	}

	/**
	 * J2C: encodes src[sp, sl) into dst[dp, dl). Stops when the output is full
	 * (returns true) or the input is used up (false). A high surrogate at the
	 * end stays unread unless endOfInput, where it is malformed.
	 */
	public boolean j2cEncode(char[] src, int sp, int sl, byte[] dst, int dp, int dl, boolean endOfInput)
			throws CharacterCodingException {
		boolean overflow = false;
		while (sp < sl) {
			char c = src[sp];
			int limit = charset.kind == Charset.UTF8 ? 0x10000 : charset.kind == Charset.LATIN1 ? 0x100 : 0x80;
			if (c < 0x80 || (c < limit && (c < '\uD800' || c > '\uDFFF'))) {
				int size = c < 0x80 || charset.kind != Charset.UTF8 ? 1 : c < 0x800 ? 2 : 3;
				if (dl - dp < size) {
					overflow = true;
					break;
				}
				if (size == 1) {
					dst[dp++] = (byte) c;
				} else if (size == 2) {
					dst[dp++] = (byte) (0xC0 | (c >> 6));
					dst[dp++] = (byte) (0x80 | (c & 0x3F));
				} else {
					dst[dp++] = (byte) (0xE0 | (c >> 12));
					dst[dp++] = (byte) (0x80 | ((c >> 6) & 0x3F));
					dst[dp++] = (byte) (0x80 | (c & 0x3F));
				}
				sp++;
				continue;
			}
			boolean malformed;
			int length;
			if (c >= '\uD800' && c <= '\uDBFF') {
				if (sp + 1 >= sl) {
					if (!endOfInput) {
						break;
					}
					malformed = true;
					length = 1;
				} else if (src[sp + 1] >= '\uDC00' && src[sp + 1] <= '\uDFFF') {
					if (charset.kind == Charset.UTF8) {
						if (dl - dp < 4) {
							overflow = true;
							break;
						}
						int cp = ((c - 0xD800) << 10) + (src[sp + 1] - 0xDC00) + 0x10000;
						dst[dp++] = (byte) (0xF0 | (cp >> 18));
						dst[dp++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
						dst[dp++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
						dst[dp++] = (byte) (0x80 | (cp & 0x3F));
						sp += 2;
						continue;
					}
					malformed = false;
					length = 2;
				} else {
					malformed = true;
					length = 1;
				}
			} else if (c >= '\uDC00' && c <= '\uDFFF') {
				malformed = true;
				length = 1;
			} else {
				malformed = false;
				length = 1;
			}
			CodingErrorAction action = malformed ? malformedInputAction : unmappableCharacterAction;
			if (action == CodingErrorAction.REPORT) {
				// The text before the error stays encoded, as with a CharBuffer
				inPosition = sp;
				outPosition = dp;
				if (malformed) {
					throw new MalformedInputException(length);
				}
				throw new UnmappableCharacterException(length);
			}
			if (action == CodingErrorAction.REPLACE) {
				if (dp >= dl) {
					overflow = true;
					break;
				}
				dst[dp++] = (byte) '?';
			}
			sp += length;
		}
		inPosition = sp;
		outPosition = dp;
		return overflow;
	}
}
