package java.util;

public interface Set<E> extends Collection<E> {
	int size();

	boolean isEmpty();

	boolean contains(Object o);

	Iterator<E> iterator();

	Object[] toArray();

	<T> T[] toArray(T[] a);

	boolean add(E e);

	boolean remove(Object o);

	boolean containsAll(Collection<?> c);

	boolean addAll(Collection<? extends E> c);

	boolean retainAll(Collection<?> c);

	boolean removeAll(Collection<?> c);

	void clear();

	boolean equals(Object o);

	int hashCode();

	static <E> Set<E> of() {
		return Collections.emptySet();
	}

	@SafeVarargs
	static <E> Set<E> of(E... elements) {
		Set<E> set = new LinkedHashSet<>();
		for (E e : elements) {
			if (!set.add(Objects.requireNonNull(e))) {
				throw new IllegalArgumentException("duplicate element: " + e);
			}
		}
		return Collections.unmodifiableSet(set);
	}

	static <E> Set<E> copyOf(Collection<? extends E> coll) {
		Set<E> set = new LinkedHashSet<>();
		for (E e : coll) {
			set.add(Objects.requireNonNull(e));
		}
		return Collections.unmodifiableSet(set);
	}
}
