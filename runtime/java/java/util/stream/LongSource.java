package java.util.stream;

import java.util.function.LongConsumer;

/** A stage of a long stream: tryAdvance pulls the next value through it */
abstract class LongSource {
	/** Passes the next value to action and returns true, or returns false at the end */
	abstract boolean tryAdvance(LongConsumer action);

	void forEachRemaining(LongConsumer action) {
		while (tryAdvance(action)) {
		}
	}

	/** The exact number of elements, or -1 if unknown without traversing */
	long exactSize() {
		return -1;
	}
}
