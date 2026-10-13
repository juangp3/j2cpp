package java.util;

public abstract class AbstractCollection<E> implements Collection<E> {
	protected AbstractCollection() {
	}

	public abstract Iterator<E> iterator();

	public abstract int size();

	public boolean isEmpty() {
		return size() == 0;
	}

	public boolean contains(Object o) {
		Iterator<E> it = iterator();
		while (it.hasNext()) {
			if (Objects.equals(o, it.next())) {
				return true;
			}
		}
		return false;
	}

	public Object[] toArray() {
		Object[] r = new Object[size()];
		Iterator<E> it = iterator();
		for (int i = 0; i < r.length && it.hasNext(); i++) {
			r[i] = it.next();
		}
		return r;
	}

	@SuppressWarnings("unchecked")
	public <T> T[] toArray(T[] a) {
		int size = size();
		T[] r = a.length >= size ? a : (T[]) Arrays.newArrayLike(a, size);
		Iterator<E> it = iterator();
		for (int i = 0; i < size && it.hasNext(); i++) {
			r[i] = (T) it.next();
		}
		if (r.length > size) {
			r[size] = null;
		}
		return r;
	}

	public boolean add(E e) {
		throw new UnsupportedOperationException();
	}

	public boolean remove(Object o) {
		Iterator<E> it = iterator();
		while (it.hasNext()) {
			if (Objects.equals(o, it.next())) {
				it.remove();
				return true;
			}
		}
		return false;
	}

	public boolean containsAll(Collection<?> c) {
		for (Object e : c) {
			if (!contains(e)) {
				return false;
			}
		}
		return true;
	}

	public boolean addAll(Collection<? extends E> c) {
		boolean modified = false;
		for (E e : c) {
			if (add(e)) {
				modified = true;
			}
		}
		return modified;
	}

	public boolean removeAll(Collection<?> c) {
		Objects.requireNonNull(c);
		boolean modified = false;
		Iterator<E> it = iterator();
		while (it.hasNext()) {
			if (c.contains(it.next())) {
				it.remove();
				modified = true;
			}
		}
		return modified;
	}

	public boolean retainAll(Collection<?> c) {
		Objects.requireNonNull(c);
		boolean modified = false;
		Iterator<E> it = iterator();
		while (it.hasNext()) {
			if (!c.contains(it.next())) {
				it.remove();
				modified = true;
			}
		}
		return modified;
	}

	public void clear() {
		Iterator<E> it = iterator();
		while (it.hasNext()) {
			it.next();
			it.remove();
		}
	}

	public String toString() {
		Iterator<E> it = iterator();
		if (!it.hasNext()) {
			return "[]";
		}
		StringBuilder sb = new StringBuilder();
		sb.append('[');
		while (true) {
			E e = it.next();
			sb.append(e == this ? "(this Collection)" : String.valueOf(e));
			if (!it.hasNext()) {
				return sb.append(']').toString();
			}
			sb.append(',').append(' ');
		}
	}
}
