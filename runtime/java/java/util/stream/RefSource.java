package java.util.stream;

import java.util.function.Consumer;

/** A stage of an object stream: tryAdvance pulls the next element through it */
abstract class RefSource<T> {
	/** Passes the next element to action and returns true, or returns false at the end */
	abstract boolean tryAdvance(Consumer<? super T> action);

	void forEachRemaining(Consumer<? super T> action) {
		while (tryAdvance(action)) {
		}
	}

	/** The exact number of elements, or -1 if unknown without traversing */
	long exactSize() {
		return -1;
	}
}
