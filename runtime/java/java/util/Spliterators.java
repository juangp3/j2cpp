package java.util;

import java.util.function.Consumer;

/**
 * Spliterators, written from the Java SE specification: spliterators over
 * collections, arrays and iterators, and the iterator over a spliterator.
 * They do not split. Only the reference forms are provided.
 */
public final class Spliterators {
	private Spliterators() {
	}

	@SuppressWarnings("unchecked")
	public static <T> Spliterator<T> emptySpliterator() {
		return (Spliterator<T>) new ArraySpliterator<Object>(new Object[0], 0, 0, 0);
	}

	public static <T> Spliterator<T> spliterator(Object[] array, int additionalCharacteristics) {
		return new ArraySpliterator<T>(Objects.requireNonNull(array), 0, array.length, additionalCharacteristics);
	}

	public static <T> Spliterator<T> spliterator(Object[] array, int fromIndex, int toIndex,
			int additionalCharacteristics) {
		checkFromToBounds(Objects.requireNonNull(array).length, fromIndex, toIndex);
		return new ArraySpliterator<T>(array, fromIndex, toIndex, additionalCharacteristics);
	}

	/** The bounds check of the array spliterators and Arrays.stream */
	static void checkFromToBounds(int arrayLength, int origin, int fence) {
		if (origin > fence) {
			throw new ArrayIndexOutOfBoundsException("origin(" + origin + ") > fence(" + fence + ")");
		}
		if (origin < 0) {
			throw new ArrayIndexOutOfBoundsException(origin);
		}
		if (fence > arrayLength) {
			throw new ArrayIndexOutOfBoundsException(fence);
		}
	}

	/** Late-binding: the iterator and size are taken at the first use */
	public static <T> Spliterator<T> spliterator(Collection<? extends T> c, int characteristics) {
		return new IteratorSpliterator<T>(Objects.requireNonNull(c), null, -1, sized(characteristics));
	}

	public static <T> Spliterator<T> spliterator(Iterator<? extends T> iterator, long size, int characteristics) {
		return new IteratorSpliterator<T>(null, Objects.requireNonNull(iterator), size, sized(characteristics));
	}

	public static <T> Spliterator<T> spliteratorUnknownSize(Iterator<? extends T> iterator, int characteristics) {
		return new IteratorSpliterator<T>(null, Objects.requireNonNull(iterator), Long.MAX_VALUE,
				characteristics & ~(Spliterator.SIZED | Spliterator.SUBSIZED));
	}

	private static int sized(int characteristics) {
		return (characteristics & Spliterator.CONCURRENT) == 0
				? characteristics | Spliterator.SIZED | Spliterator.SUBSIZED
				: characteristics;
	}

	public static <T> Iterator<T> iterator(Spliterator<? extends T> spliterator) {
		Objects.requireNonNull(spliterator);
		return new SpliteratorIterator<T>(spliterator);
	}

	static final class ArraySpliterator<T> implements Spliterator<T> {
		private final Object[] array;
		private int index;
		private final int fence;
		private final int characteristics;

		ArraySpliterator(Object[] array, int origin, int fence, int additionalCharacteristics) {
			this.array = array;
			this.index = origin;
			this.fence = fence;
			this.characteristics = additionalCharacteristics | Spliterator.SIZED | Spliterator.SUBSIZED;
		}

		@SuppressWarnings("unchecked")
		public boolean tryAdvance(Consumer<? super T> action) {
			Objects.requireNonNull(action);
			if (index >= fence) {
				return false;
			}
			action.accept((T) array[index++]);
			return true;
		}

		public Spliterator<T> trySplit() {
			return null;
		}

		public long estimateSize() {
			return fence - index;
		}

		public int characteristics() {
			return characteristics;
		}
	}

	static final class IteratorSpliterator<T> implements Spliterator<T> {
		private final Collection<? extends T> collection;
		private Iterator<? extends T> iterator;
		private long size;
		private final int characteristics;

		IteratorSpliterator(Collection<? extends T> collection, Iterator<? extends T> iterator, long size,
				int characteristics) {
			this.collection = collection;
			this.iterator = iterator;
			this.size = size;
			this.characteristics = characteristics;
		}

		private void start() {
			if (iterator == null) {
				iterator = collection.iterator();
				size = collection.size();
			}
		}

		public boolean tryAdvance(Consumer<? super T> action) {
			Objects.requireNonNull(action);
			start();
			if (!iterator.hasNext()) {
				return false;
			}
			action.accept(iterator.next());
			return true;
		}

		public void forEachRemaining(Consumer<? super T> action) {
			Objects.requireNonNull(action);
			start();
			while (iterator.hasNext()) {
				action.accept(iterator.next());
			}
		}

		public Spliterator<T> trySplit() {
			return null;
		}

		public long estimateSize() {
			if (iterator == null) {
				return collection.size();
			}
			return size;
		}

		public int characteristics() {
			return characteristics;
		}
	}

	static final class SpliteratorIterator<T> implements Iterator<T>, Consumer<T> {
		private final Spliterator<? extends T> spliterator;
		private boolean ready;
		private T next;

		SpliteratorIterator(Spliterator<? extends T> spliterator) {
			this.spliterator = spliterator;
		}

		public void accept(T t) {
			ready = true;
			next = t;
		}

		public boolean hasNext() {
			if (!ready) {
				spliterator.tryAdvance(this);
			}
			return ready;
		}

		public T next() {
			if (!ready && !hasNext()) {
				throw new NoSuchElementException();
			}
			ready = false;
			T t = next;
			next = null;
			return t;
		}
	}
}
