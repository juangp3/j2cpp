package java.io;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class BufferedReader extends Reader {
	private static final int INVALIDATED = -2;
	private static final int UNMARKED = -1;
	private static final int DEFAULT_CHAR_BUFFER_SIZE = 8192;
	private static final int DEFAULT_EXPECTED_LINE_LENGTH = 80;

	private Reader in;
	private char[] cb;
	private int nChars;
	private int nextChar;
	private int markedChar = UNMARKED;
	private int readAheadLimit = 0;
	private boolean skipLF = false;
	private boolean markedSkipLF = false;

	public BufferedReader(Reader in, int sz) {
		super(in);
		if (sz <= 0) {
			throw new IllegalArgumentException("Buffer size <= 0");
		}
		this.in = in;
		cb = new char[sz];
		nextChar = nChars = 0;
	}

	public BufferedReader(Reader in) {
		this(in, DEFAULT_CHAR_BUFFER_SIZE);
	}

	private void ensureOpen() throws IOException {
		if (in == null) {
			throw new IOException("Stream closed");
		}
	}

	private void fill() throws IOException {
		int dst;
		if (markedChar <= UNMARKED) {
			dst = 0;
		} else {
			int delta = nextChar - markedChar;
			if (delta >= readAheadLimit) {
				markedChar = INVALIDATED;
				readAheadLimit = 0;
				dst = 0;
			} else {
				if (readAheadLimit <= cb.length) {
					System.arraycopy(cb, markedChar, cb, 0, delta);
					markedChar = 0;
					dst = delta;
				} else {
					char[] ncb = new char[readAheadLimit];
					System.arraycopy(cb, markedChar, ncb, 0, delta);
					cb = ncb;
					markedChar = 0;
					dst = delta;
				}
				nextChar = nChars = delta;
			}
		}
		int n;
		do {
			n = in.read(cb, dst, cb.length - dst);
		} while (n == 0);
		if (n > 0) {
			nChars = dst + n;
			nextChar = dst;
		}
	}

	public int read() throws IOException {
		ensureOpen();
		for (;;) {
			if (nextChar >= nChars) {
				fill();
				if (nextChar >= nChars) {
					return -1;
				}
			}
			if (skipLF) {
				skipLF = false;
				if (cb[nextChar] == '\n') {
					nextChar++;
					continue;
				}
			}
			return cb[nextChar++];
		}
	}

	private int read1(char[] cbuf, int off, int len) throws IOException {
		if (nextChar >= nChars) {
			if (len >= cb.length && markedChar <= UNMARKED && !skipLF) {
				return in.read(cbuf, off, len);
			}
			fill();
		}
		if (nextChar >= nChars) {
			return -1;
		}
		if (skipLF) {
			skipLF = false;
			if (cb[nextChar] == '\n') {
				nextChar++;
				if (nextChar >= nChars) {
					fill();
				}
				if (nextChar >= nChars) {
					return -1;
				}
			}
		}
		int n = Math.min(len, nChars - nextChar);
		System.arraycopy(cb, nextChar, cbuf, off, n);
		nextChar += n;
		return n;
	}

	public int read(char[] cbuf, int off, int len) throws IOException {
		ensureOpen();
		java.util.Objects.checkFromIndexSize(off, len, cbuf.length);
		if (len == 0) {
			return 0;
		}
		int n = read1(cbuf, off, len);
		if (n <= 0) {
			return n;
		}
		while ((n < len) && in.ready()) {
			int n1 = read1(cbuf, off + n, len - n);
			if (n1 <= 0) {
				break;
			}
			n += n1;
		}
		return n;
	}

	public String readLine() throws IOException {
		StringBuilder s = null;
		ensureOpen();
		boolean omitLF = skipLF;
		for (;;) {
			if (nextChar >= nChars) {
				fill();
			}
			if (nextChar >= nChars) {
				if (s != null && s.length() > 0) {
					return s.toString();
				}
				return null;
			}
			boolean eol = false;
			char c = 0;
			int i;
			if (omitLF && (cb[nextChar] == '\n')) {
				nextChar++;
			}
			skipLF = false;
			omitLF = false;
			for (i = nextChar; i < nChars; i++) {
				c = cb[i];
				if ((c == '\n') || (c == '\r')) {
					eol = true;
					break;
				}
			}
			int startChar = nextChar;
			nextChar = i;
			if (eol) {
				String str;
				if (s == null) {
					str = new String(cb, startChar, i - startChar);
				} else {
					s.append(cb, startChar, i - startChar);
					str = s.toString();
				}
				nextChar++;
				if (c == '\r') {
					skipLF = true;
				}
				return str;
			}
			if (s == null) {
				s = new StringBuilder(DEFAULT_EXPECTED_LINE_LENGTH);
			}
			s.append(cb, startChar, i - startChar);
		}
	}

	public long skip(long n) throws IOException {
		if (n < 0L) {
			throw new IllegalArgumentException("skip value is negative");
		}
		ensureOpen();
		long r = n;
		while (r > 0) {
			if (nextChar >= nChars) {
				fill();
			}
			if (nextChar >= nChars) {
				break;
			}
			if (skipLF) {
				skipLF = false;
				if (cb[nextChar] == '\n') {
					nextChar++;
				}
			}
			long d = nChars - nextChar;
			if (r <= d) {
				nextChar += (int) r;
				r = 0;
				break;
			}
			r -= d;
			nextChar = nChars;
		}
		return n - r;
	}

	public boolean ready() throws IOException {
		ensureOpen();
		if (skipLF) {
			if (nextChar >= nChars && in.ready()) {
				fill();
			}
			if (nextChar < nChars) {
				if (cb[nextChar] == '\n') {
					nextChar++;
				}
				skipLF = false;
			}
		}
		return (nextChar < nChars) || in.ready();
	}

	public boolean markSupported() {
		return true;
	}

	public void mark(int readAheadLimit) throws IOException {
		if (readAheadLimit < 0) {
			throw new IllegalArgumentException("Read-ahead limit < 0");
		}
		ensureOpen();
		this.readAheadLimit = readAheadLimit;
		markedChar = nextChar;
		markedSkipLF = skipLF;
	}

	public void reset() throws IOException {
		ensureOpen();
		if (markedChar < 0) {
			throw new IOException((markedChar == INVALIDATED) ? "Mark invalid" : "Stream not marked");
		}
		nextChar = markedChar;
		skipLF = markedSkipLF;
	}

	public void close() throws IOException {
		if (in == null) {
			return;
		}
		try {
			in.close();
		} finally {
			in = null;
			cb = null;
		}
	}

	public Stream<String> lines() {
		Iterator<String> iter = new Iterator<String>() {
			String nextLine = null;

			public boolean hasNext() {
				if (nextLine != null) {
					return true;
				}
				try {
					nextLine = readLine();
					return (nextLine != null);
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			}

			public String next() {
				if (nextLine != null || hasNext()) {
					String line = nextLine;
					nextLine = null;
					return line;
				}
				throw new NoSuchElementException();
			}
		};
		return StreamSupport.stream(
				Spliterators.spliteratorUnknownSize(iter, Spliterator.ORDERED | Spliterator.NONNULL), false);
	}
}
