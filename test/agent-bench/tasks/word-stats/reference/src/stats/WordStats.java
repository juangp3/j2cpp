package stats;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public class WordStats {
	interface Sink {
		void accept(String word);

		int total();
	}

	static Sink counting(Map<String, Integer> counts) {
		return new Sink() {
			int total;

			@Override
			public void accept(String word) {
				total++;
				counts.merge(word, 1, Integer::sum);
			}

			@Override
			public int total() {
				return total;
			}

			Consumer<String> asConsumer() {
				var self = this;
				return (p0) -> self.accept(p0);
			}
		};
	}

	public static void main(String[] args) throws IOException {
		Map<String, Integer> counts = new TreeMap<String, Integer>();
		Sink sink = counting(counts);
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
		List<String> lines = new ArrayList<String>();
		for (String line = in.readLine(); line != null; line = in.readLine()) {
			lines.add(line);
			for (String word : line.toLowerCase().split("[^\\p{L}']+")) {
				if (!word.isEmpty()) {
					sink.accept(word);
				}
			}
		}
		System.out.println(lines.size() + " lines, " + sink.total() + " words, " + counts.size() + " distinct");
		counts.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
				.limit(5)
				.forEach(entry -> System.out.println(String.format("%-8s %3d %s", entry.getKey(), entry.getValue(),
						String.format("%.1f%%", 100.0 * entry.getValue() / sink.total()))));
	}
}
