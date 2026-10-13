package java.util;

import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Comparator, written from the Java SE specification. The combinators are
 * named classes in Comparators rather than lambdas, which would capture the
 * interface's this.
 */
public interface Comparator<T> {
	int compare(T o1, T o2);

	boolean equals(Object obj);

	default Comparator<T> reversed() {
		return new Comparators.Reversed<T>(this);
	}

	default Comparator<T> thenComparing(Comparator<? super T> other) {
		Objects.requireNonNull(other);
		return new Comparators.Then<T>(this, other);
	}

	default <U> Comparator<T> thenComparing(Function<? super T, ? extends U> keyExtractor,
			Comparator<? super U> keyComparator) {
		return thenComparing(comparing(keyExtractor, keyComparator));
	}

	default <U extends Comparable<? super U>> Comparator<T> thenComparing(
			Function<? super T, ? extends U> keyExtractor) {
		return thenComparing(comparing(keyExtractor));
	}

	default Comparator<T> thenComparingInt(ToIntFunction<? super T> keyExtractor) {
		return thenComparing(comparingInt(keyExtractor));
	}

	default Comparator<T> thenComparingLong(ToLongFunction<? super T> keyExtractor) {
		return thenComparing(comparingLong(keyExtractor));
	}

	default Comparator<T> thenComparingDouble(ToDoubleFunction<? super T> keyExtractor) {
		return thenComparing(comparingDouble(keyExtractor));
	}

	@SuppressWarnings("unchecked")
	static <T extends Comparable<? super T>> Comparator<T> reverseOrder() {
		return (Comparator<T>) Collections.reverseOrder();
	}

	@SuppressWarnings("unchecked")
	static <T extends Comparable<? super T>> Comparator<T> naturalOrder() {
		return (Comparator<T>) Comparators.NATURAL;
	}

	static <T> Comparator<T> nullsFirst(Comparator<? super T> comparator) {
		return new Comparators.Nulls<T>(true, comparator);
	}

	static <T> Comparator<T> nullsLast(Comparator<? super T> comparator) {
		return new Comparators.Nulls<T>(false, comparator);
	}

	static <T, U> Comparator<T> comparing(Function<? super T, ? extends U> keyExtractor,
			Comparator<? super U> keyComparator) {
		Objects.requireNonNull(keyExtractor);
		Objects.requireNonNull(keyComparator);
		return new Comparators.Key<T, U>(keyExtractor, keyComparator);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	static <T, U extends Comparable<? super U>> Comparator<T> comparing(
			Function<? super T, ? extends U> keyExtractor) {
		Objects.requireNonNull(keyExtractor);
		return new Comparators.Key<T, U>(keyExtractor, (Comparator) Comparators.NATURAL);
	}

	static <T> Comparator<T> comparingInt(ToIntFunction<? super T> keyExtractor) {
		Objects.requireNonNull(keyExtractor);
		return new Comparators.IntKey<T>(keyExtractor);
	}

	static <T> Comparator<T> comparingLong(ToLongFunction<? super T> keyExtractor) {
		Objects.requireNonNull(keyExtractor);
		return new Comparators.LongKey<T>(keyExtractor);
	}

	static <T> Comparator<T> comparingDouble(ToDoubleFunction<? super T> keyExtractor) {
		Objects.requireNonNull(keyExtractor);
		return new Comparators.DoubleKey<T>(keyExtractor);
	}
}
