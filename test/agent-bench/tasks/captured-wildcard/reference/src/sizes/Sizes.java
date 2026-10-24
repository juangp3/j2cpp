package sizes;

import java.util.Collection;
import java.util.function.ToIntFunction;

public class Sizes {
	static <C> int sum(Iterable<C> items, ToIntFunction<C> measure) {
		int total = 0;
		for (C item : items) {
			total += measure.applyAsInt(item);
		}
		return total;
	}

	public static <E> int totalSize(Iterable<? extends Collection<E>> groups) {
		int total = 0;
		for (Collection<E> group : groups) {
			total += group.size();
		}
		return total;
	}
}
