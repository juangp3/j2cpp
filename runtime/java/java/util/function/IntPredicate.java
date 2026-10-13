package java.util.function;

import java.util.Objects;

@FunctionalInterface
public interface IntPredicate {
	boolean test(int value);

	default IntPredicate and(IntPredicate other) {
		Objects.requireNonNull(other);
		return new Functions.IntPredicateOp(Functions.AND, this, other);
	}

	default IntPredicate negate() {
		return new Functions.IntPredicateOp(Functions.NOT, this, null);
	}

	default IntPredicate or(IntPredicate other) {
		Objects.requireNonNull(other);
		return new Functions.IntPredicateOp(Functions.OR, this, other);
	}
}
