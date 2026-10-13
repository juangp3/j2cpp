package java.util.stream;

import java.util.IntSummaryStatistics;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.PrimitiveIterator;
import java.util.function.BiConsumer;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

/**
 * IntStream, written from the Java SE specification. See AbstractPipeline for
 * how the runtime evaluates it. mapMulti and spliterator are not provided.
 */
public interface IntStream extends BaseStream<Integer, IntStream> {
	IntStream filter(IntPredicate predicate);

	IntStream map(IntUnaryOperator mapper);

	<U> Stream<U> mapToObj(IntFunction<? extends U> mapper);

	LongStream mapToLong(IntToLongFunction mapper);

	DoubleStream mapToDouble(IntToDoubleFunction mapper);

	IntStream flatMap(IntFunction<? extends IntStream> mapper);

	IntStream distinct();

	IntStream sorted();

	IntStream peek(IntConsumer action);

	IntStream limit(long maxSize);

	IntStream skip(long n);

	IntStream takeWhile(IntPredicate predicate);

	IntStream dropWhile(IntPredicate predicate);

	void forEach(IntConsumer action);

	void forEachOrdered(IntConsumer action);

	int[] toArray();

	int reduce(int identity, IntBinaryOperator op);

	OptionalInt reduce(IntBinaryOperator op);

	<R> R collect(Supplier<R> supplier, ObjIntConsumer<R> accumulator, BiConsumer<R, R> combiner);

	int sum();

	OptionalInt min();

	OptionalInt max();

	long count();

	OptionalDouble average();

	IntSummaryStatistics summaryStatistics();

	boolean anyMatch(IntPredicate predicate);

	boolean allMatch(IntPredicate predicate);

	boolean noneMatch(IntPredicate predicate);

	OptionalInt findFirst();

	OptionalInt findAny();

	LongStream asLongStream();

	DoubleStream asDoubleStream();

	Stream<Integer> boxed();

	IntStream sequential();

	IntStream parallel();

	IntStream unordered();

	IntStream onClose(Runnable closeHandler);

	PrimitiveIterator.OfInt iterator();

	static Builder builder() {
		return new IntPipeline.IntBuilder();
	}

	static IntStream empty() {
		return IntPipeline.ofArray(new int[0], 0, 0);
	}

	static IntStream of(int t) {
		return IntPipeline.ofArray(new int[] { t }, 0, 1);
	}

	static IntStream of(int... values) {
		return IntPipeline.ofArray(values, 0, values.length);
	}

	static IntStream iterate(int seed, IntUnaryOperator f) {
		Objects.requireNonNull(f);
		return IntPipeline.iterate(seed, null, f);
	}

	static IntStream iterate(int seed, IntPredicate hasNext, IntUnaryOperator next) {
		Objects.requireNonNull(hasNext);
		Objects.requireNonNull(next);
		return IntPipeline.iterate(seed, hasNext, next);
	}

	static IntStream generate(IntSupplier s) {
		Objects.requireNonNull(s);
		return IntPipeline.generate(s);
	}

	static IntStream range(int startInclusive, int endExclusive) {
		if (startInclusive >= endExclusive) {
			return empty();
		}
		return IntPipeline.range(startInclusive, (long) endExclusive - startInclusive);
	}

	static IntStream rangeClosed(int startInclusive, int endInclusive) {
		if (startInclusive > endInclusive) {
			return empty();
		}
		return IntPipeline.range(startInclusive, (long) endInclusive - startInclusive + 1);
	}

	static IntStream concat(IntStream a, IntStream b) {
		Objects.requireNonNull(a);
		Objects.requireNonNull(b);
		return IntPipeline.concat(a, b);
	}

	interface Builder extends IntConsumer {
		void accept(int t);

		default Builder add(int t) {
			accept(t);
			return this;
		}

		IntStream build();
	}
}
