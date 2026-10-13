package java.util.function;

import java.util.Objects;

@FunctionalInterface
public interface IntConsumer {
	void accept(int value);

	default IntConsumer andThen(IntConsumer after) {
		Objects.requireNonNull(after);
		return new Functions.IntConsumerThen(this, after);
	}
}
