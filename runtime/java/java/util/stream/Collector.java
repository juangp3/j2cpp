package java.util.stream;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;

/** Collector, written from the Java SE specification. */
public interface Collector<T, A, R> {
	Supplier<A> supplier();

	BiConsumer<A, T> accumulator();

	BinaryOperator<A> combiner();

	Function<A, R> finisher();

	Set<Characteristics> characteristics();

	static <T, R> Collector<T, R, R> of(Supplier<R> supplier, BiConsumer<R, T> accumulator, BinaryOperator<R> combiner,
			Characteristics... characteristics) {
		Objects.requireNonNull(supplier);
		Objects.requireNonNull(accumulator);
		Objects.requireNonNull(combiner);
		Objects.requireNonNull(characteristics);
		Set<Characteristics> set = new HashSet<Characteristics>();
		set.add(Characteristics.IDENTITY_FINISH);
		for (Characteristics c : characteristics) {
			set.add(c);
		}
		return new Collectors.CollectorImpl<T, R, R>(supplier, accumulator, combiner, null,
				Collections.unmodifiableSet(set));
	}

	static <T, A, R> Collector<T, A, R> of(Supplier<A> supplier, BiConsumer<A, T> accumulator,
			BinaryOperator<A> combiner, Function<A, R> finisher, Characteristics... characteristics) {
		Objects.requireNonNull(supplier);
		Objects.requireNonNull(accumulator);
		Objects.requireNonNull(combiner);
		Objects.requireNonNull(finisher);
		Objects.requireNonNull(characteristics);
		Set<Characteristics> set = new HashSet<Characteristics>();
		for (Characteristics c : characteristics) {
			set.add(c);
		}
		return new Collectors.CollectorImpl<T, A, R>(supplier, accumulator, combiner, finisher,
				Collections.unmodifiableSet(set));
	}

	enum Characteristics {
		CONCURRENT, UNORDERED, IDENTITY_FINISH
	}
}
