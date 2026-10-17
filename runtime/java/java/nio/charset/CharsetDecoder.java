package java.nio.charset;

/**
 * Simplified CharsetDecoder, without ByteBuffer: the runtime's readers and
 * String call j2cDecode on arrays. Malformed input is found and measured
 * as the JDK's decoders do, so replacement characters appear in the same
 * places.
 */
public class CharsetDecoder {
	private final Charset charset;
	private CodingErrorAction malformedInputAction = CodingErrorAction.REPORT;
	private CodingErrorAction unmappableCharacterAction = CodingErrorAction.REPORT;
	private int inPosition;
	private int outPosition;

	CharsetDecoder(Charset charset) {
		this.charset = charset;
	}

	public final Charset charset() {
		return charset;
	}

	public final String replacement() {
		return "�";
	}

	public CodingErrorAction malformedInputAction() {
		return malformedInputAction;
	}

	public CodingErrorAction unmappableCharacterAction() {
		return unmappableCharacterAction;
	}

	public final CharsetDecoder onMalformedInput(CodingErrorAction newAction) {
		if (newAction == null) {
			throw new IllegalArgumentException("Null action");
		}
		malformedInputAction = newAction;
		return this;
	}

	public final CharsetDecoder onUnmappableCharacter(CodingErrorAction newAction) {
		if (newAction == null) {
			throw new IllegalArgumentException("Null action");
		}
		unmappableCharacterAction = newAction;
		return this;
	}

	public final float averageCharsPerByte() {
		return 1.0f;
	}

	public final float maxCharsPerByte() {
		return 1.0f;
	}

	public final CharsetDecoder reset() {
		return this;
	}

	/** J2C: the input position the last j2cDecode stopped at */
	public int j2cInPosition() {
		return inPosition;
	}

	/** J2C: the output position the last j2cDecode stopped at */
	public int j2cOutPosition() {
		return outPosition;
	}

	/**
	 * J2C: decodes src[sp, sl) into dst[dp, dl). Stops when the output is full
	 * (returns true) or the input is used up or ends inside a sequence
	 * (returns false; the incomplete bytes stay unread unless endOfInput,
	 * where they are malformed). The positions reached are j2cInPosition and
	 * j2cOutPosition.
	 */
	public boolean j2cDecode(byte[] src, int sp, int sl, char[] dst, int dp, int dl, boolean endOfInput)
			throws CharacterCodingException {
		boolean overflow = false;
		while (sp < sl) {
			int b1 = src[sp];
			int malformed = 0;
			if (b1 >= 0) {
				if (dp >= dl) {
					overflow = true;
					break;
				}
				dst[dp++] = (char) b1;
				sp++;
				continue;
			}
			if (charset.kind == Charset.LATIN1) {
				if (dp >= dl) {
					overflow = true;
					break;
				}
				dst[dp++] = (char) (b1 & 0xFF);
				sp++;
				continue;
			}
			int remaining = sl - sp;
			if (charset.kind == Charset.ASCII) {
				malformed = 1;
			} else if ((b1 >> 5) == -2 && (b1 & 0x1E) != 0) {
				if (remaining < 2) {
					break;
				}
				int b2 = src[sp + 1];
				if ((b2 & 0xC0) != 0x80) {
					malformed = 1;
				} else {
					if (dp >= dl) {
						overflow = true;
						break;
					}
					dst[dp++] = (char) (((b1 & 0x1F) << 6) | (b2 & 0x3F));
					sp += 2;
					continue;
				}
			} else if ((b1 >> 4) == -2) {
				if (remaining < 3) {
					if (remaining > 1 && isMalformed3Second(b1, src[sp + 1])) {
						malformed = 1;
					} else {
						break;
					}
				} else {
					int b2 = src[sp + 1];
					int b3 = src[sp + 2];
					if (isMalformed3Second(b1, b2) || (b3 & 0xC0) != 0x80) {
						malformed = isMalformed3Second(b1, b2) ? 1 : 2;
					} else {
						char c = (char) (((b1 & 0x0F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F));
						if (c >= '\uD800' && c <= '\uDFFF') {
							malformed = 3;
						} else {
							if (dp >= dl) {
								overflow = true;
								break;
							}
							dst[dp++] = c;
							sp += 3;
							continue;
						}
					}
				}
			} else if ((b1 >> 3) == -2) {
				int u1 = b1 & 0xFF;
				if (remaining < 4) {
					// A lead byte past F4 or a second byte out of range is malformed
					// before the rest arrives
					if (u1 > 0xF4 || (remaining > 1 && isMalformed4Second(u1, src[sp + 1] & 0xFF))) {
						malformed = 1;
					} else if (remaining > 2 && (src[sp + 2] & 0xC0) != 0x80) {
						malformed = 2;
					} else {
						break;
					}
				} else {
					int b2 = src[sp + 1];
					int b3 = src[sp + 2];
					int b4 = src[sp + 3];
					int uc = ((b1 & 0x07) << 18) | ((b2 & 0x3F) << 12) | ((b3 & 0x3F) << 6) | (b4 & 0x3F);
					if ((b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80 || (b4 & 0xC0) != 0x80 || uc < 0x10000
							|| uc > 0x10FFFF) {
						// Measured as the JDK measures it
						int u2 = b2 & 0xFF;
						if (u1 > 0xF4 || (u1 == 0xF0 && (u2 < 0x90 || u2 > 0xBF))
								|| (u1 == 0xF4 && (u2 & 0xF0) != 0x80) || (u2 & 0xC0) != 0x80) {
							malformed = 1;
						} else if ((b3 & 0xC0) != 0x80) {
							malformed = 2;
						} else {
							malformed = 3;
						}
					} else {
						if (dl - dp < 2) {
							overflow = true;
							break;
						}
						dst[dp++] = (char) (0xD800 + ((uc - 0x10000) >> 10));
						dst[dp++] = (char) (0xDC00 + ((uc - 0x10000) & 0x3FF));
						sp += 4;
						continue;
					}
				}
			} else {
				malformed = 1;
			}
			inPosition = sp;
			outPosition = dp;
			if (!replaceMalformed(malformed, dst, dp, dl)) {
				overflow = true;
				break;
			}
			if (malformedInputAction == CodingErrorAction.REPLACE) {
				dp++;
			}
			sp += malformed;
		}
		if (!overflow && endOfInput && sp < sl) {
			// The input ends inside a sequence: all of it is one malformed input
			inPosition = sp;
			outPosition = dp;
			if (!replaceMalformed(sl - sp, dst, dp, dl)) {
				overflow = true;
			} else {
				if (malformedInputAction == CodingErrorAction.REPLACE) {
					dp++;
				}
				sp = sl;
			}
		}
		inPosition = sp;
		outPosition = dp;
		return overflow;
	}

	/**
	 * Writes the replacement (without advancing); false when there is no room
	 * for it. A report leaves the positions at the malformed input.
	 */
	private boolean replaceMalformed(int length, char[] dst, int dp, int dl) throws CharacterCodingException {
		if (malformedInputAction == CodingErrorAction.REPORT) {
			throw new MalformedInputException(length);
		}
		if (malformedInputAction == CodingErrorAction.REPLACE) {
			if (dp >= dl) {
				return false;
			}
			dst[dp] = '�';
		}
		return true;
	}

	private static boolean isMalformed3Second(int b1, int b2) {
		return (b1 == (byte) 0xE0 && (b2 & 0xE0) == 0x80) || (b2 & 0xC0) != 0x80;
	}

	private static boolean isMalformed4Second(int u1, int u2) {
		return (u1 == 0xF0 && (u2 < 0x90 || u2 > 0xBF)) || (u1 == 0xF4 && (u2 & 0xF0) != 0x80)
				|| (u2 & 0xC0) != 0x80;
	}
}
