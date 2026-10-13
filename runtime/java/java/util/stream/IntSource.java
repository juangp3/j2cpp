package java.util.stream;

import java.util.function.IntConsumer;

/** A stage of an int stream: tryAdvance pulls the next value through it */
abstract class IntSource {
	/** Passes the next value to action and returns true, or returns false at the end */
	abstract boolean tryAdvance(IntConsumer action);

	void forEachRemaining(IntConsumer action) {
		while (tryAdvance(action)) {
		}
	}

	/** The exact number of elements, or -1 if unknown without traversing */
	long exactSize() {
		return -1;
	}
}
