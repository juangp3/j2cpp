package java.util.function;

import java.util.Objects;

@FunctionalInterface
public interface LongPredicate {
	boolean test(long value);

	default LongPredicate and(LongPredicate other) {
		Objects.requireNonNull(other);
		return new Functions.LongPredicateOp(Functions.AND, this, other);
	}

	default LongPredicate negate() {
		return new Functions.LongPredicateOp(Functions.NOT, this, null);
	}

	default LongPredicate or(LongPredicate other) {
		Objects.requireNonNull(other);
		return new Functions.LongPredicateOp(Functions.OR, this, other);
	}
}
