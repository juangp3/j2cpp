package java.util.function;

import java.util.Objects;

@FunctionalInterface
public interface LongUnaryOperator {
	long applyAsLong(long operand);

	default LongUnaryOperator compose(LongUnaryOperator before) {
		Objects.requireNonNull(before);
		return new Functions.LongUnaryThen(before, this);
	}

	default LongUnaryOperator andThen(LongUnaryOperator after) {
		Objects.requireNonNull(after);
		return new Functions.LongUnaryThen(this, after);
	}

	static LongUnaryOperator identity() {
		return t -> t;
	}
}
