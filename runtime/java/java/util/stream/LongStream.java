package java.util.stream;

import java.util.LongSummaryStatistics;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.PrimitiveIterator;
import java.util.function.BiConsumer;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.LongToDoubleFunction;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;

/**
 * LongStream, written from the Java SE specification. See AbstractPipeline for
 * how the runtime evaluates it. mapMulti and spliterator are not provided.
 */
public interface LongStream extends BaseStream<Long, LongStream> {
	LongStream filter(LongPredicate predicate);

	LongStream map(LongUnaryOperator mapper);

	<U> Stream<U> mapToObj(LongFunction<? extends U> mapper);

	IntStream mapToInt(LongToIntFunction mapper);

	DoubleStream mapToDouble(LongToDoubleFunction mapper);

	LongStream flatMap(LongFunction<? extends LongStream> mapper);

	LongStream distinct();

	LongStream sorted();

	LongStream peek(LongConsumer action);

	LongStream limit(long maxSize);

	LongStream skip(long n);

	LongStream takeWhile(LongPredicate predicate);

	LongStream dropWhile(LongPredicate predicate);

	void forEach(LongConsumer action);

	void forEachOrdered(LongConsumer action);

	long[] toArray();

	long reduce(long identity, LongBinaryOperator op);

	OptionalLong reduce(LongBinaryOperator op);

	<R> R collect(Supplier<R> supplier, ObjLongConsumer<R> accumulator, BiConsumer<R, R> combiner);

	long sum();

	OptionalLong min();

	OptionalLong max();

	long count();

	OptionalDouble average();

	LongSummaryStatistics summaryStatistics();

	boolean anyMatch(LongPredicate predicate);

	boolean allMatch(LongPredicate predicate);

	boolean noneMatch(LongPredicate predicate);

	OptionalLong findFirst();

	OptionalLong findAny();

	DoubleStream asDoubleStream();

	Stream<Long> boxed();

	LongStream sequential();

	LongStream parallel();

	LongStream unordered();

	LongStream onClose(Runnable closeHandler);

	PrimitiveIterator.OfLong iterator();

	static Builder builder() {
		return new LongPipeline.LongBuilder();
	}

	static LongStream empty() {
		return LongPipeline.ofArray(new long[0], 0, 0);
	}

	static LongStream of(long t) {
		return LongPipeline.ofArray(new long[] { t }, 0, 1);
	}

	static LongStream of(long... values) {
		return LongPipeline.ofArray(values, 0, values.length);
	}

	static LongStream iterate(long seed, LongUnaryOperator f) {
		Objects.requireNonNull(f);
		return LongPipeline.iterate(seed, null, f);
	}

	static LongStream iterate(long seed, LongPredicate hasNext, LongUnaryOperator next) {
		Objects.requireNonNull(hasNext);
		Objects.requireNonNull(next);
		return LongPipeline.iterate(seed, hasNext, next);
	}

	static LongStream generate(LongSupplier s) {
		Objects.requireNonNull(s);
		return LongPipeline.generate(s);
	}

	static LongStream range(long startInclusive, long endExclusive) {
		if (startInclusive >= endExclusive) {
			return empty();
		}
		return LongPipeline.range(startInclusive, endExclusive - 1);
	}

	static LongStream rangeClosed(long startInclusive, long endInclusive) {
		if (startInclusive > endInclusive) {
			return empty();
		}
		return LongPipeline.range(startInclusive, endInclusive);
	}

	static LongStream concat(LongStream a, LongStream b) {
		Objects.requireNonNull(a);
		Objects.requireNonNull(b);
		return LongPipeline.concat(a, b);
	}

	interface Builder extends LongConsumer {
		void accept(long t);

		default Builder add(long t) {
			accept(t);
			return this;
		}

		LongStream build();
	}
}
