package library.util;

import java.io.Serializable;
import java.util.Comparator;
import java.util.function.Function;

public class Orders {
	public static <T, K extends Comparable<K>> Comparator<T> by(Function<T, K> key) {
		return (Comparator<T> & Serializable) (a, b) -> key.apply(a).compareTo(key.apply(b));
	}
}
