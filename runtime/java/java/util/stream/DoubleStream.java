package java.util.stream;

import java.util.DoubleSummaryStatistics;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.PrimitiveIterator;
import java.util.function.BiConsumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Supplier;

/**
 * DoubleStream, written from the Java SE specification. See AbstractPipeline for
 * how the runtime evaluates it. mapMulti and spliterator are not provided.
 */
public interface DoubleStream extends BaseStream<Double, DoubleStream> {
	DoubleStream filter(DoublePredicate predicate);

	DoubleStream map(DoubleUnaryOperator mapper);

	<U> Stream<U> mapToObj(DoubleFunction<? extends U> mapper);

	IntStream mapToInt(DoubleToIntFunction mapper);

	LongStream mapToLong(DoubleToLongFunction mapper);

	DoubleStream flatMap(DoubleFunction<? extends DoubleStream> mapper);

	DoubleStream distinct();

	DoubleStream sorted();

	DoubleStream peek(DoubleConsumer action);

	DoubleStream limit(long maxSize);

	DoubleStream skip(long n);

	DoubleStream takeWhile(DoublePredicate predicate);

	DoubleStream dropWhile(DoublePredicate predicate);

	void forEach(DoubleConsumer action);

	void forEachOrdered(DoubleConsumer action);

	double[] toArray();

	double reduce(double identity, DoubleBinaryOperator op);

	OptionalDouble reduce(DoubleBinaryOperator op);

	<R> R collect(Supplier<R> supplier, ObjDoubleConsumer<R> accumulator, BiConsumer<R, R> combiner);

	double sum();

	OptionalDouble min();

	OptionalDouble max();

	long count();

	OptionalDouble average();

	DoubleSummaryStatistics summaryStatistics();

	boolean anyMatch(DoublePredicate predicate);

	boolean allMatch(DoublePredicate predicate);

	boolean noneMatch(DoublePredicate predicate);

	OptionalDouble findFirst();

	OptionalDouble findAny();

	Stream<Double> boxed();

	DoubleStream sequential();

	DoubleStream parallel();

	DoubleStream unordered();

	DoubleStream onClose(Runnable closeHandler);

	PrimitiveIterator.OfDouble iterator();

	static Builder builder() {
		return new DoublePipeline.DoubleBuilder();
	}

	static DoubleStream empty() {
		return DoublePipeline.ofArray(new double[0], 0, 0);
	}

	static DoubleStream of(double t) {
		return DoublePipeline.ofArray(new double[] { t }, 0, 1);
	}

	static DoubleStream of(double... values) {
		return DoublePipeline.ofArray(values, 0, values.length);
	}

	static DoubleStream iterate(double seed, DoubleUnaryOperator f) {
		Objects.requireNonNull(f);
		return DoublePipeline.iterate(seed, null, f);
	}

	static DoubleStream iterate(double seed, DoublePredicate hasNext, DoubleUnaryOperator next) {
		Objects.requireNonNull(hasNext);
		Objects.requireNonNull(next);
		return DoublePipeline.iterate(seed, hasNext, next);
	}

	static DoubleStream generate(DoubleSupplier s) {
		Objects.requireNonNull(s);
		return DoublePipeline.generate(s);
	}

	static DoubleStream concat(DoubleStream a, DoubleStream b) {
		Objects.requireNonNull(a);
		Objects.requireNonNull(b);
		return DoublePipeline.concat(a, b);
	}

	interface Builder extends DoubleConsumer {
		void accept(double t);

		default Builder add(double t) {
			accept(t);
			return this;
		}

		DoubleStream build();
	}
}
