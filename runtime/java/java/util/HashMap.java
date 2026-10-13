package java.util;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * HashMap, written from the Java SE specification. Programs print maps, so
 * iteration order matches the JVM's: a power-of-two table indexed by the
 * key's hash with its high bits folded in, bins in insertion order, and
 * resizing at a load factor of 0.75 keeps that order within each bin. The
 * JVM turns bins of 8 or more colliding keys into trees, which can reorder
 * them; this implementation keeps lists.
 */
public class HashMap<K, V> extends AbstractMap<K, V> implements Map<K, V>, Cloneable, java.io.Serializable {
	static final int DEFAULT_INITIAL_CAPACITY = 16;
	static final int MAXIMUM_CAPACITY = 1 << 30;
	static final float DEFAULT_LOAD_FACTOR = 0.75f;

	static class Node<K, V> implements Map.Entry<K, V> {
		final int hash;
		final K key;
		V value;
		Node<K, V> next;

		Node(int hash, K key, V value, Node<K, V> next) {
			this.hash = hash;
			this.key = key;
			this.value = value;
			this.next = next;
		}

		public final K getKey() {
			return key;
		}

		public final V getValue() {
			return value;
		}

		public final V setValue(V newValue) {
			V oldValue = value;
			value = newValue;
			return oldValue;
		}

		public final boolean equals(Object o) {
			return AbstractMap.entryEquals(this, o);
		}

		public final int hashCode() {
			return Objects.hashCode(key) ^ Objects.hashCode(value);
		}

		public final String toString() {
			return key + "=" + value;
		}
	}

	static final int hash(Object key) {
		int h;
		return key == null ? 0 : (h = key.hashCode()) ^ (h >>> 16);
	}

	static final int tableSizeFor(int cap) {
		int n = 1;
		while (n < cap && n < MAXIMUM_CAPACITY) {
			n <<= 1;
		}
		return n;
	}

	Node<K, V>[] table;
	int size;
	int modCount;
	/** The size that triggers a resize; before the table exists, its capacity (0 for the default) */
	int threshold;
	final float loadFactor;

	public HashMap(int initialCapacity, float loadFactor) {
		if (initialCapacity < 0) {
			throw new IllegalArgumentException("Illegal initial capacity: " + initialCapacity);
		}
		if (!(loadFactor > 0)) {
			throw new IllegalArgumentException("Illegal load factor: " + loadFactor);
		}
		this.loadFactor = loadFactor;
		this.threshold = tableSizeFor(Math.min(initialCapacity, MAXIMUM_CAPACITY));
	}

	public HashMap(int initialCapacity) {
		this(initialCapacity, DEFAULT_LOAD_FACTOR);
	}

	public HashMap() {
		this.loadFactor = DEFAULT_LOAD_FACTOR;
	}

	public HashMap(Map<? extends K, ? extends V> m) {
		this.loadFactor = DEFAULT_LOAD_FACTOR;
		putMapEntries(m);
	}

	final void putMapEntries(Map<? extends K, ? extends V> m) {
		int s = m.size();
		if (s > 0) {
			if (table == null) {
				float ft = ((float) s / loadFactor) + 1.0F;
				int t = ft < (float) MAXIMUM_CAPACITY ? (int) ft : MAXIMUM_CAPACITY;
				if (t > threshold) {
					threshold = tableSizeFor(t);
				}
			} else {
				while (s > threshold && table.length < MAXIMUM_CAPACITY) {
					resize();
				}
			}
			for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
				putVal(hash(e.getKey()), e.getKey(), e.getValue(), false);
			}
		}
	}

	public int size() {
		return size;
	}

	public boolean isEmpty() {
		return size == 0;
	}

	public V get(Object key) {
		Node<K, V> e = getNode(key);
		return e == null ? null : e.value;
	}

	final Node<K, V> getNode(Object key) {
		Node<K, V>[] tab = table;
		if (tab == null) {
			return null;
		}
		int h = hash(key);
		for (Node<K, V> e = tab[(tab.length - 1) & h]; e != null; e = e.next) {
			if (e.hash == h && Objects.equals(key, e.key)) {
				return e;
			}
		}
		return null;
	}

	public boolean containsKey(Object key) {
		return getNode(key) != null;
	}

	public V put(K key, V value) {
		return putVal(hash(key), key, value, false);
	}

	final V putVal(int hash, K key, V value, boolean onlyIfAbsent) {
		Node<K, V>[] tab = table;
		if (tab == null || tab.length == 0) {
			tab = resize();
		}
		int i = (tab.length - 1) & hash;
		Node<K, V> last = null;
		for (Node<K, V> e = tab[i]; e != null; e = e.next) {
			if (e.hash == hash && Objects.equals(key, e.key)) {
				V oldValue = e.value;
				if (!onlyIfAbsent || oldValue == null) {
					e.value = value;
				}
				afterNodeAccess(e);
				return oldValue;
			}
			last = e;
		}
		Node<K, V> node = newNode(hash, key, value, null);
		if (last == null) {
			tab[i] = node;
		} else {
			last.next = node;
		}
		modCount++;
		if (++size > threshold) {
			resize();
		}
		afterNodeInsertion();
		return null;
	}

	@SuppressWarnings("unchecked")
	final Node<K, V>[] resize() {
		Node<K, V>[] oldTab = table;
		int oldCap = oldTab == null ? 0 : oldTab.length;
		int newCap;
		if (oldCap > 0) {
			if (oldCap >= MAXIMUM_CAPACITY) {
				threshold = Integer.MAX_VALUE;
				return oldTab;
			}
			newCap = oldCap << 1;
		} else if (threshold > 0) {
			newCap = threshold;
		} else {
			newCap = DEFAULT_INITIAL_CAPACITY;
		}
		float ft = (float) newCap * loadFactor;
		threshold = newCap < MAXIMUM_CAPACITY && ft < (float) MAXIMUM_CAPACITY ? (int) ft : Integer.MAX_VALUE;
		Node<K, V>[] newTab = (Node<K, V>[]) new Node[newCap];
		table = newTab;
		if (oldTab != null) {
			for (int j = 0; j < oldCap; j++) {
				// Each bin splits into the same index and index + oldCap, in order
				Node<K, V> loHead = null, loTail = null, hiHead = null, hiTail = null;
				for (Node<K, V> e = oldTab[j]; e != null; e = e.next) {
					if ((e.hash & oldCap) == 0) {
						if (loTail == null) {
							loHead = e;
						} else {
							loTail.next = e;
						}
						loTail = e;
					} else {
						if (hiTail == null) {
							hiHead = e;
						} else {
							hiTail.next = e;
						}
						hiTail = e;
					}
				}
				if (loTail != null) {
					loTail.next = null;
					newTab[j] = loHead;
				}
				if (hiTail != null) {
					hiTail.next = null;
					newTab[j + oldCap] = hiHead;
				}
			}
		}
		return newTab;
	}

	public void putAll(Map<? extends K, ? extends V> m) {
		putMapEntries(m);
	}

	public V remove(Object key) {
		Node<K, V> e = removeNode(hash(key), key);
		return e == null ? null : e.value;
	}

	final Node<K, V> removeNode(int hash, Object key) {
		Node<K, V>[] tab = table;
		if (tab == null) {
			return null;
		}
		int i = (tab.length - 1) & hash;
		Node<K, V> prev = null;
		for (Node<K, V> e = tab[i]; e != null; prev = e, e = e.next) {
			if (e.hash == hash && Objects.equals(key, e.key)) {
				if (prev == null) {
					tab[i] = e.next;
				} else {
					prev.next = e.next;
				}
				modCount++;
				size--;
				afterNodeRemoval(e);
				return e;
			}
		}
		return null;
	}

	public void clear() {
		modCount++;
		if (table != null && size > 0) {
			size = 0;
			for (int i = 0; i < table.length; i++) {
				table[i] = null;
			}
		}
		afterClear();
	}

	public boolean containsValue(Object value) {
		for (Node<K, V> e = firstNode(); e != null; e = nextNode(e)) {
			if (Objects.equals(value, e.value)) {
				return true;
			}
		}
		return false;
	}

	public V getOrDefault(Object key, V defaultValue) {
		Node<K, V> e = getNode(key);
		return e == null ? defaultValue : e.value;
	}

	public V putIfAbsent(K key, V value) {
		return putVal(hash(key), key, value, true);
	}

	/*
	 * computeIfAbsent, compute and merge insert a new key at the head of its
	 * bin, after resizing if the map was already over its threshold, where
	 * put appends after resizing; iteration order shows the difference.
	 */

	/** The table for an insertion by computeIfAbsent, compute or merge */
	private Node<K, V>[] tableForCompute() {
		Node<K, V>[] tab = table;
		if (size > threshold || tab == null || tab.length == 0) {
			tab = resize();
		}
		return tab;
	}

	private Node<K, V> find(Node<K, V> first, int hash, Object key) {
		for (Node<K, V> e = first; e != null; e = e.next) {
			if (e.hash == hash && Objects.equals(key, e.key)) {
				return e;
			}
		}
		return null;
	}

	private void insertFirst(Node<K, V>[] tab, int i, int hash, K key, V value, int expectedModCount) {
		tab[i] = newNode(hash, key, value, tab[i]);
		modCount = expectedModCount + 1;
		size++;
		afterNodeInsertion();
	}

	public V computeIfAbsent(K key, java.util.function.Function<? super K, ? extends V> mappingFunction) {
		Objects.requireNonNull(mappingFunction);
		int hash = hash(key);
		Node<K, V>[] tab = tableForCompute();
		int i = (tab.length - 1) & hash;
		Node<K, V> old = find(tab[i], hash, key);
		if (old != null && old.value != null) {
			afterNodeAccess(old);
			return old.value;
		}
		int mc = modCount;
		V v = mappingFunction.apply(key);
		if (mc != modCount) {
			throw new ConcurrentModificationException();
		}
		if (v == null) {
			return null;
		}
		if (old != null) {
			old.value = v;
			afterNodeAccess(old);
			return v;
		}
		insertFirst(tab, i, hash, key, v, mc);
		return v;
	}

	public V compute(K key, java.util.function.BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		Objects.requireNonNull(remappingFunction);
		int hash = hash(key);
		Node<K, V>[] tab = tableForCompute();
		int i = (tab.length - 1) & hash;
		Node<K, V> old = find(tab[i], hash, key);
		V oldValue = old == null ? null : old.value;
		int mc = modCount;
		V v = remappingFunction.apply(key, oldValue);
		if (mc != modCount) {
			throw new ConcurrentModificationException();
		}
		if (old != null) {
			if (v != null) {
				old.value = v;
				afterNodeAccess(old);
			} else {
				removeNode(hash, key);
			}
		} else if (v != null) {
			insertFirst(tab, i, hash, key, v, mc);
		}
		return v;
	}

	public V merge(K key, V value, java.util.function.BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		Objects.requireNonNull(value);
		Objects.requireNonNull(remappingFunction);
		int hash = hash(key);
		Node<K, V>[] tab = tableForCompute();
		int i = (tab.length - 1) & hash;
		Node<K, V> old = find(tab[i], hash, key);
		if (old != null) {
			V v;
			if (old.value != null) {
				int mc = modCount;
				v = remappingFunction.apply(old.value, value);
				if (mc != modCount) {
					throw new ConcurrentModificationException();
				}
			} else {
				v = value;
			}
			if (v != null) {
				old.value = v;
				afterNodeAccess(old);
			} else {
				removeNode(hash, key);
			}
			return v;
		}
		insertFirst(tab, i, hash, key, value, modCount);
		return value;
	}

	public void forEach(BiConsumer<? super K, ? super V> action) {
		Objects.requireNonNull(action);
		int mc = modCount;
		for (Node<K, V> e = firstNode(); e != null; e = nextNode(e)) {
			action.accept(e.key, e.value);
			if (modCount != mc) {
				throw new ConcurrentModificationException();
			}
		}
	}

	@SuppressWarnings("unchecked")
	public Object clone() {
		HashMap<K, V> result = new HashMap<K, V>();
		result.putMapEntries(this);
		return result;
	}

	// Hooks for LinkedHashMap

	Node<K, V> newNode(int hash, K key, V value, Node<K, V> next) {
		return new Node<K, V>(hash, key, value, next);
	}

	void afterNodeAccess(Node<K, V> e) {
	}

	void afterNodeInsertion() {
	}

	void afterNodeRemoval(Node<K, V> e) {
	}

	void afterClear() {
	}

	/** The first node in iteration order */
	Node<K, V> firstNode() {
		Node<K, V>[] tab = table;
		if (tab == null || size == 0) {
			return null;
		}
		for (int i = 0; i < tab.length; i++) {
			if (tab[i] != null) {
				return tab[i];
			}
		}
		return null;
	}

	/** The node after e in iteration order */
	Node<K, V> nextNode(Node<K, V> e) {
		if (e.next != null) {
			return e.next;
		}
		Node<K, V>[] tab = table;
		for (int i = ((tab.length - 1) & e.hash) + 1; i < tab.length; i++) {
			if (tab[i] != null) {
				return tab[i];
			}
		}
		return null;
	}

	public Set<K> keySet() {
		return new KeySet();
	}

	public Collection<V> values() {
		return new Values();
	}

	public Set<Map.Entry<K, V>> entrySet() {
		return new EntrySet();
	}

	abstract class HashIterator {
		Node<K, V> next;
		Node<K, V> current;
		int expectedModCount;

		HashIterator() {
			expectedModCount = modCount;
			next = firstNode();
		}

		public final boolean hasNext() {
			return next != null;
		}

		final Node<K, V> nextEntry() {
			if (modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}
			Node<K, V> e = next;
			if (e == null) {
				throw new NoSuchElementException();
			}
			current = e;
			next = nextNode(e);
			return e;
		}

		public final void remove() {
			Node<K, V> p = current;
			if (p == null) {
				throw new IllegalStateException();
			}
			if (modCount != expectedModCount) {
				throw new ConcurrentModificationException();
			}
			current = null;
			removeNode(p.hash, p.key);
			expectedModCount = modCount;
		}
	}

	final class KeyIterator extends HashIterator implements Iterator<K> {
		public K next() {
			return nextEntry().key;
		}
	}

	final class ValueIterator extends HashIterator implements Iterator<V> {
		public V next() {
			return nextEntry().value;
		}
	}

	final class EntryIterator extends HashIterator implements Iterator<Map.Entry<K, V>> {
		public Map.Entry<K, V> next() {
			return nextEntry();
		}
	}

	final class KeySet extends AbstractSet<K> {
		public int size() {
			return size;
		}

		public void clear() {
			HashMap.this.clear();
		}

		public Iterator<K> iterator() {
			return new KeyIterator();
		}

		public boolean contains(Object o) {
			return containsKey(o);
		}

		public boolean remove(Object key) {
			return removeNode(hash(key), key) != null;
		}

		public void forEach(Consumer<? super K> action) {
			Objects.requireNonNull(action);
			for (Node<K, V> e = firstNode(); e != null; e = nextNode(e)) {
				action.accept(e.key);
			}
		}
	}

	final class Values extends AbstractCollection<V> {
		public int size() {
			return size;
		}

		public void clear() {
			HashMap.this.clear();
		}

		public Iterator<V> iterator() {
			return new ValueIterator();
		}

		public boolean contains(Object o) {
			return containsValue(o);
		}
	}

	final class EntrySet extends AbstractSet<Map.Entry<K, V>> {
		public int size() {
			return size;
		}

		public void clear() {
			HashMap.this.clear();
		}

		public Iterator<Map.Entry<K, V>> iterator() {
			return new EntryIterator();
		}

		public boolean contains(Object o) {
			if (!(o instanceof Map.Entry)) {
				return false;
			}
			Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
			Node<K, V> candidate = getNode(e.getKey());
			return candidate != null && candidate.equals(e);
		}

		public boolean remove(Object o) {
			if (contains(o)) {
				Object key = ((Map.Entry<?, ?>) o).getKey();
				return removeNode(hash(key), key) != null;
			}
			return false;
		}
	}
}
