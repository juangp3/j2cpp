package java.io;
/** Simplified PrintStream: encodes UTF-16 to UTF-8 itself, no charset machinery. */
public class PrintStream {
	private final OutputStream out;
	public PrintStream(OutputStream out) {
		this.out = out;
	}
	public void print(String s) {
		byte[] buf = new byte[s.length() * 3];
		int n = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c < 0x80) {
				buf[n++] = (byte) c;
			} else if (c < 0x800) {
				buf[n++] = (byte) (0xC0 | (c >> 6));
				buf[n++] = (byte) (0x80 | (c & 0x3F));
			} else {
				buf[n++] = (byte) (0xE0 | (c >> 12));
				buf[n++] = (byte) (0x80 | ((c >> 6) & 0x3F));
				buf[n++] = (byte) (0x80 | (c & 0x3F));
			}
		}
		out.write(buf, 0, n);
	}
	public void println(String s) {
		print(s);
		out.write(new byte[] { 10 }, 0, 1);
	}
}
