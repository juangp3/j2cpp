package java.util.concurrent;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * ConcurrentHashMap, written from the Java SE specification. One lock (a
 * private monitor) guards the whole table instead of the JDK's per-bin
 * locks, so threads do not update it in parallel; the compute methods run
 * their functions under it, so they are atomic, as in the JDK.
 *
 * The table grows as the JDK's does (initial sizes, the 3/4 threshold
 * reached rather than exceeded, and the order in which a resize splits a
 * bin), so a printed map lists its entries in the JVM's order. Iterators and
 * views are weakly consistent: an iterator walks a snapshot taken when it was
 * created and never throws ConcurrentModificationException. Null keys and
 * values throw NullPointerException.
 *
 * Absent: the bulk parallel operations (forEach, search and reduce with a
 * parallelism threshold), keys(), elements() and contains(Object).
 */
public class ConcurrentHashMap<K, V> extends AbstractMap<K, V> implements ConcurrentMap<K, V>, java.io.Serializable {
	private static final int MAXIMUM_CAPACITY = 1 << 30;
	private static final int DEFAULT_CAPACITY = 16;

	static final class Node<K, V> {
		final int hash;
		final K key;
		V val;
		Node<K, V> next;

		Node(int hash, K key, V val, Node<K, V> next) {
			this.hash = hash;
			this.key = key;
			this.val = val;
			this.next = next;
		}
	}

	private final Object lock = new Object();
	/** Guarded by lock */
	private Node<K, V>[] table;
	private int size;
	/** Before the table exists, its capacity (0 for the default); then the size that triggers a resize */
	private int sizeCtl;

	public ConcurrentHashMap() {
	}

	public ConcurrentHashMap(int initialCapacity) {
		this(initialCapacity, 0.75f, 1);
	}

	public ConcurrentHashMap(Map<? extends K, ? extends V> m) {
		this.sizeCtl = DEFAULT_CAPACITY;
		putAll(m);
	}

	public ConcurrentHashMap(int initialCapacity, float loadFactor) {
		this(initialCapacity, loadFactor, 1);
	}

	public ConcurrentHashMap(int initialCapacity, float loadFactor, int concurrencyLevel) {
		if (!(loadFactor > 0.0f) || initialCapacity < 0 || concurrencyLevel <= 0) {
			throw new IllegalArgumentException();
		}
		if (initialCapacity < concurrencyLevel) {
			initialCapacity = concurrencyLevel;
		}
		long size = (long) (1.0 + (long) initialCapacity / loadFactor);
		this.sizeCtl = size >= (long) MAXIMUM_CAPACITY ? MAXIMUM_CAPACITY : tableSizeFor((int) size);
	}

	public static <K> KeySetView<K, Boolean> newKeySet() {
		return new KeySetView<K, Boolean>(new ConcurrentHashMap<K, Boolean>(), Boolean.TRUE);
	}

	public static <K> KeySetView<K, Boolean> newKeySet(int initialCapacity) {
		return new KeySetView<K, Boolean>(new ConcurrentHashMap<K, Boolean>(initialCapacity), Boolean.TRUE);
	}

	static int spread(int h) {
		return (h ^ (h >>> 16)) & 0x7fffffff;
	}

	private static int tableSizeFor(int c) {
		int n = c - 1;
		n |= n >>> 1;
		n |= n >>> 2;
		n |= n >>> 4;
		n |= n >>> 8;
		n |= n >>> 16;
		return n < 0 ? 1 : n >= MAXIMUM_CAPACITY ? MAXIMUM_CAPACITY : n + 1;
	}

	@SuppressWarnings("unchecked")
	private Node<K, V>[] newTable(int n) {
		return (Node<K, V>[]) new Node<?, ?>[n];
	}

	/** With lock held */
	private Node<K, V>[] initTable() {
		if (table == null) {
			int n = sizeCtl > 0 ? sizeCtl : DEFAULT_CAPACITY;
			table = newTable(n);
			sizeCtl = n - (n >>> 2);
		}
		return table;
	}

	/** Doubles the table, splitting each bin as the JDK's transfer does; with lock held */
	private void transfer() {
		Node<K, V>[] tab = table;
		int n = tab.length;
		Node<K, V>[] nextTab = newTable(n << 1);
		for (int i = n - 1; i >= 0; i--) {
			Node<K, V> f = tab[i];
			if (f == null) {
				continue;
			}
			// The tail that lands in one half keeps its nodes; the nodes before
			// it are copied to the front of their half, reversing them
			int runBit = f.hash & n;
			Node<K, V> lastRun = f;
			for (Node<K, V> p = f.next; p != null; p = p.next) {
				int b = p.hash & n;
				if (b != runBit) {
					runBit = b;
					lastRun = p;
				}
			}
			Node<K, V> ln = runBit == 0 ? lastRun : null;
			Node<K, V> hn = runBit == 0 ? null : lastRun;
			for (Node<K, V> p = f; p != lastRun; p = p.next) {
				if ((p.hash & n) == 0) {
					ln = new Node<K, V>(p.hash, p.key, p.val, ln);
				} else {
					hn = new Node<K, V>(p.hash, p.key, p.val, hn);
				}
			}
			nextTab[i] = ln;
			nextTab[i + n] = hn;
		}
		table = nextTab;
		sizeCtl = (n << 1) - (n >>> 1);
	}

	/** With lock held */
	private void addCount() {
		size++;
		while (size >= sizeCtl && table.length < MAXIMUM_CAPACITY) {
			transfer();
		}
	}

	/** Grows the table for size more entries, as putAll does; with lock held */
	private void tryPresize(int size) {
		int c = size >= (MAXIMUM_CAPACITY >>> 1) ? MAXIMUM_CAPACITY : tableSizeFor(size + (size >>> 1) + 1);
		while (true) {
			if (table == null) {
				int n = sizeCtl > c ? sizeCtl : c;
				table = newTable(n);
				sizeCtl = n - (n >>> 2);
			} else if (c <= sizeCtl || table.length >= MAXIMUM_CAPACITY) {
				return;
			} else {
				transfer();
			}
		}
	}

	/** With lock held */
	private Node<K, V> findNode(Object key) {
		Node<K, V>[] tab = table;
		if (tab == null) {
			return null;
		}
		int h = spread(key.hashCode());
		for (Node<K, V> e = tab[(tab.length - 1) & h]; e != null; e = e.next) {
			if (e.hash == h && (e.key == key || key.equals(e.key))) {
				return e;
			}
		}
		return null;
	}

	/** With lock held */
	private V putVal(K key, V value, boolean onlyIfAbsent) {
		if (key == null || value == null) {
			throw new NullPointerException();
		}
		int hash = spread(key.hashCode());
		Node<K, V>[] tab = table == null ? initTable() : table;
		int i = (tab.length - 1) & hash;
		Node<K, V> e = tab[i];
		if (e == null) {
			tab[i] = new Node<K, V>(hash, key, value, null);
		} else {
			while (true) {
				if (e.hash == hash && (e.key == key || key.equals(e.key))) {
					V old = e.val;
					if (!onlyIfAbsent) {
						e.val = value;
					}
					return old;
				}
				if (e.next == null) {
					e.next = new Node<K, V>(hash, key, value, null);
					break;
				}
				e = e.next;
			}
		}
		addCount();
		return null;
	}

	/** Removes key's node; with lock held */
	private V removeNode(Object key) {
		Node<K, V>[] tab = table;
		if (tab == null) {
			return null;
		}
		int h = spread(key.hashCode());
		int i = (tab.length - 1) & h;
		Node<K, V> previous = null;
		for (Node<K, V> e = tab[i]; e != null; previous = e, e = e.next) {
			if (e.hash == h && (e.key == key || key.equals(e.key))) {
				if (previous == null) {
					tab[i] = e.next;
				} else {
					previous.next = e.next;
				}
				size--;
				return e.val;
			}
		}
		return null;
	}

	/** The nodes in table order, at this moment */
	private ArrayList<Node<K, V>> snapshot() {
		synchronized (lock) {
			ArrayList<Node<K, V>> nodes = new ArrayList<Node<K, V>>(size);
			if (table != null) {
				for (Node<K, V> f : table) {
					for (Node<K, V> e = f; e != null; e = e.next) {
						nodes.add(e);
					}
				}
			}
			return nodes;
		}
	}

	public int size() {
		synchronized (lock) {
			return size;
		}
	}

	public long mappingCount() {
		return size();
	}

	public boolean isEmpty() {
		return size() == 0;
	}

	public V get(Object key) {
		if (key == null) {
			throw new NullPointerException();
		}
		synchronized (lock) {
			Node<K, V> e = findNode(key);
			return e == null ? null : e.val;
		}
	}

	public boolean containsKey(Object key) {
		return get(key) != null;
	}

	public boolean containsValue(Object value) {
		if (value == null) {
			throw new NullPointerException();
		}
		for (Node<K, V> e : snapshot()) {
			V v = e.val;
			if (v == value || value.equals(v)) {
				return true;
			}
		}
		return false;
	}

	public V put(K key, V value) {
		synchronized (lock) {
			return putVal(key, value, false);
		}
	}

	public void putAll(Map<? extends K, ? extends V> m) {
		synchronized (lock) {
			tryPresize(m.size());
			for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
				putVal(e.getKey(), e.getValue(), false);
			}
		}
	}

	public V remove(Object key) {
		if (key == null) {
			throw new NullPointerException();
		}
		synchronized (lock) {
			return removeNode(key);
		}
	}

	public void clear() {
		synchronized (lock) {
			if (table != null) {
				for (int i = 0; i < table.length; i++) {
					table[i] = null;
				}
			}
			size = 0;
		}
	}

	public V getOrDefault(Object key, V defaultValue) {
		V v = get(key);
		return v == null ? defaultValue : v;
	}

	public V putIfAbsent(K key, V value) {
		synchronized (lock) {
			return putVal(key, value, true);
		}
	}

	public boolean remove(Object key, Object value) {
		if (key == null) {
			throw new NullPointerException();
		}
		synchronized (lock) {
			Node<K, V> e = findNode(key);
			if (value != null && e != null && (e.val == value || value.equals(e.val))) {
				removeNode(key);
				return true;
			}
			return false;
		}
	}

	public boolean replace(K key, V oldValue, V newValue) {
		if (key == null || oldValue == null || newValue == null) {
			throw new NullPointerException();
		}
		synchronized (lock) {
			Node<K, V> e = findNode(key);
			if (e != null && (e.val == oldValue || oldValue.equals(e.val))) {
				e.val = newValue;
				return true;
			}
			return false;
		}
	}

	public V replace(K key, V value) {
		if (key == null || value == null) {
			throw new NullPointerException();
		}
		synchronized (lock) {
			Node<K, V> e = findNode(key);
			if (e == null) {
				return null;
			}
			V old = e.val;
			e.val = value;
			return old;
		}
	}

	public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
		if (key == null || mappingFunction == null) {
			throw new NullPointerException();
		}
		synchronized (lock) {
			Node<K, V> e = findNode(key);
			if (e != null) {
				return e.val;
			}
			V value = mappingFunction.apply(key);
			if (value != null) {
				putVal(key, value, false);
			}
			return value;
		}
	}

	public V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		if (key == null || remappingFunction == null) {
			throw new NullPointerException();
		}
		synchronized (lock) {
			Node<K, V> e = findNode(key);
			if (e == null) {
				return null;
			}
			V value = remappingFunction.apply(key, e.val);
			if (value == null) {
				removeNode(key);
			} else {
				e.val = value;
			}
			return value;
		}
	}

	public V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
		if (key == null || remappingFunction == null) {
			throw new NullPointerException();
		}
		synchronized (lock) {
			Node<K, V> e = findNode(key);
			V value = remappingFunction.apply(key, e == null ? null : e.val);
			if (e != null) {
				if (value == null) {
					removeNode(key);
				} else {
					e.val = value;
				}
			} else if (value != null) {
				putVal(key, value, false);
			}
			return value;
		}
	}

	public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
		if (key == null || value == null || remappingFunction == null) {
			throw new NullPointerException();
		}
		synchronized (lock) {
			Node<K, V> e = findNode(key);
			if (e == null) {
				putVal(key, value, false);
				return value;
			}
			V merged = remappingFunction.apply(e.val, value);
			if (merged == null) {
				removeNode(key);
			} else {
				e.val = merged;
			}
			return merged;
		}
	}

	public void forEach(BiConsumer<? super K, ? super V> action) {
		if (action == null) {
			throw new NullPointerException();
		}
		for (Node<K, V> e : snapshot()) {
			action.accept(e.key, e.val);
		}
	}

	public void replaceAll(BiFunction<? super K, ? super V, ? extends V> function) {
		if (function == null) {
			throw new NullPointerException();
		}
		synchronized (lock) {
			for (Node<K, V> e : snapshot()) {
				V value = function.apply(e.key, e.val);
				if (value == null) {
					throw new NullPointerException();
				}
				e.val = value;
			}
		}
	}

	public KeySetView<K, V> keySet() {
		return new KeySetView<K, V>(this, null);
	}

	public KeySetView<K, V> keySet(V mappedValue) {
		if (mappedValue == null) {
			throw new NullPointerException();
		}
		return new KeySetView<K, V>(this, mappedValue);
	}

	public Collection<V> values() {
		return new ValuesView<K, V>(this);
	}

	public Set<Map.Entry<K, V>> entrySet() {
		return new EntrySetView<K, V>(this);
	}

	/** Walks a snapshot; remove() removes the last key from the map */
	private abstract static class SnapshotIterator<K, V, E> implements Iterator<E> {
		final ConcurrentHashMap<K, V> map;
		private final ArrayList<Node<K, V>> nodes;
		private int next;
		private Node<K, V> last;

		SnapshotIterator(ConcurrentHashMap<K, V> map) {
			this.map = map;
			this.nodes = map.snapshot();
		}

		public final boolean hasNext() {
			return next < nodes.size();
		}

		final Node<K, V> nextNode() {
			if (next >= nodes.size()) {
				throw new NoSuchElementException();
			}
			last = nodes.get(next++);
			return last;
		}

		public final void remove() {
			if (last == null) {
				throw new IllegalStateException();
			}
			map.remove(last.key);
			last = null;
		}
	}

	private static final class KeyIterator<K, V> extends SnapshotIterator<K, V, K> {
		KeyIterator(ConcurrentHashMap<K, V> map) {
			super(map);
		}

		public K next() {
			return nextNode().key;
		}
	}

	private static final class ValueIterator<K, V> extends SnapshotIterator<K, V, V> {
		ValueIterator(ConcurrentHashMap<K, V> map) {
			super(map);
		}

		public V next() {
			return nextNode().val;
		}
	}

	private static final class EntryIterator<K, V> extends SnapshotIterator<K, V, Map.Entry<K, V>> {
		EntryIterator(ConcurrentHashMap<K, V> map) {
			super(map);
		}

		public Map.Entry<K, V> next() {
			Node<K, V> e = nextNode();
			return new MapEntry<K, V>(e.key, e.val, map);
		}
	}

	/** An entry of an iterator: setValue writes through to the map */
	private static final class MapEntry<K, V> implements Map.Entry<K, V> {
		private final K key;
		private V val;
		private final ConcurrentHashMap<K, V> map;

		MapEntry(K key, V val, ConcurrentHashMap<K, V> map) {
			this.key = key;
			this.val = val;
			this.map = map;
		}

		public K getKey() {
			return key;
		}

		public V getValue() {
			return val;
		}

		public V setValue(V value) {
			if (value == null) {
				throw new NullPointerException();
			}
			V v = val;
			val = value;
			map.put(key, value);
			return v;
		}

		public boolean equals(Object o) {
			if (!(o instanceof Map.Entry)) {
				return false;
			}
			Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
			Object k = e.getKey();
			Object v = e.getValue();
			return k != null && v != null && (k == key || k.equals(key)) && (v == val || v.equals(val));
		}

		public int hashCode() {
			return key.hashCode() ^ val.hashCode();
		}

		public String toString() {
			return key + "=" + val;
		}
	}

	public static class KeySetView<K, V> extends AbstractSet<K> implements java.io.Serializable {
		private final ConcurrentHashMap<K, V> map;
		private final V value;

		KeySetView(ConcurrentHashMap<K, V> map, V value) {
			this.map = map;
			this.value = value;
		}

		public V getMappedValue() {
			return value;
		}

		public ConcurrentHashMap<K, V> getMap() {
			return map;
		}

		public Iterator<K> iterator() {
			return new KeyIterator<K, V>(map);
		}

		public int size() {
			return map.size();
		}

		public boolean isEmpty() {
			return map.isEmpty();
		}

		public boolean contains(Object o) {
			return map.containsKey(o);
		}

		public boolean remove(Object o) {
			return map.remove(o) != null;
		}

		public void clear() {
			map.clear();
		}

		public boolean add(K e) {
			if (value == null) {
				throw new UnsupportedOperationException();
			}
			return map.putIfAbsent(e, value) == null;
		}

		public boolean addAll(Collection<? extends K> c) {
			boolean added = false;
			for (K e : c) {
				if (add(e)) {
					added = true;
				}
			}
			return added;
		}
	}

	private static final class ValuesView<K, V> extends AbstractCollection<V> {
		private final ConcurrentHashMap<K, V> map;

		ValuesView(ConcurrentHashMap<K, V> map) {
			this.map = map;
		}

		public Iterator<V> iterator() {
			return new ValueIterator<K, V>(map);
		}

		public int size() {
			return map.size();
		}

		public boolean contains(Object o) {
			return map.containsValue(o);
		}

		public void clear() {
			map.clear();
		}

		public boolean add(V e) {
			throw new UnsupportedOperationException();
		}
	}

	private static final class EntrySetView<K, V> extends AbstractSet<Map.Entry<K, V>> {
		private final ConcurrentHashMap<K, V> map;

		EntrySetView(ConcurrentHashMap<K, V> map) {
			this.map = map;
		}

		public Iterator<Map.Entry<K, V>> iterator() {
			return new EntryIterator<K, V>(map);
		}

		public int size() {
			return map.size();
		}

		public boolean contains(Object o) {
			if (!(o instanceof Map.Entry)) {
				return false;
			}
			Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
			Object k = e.getKey();
			Object v = e.getValue();
			if (k == null || v == null) {
				return false;
			}
			Object current = map.get(k);
			return current != null && (current == v || v.equals(current));
		}

		public boolean remove(Object o) {
			if (!(o instanceof Map.Entry)) {
				return false;
			}
			Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
			Object k = e.getKey();
			Object v = e.getValue();
			return k != null && v != null && map.remove(k, v);
		}

		public void clear() {
			map.clear();
		}

		public boolean add(Map.Entry<K, V> e) {
			return map.put(e.getKey(), e.getValue()) == null;
		}
	}
}
