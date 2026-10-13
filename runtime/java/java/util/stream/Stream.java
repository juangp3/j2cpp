package java.util.stream;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

/**
 * Stream, written from the Java SE specification. See AbstractPipeline for how
 * the runtime evaluates it. mapMulti and spliterator are not provided.
 */
public interface Stream<T> extends BaseStream<T, Stream<T>> {
	Stream<T> filter(Predicate<? super T> predicate);

	<R> Stream<R> map(Function<? super T, ? extends R> mapper);

	IntStream mapToInt(ToIntFunction<? super T> mapper);

	LongStream mapToLong(ToLongFunction<? super T> mapper);

	DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper);

	<R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper);

	IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper);

	LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper);

	DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper);

	Stream<T> distinct();

	Stream<T> sorted();

	Stream<T> sorted(Comparator<? super T> comparator);

	Stream<T> peek(Consumer<? super T> action);

	Stream<T> limit(long maxSize);

	Stream<T> skip(long n);

	Stream<T> takeWhile(Predicate<? super T> predicate);

	Stream<T> dropWhile(Predicate<? super T> predicate);

	void forEach(Consumer<? super T> action);

	void forEachOrdered(Consumer<? super T> action);

	Object[] toArray();

	<A> A[] toArray(IntFunction<A[]> generator);

	T reduce(T identity, BinaryOperator<T> accumulator);

	Optional<T> reduce(BinaryOperator<T> accumulator);

	<U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner);

	<R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner);

	<R, A> R collect(Collector<? super T, A, R> collector);

	List<T> toList();

	Optional<T> min(Comparator<? super T> comparator);

	Optional<T> max(Comparator<? super T> comparator);

	long count();

	boolean anyMatch(Predicate<? super T> predicate);

	boolean allMatch(Predicate<? super T> predicate);

	boolean noneMatch(Predicate<? super T> predicate);

	Optional<T> findFirst();

	Optional<T> findAny();

	Iterator<T> iterator();

	Stream<T> sequential();

	Stream<T> parallel();

	Stream<T> unordered();

	Stream<T> onClose(Runnable closeHandler);

	static <T> Builder<T> builder() {
		return new ReferencePipeline.StreamBuilder<T>();
	}

	static <T> Stream<T> empty() {
		return ReferencePipeline.<T>ofArray(new Object[0], 0, 0);
	}

	static <T> Stream<T> of(T t) {
		return ReferencePipeline.<T>ofArray(new Object[] { t }, 0, 1);
	}

	static <T> Stream<T> ofNullable(T t) {
		return t == null ? Stream.<T>empty() : Stream.<T>of(t);
	}

	@SafeVarargs
	static <T> Stream<T> of(T... values) {
		return ReferencePipeline.<T>ofArray(values, 0, values.length);
	}

	static <T> Stream<T> iterate(T seed, UnaryOperator<T> f) {
		Objects.requireNonNull(f);
		return ReferencePipeline.<T>iterate(seed, null, f);
	}

	static <T> Stream<T> iterate(T seed, Predicate<? super T> hasNext, UnaryOperator<T> next) {
		Objects.requireNonNull(hasNext);
		Objects.requireNonNull(next);
		return ReferencePipeline.<T>iterate(seed, hasNext, next);
	}

	static <T> Stream<T> generate(Supplier<? extends T> s) {
		Objects.requireNonNull(s);
		return ReferencePipeline.<T>generate(s);
	}

	static <T> Stream<T> concat(Stream<? extends T> a, Stream<? extends T> b) {
		Objects.requireNonNull(a);
		Objects.requireNonNull(b);
		return ReferencePipeline.<T>concat(a, b);
	}

	interface Builder<T> extends Consumer<T> {
		void accept(T t);

		default Builder<T> add(T t) {
			accept(t);
			return this;
		}

		Stream<T> build();
	}
}
