package java.util;

public abstract class AbstractSet<E> extends AbstractCollection<E> implements Set<E> {
	protected AbstractSet() {
	}

	public boolean equals(Object o) {
		if (o == this) {
			return true;
		}
		if (!(o instanceof Set)) {
			return false;
		}
		Collection<?> c = (Collection<?>) o;
		if (c.size() != size()) {
			return false;
		}
		return containsAll(c);
	}

	public int hashCode() {
		int h = 0;
		for (E e : this) {
			if (e != null) {
				h += e.hashCode();
			}
		}
		return h;
	}

	public boolean removeAll(Collection<?> c) {
		Objects.requireNonNull(c);
		boolean modified = false;
		if (size() > c.size()) {
			for (Object e : c) {
				if (remove(e)) {
					modified = true;
				}
			}
		} else {
			Iterator<?> it = iterator();
			while (it.hasNext()) {
				if (c.contains(it.next())) {
					it.remove();
					modified = true;
				}
			}
		}
		return modified;
	}
}
