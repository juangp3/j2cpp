package java.util.stream;

import java.util.function.DoubleConsumer;

/** A stage of a double stream: tryAdvance pulls the next value through it */
abstract class DoubleSource {
	/** Passes the next value to action and returns true, or returns false at the end */
	abstract boolean tryAdvance(DoubleConsumer action);

	void forEachRemaining(DoubleConsumer action) {
		while (tryAdvance(action)) {
		}
	}

	/** The exact number of elements, or -1 if unknown without traversing */
	long exactSize() {
		return -1;
	}
}
