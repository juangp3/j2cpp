package java.util;

/**
 * LinkedHashMap, written from the Java SE specification: a HashMap whose
 * nodes are also linked in insertion order, or access order when asked.
 */
public class LinkedHashMap<K, V> extends HashMap<K, V> implements Map<K, V> {
	static class Entry<K, V> extends HashMap.Node<K, V> {
		Entry<K, V> before;
		Entry<K, V> after;

		Entry(int hash, K key, V value, HashMap.Node<K, V> next) {
			super(hash, key, value, next);
		}
	}

	transient Entry<K, V> head;
	transient Entry<K, V> tail;
	final boolean accessOrder;

	public LinkedHashMap(int initialCapacity, float loadFactor) {
		super(initialCapacity, loadFactor);
		accessOrder = false;
	}

	public LinkedHashMap(int initialCapacity) {
		super(initialCapacity);
		accessOrder = false;
	}

	public LinkedHashMap() {
		accessOrder = false;
	}

	public LinkedHashMap(Map<? extends K, ? extends V> m) {
		accessOrder = false;
		putMapEntries(m);
	}

	public LinkedHashMap(int initialCapacity, float loadFactor, boolean accessOrder) {
		super(initialCapacity, loadFactor);
		this.accessOrder = accessOrder;
	}

	protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
		return false;
	}

	HashMap.Node<K, V> newNode(int hash, K key, V value, HashMap.Node<K, V> next) {
		Entry<K, V> p = new Entry<K, V>(hash, key, value, next);
		Entry<K, V> last = tail;
		tail = p;
		if (last == null) {
			head = p;
		} else {
			p.before = last;
			last.after = p;
		}
		return p;
	}

	void afterNodeRemoval(HashMap.Node<K, V> e) {
		Entry<K, V> p = (Entry<K, V>) e;
		Entry<K, V> b = p.before;
		Entry<K, V> a = p.after;
		p.before = p.after = null;
		if (b == null) {
			head = a;
		} else {
			b.after = a;
		}
		if (a == null) {
			tail = b;
		} else {
			a.before = b;
		}
	}

	void afterNodeInsertion() {
		Entry<K, V> first = head;
		if (first != null && removeEldestEntry(first)) {
			removeNode(first.hash, first.key);
		}
	}

	void afterNodeAccess(HashMap.Node<K, V> e) {
		Entry<K, V> p = (Entry<K, V>) e;
		if (!accessOrder || tail == p) {
			return;
		}
		Entry<K, V> b = p.before;
		Entry<K, V> a = p.after;
		p.after = null;
		if (b == null) {
			head = a;
		} else {
			b.after = a;
		}
		a.before = b;
		p.before = tail;
		tail.after = p;
		tail = p;
		modCount++;
	}

	void afterClear() {
		head = tail = null;
	}

	HashMap.Node<K, V> firstNode() {
		return head;
	}

	HashMap.Node<K, V> nextNode(HashMap.Node<K, V> e) {
		return ((Entry<K, V>) e).after;
	}

	public V get(Object key) {
		HashMap.Node<K, V> e = getNode(key);
		if (e == null) {
			return null;
		}
		afterNodeAccess(e);
		return e.value;
	}

	public V getOrDefault(Object key, V defaultValue) {
		HashMap.Node<K, V> e = getNode(key);
		if (e == null) {
			return defaultValue;
		}
		afterNodeAccess(e);
		return e.value;
	}
}
