package java.util.function;

import java.util.Objects;

@FunctionalInterface
public interface DoublePredicate {
	boolean test(double value);

	default DoublePredicate and(DoublePredicate other) {
		Objects.requireNonNull(other);
		return new Functions.DoublePredicateOp(Functions.AND, this, other);
	}

	default DoublePredicate negate() {
		return new Functions.DoublePredicateOp(Functions.NOT, this, null);
	}

	default DoublePredicate or(DoublePredicate other) {
		Objects.requireNonNull(other);
		return new Functions.DoublePredicateOp(Functions.OR, this, other);
	}
}
