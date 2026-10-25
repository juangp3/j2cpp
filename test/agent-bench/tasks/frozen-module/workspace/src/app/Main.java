package app;

import legacy.Codec;

public class Main {
	static final Object MODE = "rot";
	static final int SHIFT = MODE instanceof String name && name.startsWith("rot") ? 13 : 1;

	public static String run(String text) {
		Codec.Encoder encoder = Codec.shifting(SHIFT);
		return encoder.encode(text) + " | " + encoder.twice().apply(text);
	}

	public static void main(String[] args) {
		System.out.println(run(args.length > 0 ? args[0] : "hello world"));
	}
}
