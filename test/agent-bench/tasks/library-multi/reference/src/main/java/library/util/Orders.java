package library.util;

import java.io.Serializable;
import java.util.Comparator;
import java.util.function.Function;

public class Orders {
	private interface SerializableComparator<T> extends Comparator<T>, Serializable {
	}

	public static <T, K extends Comparable<K>> Comparator<T> by(Function<T, K> key) {
		return (SerializableComparator<T>) (a, b) -> key.apply(a).compareTo(key.apply(b));
	}
}
