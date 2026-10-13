package java.util;

import java.util.function.UnaryOperator;

public interface List<E> extends Collection<E> {
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

	boolean addAll(int index, Collection<? extends E> c);

	boolean removeAll(Collection<?> c);

	boolean retainAll(Collection<?> c);

	void clear();

	boolean equals(Object o);

	int hashCode();

	E get(int index);

	E set(int index, E element);

	void add(int index, E element);

	E remove(int index);

	int indexOf(Object o);

	int lastIndexOf(Object o);

	ListIterator<E> listIterator();

	ListIterator<E> listIterator(int index);

	List<E> subList(int fromIndex, int toIndex);

	default void replaceAll(UnaryOperator<E> operator) {
		Objects.requireNonNull(operator);
		ListIterator<E> li = listIterator();
		while (li.hasNext()) {
			li.set(operator.apply(li.next()));
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	default void sort(Comparator<? super E> c) {
		Object[] a = toArray();
		Arrays.sort(a, (Comparator) c);
		ListIterator<E> i = listIterator();
		for (Object e : a) {
			i.next();
			i.set((E) e);
		}
	}

	static <E> List<E> of() {
		return Collections.emptyList();
	}

	@SafeVarargs
	static <E> List<E> of(E... elements) {
		ArrayList<E> list = new ArrayList<>(elements.length);
		for (E e : elements) {
			list.add(Objects.requireNonNull(e));
		}
		return Collections.unmodifiableList(list);
	}

	static <E> List<E> copyOf(Collection<? extends E> coll) {
		ArrayList<E> list = new ArrayList<>(coll.size());
		for (E e : coll) {
			list.add(Objects.requireNonNull(e));
		}
		return Collections.unmodifiableList(list);
	}
}
