package java.io;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

/**
 * Encodes characters into an 8192-byte buffer, written when it fills, on
 * flush and on close, as the JDK's StreamEncoder does. A high surrogate
 * at the end of a write waits for the next.
 */
public class OutputStreamWriter extends Writer {
	private static final int DEFAULT_BYTE_BUFFER_SIZE = 8192;

	private final OutputStream out;
	private final CharsetEncoder encoder;
	private final byte[] bb = new byte[DEFAULT_BYTE_BUFFER_SIZE];
	private int bbPos;
	private boolean haveLeftoverChar;
	private char leftoverChar;
	private boolean closed;

	public OutputStreamWriter(OutputStream out) {
		this(out, Charset.defaultCharset());
	}

	public OutputStreamWriter(OutputStream out, String charsetName) throws UnsupportedEncodingException {
		this(out, lookup(charsetName));
	}

	public OutputStreamWriter(OutputStream out, Charset cs) {
		this(out, replacing(cs));
	}

	public OutputStreamWriter(OutputStream out, CharsetEncoder enc) {
		super(out);
		if (enc == null) {
			throw new NullPointerException("charset encoder");
		}
		this.out = out;
		this.encoder = enc;
	}

	private static Charset lookup(String charsetName) throws UnsupportedEncodingException {
		if (charsetName == null) {
			throw new NullPointerException("charsetName");
		}
		try {
			if (Charset.isSupported(charsetName)) {
				return Charset.forName(charsetName);
			}
		} catch (IllegalArgumentException e) {
			// An illegal name is unsupported too
		}
		throw new UnsupportedEncodingException(charsetName);
	}

	private static CharsetEncoder replacing(Charset cs) {
		if (cs == null) {
			throw new NullPointerException("charset");
		}
		return cs.newEncoder().onMalformedInput(CodingErrorAction.REPLACE)
				.onUnmappableCharacter(CodingErrorAction.REPLACE);
	}

	public String getEncoding() {
		return closed ? null : encoder.charset().j2cHistoricalName();
	}

	private void ensureOpen() throws IOException {
		if (closed) {
			throw new IOException("Stream closed");
		}
	}

	public void write(int c) throws IOException {
		write(new char[] { (char) c }, 0, 1);
	}

	public void write(char[] cbuf, int off, int len) throws IOException {
		ensureOpen();
		if ((off < 0) || (off > cbuf.length) || (len < 0) || ((off + len) > cbuf.length) || ((off + len) < 0)) {
			throw new IndexOutOfBoundsException();
		}
		if (len == 0) {
			return;
		}
		while (haveLeftoverChar && len > 0) {
			haveLeftoverChar = false;
			encode(new char[] { leftoverChar, cbuf[off] }, 0, 2, false);
			off++;
			len--;
		}
		encode(cbuf, off, off + len, false);
	}

	public void write(String str, int off, int len) throws IOException {
		if (len < 0) {
			throw new IndexOutOfBoundsException();
		}
		char[] cbuf = new char[len];
		str.getChars(off, off + len, cbuf, 0);
		write(cbuf, 0, len);
	}

	/** Encodes chars[from, to); a final high surrogate becomes the leftover unless endOfInput */
	private void encode(char[] chars, int from, int to, boolean endOfInput) throws IOException {
		int p = from;
		for (;;) {
			boolean overflow;
			try {
				overflow = encoder.j2cEncode(chars, p, to, bb, bbPos, bb.length, endOfInput);
			} finally {
				// Bytes encoded before malformed input are kept, and written on close
				p = encoder.j2cInPosition();
				bbPos = encoder.j2cOutPosition();
			}
			if (overflow) {
				writeBytes();
				continue;
			}
			if (p < to) {
				haveLeftoverChar = true;
				leftoverChar = chars[p];
			}
			return;
		}
	}

	private void writeBytes() throws IOException {
		if (bbPos > 0) {
			int n = bbPos;
			bbPos = 0;
			out.write(bb, 0, n);
		}
	}

	/** Writes the encoded bytes without flushing the stream, for PrintStream */
	void flushBuffer() throws IOException {
		ensureOpen();
		writeBytes();
	}

	public void flush() throws IOException {
		ensureOpen();
		writeBytes();
		out.flush();
	}

	public void close() throws IOException {
		if (closed) {
			return;
		}
		try {
			if (haveLeftoverChar) {
				haveLeftoverChar = false;
				encode(new char[] { leftoverChar }, 0, 1, true);
			}
			writeBytes();
			out.flush();
		} finally {
			closed = true;
			out.close();
		}
	}
}
