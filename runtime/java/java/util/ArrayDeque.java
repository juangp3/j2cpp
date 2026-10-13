package java.util;

/**
 * ArrayDeque, written from the Java SE specification: a circular array that
 * grows when full. Null elements are rejected.
 */
public class ArrayDeque<E> extends AbstractCollection<E> implements Deque<E>, Cloneable, java.io.Serializable {
	private Object[] elements;
	/** Index of the first element */
	private int head;
	private int size;
	/** Counts structural modifications, so iterators fail fast */
	private int modCount;

	public ArrayDeque() {
		elements = new Object[16];
	}

	public ArrayDeque(int numElements) {
		elements = new Object[Math.max(1, numElements)];
	}

	public ArrayDeque(Collection<? extends E> c) {
		this(c.size());
		addAll(c);
	}

	private int index(int i) {
		int j = head + i;
		return j >= elements.length ? j - elements.length : j;
	}

	@SuppressWarnings("unchecked")
	private E at(int i) {
		return (E) elements[index(i)];
	}

	private void grow() {
		Object[] a = new Object[elements.length * 2];
		for (int i = 0; i < size; i++) {
			a[i] = elements[index(i)];
		}
		elements = a;
		head = 0;
	}

	public void addFirst(E e) {
		Objects.requireNonNull(e);
		if (size == elements.length) {
			grow();
		}
		head = head == 0 ? elements.length - 1 : head - 1;
		elements[head] = e;
		size++;
		modCount++;
	}

	public void addLast(E e) {
		Objects.requireNonNull(e);
		if (size == elements.length) {
			grow();
		}
		elements[index(size)] = e;
		size++;
		modCount++;
	}

	public boolean offerFirst(E e) {
		addFirst(e);
		return true;
	}

	public boolean offerLast(E e) {
		addLast(e);
		return true;
	}

	public E removeFirst() {
		E e = pollFirst();
		if (e == null) {
			throw new NoSuchElementException();
		}
		return e;
	}

	public E removeLast() {
		E e = pollLast();
		if (e == null) {
			throw new NoSuchElementException();
		}
		return e;
	}

	public E pollFirst() {
		if (size == 0) {
			return null;
		}
		E e = at(0);
		elements[head] = null;
		head = index(1);
		size--;
		modCount++;
		return e;
	}

	public E pollLast() {
		if (size == 0) {
			return null;
		}
		int last = index(size - 1);
		E e = at(size - 1);
		elements[last] = null;
		size--;
		modCount++;
		return e;
	}

	public E getFirst() {
		if (size == 0) {
			throw new NoSuchElementException();
		}
		return at(0);
	}

	public E getLast() {
		if (size == 0) {
			throw new NoSuchElementException();
		}
		return at(size - 1);
	}

	public E peekFirst() {
		return size == 0 ? null : at(0);
	}

	public E peekLast() {
		return size == 0 ? null : at(size - 1);
	}

	public boolean removeFirstOccurrence(Object o) {
		if (o != null) {
			for (int i = 0; i < size; i++) {
				if (o.equals(at(i))) {
					delete(i);
					return true;
				}
			}
		}
		return false;
	}

	public boolean removeLastOccurrence(Object o) {
		if (o != null) {
			for (int i = size - 1; i >= 0; i--) {
				if (o.equals(at(i))) {
					delete(i);
					return true;
				}
			}
		}
		return false;
	}

	/** Removes the element at logical index i, shifting the later ones down */
	private void delete(int i) {
		for (int j = i; j < size - 1; j++) {
			elements[index(j)] = elements[index(j + 1)];
		}
		elements[index(size - 1)] = null;
		size--;
		modCount++;
	}

	public boolean add(E e) {
		addLast(e);
		return true;
	}

	public boolean offer(E e) {
		return offerLast(e);
	}

	public E remove() {
		return removeFirst();
	}

	public E poll() {
		return pollFirst();
	}

	public E element() {
		return getFirst();
	}

	public E peek() {
		return peekFirst();
	}

	public void push(E e) {
		addFirst(e);
	}

	public E pop() {
		return removeFirst();
	}

	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public boolean contains(Object o) {
		if (o != null) {
			for (int i = 0; i < size; i++) {
				if (o.equals(at(i))) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean remove(Object o) {
		return removeFirstOccurrence(o);
	}

	public void clear() {
		for (int i = 0; i < size; i++) {
			elements[index(i)] = null;
		}
		head = 0;
		size = 0;
		modCount++;
	}

	public Object[] toArray() {
		Object[] a = new Object[size];
		for (int i = 0; i < size; i++) {
			a[i] = elements[index(i)];
		}
		return a;
	}

	public Iterator<E> iterator() {
		return new DequeIterator(false);
	}

	public Iterator<E> descendingIterator() {
		return new DequeIterator(true);
	}

	public ArrayDeque<E> clone() {
		return new ArrayDeque<E>(this);
	}

	private class DequeIterator implements Iterator<E> {
		private final boolean descending;
		/** Logical index of the next element */
		private int cursor;
		private int lastRet = -1;
		private int expectedModCount = modCount;

		DequeIterator(boolean descending) {
			this.descending = descending;
			cursor = descending ? size - 1 : 0;
		}

		public boolean hasNext() {
			return descending ? cursor >= 0 : cursor < size;
		}

		public E next() {
			if (modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			lastRet = cursor;
			cursor += descending ? -1 : 1;
			return at(lastRet);
		}

		public void remove() {
			if (lastRet < 0) {
				throw new IllegalStateException();
			}
			if (modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}
			delete(lastRet);
			if (!descending) {
				cursor--;
			}
			lastRet = -1;
			expectedModCount = modCount;
		}
	}
}
