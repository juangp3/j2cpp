package java.util;

import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/** PrimitiveIterator and its int, long and double forms, written from the Java SE specification. */
public interface PrimitiveIterator<T, T_CONS> extends Iterator<T> {
	void forEachRemaining(T_CONS action);

	interface OfInt extends PrimitiveIterator<Integer, IntConsumer> {
		int nextInt();

		default Integer next() {
			return Integer.valueOf(nextInt());
		}

		default void forEachRemaining(IntConsumer action) {
			Objects.requireNonNull(action);
			while (hasNext()) {
				action.accept(nextInt());
			}
		}
	}

	interface OfLong extends PrimitiveIterator<Long, LongConsumer> {
		long nextLong();

		default Long next() {
			return Long.valueOf(nextLong());
		}

		default void forEachRemaining(LongConsumer action) {
			Objects.requireNonNull(action);
			while (hasNext()) {
				action.accept(nextLong());
			}
		}
	}

	interface OfDouble extends PrimitiveIterator<Double, DoubleConsumer> {
		double nextDouble();

		default Double next() {
			return Double.valueOf(nextDouble());
		}

		default void forEachRemaining(DoubleConsumer action) {
			Objects.requireNonNull(action);
			while (hasNext()) {
				action.accept(nextDouble());
			}
		}
	}
}
