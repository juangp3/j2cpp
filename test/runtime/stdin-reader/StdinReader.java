import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

// System.in as bytes, then a BufferedReader line loop to the end (R6)
public class StdinReader {
	public static void main(String[] args) throws IOException {
		StringBuilder raw = new StringBuilder();
		for (int i = 0; i < 3; i++) {
			raw.append((char) System.in.read());
		}
		System.out.println("raw " + raw + " available " + (System.in.available() > 0));

		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		String line;
		int count = 0;
		int chars = 0;
		while ((line = reader.readLine()) != null) {
			count++;
			chars += line.length();
			StringBuilder codes = new StringBuilder();
			for (int i = 0; i < line.length(); i++) {
				char c = line.charAt(i);
				if (c < 0x20 || c > 0x7E) {
					codes.append(String.format("\\u%04x", (int) c));
				} else {
					codes.append(c);
				}
			}
			System.out.println(count + ": " + codes);
		}
		System.out.println(count + " lines, " + chars + " chars");
		System.out.println(reader.readLine() + " " + reader.read() + " " + System.in.read());
		reader.close();
		try {
			System.in.read();
		} catch (IOException e) {
			System.out.println("IOException " + e.getMessage());
		}
	}
}
