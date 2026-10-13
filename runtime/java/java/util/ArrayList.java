package java.util;

import java.util.function.Consumer;

public class ArrayList<E> extends AbstractList<E> implements List<E>, RandomAccess, Cloneable, java.io.Serializable {
	private static final Object[] EMPTY = {};

	private Object[] elementData;
	private int size;

	public ArrayList() {
		elementData = EMPTY;
	}

	public ArrayList(int initialCapacity) {
		if (initialCapacity < 0) {
			throw new IllegalArgumentException("Illegal Capacity: " + initialCapacity);
		}
		elementData = initialCapacity == 0 ? EMPTY : new Object[initialCapacity];
	}

	public ArrayList(Collection<? extends E> c) {
		Object[] a = c.toArray();
		size = a.length;
		elementData = size == 0 ? EMPTY : Arrays.copyOf(a, size);
	}

	public void ensureCapacity(int minCapacity) {
		if (minCapacity > elementData.length) {
			modCount++;
			grow(minCapacity);
		}
	}

	public void trimToSize() {
		modCount++;
		if (size < elementData.length) {
			elementData = size == 0 ? EMPTY : Arrays.copyOf(elementData, size);
		}
	}

	private void grow(int minCapacity) {
		int capacity = Math.max(minCapacity, elementData.length + (elementData.length >> 1));
		elementData = Arrays.copyOf(elementData, Math.max(capacity, 10));
	}

	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public boolean contains(Object o) {
		return indexOf(o) >= 0;
	}

	public int indexOf(Object o) {
		for (int i = 0; i < size; i++) {
			if (Objects.equals(o, elementData[i])) {
				return i;
			}
		}
		return -1;
	}

	public int lastIndexOf(Object o) {
		for (int i = size - 1; i >= 0; i--) {
			if (Objects.equals(o, elementData[i])) {
				return i;
			}
		}
		return -1;
	}

	public Object clone() {
		ArrayList<E> copy = new ArrayList<E>(this);
		return copy;
	}

	public Object[] toArray() {
		return Arrays.copyOf(elementData, size);
	}

	@SuppressWarnings("unchecked")
	public <T> T[] toArray(T[] a) {
		if (a.length < size) {
			a = (T[]) Arrays.newArrayLike(a, size);
		}
		System.arraycopy(elementData, 0, a, 0, size);
		if (a.length > size) {
			a[size] = null;
		}
		return a;
	}

	@SuppressWarnings("unchecked")
	E elementData(int index) {
		return (E) elementData[index];
	}

	public E get(int index) {
		Objects.checkIndex(index, size);
		return elementData(index);
	}

	public E set(int index, E element) {
		Objects.checkIndex(index, size);
		E oldValue = elementData(index);
		elementData[index] = element;
		return oldValue;
	}

	public boolean add(E e) {
		modCount++;
		if (size == elementData.length) {
			grow(size + 1);
		}
		elementData[size++] = e;
		return true;
	}

	public void add(int index, E element) {
		rangeCheckForAdd(index, size);
		modCount++;
		if (size == elementData.length) {
			grow(size + 1);
		}
		System.arraycopy(elementData, index, elementData, index + 1, size - index);
		elementData[index] = element;
		size++;
	}

	public E remove(int index) {
		Objects.checkIndex(index, size);
		E oldValue = elementData(index);
		fastRemove(index);
		return oldValue;
	}

	public boolean remove(Object o) {
		int i = indexOf(o);
		if (i < 0) {
			return false;
		}
		fastRemove(i);
		return true;
	}

	private void fastRemove(int i) {
		modCount++;
		int newSize = size - 1;
		if (newSize > i) {
			System.arraycopy(elementData, i + 1, elementData, i, newSize - i);
		}
		elementData[size = newSize] = null;
	}

	public void clear() {
		modCount++;
		for (int i = 0; i < size; i++) {
			elementData[i] = null;
		}
		size = 0;
	}

	public boolean addAll(Collection<? extends E> c) {
		Object[] a = c.toArray();
		modCount++;
		if (a.length == 0) {
			return false;
		}
		if (a.length > elementData.length - size) {
			grow(size + a.length);
		}
		System.arraycopy(a, 0, elementData, size, a.length);
		size += a.length;
		return true;
	}

	public boolean addAll(int index, Collection<? extends E> c) {
		rangeCheckForAdd(index, size);
		Object[] a = c.toArray();
		modCount++;
		if (a.length == 0) {
			return false;
		}
		if (a.length > elementData.length - size) {
			grow(size + a.length);
		}
		System.arraycopy(elementData, index, elementData, index + a.length, size - index);
		System.arraycopy(a, 0, elementData, index, a.length);
		size += a.length;
		return true;
	}

	protected void removeRange(int fromIndex, int toIndex) {
		if (fromIndex > toIndex) {
			throw new IndexOutOfBoundsException("From Index: " + fromIndex + " > To Index: " + toIndex);
		}
		modCount++;
		System.arraycopy(elementData, toIndex, elementData, fromIndex, size - toIndex);
		int newSize = size - (toIndex - fromIndex);
		for (int i = newSize; i < size; i++) {
			elementData[i] = null;
		}
		size = newSize;
	}

	public void forEach(Consumer<? super E> action) {
		Objects.requireNonNull(action);
		int expectedModCount = modCount;
		for (int i = 0; modCount == expectedModCount && i < size; i++) {
			action.accept(elementData(i));
		}
		if (modCount != expectedModCount) {
			throw new ConcurrentModificationException();
		}
	}

	@SuppressWarnings("unchecked")
	public void sort(Comparator<? super E> c) {
		int expectedModCount = modCount;
		Arrays.sort((E[]) elementData, 0, size, c);
		if (modCount != expectedModCount) {
			throw new ConcurrentModificationException();
		}
		modCount++;
	}
}
