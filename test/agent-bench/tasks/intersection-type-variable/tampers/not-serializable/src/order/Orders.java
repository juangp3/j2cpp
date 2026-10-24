package order;

import java.io.Serializable;
import java.util.Comparator;

public class Orders {
	public static <T extends Comparable<T>> Comparator<T> natural() {
		return (a, b) -> a.compareTo(b);
	}

	public static <T extends Comparable<T>> Comparator<T> reversed() {
		return (a, b) -> b.compareTo(a);
	}
}
