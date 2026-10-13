package java.util.function;

/**
 * The objects the default methods of java.util.function return (andThen,
 * compose, and, or, negate), written from the Java SE specification. They are
 * named classes rather than lambdas, which would capture the interface's this.
 */
final class Functions {
	private Functions() {
	}

	static final int AND = 0;
	static final int OR = 1;
	static final int NOT = 2;

	static final class ConsumerThen<T> implements Consumer<T> {
		private final Consumer<? super T> first;
		private final Consumer<? super T> second;

		ConsumerThen(Consumer<? super T> first, Consumer<? super T> second) {
			this.first = first;
			this.second = second;
		}

		public void accept(T t) {
			first.accept(t);
			second.accept(t);
		}
	}

	static final class BiConsumerThen<T, U> implements BiConsumer<T, U> {
		private final BiConsumer<? super T, ? super U> first;
		private final BiConsumer<? super T, ? super U> second;

		BiConsumerThen(BiConsumer<? super T, ? super U> first, BiConsumer<? super T, ? super U> second) {
			this.first = first;
			this.second = second;
		}

		public void accept(T t, U u) {
			first.accept(t, u);
			second.accept(t, u);
		}
	}

	static final class IntConsumerThen implements IntConsumer {
		private final IntConsumer first;
		private final IntConsumer second;

		IntConsumerThen(IntConsumer first, IntConsumer second) {
			this.first = first;
			this.second = second;
		}

		public void accept(int value) {
			first.accept(value);
			second.accept(value);
		}
	}

	static final class LongConsumerThen implements LongConsumer {
		private final LongConsumer first;
		private final LongConsumer second;

		LongConsumerThen(LongConsumer first, LongConsumer second) {
			this.first = first;
			this.second = second;
		}

		public void accept(long value) {
			first.accept(value);
			second.accept(value);
		}
	}

	static final class DoubleConsumerThen implements DoubleConsumer {
		private final DoubleConsumer first;
		private final DoubleConsumer second;

		DoubleConsumerThen(DoubleConsumer first, DoubleConsumer second) {
			this.first = first;
			this.second = second;
		}

		public void accept(double value) {
			first.accept(value);
			second.accept(value);
		}
	}

	/** first, then second: andThen(second) and second.compose(first) */
	static final class FunctionThen<T, R, V> implements Function<T, V> {
		private final Function<? super T, ? extends R> first;
		private final Function<? super R, ? extends V> second;

		FunctionThen(Function<? super T, ? extends R> first, Function<? super R, ? extends V> second) {
			this.first = first;
			this.second = second;
		}

		public V apply(T t) {
			return second.apply(first.apply(t));
		}
	}

	static final class BiFunctionThen<T, U, R, V> implements BiFunction<T, U, V> {
		private final BiFunction<? super T, ? super U, ? extends R> first;
		private final Function<? super R, ? extends V> second;

		BiFunctionThen(BiFunction<? super T, ? super U, ? extends R> first, Function<? super R, ? extends V> second) {
			this.first = first;
			this.second = second;
		}

		public V apply(T t, U u) {
			return second.apply(first.apply(t, u));
		}
	}

	static final class IntUnaryThen implements IntUnaryOperator {
		private final IntUnaryOperator first;
		private final IntUnaryOperator second;

		IntUnaryThen(IntUnaryOperator first, IntUnaryOperator second) {
			this.first = first;
			this.second = second;
		}

		public int applyAsInt(int operand) {
			return second.applyAsInt(first.applyAsInt(operand));
		}
	}

	static final class LongUnaryThen implements LongUnaryOperator {
		private final LongUnaryOperator first;
		private final LongUnaryOperator second;

		LongUnaryThen(LongUnaryOperator first, LongUnaryOperator second) {
			this.first = first;
			this.second = second;
		}

		public long applyAsLong(long operand) {
			return second.applyAsLong(first.applyAsLong(operand));
		}
	}

	static final class DoubleUnaryThen implements DoubleUnaryOperator {
		private final DoubleUnaryOperator first;
		private final DoubleUnaryOperator second;

		DoubleUnaryThen(DoubleUnaryOperator first, DoubleUnaryOperator second) {
			this.first = first;
			this.second = second;
		}

		public double applyAsDouble(double operand) {
			return second.applyAsDouble(first.applyAsDouble(operand));
		}
	}

	/** and, or (short-circuiting, like && and ||) or not of the first */
	static final class PredicateOp<T> implements Predicate<T> {
		private final int op;
		private final Predicate<? super T> first;
		private final Predicate<? super T> second;

		PredicateOp(int op, Predicate<? super T> first, Predicate<? super T> second) {
			this.op = op;
			this.first = first;
			this.second = second;
		}

		public boolean test(T t) {
			if (op == AND) {
				return first.test(t) && second.test(t);
			}
			if (op == OR) {
				return first.test(t) || second.test(t);
			}
			return !first.test(t);
		}
	}

	static final class BiPredicateOp<T, U> implements BiPredicate<T, U> {
		private final int op;
		private final BiPredicate<? super T, ? super U> first;
		private final BiPredicate<? super T, ? super U> second;

		BiPredicateOp(int op, BiPredicate<? super T, ? super U> first, BiPredicate<? super T, ? super U> second) {
			this.op = op;
			this.first = first;
			this.second = second;
		}

		public boolean test(T t, U u) {
			if (op == AND) {
				return first.test(t, u) && second.test(t, u);
			}
			if (op == OR) {
				return first.test(t, u) || second.test(t, u);
			}
			return !first.test(t, u);
		}
	}

	static final class IntPredicateOp implements IntPredicate {
		private final int op;
		private final IntPredicate first;
		private final IntPredicate second;

		IntPredicateOp(int op, IntPredicate first, IntPredicate second) {
			this.op = op;
			this.first = first;
			this.second = second;
		}

		public boolean test(int value) {
			if (op == AND) {
				return first.test(value) && second.test(value);
			}
			if (op == OR) {
				return first.test(value) || second.test(value);
			}
			return !first.test(value);
		}
	}

	static final class LongPredicateOp implements LongPredicate {
		private final int op;
		private final LongPredicate first;
		private final LongPredicate second;

		LongPredicateOp(int op, LongPredicate first, LongPredicate second) {
			this.op = op;
			this.first = first;
			this.second = second;
		}

		public boolean test(long value) {
			if (op == AND) {
				return first.test(value) && second.test(value);
			}
			if (op == OR) {
				return first.test(value) || second.test(value);
			}
			return !first.test(value);
		}
	}

	static final class DoublePredicateOp implements DoublePredicate {
		private final int op;
		private final DoublePredicate first;
		private final DoublePredicate second;

		DoublePredicateOp(int op, DoublePredicate first, DoublePredicate second) {
			this.op = op;
			this.first = first;
			this.second = second;
		}

		public boolean test(double value) {
			if (op == AND) {
				return first.test(value) && second.test(value);
			}
			if (op == OR) {
				return first.test(value) || second.test(value);
			}
			return !first.test(value);
		}
	}
}
