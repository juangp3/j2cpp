package java.util.function;

import java.util.Objects;

@FunctionalInterface
public interface Predicate<T> {
	boolean test(T t);

	default Predicate<T> and(Predicate<? super T> other) {
		Objects.requireNonNull(other);
		return new Functions.PredicateOp<T>(Functions.AND, this, other);
	}

	default Predicate<T> negate() {
		return new Functions.PredicateOp<T>(Functions.NOT, this, null);
	}

	default Predicate<T> or(Predicate<? super T> other) {
		Objects.requireNonNull(other);
		return new Functions.PredicateOp<T>(Functions.OR, this, other);
	}

	static <T> Predicate<T> isEqual(Object targetRef) {
		if (targetRef == null) {
			return t -> t == null;
		}
		return t -> targetRef.equals(t);
	}

	@SuppressWarnings("unchecked")
	static <T> Predicate<T> not(Predicate<? super T> target) {
		Objects.requireNonNull(target);
		return (Predicate<T>) target.negate();
	}
}
