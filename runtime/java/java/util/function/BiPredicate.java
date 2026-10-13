package java.util.function;

import java.util.Objects;

@FunctionalInterface
public interface BiPredicate<T, U> {
	boolean test(T t, U u);

	default BiPredicate<T, U> and(BiPredicate<? super T, ? super U> other) {
		Objects.requireNonNull(other);
		return new Functions.BiPredicateOp<T, U>(Functions.AND, this, other);
	}

	default BiPredicate<T, U> negate() {
		return new Functions.BiPredicateOp<T, U>(Functions.NOT, this, null);
	}

	default BiPredicate<T, U> or(BiPredicate<? super T, ? super U> other) {
		Objects.requireNonNull(other);
		return new Functions.BiPredicateOp<T, U>(Functions.OR, this, other);
	}
}
