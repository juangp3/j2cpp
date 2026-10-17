package java.io;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;

/**
 * Decodes bytes as the JDK's StreamDecoder does: 8192 bytes are read at a
 * time, and a read blocks at most once.
 */
public class InputStreamReader extends Reader {
	private static final int DEFAULT_BYTE_BUFFER_SIZE = 8192;

	private final InputStream in;
	private final CharsetDecoder decoder;
	private final byte[] bb = new byte[DEFAULT_BYTE_BUFFER_SIZE];
	private int bbPos;
	private int bbLim;
	private boolean haveLeftoverChar;
	private char leftoverChar;
	private boolean closed;

	public InputStreamReader(InputStream in) {
		this(in, Charset.defaultCharset());
	}

	public InputStreamReader(InputStream in, String charsetName) throws UnsupportedEncodingException {
		this(in, lookup(charsetName));
	}

	public InputStreamReader(InputStream in, Charset cs) {
		this(in, replacing(cs));
	}

	public InputStreamReader(InputStream in, CharsetDecoder dec) {
		super(in);
		if (dec == null) {
			throw new NullPointerException("charset decoder");
		}
		this.in = in;
		this.decoder = dec;
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

	private static CharsetDecoder replacing(Charset cs) {
		if (cs == null) {
			throw new NullPointerException("charset");
		}
		return cs.newDecoder().onMalformedInput(CodingErrorAction.REPLACE)
				.onUnmappableCharacter(CodingErrorAction.REPLACE);
	}

	public String getEncoding() {
		return closed ? null : decoder.charset().j2cHistoricalName();
	}

	private void ensureOpen() throws IOException {
		if (closed) {
			throw new IOException("Stream closed");
		}
	}

	public int read() throws IOException {
		ensureOpen();
		return read0();
	}

	private int read0() throws IOException {
		if (haveLeftoverChar) {
			haveLeftoverChar = false;
			return leftoverChar;
		}
		char[] cb = new char[2];
		int n = read(cb, 0, 2);
		if (n == -1) {
			return -1;
		}
		if (n == 2) {
			leftoverChar = cb[1];
			haveLeftoverChar = true;
		}
		return cb[0];
	}

	public int read(char[] cbuf, int offset, int length) throws IOException {
		ensureOpen();
		int off = offset;
		int len = length;
		if ((off < 0) || (off > cbuf.length) || (len < 0) || ((off + len) > cbuf.length) || ((off + len) < 0)) {
			throw new IndexOutOfBoundsException();
		}
		if (len == 0) {
			return 0;
		}
		int n = 0;
		if (haveLeftoverChar) {
			cbuf[off] = leftoverChar;
			off++;
			len--;
			haveLeftoverChar = false;
			n = 1;
			if ((len == 0) || !implReady()) {
				return n;
			}
		}
		if (len == 1) {
			int c = read0();
			if (c == -1) {
				return (n == 0) ? -1 : n;
			}
			cbuf[off] = (char) c;
			return n + 1;
		}
		int nr = implRead(cbuf, off, off + len);
		return (nr < 0) ? (n == 1 ? 1 : nr) : (n + nr);
	}

	private boolean inReady() {
		try {
			return in.available() > 0;
		} catch (IOException e) {
			return false;
		}
	}

	private boolean implReady() {
		return bbPos < bbLim || inReady();
	}

	private int readBytes() throws IOException {
		if (bbPos > 0) {
			System.arraycopy(bb, bbPos, bb, 0, bbLim - bbPos);
			bbLim -= bbPos;
			bbPos = 0;
		}
		int n = in.read(bb, bbLim, bb.length - bbLim);
		if (n < 0) {
			return n;
		}
		if (n == 0) {
			throw new IOException("Underlying input stream returned zero bytes");
		}
		bbLim += n;
		return bbLim - bbPos;
	}

	private int implRead(char[] cbuf, int off, int end) throws IOException {
		int pos = off;
		boolean eof = false;
		for (;;) {
			boolean overflow;
			try {
				overflow = decoder.j2cDecode(bb, bbPos, bbLim, cbuf, pos, end, eof);
			} finally {
				bbPos = decoder.j2cInPosition();
				pos = decoder.j2cOutPosition();
			}
			if (overflow) {
				break;
			}
			if (eof) {
				break;
			}
			if (pos == end) {
				break;
			}
			if (pos > off && !inReady()) {
				break;
			}
			int n = readBytes();
			if (n < 0) {
				eof = true;
				if (pos == off && bbPos == bbLim) {
					break;
				}
			}
		}
		if (pos == off && eof) {
			return -1;
		}
		return pos - off;
	}

	public boolean ready() throws IOException {
		ensureOpen();
		return haveLeftoverChar || implReady();
	}

	public void close() throws IOException {
		if (closed) {
			return;
		}
		closed = true;
		in.close();
	}
}
