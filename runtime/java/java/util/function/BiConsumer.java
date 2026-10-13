package java.util.function;

import java.util.Objects;

@FunctionalInterface
public interface BiConsumer<T, U> {
	void accept(T t, U u);

	default BiConsumer<T, U> andThen(BiConsumer<? super T, ? super U> after) {
		Objects.requireNonNull(after);
		return new Functions.BiConsumerThen<T, U>(this, after);
	}
}
