package java.lang;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;

public interface Iterable<T> {
	Iterator<T> iterator();

	default void forEach(Consumer<? super T> action) {
		java.util.Objects.requireNonNull(action);
		for (T t : this) {
			action.accept(t);
		}
	}

	/**
	 * Sized and late-binding for a Collection, as Collection.spliterator() is.
	 * Collection does not override this default: J2C would drop an interface
	 * default that overrides another.
	 */
	@SuppressWarnings("unchecked")
	default Spliterator<T> spliterator() {
		if (this instanceof java.util.Collection) {
			return Spliterators.spliterator((java.util.Collection<T>) this, 0);
		}
		return Spliterators.spliteratorUnknownSize(iterator(), 0);
	}
}
