import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// G8: built with make J2C_GC=boehm, a loop that allocates about 1 GB keeps
// its resident memory under max-rss-mb.txt, and what static fields,
// collections, locals and exceptions in flight still reach survives
public class Allocation {
	static final List<String> kept = new ArrayList<>();
	static final Map<Integer, int[]> tables = new HashMap<>();
	static Node chain;

	static class Node {
		final int value;
		final Node next;
		final int[] payload;

		Node(int value, Node next, int size) {
			this.value = value;
			this.next = next;
			payload = new int[size];
			for (int i = 0; i < size; i++) {
				payload[i] = value + i;
			}
		}

		int sum() {
			int sum = 0;
			for (int x : payload) {
				sum += x;
			}
			return sum;
		}
	}

	// Garbage: an array, strings and a short list
	static int churn(int i) {
		int[] data = new int[1024];
		for (int j = 0; j < data.length; j++) {
			data[j] = i + j;
		}
		String s = new StringBuilder().append("item ").append(i).toString() + "/" + data[1023];
		List<String> parts = new ArrayList<>();
		for (int j = 0; j < 8; j++) {
			parts.add(s + j);
		}
		return parts.get(7).length() + data[512];
	}

	static void fail(int i) {
		throw new IllegalStateException("failed " + i);
	}

	// The finally block allocates while the exception unwinds, when only
	// the exception object refers to the IllegalStateException
	static String risky(int i) {
		try {
			if (i % 5 == 0) {
				fail(i);
			}
			return "ok " + i;
		} finally {
			churn(i);
		}
	}

	public static void main(String[] args) {
		String local = "local " + args.length;
		Node[] window = new Node[1000];
		long total = 0;
		int failures = 0;
		for (int i = 0; i < 40000; i++) {
			total += churn(i);
			window[i % window.length] = new Node(i, null, 64);
			if (i % 200 == 0) {
				chain = new Node(i, chain, 16);
			}
			if (i % 400 == 0) {
				kept.add("kept " + i);
			}
			if (i % 2000 == 0) {
				tables.put(i, new int[] { i, i * 2 });
			}
			try {
				total += risky(i).length();
			} catch (IllegalStateException e) {
				if (!e.getMessage().equals("failed " + i)) {
					System.out.println("wrong message at " + i + ": " + e.getMessage());
				}
				failures++;
			}
		}

		System.out.println("total " + total);
		System.out.println("failures " + failures);
		System.out.println(local);

		int links = 0;
		long chainSum = 0;
		for (Node n = chain; n != null; n = n.next) {
			if (n.sum() != 16 * n.value + 120) {
				System.out.println("corrupt chain node " + n.value);
			}
			chainSum += n.value;
			links++;
		}
		System.out.println("chain " + links + " " + chainSum);

		long windowSum = 0;
		for (Node n : window) {
			if (n.sum() != 64 * n.value + 2016) {
				System.out.println("corrupt window node " + n.value);
			}
			windowSum += n.value;
		}
		System.out.println("window " + windowSum);

		System.out.println("kept " + kept.size() + " " + kept.get(0) + " " + kept.get(kept.size() - 1));
		for (int i = 0; i < kept.size(); i++) {
			if (!kept.get(i).equals("kept " + i * 400)) {
				System.out.println("corrupt kept " + i);
			}
		}

		long tableSum = 0;
		for (Map.Entry<Integer, int[]> e : tables.entrySet()) {
			int[] t = e.getValue();
			if (t[0] != e.getKey() || t[1] != 2 * e.getKey()) {
				System.out.println("corrupt table " + e.getKey());
			}
			tableSum += t[0] + t[1];
		}
		System.out.println("tables " + tables.size() + " " + tableSum);
	}
}
