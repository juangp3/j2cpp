package java.io;

/** Simplified PrintStream: encodes UTF-16 to UTF-8 itself, no charset machinery. */
public class PrintStream {
	private final OutputStream out;

	public PrintStream(OutputStream out) {
		this.out = out;
	}

	public void print(String s) {
		if (s == null) {
			s = "null";
		}
		byte[] buf = new byte[s.length() * 3];
		int n = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c >= 0xD800 && c <= 0xDBFF && i + 1 < s.length()) {
				char low = s.charAt(i + 1);
				if (low >= 0xDC00 && low <= 0xDFFF) {
					int cp = ((c - 0xD800) << 10) + (low - 0xDC00) + 0x10000;
					buf = ensure(buf, n + 4);
					buf[n++] = (byte) (0xF0 | (cp >> 18));
					buf[n++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
					buf[n++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
					buf[n++] = (byte) (0x80 | (cp & 0x3F));
					i++;
					continue;
				}
			}
			if (c < 0x80) {
				buf[n++] = (byte) c;
			} else if (c < 0x800) {
				buf[n++] = (byte) (0xC0 | (c >> 6));
				buf[n++] = (byte) (0x80 | (c & 0x3F));
			} else if (c >= 0xD800 && c <= 0xDFFF) {
				// An unpaired surrogate is written as '?', as Java does
				buf[n++] = (byte) '?';
			} else {
				buf[n++] = (byte) (0xE0 | (c >> 12));
				buf[n++] = (byte) (0x80 | ((c >> 6) & 0x3F));
				buf[n++] = (byte) (0x80 | (c & 0x3F));
			}
		}
		out.write(buf, 0, n);
	}

	private static byte[] ensure(byte[] buf, int size) {
		if (size <= buf.length) {
			return buf;
		}
		byte[] larger = new byte[size * 2];
		System.arraycopy(buf, 0, larger, 0, buf.length);
		return larger;
	}

	public void print(Object obj) {
		print(String.valueOf(obj));
	}

	public void print(char[] s) {
		print(new String(s));
	}

	public void print(char c) {
		print(String.valueOf(c));
	}

	public void print(boolean b) {
		print(String.valueOf(b));
	}

	public void print(int i) {
		print(String.valueOf(i));
	}

	public void print(long l) {
		print(String.valueOf(l));
	}

	public void print(float f) {
		print(String.valueOf(f));
	}

	public void print(double d) {
		print(String.valueOf(d));
	}

	public void println() {
		out.write(new byte[] { 10 }, 0, 1);
	}

	public void println(String s) {
		print(s);
		println();
	}

	public void println(Object obj) {
		println(String.valueOf(obj));
	}

	public void println(char[] s) {
		println(new String(s));
	}

	public void println(char c) {
		println(String.valueOf(c));
	}

	public void println(boolean b) {
		println(String.valueOf(b));
	}

	public void println(int i) {
		println(String.valueOf(i));
	}

	public void println(long l) {
		println(String.valueOf(l));
	}

	public void println(float f) {
		println(String.valueOf(f));
	}

	public void println(double d) {
		println(String.valueOf(d));
	}

	public void flush() {
	}
}
