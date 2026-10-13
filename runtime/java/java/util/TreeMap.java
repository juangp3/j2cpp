package java.util;

/**
 * TreeMap, written from the Java SE specification: a red-black tree with
 * the textbook insertion and deletion fix-ups. Sub-maps, head and tail maps
 * and descending maps are one view class with optional bounds and a
 * direction.
 */
public class TreeMap<K, V> extends AbstractMap<K, V> implements NavigableMap<K, V>, Cloneable, java.io.Serializable {
	private static final boolean RED = false;
	private static final boolean BLACK = true;

	static final class Entry<K, V> implements Map.Entry<K, V> {
		K key;
		V value;
		Entry<K, V> left;
		Entry<K, V> right;
		Entry<K, V> parent;
		boolean color = BLACK;

		Entry(K key, V value, Entry<K, V> parent) {
			this.key = key;
			this.value = value;
			this.parent = parent;
		}

		public K getKey() {
			return key;
		}

		public V getValue() {
			return value;
		}

		public V setValue(V value) {
			V oldValue = this.value;
			this.value = value;
			return oldValue;
		}

		public boolean equals(Object o) {
			return AbstractMap.entryEquals(this, o);
		}

		public int hashCode() {
			return AbstractMap.entryHashCode(this);
		}

		public String toString() {
			return key + "=" + value;
		}
	}

	private final Comparator<? super K> comparator;
	private Entry<K, V> root;
	private int size = 0;
	int modCount = 0;

	public TreeMap() {
		comparator = null;
	}

	public TreeMap(Comparator<? super K> comparator) {
		this.comparator = comparator;
	}

	@SuppressWarnings("unchecked")
	public TreeMap(Map<? extends K, ? extends V> m) {
		comparator = m instanceof SortedMap ? ((SortedMap<K, ? extends V>) m).comparator() : null;
		putAll(m);
	}

	public TreeMap(SortedMap<K, ? extends V> m) {
		comparator = m.comparator();
		putAll(m);
	}

	@SuppressWarnings("unchecked")
	final int compare(Object k1, Object k2) {
		return comparator == null ? ((Comparable<? super K>) k1).compareTo((K) k2)
				: comparator.compare((K) k1, (K) k2);
	}

	public int size() {
		return size;
	}

	public boolean containsKey(Object key) {
		return getEntry(key) != null;
	}

	public boolean containsValue(Object value) {
		for (Entry<K, V> e = getFirstEntry(); e != null; e = successor(e)) {
			if (Objects.equals(value, e.value)) {
				return true;
			}
		}
		return false;
	}

	public V get(Object key) {
		Entry<K, V> p = getEntry(key);
		return p == null ? null : p.value;
	}

	public Comparator<? super K> comparator() {
		return comparator;
	}

	public K firstKey() {
		return key(getFirstEntry());
	}

	public K lastKey() {
		return key(getLastEntry());
	}

	public void putAll(Map<? extends K, ? extends V> map) {
		for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
			put(e.getKey(), e.getValue());
		}
	}

	final Entry<K, V> getEntry(Object key) {
		Objects.requireNonNull(key);
		Entry<K, V> p = root;
		while (p != null) {
			int cmp = compare(key, p.key);
			if (cmp < 0) {
				p = p.left;
			} else if (cmp > 0) {
				p = p.right;
			} else {
				return p;
			}
		}
		return null;
	}

	/** The entry with the least key greater than or equal to key (or greater, if not inclusive) */
	final Entry<K, V> getCeilingEntry(Object key, boolean inclusive) {
		Entry<K, V> p = root;
		Entry<K, V> best = null;
		while (p != null) {
			int cmp = compare(key, p.key);
			if (cmp < 0 || (cmp == 0 && inclusive)) {
				if (cmp == 0) {
					return p;
				}
				best = p;
				p = p.left;
			} else {
				p = p.right;
			}
		}
		return best;
	}

	/** The entry with the greatest key less than or equal to key (or less, if not inclusive) */
	final Entry<K, V> getFloorEntry(Object key, boolean inclusive) {
		Entry<K, V> p = root;
		Entry<K, V> best = null;
		while (p != null) {
			int cmp = compare(key, p.key);
			if (cmp > 0 || (cmp == 0 && inclusive)) {
				if (cmp == 0) {
					return p;
				}
				best = p;
				p = p.right;
			} else {
				p = p.left;
			}
		}
		return best;
	}

	public V put(K key, V value) {
		Entry<K, V> t = root;
		if (t == null) {
			compare(key, key); // type and null check
			root = new Entry<K, V>(key, value, null);
			size = 1;
			modCount++;
			return null;
		}
		Objects.requireNonNull(key);
		int cmp;
		Entry<K, V> parent;
		do {
			parent = t;
			cmp = compare(key, t.key);
			if (cmp < 0) {
				t = t.left;
			} else if (cmp > 0) {
				t = t.right;
			} else {
				return t.setValue(value);
			}
		} while (t != null);
		Entry<K, V> e = new Entry<K, V>(key, value, parent);
		if (cmp < 0) {
			parent.left = e;
		} else {
			parent.right = e;
		}
		fixAfterInsertion(e);
		size++;
		modCount++;
		return null;
	}

	public V remove(Object key) {
		Entry<K, V> p = getEntry(key);
		if (p == null) {
			return null;
		}
		V oldValue = p.value;
		deleteEntry(p);
		return oldValue;
	}

	public void clear() {
		modCount++;
		size = 0;
		root = null;
	}

	@SuppressWarnings("unchecked")
	public Object clone() {
		TreeMap<K, V> clone = new TreeMap<K, V>(comparator);
		clone.putAll(this);
		return clone;
	}

	public Map.Entry<K, V> firstEntry() {
		return exportEntry(getFirstEntry());
	}

	public Map.Entry<K, V> lastEntry() {
		return exportEntry(getLastEntry());
	}

	public Map.Entry<K, V> pollFirstEntry() {
		Entry<K, V> p = getFirstEntry();
		Map.Entry<K, V> result = exportEntry(p);
		if (p != null) {
			deleteEntry(p);
		}
		return result;
	}

	public Map.Entry<K, V> pollLastEntry() {
		Entry<K, V> p = getLastEntry();
		Map.Entry<K, V> result = exportEntry(p);
		if (p != null) {
			deleteEntry(p);
		}
		return result;
	}

	public Map.Entry<K, V> lowerEntry(K key) {
		return exportEntry(getFloorEntry(key, false));
	}

	public K lowerKey(K key) {
		return keyOrNull(getFloorEntry(key, false));
	}

	public Map.Entry<K, V> floorEntry(K key) {
		return exportEntry(getFloorEntry(key, true));
	}

	public K floorKey(K key) {
		return keyOrNull(getFloorEntry(key, true));
	}

	public Map.Entry<K, V> ceilingEntry(K key) {
		return exportEntry(getCeilingEntry(key, true));
	}

	public K ceilingKey(K key) {
		return keyOrNull(getCeilingEntry(key, true));
	}

	public Map.Entry<K, V> higherEntry(K key) {
		return exportEntry(getCeilingEntry(key, false));
	}

	public K higherKey(K key) {
		return keyOrNull(getCeilingEntry(key, false));
	}

	public Set<K> keySet() {
		return navigableKeySet();
	}

	public NavigableSet<K> navigableKeySet() {
		return TreeSet.keySetOf(this);
	}

	public NavigableSet<K> descendingKeySet() {
		return descendingMap().navigableKeySet();
	}

	public Collection<V> values() {
		return new View<K, V>(this, true, null, true, true, null, true, false).values();
	}

	public Set<Map.Entry<K, V>> entrySet() {
		return new View<K, V>(this, true, null, true, true, null, true, false).entrySet();
	}

	public NavigableMap<K, V> descendingMap() {
		return new View<K, V>(this, true, null, true, true, null, true, true);
	}

	public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
		if (compare(fromKey, toKey) > 0) {
			throw new IllegalArgumentException("fromKey > toKey");
		}
		return new View<K, V>(this, false, fromKey, fromInclusive, false, toKey, toInclusive, false);
	}

	public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
		compare(toKey, toKey);
		return new View<K, V>(this, true, null, true, false, toKey, inclusive, false);
	}

	public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
		compare(fromKey, fromKey);
		return new View<K, V>(this, false, fromKey, inclusive, true, null, true, false);
	}

	public SortedMap<K, V> subMap(K fromKey, K toKey) {
		return subMap(fromKey, true, toKey, false);
	}

	public SortedMap<K, V> headMap(K toKey) {
		return headMap(toKey, false);
	}

	public SortedMap<K, V> tailMap(K fromKey) {
		return tailMap(fromKey, true);
	}

	static <K, V> Map.Entry<K, V> exportEntry(Entry<K, V> e) {
		return e == null ? null : new AbstractMap.SimpleImmutableEntry<K, V>(e);
	}

	static <K> K keyOrNull(Entry<K, ?> e) {
		return e == null ? null : e.key;
	}

	static <K> K key(Entry<K, ?> e) {
		if (e == null) {
			throw new NoSuchElementException();
		}
		return e.key;
	}

	final Entry<K, V> getFirstEntry() {
		Entry<K, V> p = root;
		if (p != null) {
			while (p.left != null) {
				p = p.left;
			}
		}
		return p;
	}

	final Entry<K, V> getLastEntry() {
		Entry<K, V> p = root;
		if (p != null) {
			while (p.right != null) {
				p = p.right;
			}
		}
		return p;
	}

	static <K, V> Entry<K, V> successor(Entry<K, V> t) {
		if (t == null) {
			return null;
		}
		if (t.right != null) {
			Entry<K, V> p = t.right;
			while (p.left != null) {
				p = p.left;
			}
			return p;
		}
		Entry<K, V> p = t.parent;
		Entry<K, V> ch = t;
		while (p != null && ch == p.right) {
			ch = p;
			p = p.parent;
		}
		return p;
	}

	static <K, V> Entry<K, V> predecessor(Entry<K, V> t) {
		if (t == null) {
			return null;
		}
		if (t.left != null) {
			Entry<K, V> p = t.left;
			while (p.right != null) {
				p = p.right;
			}
			return p;
		}
		Entry<K, V> p = t.parent;
		Entry<K, V> ch = t;
		while (p != null && ch == p.left) {
			ch = p;
			p = p.parent;
		}
		return p;
	}

	// Red-black balancing; a null child counts as black

	private static <K, V> boolean colorOf(Entry<K, V> p) {
		return p == null ? BLACK : p.color;
	}

	private static <K, V> Entry<K, V> parentOf(Entry<K, V> p) {
		return p == null ? null : p.parent;
	}

	private static <K, V> void setColor(Entry<K, V> p, boolean c) {
		if (p != null) {
			p.color = c;
		}
	}

	private static <K, V> Entry<K, V> leftOf(Entry<K, V> p) {
		return p == null ? null : p.left;
	}

	private static <K, V> Entry<K, V> rightOf(Entry<K, V> p) {
		return p == null ? null : p.right;
	}

	private void rotateLeft(Entry<K, V> p) {
		if (p == null) {
			return;
		}
		Entry<K, V> r = p.right;
		p.right = r.left;
		if (r.left != null) {
			r.left.parent = p;
		}
		r.parent = p.parent;
		if (p.parent == null) {
			root = r;
		} else if (p.parent.left == p) {
			p.parent.left = r;
		} else {
			p.parent.right = r;
		}
		r.left = p;
		p.parent = r;
	}

	private void rotateRight(Entry<K, V> p) {
		if (p == null) {
			return;
		}
		Entry<K, V> l = p.left;
		p.left = l.right;
		if (l.right != null) {
			l.right.parent = p;
		}
		l.parent = p.parent;
		if (p.parent == null) {
			root = l;
		} else if (p.parent.right == p) {
			p.parent.right = l;
		} else {
			p.parent.left = l;
		}
		l.right = p;
		p.parent = l;
	}

	private void fixAfterInsertion(Entry<K, V> x) {
		x.color = RED;
		while (x != null && x != root && x.parent.color == RED) {
			Entry<K, V> grandparent = parentOf(parentOf(x));
			if (parentOf(x) == leftOf(grandparent)) {
				Entry<K, V> uncle = rightOf(grandparent);
				if (colorOf(uncle) == RED) {
					setColor(parentOf(x), BLACK);
					setColor(uncle, BLACK);
					setColor(grandparent, RED);
					x = grandparent;
				} else {
					if (x == rightOf(parentOf(x))) {
						x = parentOf(x);
						rotateLeft(x);
					}
					setColor(parentOf(x), BLACK);
					setColor(parentOf(parentOf(x)), RED);
					rotateRight(parentOf(parentOf(x)));
				}
			} else {
				Entry<K, V> uncle = leftOf(grandparent);
				if (colorOf(uncle) == RED) {
					setColor(parentOf(x), BLACK);
					setColor(uncle, BLACK);
					setColor(grandparent, RED);
					x = grandparent;
				} else {
					if (x == leftOf(parentOf(x))) {
						x = parentOf(x);
						rotateRight(x);
					}
					setColor(parentOf(x), BLACK);
					setColor(parentOf(parentOf(x)), RED);
					rotateLeft(parentOf(parentOf(x)));
				}
			}
		}
		root.color = BLACK;
	}

	/**
	 * Removes p. A node with two children takes its successor's key and
	 * value and the successor's node is unlinked instead, so iterators that
	 * hold the successor must step back to p (see View.EntryIterator).
	 */
	final void deleteEntry(Entry<K, V> p) {
		modCount++;
		size--;
		if (p.left != null && p.right != null) {
			Entry<K, V> s = successor(p);
			p.key = s.key;
			p.value = s.value;
			p = s;
		}
		Entry<K, V> replacement = p.left != null ? p.left : p.right;
		if (replacement != null) {
			replacement.parent = p.parent;
			if (p.parent == null) {
				root = replacement;
			} else if (p == p.parent.left) {
				p.parent.left = replacement;
			} else {
				p.parent.right = replacement;
			}
			p.left = p.right = p.parent = null;
			if (p.color == BLACK) {
				fixAfterDeletion(replacement);
			}
		} else if (p.parent == null) {
			root = null;
		} else {
			if (p.color == BLACK) {
				fixAfterDeletion(p);
			}
			if (p.parent != null) {
				if (p == p.parent.left) {
					p.parent.left = null;
				} else if (p == p.parent.right) {
					p.parent.right = null;
				}
				p.parent = null;
			}
		}
	}

	private void fixAfterDeletion(Entry<K, V> x) {
		while (x != root && colorOf(x) == BLACK) {
			if (x == leftOf(parentOf(x))) {
				Entry<K, V> sibling = rightOf(parentOf(x));
				if (colorOf(sibling) == RED) {
					setColor(sibling, BLACK);
					setColor(parentOf(x), RED);
					rotateLeft(parentOf(x));
					sibling = rightOf(parentOf(x));
				}
				if (colorOf(leftOf(sibling)) == BLACK && colorOf(rightOf(sibling)) == BLACK) {
					setColor(sibling, RED);
					x = parentOf(x);
				} else {
					if (colorOf(rightOf(sibling)) == BLACK) {
						setColor(leftOf(sibling), BLACK);
						setColor(sibling, RED);
						rotateRight(sibling);
						sibling = rightOf(parentOf(x));
					}
					setColor(sibling, colorOf(parentOf(x)));
					setColor(parentOf(x), BLACK);
					setColor(rightOf(sibling), BLACK);
					rotateLeft(parentOf(x));
					x = root;
				}
			} else {
				Entry<K, V> sibling = leftOf(parentOf(x));
				if (colorOf(sibling) == RED) {
					setColor(sibling, BLACK);
					setColor(parentOf(x), RED);
					rotateRight(parentOf(x));
					sibling = leftOf(parentOf(x));
				}
				if (colorOf(rightOf(sibling)) == BLACK && colorOf(leftOf(sibling)) == BLACK) {
					setColor(sibling, RED);
					x = parentOf(x);
				} else {
					if (colorOf(leftOf(sibling)) == BLACK) {
						setColor(rightOf(sibling), BLACK);
						setColor(sibling, RED);
						rotateLeft(sibling);
						sibling = leftOf(parentOf(x));
					}
					setColor(sibling, colorOf(parentOf(x)));
					setColor(parentOf(x), BLACK);
					setColor(leftOf(sibling), BLACK);
					rotateRight(parentOf(x));
					x = root;
				}
			}
		}
		setColor(x, BLACK);
	}

	/**
	 * A range of the map, in ascending or descending order. Bounds are in
	 * the map's own (ascending) order; fromStart and toEnd mean unbounded.
	 */
	static final class View<K, V> extends AbstractMap<K, V> implements NavigableMap<K, V>, java.io.Serializable {
		final TreeMap<K, V> m;
		final boolean fromStart;
		final K lo;
		final boolean loInclusive;
		final boolean toEnd;
		final K hi;
		final boolean hiInclusive;
		final boolean descending;

		View(TreeMap<K, V> m, boolean fromStart, K lo, boolean loInclusive, boolean toEnd, K hi, boolean hiInclusive,
				boolean descending) {
			this.m = m;
			this.fromStart = fromStart;
			this.lo = lo;
			this.loInclusive = loInclusive;
			this.toEnd = toEnd;
			this.hi = hi;
			this.hiInclusive = hiInclusive;
			this.descending = descending;
		}

		final boolean tooLow(Object key) {
			if (fromStart) {
				return false;
			}
			int c = m.compare(key, lo);
			return c < 0 || (c == 0 && !loInclusive);
		}

		final boolean tooHigh(Object key) {
			if (toEnd) {
				return false;
			}
			int c = m.compare(key, hi);
			return c > 0 || (c == 0 && !hiInclusive);
		}

		final boolean inRange(Object key) {
			return !tooLow(key) && !tooHigh(key);
		}

		/** True if key is within the range or on an exclusive bound */
		final boolean inClosedRange(Object key) {
			return (fromStart || m.compare(key, lo) >= 0) && (toEnd || m.compare(hi, key) >= 0);
		}

		final TreeMap.Entry<K, V> absLowest() {
			TreeMap.Entry<K, V> e = fromStart ? m.getFirstEntry() : m.getCeilingEntry(lo, loInclusive);
			return e == null || tooHigh(e.key) ? null : e;
		}

		final TreeMap.Entry<K, V> absHighest() {
			TreeMap.Entry<K, V> e = toEnd ? m.getLastEntry() : m.getFloorEntry(hi, hiInclusive);
			return e == null || tooLow(e.key) ? null : e;
		}

		final TreeMap.Entry<K, V> absCeiling(K key, boolean inclusive) {
			if (tooLow(key)) {
				return absLowest();
			}
			TreeMap.Entry<K, V> e = m.getCeilingEntry(key, inclusive);
			return e == null || tooHigh(e.key) ? null : e;
		}

		final TreeMap.Entry<K, V> absFloor(K key, boolean inclusive) {
			if (tooHigh(key)) {
				return absHighest();
			}
			TreeMap.Entry<K, V> e = m.getFloorEntry(key, inclusive);
			return e == null || tooLow(e.key) ? null : e;
		}

		// Navigation in this view's order

		final TreeMap.Entry<K, V> first() {
			return descending ? absHighest() : absLowest();
		}

		final TreeMap.Entry<K, V> last() {
			return descending ? absLowest() : absHighest();
		}

		final TreeMap.Entry<K, V> ceiling(K key, boolean inclusive) {
			return descending ? absFloor(key, inclusive) : absCeiling(key, inclusive);
		}

		final TreeMap.Entry<K, V> floor(K key, boolean inclusive) {
			return descending ? absCeiling(key, inclusive) : absFloor(key, inclusive);
		}

		final TreeMap.Entry<K, V> next(TreeMap.Entry<K, V> e) {
			TreeMap.Entry<K, V> n = descending ? predecessor(e) : successor(e);
			return n == null || !inRange(n.key) ? null : n;
		}

		public boolean isEmpty() {
			return first() == null;
		}

		public int size() {
			if (fromStart && toEnd) {
				return m.size();
			}
			int n = 0;
			for (TreeMap.Entry<K, V> e = first(); e != null; e = next(e)) {
				n++;
			}
			return n;
		}

		public boolean containsKey(Object key) {
			return inRange(key) && m.containsKey(key);
		}

		public V get(Object key) {
			return inRange(key) ? m.get(key) : null;
		}

		public V put(K key, V value) {
			if (!inRange(key)) {
				throw new IllegalArgumentException("key out of range");
			}
			return m.put(key, value);
		}

		public V remove(Object key) {
			return inRange(key) ? m.remove(key) : null;
		}

		public Comparator<? super K> comparator() {
			Comparator<? super K> c = m.comparator();
			return descending ? Collections.reverseOrder(c) : c;
		}

		public K firstKey() {
			return key(first());
		}

		public K lastKey() {
			return key(last());
		}

		public Map.Entry<K, V> firstEntry() {
			return exportEntry(first());
		}

		public Map.Entry<K, V> lastEntry() {
			return exportEntry(last());
		}

		public Map.Entry<K, V> pollFirstEntry() {
			TreeMap.Entry<K, V> e = first();
			Map.Entry<K, V> result = exportEntry(e);
			if (e != null) {
				m.deleteEntry(e);
			}
			return result;
		}

		public Map.Entry<K, V> pollLastEntry() {
			TreeMap.Entry<K, V> e = last();
			Map.Entry<K, V> result = exportEntry(e);
			if (e != null) {
				m.deleteEntry(e);
			}
			return result;
		}

		public Map.Entry<K, V> lowerEntry(K key) {
			return exportEntry(floor(key, false));
		}

		public K lowerKey(K key) {
			return keyOrNull(floor(key, false));
		}

		public Map.Entry<K, V> floorEntry(K key) {
			return exportEntry(floor(key, true));
		}

		public K floorKey(K key) {
			return keyOrNull(floor(key, true));
		}

		public Map.Entry<K, V> ceilingEntry(K key) {
			return exportEntry(ceiling(key, true));
		}

		public K ceilingKey(K key) {
			return keyOrNull(ceiling(key, true));
		}

		public Map.Entry<K, V> higherEntry(K key) {
			return exportEntry(ceiling(key, false));
		}

		public K higherKey(K key) {
			return keyOrNull(ceiling(key, false));
		}

		public Set<K> keySet() {
			return navigableKeySet();
		}

		public NavigableSet<K> navigableKeySet() {
			return TreeSet.keySetOf(this);
		}

		public NavigableSet<K> descendingKeySet() {
			return descendingMap().navigableKeySet();
		}

		public NavigableMap<K, V> descendingMap() {
			return new View<K, V>(m, fromStart, lo, loInclusive, toEnd, hi, hiInclusive, !descending);
		}

		/** A narrower view: the bounds are in the map's order */
		private View<K, V> narrow(boolean newFromStart, K newLo, boolean newLoInclusive, boolean newToEnd, K newHi,
				boolean newHiInclusive) {
			if (!newFromStart && !inClosedRange(newLo)) {
				throw new IllegalArgumentException("fromKey out of range");
			}
			if (!newToEnd && !inClosedRange(newHi)) {
				throw new IllegalArgumentException("toKey out of range");
			}
			if (newFromStart) {
				newLo = lo;
				newLoInclusive = loInclusive;
				newFromStart = fromStart;
			}
			if (newToEnd) {
				newHi = hi;
				newHiInclusive = hiInclusive;
				newToEnd = toEnd;
			}
			return new View<K, V>(m, newFromStart, newLo, newLoInclusive, newToEnd, newHi, newHiInclusive, descending);
		}

		public NavigableMap<K, V> subMap(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
			if (descending) {
				return narrow(false, toKey, toInclusive, false, fromKey, fromInclusive);
			}
			return narrow(false, fromKey, fromInclusive, false, toKey, toInclusive);
		}

		public NavigableMap<K, V> headMap(K toKey, boolean inclusive) {
			if (descending) {
				return narrow(false, toKey, inclusive, true, null, true);
			}
			return narrow(true, null, true, false, toKey, inclusive);
		}

		public NavigableMap<K, V> tailMap(K fromKey, boolean inclusive) {
			if (descending) {
				return narrow(true, null, true, false, fromKey, inclusive);
			}
			return narrow(false, fromKey, inclusive, true, null, true);
		}

		public SortedMap<K, V> subMap(K fromKey, K toKey) {
			return subMap(fromKey, true, toKey, false);
		}

		public SortedMap<K, V> headMap(K toKey) {
			return headMap(toKey, false);
		}

		public SortedMap<K, V> tailMap(K fromKey) {
			return tailMap(fromKey, true);
		}

		public Set<Map.Entry<K, V>> entrySet() {
			return new EntrySet();
		}

		final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
			public Iterator<Map.Entry<K, V>> iterator() {
				return new EntryIterator();
			}

			public int size() {
				return View.this.size();
			}

			public boolean isEmpty() {
				return View.this.isEmpty();
			}

			public boolean contains(Object o) {
				if (!(o instanceof Map.Entry)) {
					return false;
				}
				Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
				Object key = entry.getKey();
				if (!inRange(key)) {
					return false;
				}
				TreeMap.Entry<K, V> node = m.getEntry(key);
				return node != null && Objects.equals(node.value, entry.getValue());
			}

			public boolean remove(Object o) {
				if (!contains(o)) {
					return false;
				}
				m.deleteEntry(m.getEntry(((Map.Entry<?, ?>) o).getKey()));
				return true;
			}

			public void clear() {
				if (fromStart && toEnd) {
					m.clear();
				} else {
					super.clear();
				}
			}
		}

		final class EntryIterator implements Iterator<Map.Entry<K, V>> {
			TreeMap.Entry<K, V> next = first();
			TreeMap.Entry<K, V> lastReturned;
			int expectedModCount = m.modCount;

			public boolean hasNext() {
				return next != null;
			}

			public Map.Entry<K, V> next() {
				TreeMap.Entry<K, V> e = next;
				if (e == null) {
					throw new NoSuchElementException();
				}
				if (m.modCount != expectedModCount) {
					throw new ConcurrentModificationException();
				}
				next = View.this.next(e);
				lastReturned = e;
				return e;
			}

			public void remove() {
				if (lastReturned == null) {
					throw new IllegalStateException();
				}
				if (m.modCount != expectedModCount) {
					throw new ConcurrentModificationException();
				}
				// Deleting a node with two children moves its successor into it
				if (!descending && lastReturned.left != null && lastReturned.right != null) {
					next = lastReturned;
				}
				m.deleteEntry(lastReturned);
				expectedModCount = m.modCount;
				lastReturned = null;
			}
		}
	}
}
