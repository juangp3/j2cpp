package java.util;

public abstract class AbstractList<E> extends AbstractCollection<E> implements List<E> {
	/** Counts structural modifications, so iterators fail fast */
	protected transient int modCount = 0;

	protected AbstractList() {
	}

	public boolean add(E e) {
		add(size(), e);
		return true;
	}

	public abstract E get(int index);

	public E set(int index, E element) {
		throw new UnsupportedOperationException();
	}

	public void add(int index, E element) {
		throw new UnsupportedOperationException();
	}

	public E remove(int index) {
		throw new UnsupportedOperationException();
	}

	public int indexOf(Object o) {
		ListIterator<E> it = listIterator();
		while (it.hasNext()) {
			if (Objects.equals(o, it.next())) {
				return it.previousIndex();
			}
		}
		return -1;
	}

	public int lastIndexOf(Object o) {
		ListIterator<E> it = listIterator(size());
		while (it.hasPrevious()) {
			if (Objects.equals(o, it.previous())) {
				return it.nextIndex();
			}
		}
		return -1;
	}

	public void clear() {
		removeRange(0, size());
	}

	public boolean addAll(int index, Collection<? extends E> c) {
		rangeCheckForAdd(index, size());
		boolean modified = false;
		for (E e : c) {
			add(index++, e);
			modified = true;
		}
		return modified;
	}

	public Iterator<E> iterator() {
		return new Itr();
	}

	public ListIterator<E> listIterator() {
		return listIterator(0);
	}

	public ListIterator<E> listIterator(int index) {
		rangeCheckForAdd(index, size());
		return new ListItr(index);
	}

	public List<E> subList(int fromIndex, int toIndex) {
		subListRangeCheck(fromIndex, toIndex, size());
		return new SubList<E>(this, fromIndex, toIndex);
	}

	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof List)) {
			return false;
		}
		ListIterator<E> e1 = listIterator();
		ListIterator<?> e2 = ((List<?>) o).listIterator();
		while (e1.hasNext() && e2.hasNext()) {
			if (!Objects.equals(e1.next(), e2.next())) {
				return false;
			}
		}
		return !(e1.hasNext() || e2.hasNext());
	}

	public int hashCode() {
		int hashCode = 1;
		for (E e : this) {
			hashCode = 31 * hashCode + (e == null ? 0 : e.hashCode());
		}
		return hashCode;
	}

	protected void removeRange(int fromIndex, int toIndex) {
		ListIterator<E> it = listIterator(fromIndex);
		for (int i = 0, n = toIndex - fromIndex; i < n; i++) {
			it.next();
			it.remove();
		}
	}

	static void rangeCheckForAdd(int index, int size) {
		if (index < 0 || index > size) {
			throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
		}
	}

	static void subListRangeCheck(int fromIndex, int toIndex, int size) {
		if (fromIndex < 0) {
			throw new IndexOutOfBoundsException("fromIndex = " + fromIndex);
		}
		if (toIndex > size) {
			throw new IndexOutOfBoundsException("toIndex = " + toIndex);
		}
		if (fromIndex > toIndex) {
			throw new IllegalArgumentException("fromIndex(" + fromIndex + ") > toIndex(" + toIndex + ")");
		}
	}

	private class Itr implements Iterator<E> {
		/** Index of the element next() returns */
		int cursor = 0;
		/** Index of the element last returned, or -1 after remove */
		int lastRet = -1;
		int expectedModCount = modCount;

		public boolean hasNext() {
			return cursor != size();
		}

		public E next() {
			checkForComodification();
			int i = cursor;
			if (i >= size()) {
				throw new NoSuchElementException();
			}
			E next = get(i);
			lastRet = i;
			cursor = i + 1;
			return next;
		}

		public void remove() {
			if (lastRet < 0) {
				throw new IllegalStateException();
			}
			checkForComodification();
			AbstractList.this.remove(lastRet);
			if (lastRet < cursor) {
				cursor--;
			}
			lastRet = -1;
			expectedModCount = modCount;
		}

		final void checkForComodification() {
			if (modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}
		}
	}

	private class ListItr extends Itr implements ListIterator<E> {
		ListItr(int index) {
			cursor = index;
		}

		public boolean hasPrevious() {
			return cursor != 0;
		}

		public E previous() {
			checkForComodification();
			int i = cursor - 1;
			if (i < 0) {
				throw new NoSuchElementException();
			}
			E previous = get(i);
			lastRet = i;
			cursor = i;
			return previous;
		}

		public int nextIndex() {
			return cursor;
		}

		public int previousIndex() {
			return cursor - 1;
		}

		public void set(E e) {
			if (lastRet < 0) {
				throw new IllegalStateException();
			}
			checkForComodification();
			AbstractList.this.set(lastRet, e);
			expectedModCount = modCount;
		}

		public void add(E e) {
			checkForComodification();
			int i = cursor;
			AbstractList.this.add(i, e);
			lastRet = -1;
			cursor = i + 1;
			expectedModCount = modCount;
		}
	}

	/** A view of a range of a list; changes go through the parent */
	private static class SubList<E> extends AbstractList<E> {
		private final AbstractList<E> parent;
		private final int offset;
		private int size;

		SubList(AbstractList<E> parent, int fromIndex, int toIndex) {
			this.parent = parent;
			this.offset = fromIndex;
			this.size = toIndex - fromIndex;
			this.modCount = parent.modCount;
		}

		public E get(int index) {
			Objects.checkIndex(index, size);
			checkForComodification();
			return parent.get(offset + index);
		}

		public E set(int index, E element) {
			Objects.checkIndex(index, size);
			checkForComodification();
			return parent.set(offset + index, element);
		}

		public int size() {
			checkForComodification();
			return size;
		}

		public void add(int index, E element) {
			rangeCheckForAdd(index, size);
			checkForComodification();
			parent.add(offset + index, element);
			modCount = parent.modCount;
			size++;
		}

		public E remove(int index) {
			Objects.checkIndex(index, size);
			checkForComodification();
			E result = parent.remove(offset + index);
			modCount = parent.modCount;
			size--;
			return result;
		}

		protected void removeRange(int fromIndex, int toIndex) {
			checkForComodification();
			parent.removeRange(offset + fromIndex, offset + toIndex);
			modCount = parent.modCount;
			size -= toIndex - fromIndex;
		}

		private void checkForComodification() {
			if (parent.modCount != modCount) {
				throw new ConcurrentModificationException();
			}
		}
	}
}
