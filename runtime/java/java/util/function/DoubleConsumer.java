package java.util.function;

import java.util.Objects;

@FunctionalInterface
public interface DoubleConsumer {
	void accept(double value);

	default DoubleConsumer andThen(DoubleConsumer after) {
		Objects.requireNonNull(after);
		return new Functions.DoubleConsumerThen(this, after);
	}
}
