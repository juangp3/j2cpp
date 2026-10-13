package java.util;

import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/** The comparators Comparator's default and static methods return */
final class Comparators {
	private Comparators() {
	}

	@SuppressWarnings("rawtypes")
	static final Comparator NATURAL = new Natural();

	@SuppressWarnings({ "unchecked", "rawtypes" })
	static final class Natural implements Comparator<Comparable> {
		public int compare(Comparable a, Comparable b) {
			return a.compareTo(b);
		}

		public Comparator<Comparable> reversed() {
			return Collections.reverseOrder();
		}
	}

	static final class Reversed<T> implements Comparator<T> {
		private final Comparator<T> comparator;

		Reversed(Comparator<T> comparator) {
			this.comparator = comparator;
		}

		public int compare(T a, T b) {
			return comparator.compare(b, a);
		}

		public Comparator<T> reversed() {
			return comparator;
		}
	}

	static final class Then<T> implements Comparator<T> {
		private final Comparator<T> first;
		private final Comparator<? super T> second;

		Then(Comparator<T> first, Comparator<? super T> second) {
			this.first = first;
			this.second = second;
		}

		public int compare(T a, T b) {
			int result = first.compare(a, b);
			return result != 0 ? result : second.compare(a, b);
		}
	}

	static final class Nulls<T> implements Comparator<T> {
		private final boolean nullFirst;
		private final Comparator<? super T> real;

		Nulls(boolean nullFirst, Comparator<? super T> real) {
			this.nullFirst = nullFirst;
			this.real = real;
		}

		public int compare(T a, T b) {
			if (a == null) {
				return b == null ? 0 : (nullFirst ? -1 : 1);
			}
			if (b == null) {
				return nullFirst ? 1 : -1;
			}
			return real == null ? 0 : real.compare(a, b);
		}
	}

	static final class Key<T, U> implements Comparator<T> {
		private final Function<? super T, ? extends U> key;
		private final Comparator<? super U> comparator;

		Key(Function<? super T, ? extends U> key, Comparator<? super U> comparator) {
			this.key = key;
			this.comparator = comparator;
		}

		public int compare(T a, T b) {
			return comparator.compare(key.apply(a), key.apply(b));
		}
	}

	static final class IntKey<T> implements Comparator<T> {
		private final ToIntFunction<? super T> key;

		IntKey(ToIntFunction<? super T> key) {
			this.key = key;
		}

		public int compare(T a, T b) {
			return Integer.compare(key.applyAsInt(a), key.applyAsInt(b));
		}
	}

	static final class LongKey<T> implements Comparator<T> {
		private final ToLongFunction<? super T> key;

		LongKey(ToLongFunction<? super T> key) {
			this.key = key;
		}

		public int compare(T a, T b) {
			return Long.compare(key.applyAsLong(a), key.applyAsLong(b));
		}
	}

	static final class DoubleKey<T> implements Comparator<T> {
		private final ToDoubleFunction<? super T> key;

		DoubleKey(ToDoubleFunction<? super T> key) {
			this.key = key;
		}

		public int compare(T a, T b) {
			return Double.compare(key.applyAsDouble(a), key.applyAsDouble(b));
		}
	}
}
