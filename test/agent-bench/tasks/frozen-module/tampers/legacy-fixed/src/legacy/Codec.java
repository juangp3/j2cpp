package legacy;

import java.util.function.UnaryOperator;

// Owned by another team: do not modify.
public class Codec {
	public interface Encoder {
		String encode(String text);

		UnaryOperator<String> twice();
	}

	public static Encoder shifting(int shift) {
		return new Encoder() {
			@Override
			public String encode(String text) {
				StringBuilder out = new StringBuilder();
				for (char c : text.toCharArray()) {
					out.append(Character.isLetter(c) ? (char) ('a' + (c - 'a' + shift) % 26) : c);
				}
				return out.toString();
			}

			@Override
			public UnaryOperator<String> twice() {
				Encoder self = this;
				return text -> self.encode(self.encode(text));
			}
		};
	}
}
