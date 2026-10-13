package java.util;

/**
 * TreeSet, written from the Java SE specification: the keys of a
 * NavigableMap. The same class serves as the key set of TreeMap and its
 * views, where add is not supported.
 */
public class TreeSet<E> extends AbstractSet<E> implements NavigableSet<E>, Cloneable, java.io.Serializable {
	private static final Object PRESENT = new Object();

	private final NavigableMap<E, Object> m;
	/** True for a key set of a map, which has values of its own */
	private final boolean keyView;

	private TreeSet(NavigableMap<E, Object> m, boolean keyView) {
		this.m = m;
		this.keyView = keyView;
	}

	@SuppressWarnings("unchecked")
	static <K> NavigableSet<K> keySetOf(NavigableMap<K, ?> map) {
		return new TreeSet<K>((NavigableMap<K, Object>) map, true);
	}

	public TreeSet() {
		this(new TreeMap<E, Object>(), false);
	}

	public TreeSet(Comparator<? super E> comparator) {
		this(new TreeMap<E, Object>(comparator), false);
	}

	public TreeSet(Collection<? extends E> c) {
		this();
		addAll(c);
	}

	public TreeSet(SortedSet<E> s) {
		this(s.comparator());
		addAll(s);
	}

	private NavigableSet<E> derived(NavigableMap<E, Object> map) {
		return new TreeSet<E>(map, keyView);
	}

	public Iterator<E> iterator() {
		return new KeyIterator<E>(m.entrySet().iterator());
	}

	public Iterator<E> descendingIterator() {
		return descendingSet().iterator();
	}

	public NavigableSet<E> descendingSet() {
		return derived(m.descendingMap());
	}

	public int size() {
		return m.size();
	}

	public boolean isEmpty() {
		return m.isEmpty();
	}

	public boolean contains(Object o) {
		return m.containsKey(o);
	}

	public boolean add(E e) {
		if (keyView) {
			throw new UnsupportedOperationException();
		}
		return m.put(e, PRESENT) == null;
	}

	public boolean remove(Object o) {
		if (!m.containsKey(o)) {
			return false;
		}
		m.remove(o);
		return true;
	}

	public void clear() {
		m.clear();
	}

	public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
		return derived(m.subMap(fromElement, fromInclusive, toElement, toInclusive));
	}

	public NavigableSet<E> headSet(E toElement, boolean inclusive) {
		return derived(m.headMap(toElement, inclusive));
	}

	public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
		return derived(m.tailMap(fromElement, inclusive));
	}

	public SortedSet<E> subSet(E fromElement, E toElement) {
		return subSet(fromElement, true, toElement, false);
	}

	public SortedSet<E> headSet(E toElement) {
		return headSet(toElement, false);
	}

	public SortedSet<E> tailSet(E fromElement) {
		return tailSet(fromElement, true);
	}

	public Comparator<? super E> comparator() {
		return m.comparator();
	}

	public E first() {
		return m.firstKey();
	}

	public E last() {
		return m.lastKey();
	}

	public E lower(E e) {
		return m.lowerKey(e);
	}

	public E floor(E e) {
		return m.floorKey(e);
	}

	public E ceiling(E e) {
		return m.ceilingKey(e);
	}

	public E higher(E e) {
		return m.higherKey(e);
	}

	public E pollFirst() {
		Map.Entry<E, ?> e = m.pollFirstEntry();
		return e == null ? null : e.getKey();
	}

	public E pollLast() {
		Map.Entry<E, ?> e = m.pollLastEntry();
		return e == null ? null : e.getKey();
	}

	public Object clone() {
		TreeSet<E> clone = new TreeSet<E>(m.comparator());
		clone.addAll(this);
		return clone;
	}

	private static final class KeyIterator<E> implements Iterator<E> {
		private final Iterator<Map.Entry<E, Object>> i;

		KeyIterator(Iterator<Map.Entry<E, Object>> i) {
			this.i = i;
		}

		public boolean hasNext() {
			return i.hasNext();
		}

		public E next() {
			return i.next().getKey();
		}

		public void remove() {
			i.remove();
		}
	}
}
