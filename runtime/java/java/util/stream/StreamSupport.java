package java.util.stream;

import java.util.Objects;
import java.util.Spliterator;

/**
 * StreamSupport, written from the Java SE specification. Only the reference
 * form taking a Spliterator is provided; streams run sequentially even when
 * parallel is true.
 */
public final class StreamSupport {
	private StreamSupport() {
	}

	public static <T> Stream<T> stream(Spliterator<T> spliterator, boolean parallel) {
		Objects.requireNonNull(spliterator);
		return ReferencePipeline.ofSpliterator(spliterator, parallel);
	}
}
