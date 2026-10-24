package order;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BenchDriver {
	public static void main(String[] args) throws Exception {
		List<String> words = new ArrayList<String>(List.of("pear", "apple", "fig"));
		words.sort(Orders.natural());
		System.out.println(words);
		words.sort(Orders.reversed());
		System.out.println(words);
		Comparator<Integer> natural = Orders.natural();
		System.out.println(natural instanceof Serializable);
		System.out.println(Orders.<Integer>reversed() instanceof Serializable);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
			out.writeObject(natural);
		}
		try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
			@SuppressWarnings("unchecked")
			Comparator<Integer> copy = (Comparator<Integer>) in.readObject();
			System.out.println(copy.compare(1, 2) < 0);
		}
	}
}
