package java.lang;

import java.util.Iterator;
import java.util.function.Consumer;

public interface Iterable<T> {
	Iterator<T> iterator();

	default void forEach(Consumer<? super T> action) {
		java.util.Objects.requireNonNull(action);
		for (T t : this) {
			action.accept(t);
		}
	}
}
