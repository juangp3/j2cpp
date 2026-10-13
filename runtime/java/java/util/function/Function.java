package java.util.function;

import java.util.Objects;

@FunctionalInterface
public interface Function<T, R> {
	R apply(T t);

	default <V> Function<V, R> compose(Function<? super V, ? extends T> before) {
		Objects.requireNonNull(before);
		return new Functions.FunctionThen<V, T, R>(before, this);
	}

	default <V> Function<T, V> andThen(Function<? super R, ? extends V> after) {
		Objects.requireNonNull(after);
		return new Functions.FunctionThen<T, R, V>(this, after);
	}

	static <T> Function<T, T> identity() {
		return t -> t;
	}
}
