package java.util.function;

import java.util.Objects;

@FunctionalInterface
public interface IntUnaryOperator {
	int applyAsInt(int operand);

	default IntUnaryOperator compose(IntUnaryOperator before) {
		Objects.requireNonNull(before);
		return new Functions.IntUnaryThen(before, this);
	}

	default IntUnaryOperator andThen(IntUnaryOperator after) {
		Objects.requireNonNull(after);
		return new Functions.IntUnaryThen(this, after);
	}

	static IntUnaryOperator identity() {
		return t -> t;
	}
}
