import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

// In-memory streams, readers and writers, and UTF-8 coding (R6)
public class StreamsIo {
	static String hex(byte[] bytes) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < bytes.length; i++) {
			if (i > 0) {
				sb.append(' ');
			}
			sb.append(String.format("%02x", bytes[i] & 0xFF));
		}
		return sb.append(']').toString();
	}

	static String codes(String s) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < s.length(); i++) {
			if (i > 0) {
				sb.append(' ');
			}
			sb.append(Integer.toHexString(s.charAt(i)));
		}
		return sb.append(']').toString();
	}

	static byte[] bytes(int... values) {
		byte[] result = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			result[i] = (byte) values[i];
		}
		return result;
	}

	/** Reads one byte at a time, so the defaults of InputStream do the rest */
	static class SlowInput extends InputStream {
		private final byte[] data;
		private int pos;

		SlowInput(byte[] data) {
			this.data = data;
		}

		public int read() {
			return pos < data.length ? data[pos++] & 0xFF : -1;
		}
	}

	/** Records the bytes written one at a time */
	static class Recorder extends OutputStream {
		final StringBuilder log = new StringBuilder();

		public void write(int b) {
			log.append((char) b);
		}

		public void flush() {
			log.append("|flush|");
		}

		public void close() {
			log.append("|close|");
		}
	}

	static class Resource implements Closeable {
		private final String name;
		private final boolean failOnClose;

		Resource(String name, boolean failOnClose) {
			this.name = name;
			this.failOnClose = failOnClose;
			System.out.println("open " + name);
		}

		public void close() throws IOException {
			System.out.println("close " + name);
			if (failOnClose) {
				throw new IOException("close failed: " + name);
			}
		}
	}

	public static void main(String[] args) throws IOException {
		byteStreams();
		readersAndWriters();
		bufferedReader();
		printWriter();
		printStream();
		utf8();
		charsets();
		resources();
	}

	static void byteStreams() throws IOException {
		System.out.println("-- byte streams");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(72);
		out.write(new byte[] { 105, 33, 63 }, 0, 2);
		out.writeBytes(" there".getBytes(StandardCharsets.UTF_8));
		System.out.println(out.size() + " " + out + " " + hex(out.toByteArray()));
		out.reset();
		out.write(65);
		System.out.println(out.toString(StandardCharsets.UTF_8) + " " + out.size());
		try {
			new ByteArrayOutputStream(-1);
		} catch (IllegalArgumentException e) {
			System.out.println(e.getMessage());
		}

		ByteArrayInputStream in = new ByteArrayInputStream("abcdefghij".getBytes(StandardCharsets.UTF_8));
		System.out.println((char) in.read() + " " + in.available() + " " + in.skip(2));
		byte[] buffer = new byte[4];
		System.out.println(in.read(buffer, 1, 3) + " " + hex(buffer));
		in.mark(0);
		System.out.println(new String(in.readNBytes(2), StandardCharsets.UTF_8));
		in.reset();
		System.out.println(new String(in.readAllBytes(), StandardCharsets.UTF_8) + " " + in.read() + " "
				+ in.read(buffer, 0, 4) + " " + in.read(buffer, 0, 0));
		ByteArrayInputStream part = new ByteArrayInputStream("0123456789".getBytes(StandardCharsets.UTF_8), 3, 4);
		ByteArrayOutputStream copy = new ByteArrayOutputStream();
		System.out.println(part.transferTo(copy) + " " + copy);
		try {
			new ByteArrayInputStream(new byte[2]).skipNBytes(3);
		} catch (EOFException e) {
			System.out.println("EOFException " + e.getMessage());
		}
		try {
			in.read(buffer, 2, 3);
		} catch (IndexOutOfBoundsException e) {
			System.out.println(e.getClass().getName());
		}

		SlowInput slow = new SlowInput("slow bytes".getBytes(StandardCharsets.UTF_8));
		byte[] four = new byte[4];
		System.out.println(slow.read(four) + " " + new String(four, StandardCharsets.UTF_8));
		Recorder recorder = new Recorder();
		System.out.println(slow.transferTo(recorder) + " " + recorder.log);
		System.out.println(new SlowInput(new byte[] { 1, 2, 3 }).readAllBytes().length);
		System.out.println(InputStream.nullInputStream().read() + " " + InputStream.nullInputStream().readAllBytes().length);
		Recorder r2 = new Recorder();
		r2.write("xyz".getBytes(StandardCharsets.UTF_8), 1, 2);
		r2.flush();
		r2.close();
		System.out.println(r2.log);
	}

	static void readersAndWriters() throws IOException {
		System.out.println("-- readers and writers");
		StringReader reader = new StringReader("Hello, reader");
		char[] cbuf = new char[5];
		System.out.println(reader.read(cbuf) + " " + new String(cbuf) + " " + (char) reader.read() + " " + reader.ready());
		reader.mark(100);
		System.out.println(reader.skip(2) + " " + (char) reader.read());
		reader.reset();
		StringWriter sink = new StringWriter();
		System.out.println(reader.transferTo(sink) + " [" + sink + "] " + reader.read());
		reader.close();
		try {
			reader.read();
		} catch (IOException e) {
			System.out.println("IOException " + e.getMessage());
		}

		StringWriter writer = new StringWriter();
		writer.write('a');
		writer.write("bcdef", 1, 3);
		writer.write(new char[] { 'x', 'y', 'z' }, 1, 1);
		writer.append("!").append('?').append("0123", 1, 3).append(null);
		System.out.println(writer);

		StringWriter target = new StringWriter();
		BufferedWriter buffered = new BufferedWriter(target, 4);
		buffered.write("ab");
		System.out.println("[" + target + "]");
		buffered.write("cdef");
		System.out.println("[" + target + "]");
		buffered.newLine();
		buffered.write(new char[] { '1', '2', '3', '4', '5', '6' }, 0, 6);
		buffered.flush();
		System.out.println("[" + target.toString().replace("\n", "\\n") + "]");
		buffered.close();
		buffered.close();
		try {
			buffered.write("more");
		} catch (IOException e) {
			System.out.println("IOException " + e.getMessage());
		}

		Writer nothing = Writer.nullWriter();
		nothing.write("ignored");
		System.out.println(Reader.nullReader().read());
	}

	static void bufferedReader() throws IOException {
		System.out.println("-- buffered reader");
		BufferedReader reader = new BufferedReader(new StringReader("one\ntwo\r\nthree\rfour\n\nlast"));
		String line;
		int count = 0;
		while ((line = reader.readLine()) != null) {
			System.out.println(++count + " [" + line + "]");
		}
		System.out.println(reader.readLine() + " " + reader.read());

		// A \r\n split across buffer fills is still one line end
		BufferedReader small = new BufferedReader(new StringReader("ab\r\ncd\r\n\r\nef"), 3);
		System.out.println(small.lines().collect(Collectors.toList()));

		BufferedReader chars = new BufferedReader(new StringReader("x\r\ny"));
		StringBuilder seen = new StringBuilder();
		int c;
		chars.readLine();
		while ((c = chars.read()) != -1) {
			seen.append(c).append(' ');
		}
		System.out.println(seen);

		BufferedReader marked = new BufferedReader(new StringReader("abcdefgh"), 2);
		marked.mark(5);
		char[] three = new char[3];
		marked.read(three, 0, 3);
		marked.reset();
		System.out.println(new String(three) + " " + (char) marked.read() + " " + marked.skip(3) + " " + marked.readLine());
		try {
			marked.reset();
			marked.mark(1);
			marked.read();
			marked.read();
			marked.read();
			marked.reset();
		} catch (IOException e) {
			System.out.println("IOException " + e.getMessage());
		}
		marked.close();
		try {
			marked.readLine();
		} catch (IOException e) {
			System.out.println("IOException " + e.getMessage());
		}
		try {
			new BufferedReader(new StringReader(""), 0);
		} catch (IllegalArgumentException e) {
			System.out.println(e.getMessage());
		}
		System.out.println(new BufferedReader(new StringReader("p\nq\nr")).lines().map(String::toUpperCase)
				.collect(Collectors.joining(",")));
		UncheckedIOException unchecked = new UncheckedIOException("wrapped", new IOException("inner"));
		System.out.println(unchecked.getMessage() + " / " + unchecked.getCause().getMessage());
	}

	static void printWriter() {
		System.out.println("-- print writer");
		StringWriter text = new StringWriter();
		PrintWriter pw = new PrintWriter(text);
		pw.print(1);
		pw.print(' ');
		pw.print(2.5);
		pw.print(true);
		pw.print((Object) null);
		pw.println();
		pw.println("line");
		pw.printf("%5.2f|%-4s|%d%n", 3.14159, "ab", 42);
		pw.format("%s=%x", "hex", 255).println();
		pw.print(new char[] { 'c', 'h' });
		pw.println('!');
		pw.append("app").append('-').append("12345", 1, 3);
		System.out.print(text);
		System.out.println();
		System.out.println(pw.checkError());
		pw.close();
		pw.println("after close");
		System.out.println(pw.checkError());

		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		PrintWriter buffered = new PrintWriter(bytes);
		buffered.println("buffered");
		System.out.println("before flush: " + bytes.size());
		buffered.flush();
		System.out.println("after flush: " + bytes.size());
		PrintWriter auto = new PrintWriter(bytes, true);
		auto.print("no newline");
		System.out.println("print: " + bytes.size());
		auto.println();
		System.out.println("println: " + bytes.size());
		auto.printf("%d", 7);
		System.out.println("printf: " + bytes.size() + " " + bytes.toString(StandardCharsets.UTF_8).replace("\n", "\\n"));

		StringWriter partial = new StringWriter();
		PrintWriter failing = new PrintWriter(partial);
		try {
			failing.printf("ok %d then %q", 1);
		} catch (java.util.UnknownFormatConversionException e) {
			System.out.println(e.getMessage() + " after [" + partial + "]");
		}
	}

	static void printStream() throws IOException {
		System.out.println("-- print stream");
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(bytes);
		ps.print("π=");
		ps.printf("%.3f", Math.PI);
		ps.println();
		ps.write('#');
		ps.write("bytes".getBytes(StandardCharsets.UTF_8), 1, 3);
		ps.append("x").append('y');
		ps.println(new char[] { 'o', 'k' });
		System.out.println(hex(bytes.toByteArray()));
		System.out.print(bytes.toString(StandardCharsets.UTF_8));
		System.out.println(ps.checkError());
		ps.close();
		ps.print("closed");
		System.out.println(ps.checkError() + " " + bytes.size());

		Recorder recorder = new Recorder();
		PrintStream auto = new PrintStream(recorder, true);
		auto.print("ab");
		auto.println("c");
		System.out.println(recorder.log.toString().replace("\n", "\\n"));

		PrintStream original = System.out;
		ByteArrayOutputStream captured = new ByteArrayOutputStream();
		System.setOut(new PrintStream(captured, true, "UTF-8"));
		System.out.println("captured line");
		System.out.printf("%s%n", "captured printf");
		System.setOut(original);
		System.out.print("captured: " + captured.toString("UTF-8"));
		try {
			new PrintStream(new ByteArrayOutputStream(), false, "no-such-charset");
		} catch (UnsupportedEncodingException e) {
			System.out.println("UnsupportedEncodingException " + e.getMessage());
		}
		try {
			new PrintStream((OutputStream) null);
		} catch (NullPointerException e) {
			System.out.println("NullPointerException " + e.getMessage());
		}
	}

	static void utf8() throws IOException {
		System.out.println("-- utf-8");
		String text = "aé€😀";
		byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
		System.out.println(encoded.length + " " + hex(encoded));
		String decoded = new String(encoded, StandardCharsets.UTF_8);
		System.out.println(decoded.equals(text) + " " + decoded.length() + " " + decoded.codePoints().count());
		System.out.println(hex("x\uD800y\uDC00z".getBytes(StandardCharsets.UTF_8)));
		System.out.println(hex("\uD83D".getBytes()) + " " + hex(new String(new char[] { '\uDE00', '\uD83D' }).getBytes()));

		int[][] malformed = {
				{ 0x80 },
				{ 0x41, 0xC3 },
				{ 0xC3, 0x41 },
				{ 0xC0, 0x80 },
				{ 0xE2, 0x82 },
				{ 0xE2, 0x82, 0x41 },
				{ 0xE2, 0x41, 0x41 },
				{ 0xE0, 0x80, 0x80 },
				{ 0xED, 0xA0, 0x80 },
				{ 0xF0, 0x9F, 0x98 },
				{ 0xF0, 0x9F, 0x41, 0x41 },
				{ 0xF0, 0x80, 0x80, 0x80 },
				{ 0xF4, 0x90, 0x80, 0x80 },
				{ 0xF5, 0x80, 0x80, 0x80 },
				{ 0xFF, 0xFE, 0x41 },
				{ 0xF0, 0x9F, 0x98, 0x80, 0x80 },
				{ 0xF0, 0x80 },
				{ 0xF5, 0x80 },
				{ 0xF4, 0x90 },
				{ 0x41, 0xF0, 0x9F, 0x98 },
		};
		for (int[] values : malformed) {
			byte[] input = bytes(values);
			String viaString = new String(input, StandardCharsets.UTF_8);
			InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(input), StandardCharsets.UTF_8);
			StringBuilder viaReader = new StringBuilder();
			int c;
			while ((c = reader.read()) != -1) {
				viaReader.append((char) c);
			}
			System.out.println(hex(input) + " -> " + codes(viaString) + " " + codes(viaReader.toString()));
		}

		// A reader that hands out one byte per read still decodes whole characters
		InputStreamReader slow = new InputStreamReader(new SlowInput(encoded) {
			public int read(byte[] b, int off, int len) {
				int c = read();
				if (c < 0) {
					return -1;
				}
				b[off] = (byte) c;
				return 1;
			}

			public int available() {
				return 0;
			}
		}, StandardCharsets.UTF_8);
		char[] chars = new char[10];
		int n;
		StringBuilder pieces = new StringBuilder();
		while ((n = slow.read(chars, 0, chars.length)) != -1) {
			pieces.append(n).append(':').append(codes(new String(chars, 0, n))).append(' ');
		}
		System.out.println(pieces);

		ByteArrayOutputStream sink = new ByteArrayOutputStream();
		OutputStreamWriter writer = new OutputStreamWriter(sink, StandardCharsets.UTF_8);
		writer.write("a\uD83D");
		System.out.println("buffered: " + sink.size());
		writer.write("\uDE00b");
		writer.write('\uD800');
		writer.write('c');
		writer.write("\uDBFF");
		writer.flush();
		System.out.println(hex(sink.toByteArray()));
		writer.close();
		System.out.println(hex(sink.toByteArray()) + " " + writer.getEncoding());
		try {
			writer.write("x");
		} catch (IOException e) {
			System.out.println("IOException " + e.getMessage());
		}

		String big = "ü".repeat(5000);
		ByteArrayOutputStream bigSink = new ByteArrayOutputStream();
		Writer bigWriter = new OutputStreamWriter(bigSink, StandardCharsets.UTF_8);
		bigWriter.write(big);
		System.out.println("before close: " + bigSink.size());
		bigWriter.close();
		System.out.println("after close: " + bigSink.size());
		BufferedReader back = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bigSink.toByteArray()), "UTF-8"));
		System.out.println(back.readLine().equals(big));
	}

	static void charsets() throws IOException {
		System.out.println("-- charsets");
		System.out.println(Charset.forName("utf8") + " " + Charset.forName("latin1") + " " + Charset.forName("ASCII")
				+ " " + Charset.defaultCharset() + " " + Charset.isSupported("UTF-8") + " " + StandardCharsets.UTF_8.name());
		System.out.println(Charset.forName("UTF-8").equals(StandardCharsets.UTF_8) + " "
				+ Charset.forName("ISO-8859-1").aliases().contains("latin1"));
		try {
			Charset.forName("x-unknown");
		} catch (IllegalArgumentException e) {
			System.out.println(e.getClass().getName() + " " + e.getMessage());
		}
		try {
			Charset.forName("bad name");
		} catch (IllegalArgumentException e) {
			System.out.println(e.getClass().getName() + " " + e.getMessage());
		}
		try {
			"text".getBytes("x-unknown");
		} catch (UnsupportedEncodingException e) {
			System.out.println("UnsupportedEncodingException " + e.getMessage());
		}
		String accented = "café ñ € x";
		System.out.println(hex(accented.getBytes(StandardCharsets.ISO_8859_1)) + " "
				+ hex(accented.getBytes(StandardCharsets.US_ASCII)));
		byte[] high = bytes(0x63, 0xE9, 0xFF, 0x41);
		System.out.println(codes(new String(high, StandardCharsets.ISO_8859_1)) + " "
				+ codes(new String(high, StandardCharsets.US_ASCII)) + " " + codes(new String(high, "ISO-8859-1")));
		System.out.println(new InputStreamReader(new ByteArrayInputStream(high), StandardCharsets.ISO_8859_1).getEncoding()
				+ " " + new InputStreamReader(new ByteArrayInputStream(high)).getEncoding() + " "
				+ new OutputStreamWriter(new ByteArrayOutputStream(), "US-ASCII").getEncoding());
		System.out.println(new String(bytes(0x68, 0x69, 0x21), 0, 2) + " " + new String(new byte[0]).isEmpty());
	}

	static void resources() {
		System.out.println("-- try-with-resources");
		try (Resource a = new Resource("a", false); Resource b = new Resource("b", false)) {
			System.out.println("body");
		} catch (IOException e) {
			System.out.println("unexpected " + e);
		}
		try (Resource a = new Resource("first", true); Resource b = new Resource("second", true)) {
			System.out.println("body throws");
			throw new IOException("body failed");
		} catch (IOException e) {
			System.out.println(e.getMessage() + " suppressed " + e.getSuppressed().length);
			for (Throwable s : e.getSuppressed()) {
				System.out.println("  " + s.getMessage());
			}
		}
		try (Resource only = new Resource("only", true)) {
			System.out.println("body ok");
		} catch (IOException e) {
			System.out.println("caught " + e.getMessage());
		}
		try (BufferedReader reader = new BufferedReader(new StringReader("in try"))) {
			System.out.println(reader.readLine());
		} catch (IOException e) {
			System.out.println("unexpected " + e);
		}
	}
}
