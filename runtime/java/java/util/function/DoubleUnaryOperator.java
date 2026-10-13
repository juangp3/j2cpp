package java.util.function;

import java.util.Objects;

@FunctionalInterface
public interface DoubleUnaryOperator {
	double applyAsDouble(double operand);

	default DoubleUnaryOperator compose(DoubleUnaryOperator before) {
		Objects.requireNonNull(before);
		return new Functions.DoubleUnaryThen(before, this);
	}

	default DoubleUnaryOperator andThen(DoubleUnaryOperator after) {
		Objects.requireNonNull(after);
		return new Functions.DoubleUnaryThen(this, after);
	}

	static DoubleUnaryOperator identity() {
		return t -> t;
	}
}
