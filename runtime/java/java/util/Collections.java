package java.util;

import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/** Collections, written from the Java SE specification */
public class Collections {
	private Collections() {
	}

	@SuppressWarnings("rawtypes")
	public static final List EMPTY_LIST = new EmptyList<Object>();

	@SuppressWarnings("rawtypes")
	public static final Set EMPTY_SET = new EmptySet<Object>();

	@SuppressWarnings("rawtypes")
	public static final Map EMPTY_MAP = new EmptyMap<Object, Object>();

	public static <T extends Comparable<? super T>> void sort(List<T> list) {
		list.sort(null);
	}

	public static <T> void sort(List<T> list, Comparator<? super T> c) {
		list.sort(c);
	}

	public static <T> int binarySearch(List<? extends Comparable<? super T>> list, T key) {
		int low = 0;
		int high = list.size() - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			int cmp = list.get(mid).compareTo(key);
			if (cmp < 0) {
				low = mid + 1;
			} else if (cmp > 0) {
				high = mid - 1;
			} else {
				return mid;
			}
		}
		return -(low + 1);
	}

	@SuppressWarnings("unchecked")
	public static <T> int binarySearch(List<? extends T> list, T key, Comparator<? super T> c) {
		if (c == null) {
			return binarySearch((List<? extends Comparable<? super T>>) list, key);
		}
		int low = 0;
		int high = list.size() - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			int cmp = c.compare(list.get(mid), key);
			if (cmp < 0) {
				low = mid + 1;
			} else if (cmp > 0) {
				high = mid - 1;
			} else {
				return mid;
			}
		}
		return -(low + 1);
	}

	public static void reverse(List<?> list) {
		for (int i = 0, j = list.size() - 1; i < j; i++, j--) {
			swap(list, i, j);
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static void swap(List<?> list, int i, int j) {
		List l = list;
		l.set(i, l.set(j, l.get(i)));
	}

	public static <T> void fill(List<? super T> list, T obj) {
		for (int i = 0; i < list.size(); i++) {
			list.set(i, obj);
		}
	}

	public static <T> void copy(List<? super T> dest, List<? extends T> src) {
		int srcSize = src.size();
		if (srcSize > dest.size()) {
			throw new IndexOutOfBoundsException("Source does not fit in dest");
		}
		for (int i = 0; i < srcSize; i++) {
			dest.set(i, src.get(i));
		}
	}

	public static <T extends Object & Comparable<? super T>> T min(Collection<? extends T> coll) {
		Iterator<? extends T> i = coll.iterator();
		T candidate = i.next();
		while (i.hasNext()) {
			T next = i.next();
			if (next.compareTo(candidate) < 0) {
				candidate = next;
			}
		}
		return candidate;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> T min(Collection<? extends T> coll, Comparator<? super T> comp) {
		if (comp == null) {
			comp = (Comparator) Comparators.NATURAL;
		}
		Iterator<? extends T> i = coll.iterator();
		T candidate = i.next();
		while (i.hasNext()) {
			T next = i.next();
			if (comp.compare(next, candidate) < 0) {
				candidate = next;
			}
		}
		return candidate;
	}

	public static <T extends Object & Comparable<? super T>> T max(Collection<? extends T> coll) {
		Iterator<? extends T> i = coll.iterator();
		T candidate = i.next();
		while (i.hasNext()) {
			T next = i.next();
			if (next.compareTo(candidate) > 0) {
				candidate = next;
			}
		}
		return candidate;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> T max(Collection<? extends T> coll, Comparator<? super T> comp) {
		if (comp == null) {
			comp = (Comparator) Comparators.NATURAL;
		}
		Iterator<? extends T> i = coll.iterator();
		T candidate = i.next();
		while (i.hasNext()) {
			T next = i.next();
			if (comp.compare(next, candidate) > 0) {
				candidate = next;
			}
		}
		return candidate;
	}

	public static int frequency(Collection<?> c, Object o) {
		int result = 0;
		for (Object e : c) {
			if (Objects.equals(o, e)) {
				result++;
			}
		}
		return result;
	}

	public static boolean disjoint(Collection<?> c1, Collection<?> c2) {
		for (Object e : c1) {
			if (c2.contains(e)) {
				return false;
			}
		}
		return true;
	}

	@SafeVarargs
	public static <T> boolean addAll(Collection<? super T> c, T... elements) {
		boolean result = false;
		for (T element : elements) {
			if (c.add(element)) {
				result = true;
			}
		}
		return result;
	}

	public static <T> List<T> nCopies(int n, T o) {
		if (n < 0) {
			throw new IllegalArgumentException("List length = " + n);
		}
		return new CopiesList<T>(n, o);
	}

	@SuppressWarnings("unchecked")
	public static <T> Comparator<T> reverseOrder() {
		return (Comparator<T>) ReverseComparator.REVERSE_ORDER;
	}

	@SuppressWarnings("unchecked")
	public static <T> Comparator<T> reverseOrder(Comparator<T> cmp) {
		if (cmp == null) {
			return (Comparator<T>) ReverseComparator.REVERSE_ORDER;
		}
		if (cmp == ReverseComparator.REVERSE_ORDER) {
			return (Comparator<T>) Comparators.NATURAL;
		}
		return cmp.reversed();
	}

	@SuppressWarnings("unchecked")
	public static final <T> List<T> emptyList() {
		return (List<T>) EMPTY_LIST;
	}

	@SuppressWarnings("unchecked")
	public static final <T> Set<T> emptySet() {
		return (Set<T>) EMPTY_SET;
	}

	@SuppressWarnings("unchecked")
	public static final <K, V> Map<K, V> emptyMap() {
		return (Map<K, V>) EMPTY_MAP;
	}

	public static <T> Iterator<T> emptyIterator() {
		return new EmptyIterator<T>();
	}

	public static <T> List<T> singletonList(T o) {
		return new SingletonList<T>(o);
	}

	public static <T> Set<T> singleton(T o) {
		return new SingletonSet<T>(o);
	}

	public static <K, V> Map<K, V> singletonMap(K key, V value) {
		Map<K, V> map = new LinkedHashMap<K, V>(1);
		map.put(key, value);
		return unmodifiableMap(map);
	}

	public static <T> Collection<T> unmodifiableCollection(Collection<? extends T> c) {
		return new UnmodifiableCollection<T>(c);
	}

	public static <T> List<T> unmodifiableList(List<? extends T> list) {
		return new UnmodifiableList<T>(list);
	}

	public static <T> Set<T> unmodifiableSet(Set<? extends T> s) {
		return new UnmodifiableSet<T>(s);
	}

	public static <T> Collection<T> synchronizedCollection(Collection<T> c) {
		return new SynchronizedCollection<T>(c, null);
	}

	public static <T> List<T> synchronizedList(List<T> list) {
		return list instanceof RandomAccess ? new SynchronizedRandomAccessList<T>(list, null)
				: new SynchronizedList<T>(list, null);
	}

	public static <T> Set<T> synchronizedSet(Set<T> s) {
		return new SynchronizedSet<T>(s, null);
	}

	public static <K, V> Map<K, V> synchronizedMap(Map<K, V> m) {
		return new SynchronizedMap<K, V>(m);
	}

	public static <K, V> Map<K, V> unmodifiableMap(Map<? extends K, ? extends V> m) {
		return new UnmodifiableMap<K, V>(m);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static final class ReverseComparator implements Comparator<Comparable<Object>> {
		static final ReverseComparator REVERSE_ORDER = new ReverseComparator();

		public int compare(Comparable<Object> c1, Comparable<Object> c2) {
			return c2.compareTo(c1);
		}

		public Comparator<Comparable<Object>> reversed() {
			return (Comparator) Comparators.NATURAL;
		}
	}

	private static class EmptyIterator<E> implements Iterator<E> {
		public boolean hasNext() {
			return false;
		}

		public E next() {
			throw new NoSuchElementException();
		}
	}

	private static class EmptyList<E> extends AbstractList<E> implements RandomAccess {
		public int size() {
			return 0;
		}

		public E get(int index) {
			throw new IndexOutOfBoundsException("Index: " + index);
		}
	}

	private static class EmptySet<E> extends AbstractSet<E> {
		public Iterator<E> iterator() {
			return new EmptyIterator<E>();
		}

		public int size() {
			return 0;
		}
	}

	private static class EmptyMap<K, V> extends AbstractMap<K, V> {
		public Set<Map.Entry<K, V>> entrySet() {
			return emptySet();
		}
	}

	private static class SingletonList<E> extends AbstractList<E> implements RandomAccess {
		private final E element;

		SingletonList(E element) {
			this.element = element;
		}

		public int size() {
			return 1;
		}

		public E get(int index) {
			if (index != 0) {
				throw new IndexOutOfBoundsException("Index: " + index + ", Size: 1");
			}
			return element;
		}
	}

	private static class SingletonSet<E> extends AbstractSet<E> {
		private final E element;

		SingletonSet(E element) {
			this.element = element;
		}

		public Iterator<E> iterator() {
			return new SingletonList<E>(element).iterator();
		}

		public int size() {
			return 1;
		}

		public boolean contains(Object o) {
			return Objects.equals(o, element);
		}
	}

	private static class CopiesList<E> extends AbstractList<E> implements RandomAccess {
		private final int n;
		private final E element;

		CopiesList(int n, E element) {
			this.n = n;
			this.element = element;
		}

		public int size() {
			return n;
		}

		public E get(int index) {
			Objects.checkIndex(index, n);
			return element;
		}
	}

	static class UnmodifiableCollection<E> implements Collection<E>, java.io.Serializable {
		final Collection<? extends E> c;

		UnmodifiableCollection(Collection<? extends E> c) {
			this.c = Objects.requireNonNull(c);
		}

		public int size() {
			return c.size();
		}

		public boolean isEmpty() {
			return c.isEmpty();
		}

		public boolean contains(Object o) {
			return c.contains(o);
		}

		public Object[] toArray() {
			return c.toArray();
		}

		public <T> T[] toArray(T[] a) {
			return c.toArray(a);
		}

		public String toString() {
			return c.toString();
		}

		public Iterator<E> iterator() {
			return new UnmodifiableIterator<E>(c.iterator());
		}

		public boolean add(E e) {
			throw new UnsupportedOperationException();
		}

		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		public boolean containsAll(Collection<?> coll) {
			return c.containsAll(coll);
		}

		public boolean addAll(Collection<? extends E> coll) {
			throw new UnsupportedOperationException();
		}

		public boolean removeAll(Collection<?> coll) {
			throw new UnsupportedOperationException();
		}

		public boolean retainAll(Collection<?> coll) {
			throw new UnsupportedOperationException();
		}

		public void clear() {
			throw new UnsupportedOperationException();
		}

		public boolean removeIf(Predicate<? super E> filter) {
			throw new UnsupportedOperationException();
		}
	}

	private static class UnmodifiableIterator<E> implements Iterator<E> {
		private final Iterator<? extends E> i;

		UnmodifiableIterator(Iterator<? extends E> i) {
			this.i = i;
		}

		public boolean hasNext() {
			return i.hasNext();
		}

		public E next() {
			return i.next();
		}
	}

	static class UnmodifiableSet<E> extends UnmodifiableCollection<E> implements Set<E> {
		UnmodifiableSet(Set<? extends E> s) {
			super(s);
		}

		public boolean equals(Object o) {
			return o == this || c.equals(o);
		}

		public int hashCode() {
			return c.hashCode();
		}
	}

	static class UnmodifiableList<E> extends UnmodifiableCollection<E> implements List<E> {
		final List<? extends E> list;

		UnmodifiableList(List<? extends E> list) {
			super(list);
			this.list = list;
		}

		public boolean equals(Object o) {
			return o == this || list.equals(o);
		}

		public int hashCode() {
			return list.hashCode();
		}

		public E get(int index) {
			return list.get(index);
		}

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
			return list.indexOf(o);
		}

		public int lastIndexOf(Object o) {
			return list.lastIndexOf(o);
		}

		public boolean addAll(int index, Collection<? extends E> c) {
			throw new UnsupportedOperationException();
		}

		public void replaceAll(UnaryOperator<E> operator) {
			throw new UnsupportedOperationException();
		}

		public void sort(Comparator<? super E> c) {
			throw new UnsupportedOperationException();
		}

		public ListIterator<E> listIterator() {
			return listIterator(0);
		}

		public ListIterator<E> listIterator(int index) {
			return new UnmodifiableListIterator<E>(list.listIterator(index));
		}

		public List<E> subList(int fromIndex, int toIndex) {
			return new UnmodifiableList<E>(list.subList(fromIndex, toIndex));
		}
	}

	private static class UnmodifiableListIterator<E> implements ListIterator<E> {
		private final ListIterator<? extends E> i;

		UnmodifiableListIterator(ListIterator<? extends E> i) {
			this.i = i;
		}

		public boolean hasNext() {
			return i.hasNext();
		}

		public E next() {
			return i.next();
		}

		public boolean hasPrevious() {
			return i.hasPrevious();
		}

		public E previous() {
			return i.previous();
		}

		public int nextIndex() {
			return i.nextIndex();
		}

		public int previousIndex() {
			return i.previousIndex();
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

		public void set(E e) {
			throw new UnsupportedOperationException();
		}

		public void add(E e) {
			throw new UnsupportedOperationException();
		}
	}

	static class UnmodifiableMap<K, V> implements Map<K, V>, java.io.Serializable {
		private final Map<? extends K, ? extends V> m;

		UnmodifiableMap(Map<? extends K, ? extends V> m) {
			this.m = Objects.requireNonNull(m);
		}

		public int size() {
			return m.size();
		}

		public boolean isEmpty() {
			return m.isEmpty();
		}

		public boolean containsKey(Object key) {
			return m.containsKey(key);
		}

		public boolean containsValue(Object val) {
			return m.containsValue(val);
		}

		public V get(Object key) {
			return m.get(key);
		}

		public V put(K key, V value) {
			throw new UnsupportedOperationException();
		}

		public V remove(Object key) {
			throw new UnsupportedOperationException();
		}

		public void putAll(Map<? extends K, ? extends V> map) {
			throw new UnsupportedOperationException();
		}

		public void clear() {
			throw new UnsupportedOperationException();
		}

		public Set<K> keySet() {
			return unmodifiableSet(m.keySet());
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		public Set<Map.Entry<K, V>> entrySet() {
			// Entries stay mutable through setValue; programs rarely rely on it
			return unmodifiableSet((Set) m.entrySet());
		}


		public Collection<V> values() {
			return unmodifiableCollection(m.values());
		}

		public boolean equals(Object o) {
			return o == this || m.equals(o);
		}

		public int hashCode() {
			return m.hashCode();
		}

		public String toString() {
			return m.toString();
		}
	}

	// Synchronized views: each method holds the monitor of mutex (the view,
	// or the view it came from); iterating needs the caller to hold it

	static class SynchronizedCollection<E> implements Collection<E>, java.io.Serializable {
		final Collection<E> c;
		final Object mutex;

		SynchronizedCollection(Collection<E> c, Object mutex) {
			this.c = Objects.requireNonNull(c);
			this.mutex = mutex == null ? this : mutex;
		}

		public int size() {
			synchronized (mutex) {
				return c.size();
			}
		}

		public boolean isEmpty() {
			synchronized (mutex) {
				return c.isEmpty();
			}
		}

		public boolean contains(Object o) {
			synchronized (mutex) {
				return c.contains(o);
			}
		}

		public Object[] toArray() {
			synchronized (mutex) {
				return c.toArray();
			}
		}

		public <T> T[] toArray(T[] a) {
			synchronized (mutex) {
				return c.toArray(a);
			}
		}

		public Iterator<E> iterator() {
			return c.iterator();
		}

		public boolean add(E e) {
			synchronized (mutex) {
				return c.add(e);
			}
		}

		public boolean remove(Object o) {
			synchronized (mutex) {
				return c.remove(o);
			}
		}

		public boolean containsAll(Collection<?> coll) {
			synchronized (mutex) {
				return c.containsAll(coll);
			}
		}

		public boolean addAll(Collection<? extends E> coll) {
			synchronized (mutex) {
				return c.addAll(coll);
			}
		}

		public boolean removeAll(Collection<?> coll) {
			synchronized (mutex) {
				return c.removeAll(coll);
			}
		}

		public boolean retainAll(Collection<?> coll) {
			synchronized (mutex) {
				return c.retainAll(coll);
			}
		}

		public void clear() {
			synchronized (mutex) {
				c.clear();
			}
		}

		public boolean removeIf(Predicate<? super E> filter) {
			synchronized (mutex) {
				return c.removeIf(filter);
			}
		}

		public void forEach(java.util.function.Consumer<? super E> action) {
			synchronized (mutex) {
				c.forEach(action);
			}
		}

		public String toString() {
			synchronized (mutex) {
				return c.toString();
			}
		}
	}

	static class SynchronizedSet<E> extends SynchronizedCollection<E> implements Set<E> {
		SynchronizedSet(Set<E> s, Object mutex) {
			super(s, mutex);
		}

		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			synchronized (mutex) {
				return c.equals(o);
			}
		}

		public int hashCode() {
			synchronized (mutex) {
				return c.hashCode();
			}
		}
	}

	static class SynchronizedList<E> extends SynchronizedCollection<E> implements List<E> {
		final List<E> list;

		SynchronizedList(List<E> list, Object mutex) {
			super(list, mutex);
			this.list = list;
		}

		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			synchronized (mutex) {
				return list.equals(o);
			}
		}

		public int hashCode() {
			synchronized (mutex) {
				return list.hashCode();
			}
		}

		public E get(int index) {
			synchronized (mutex) {
				return list.get(index);
			}
		}

		public E set(int index, E element) {
			synchronized (mutex) {
				return list.set(index, element);
			}
		}

		public void add(int index, E element) {
			synchronized (mutex) {
				list.add(index, element);
			}
		}

		public E remove(int index) {
			synchronized (mutex) {
				return list.remove(index);
			}
		}

		public int indexOf(Object o) {
			synchronized (mutex) {
				return list.indexOf(o);
			}
		}

		public int lastIndexOf(Object o) {
			synchronized (mutex) {
				return list.lastIndexOf(o);
			}
		}

		public boolean addAll(int index, Collection<? extends E> coll) {
			synchronized (mutex) {
				return list.addAll(index, coll);
			}
		}

		public ListIterator<E> listIterator() {
			return list.listIterator();
		}

		public ListIterator<E> listIterator(int index) {
			return list.listIterator(index);
		}

		public List<E> subList(int fromIndex, int toIndex) {
			synchronized (mutex) {
				return new SynchronizedList<E>(list.subList(fromIndex, toIndex), mutex);
			}
		}

		public void replaceAll(UnaryOperator<E> operator) {
			synchronized (mutex) {
				list.replaceAll(operator);
			}
		}

		public void sort(Comparator<? super E> comparator) {
			synchronized (mutex) {
				list.sort(comparator);
			}
		}
	}

	static class SynchronizedRandomAccessList<E> extends SynchronizedList<E> implements RandomAccess {
		SynchronizedRandomAccessList(List<E> list, Object mutex) {
			super(list, mutex);
		}

		public List<E> subList(int fromIndex, int toIndex) {
			synchronized (mutex) {
				return new SynchronizedRandomAccessList<E>(list.subList(fromIndex, toIndex), mutex);
			}
		}
	}

	private static class SynchronizedMap<K, V> implements Map<K, V>, java.io.Serializable {
		private final Map<K, V> m;
		final Object mutex;

		SynchronizedMap(Map<K, V> m) {
			this.m = Objects.requireNonNull(m);
			this.mutex = this;
		}

		public int size() {
			synchronized (mutex) {
				return m.size();
			}
		}

		public boolean isEmpty() {
			synchronized (mutex) {
				return m.isEmpty();
			}
		}

		public boolean containsKey(Object key) {
			synchronized (mutex) {
				return m.containsKey(key);
			}
		}

		public boolean containsValue(Object value) {
			synchronized (mutex) {
				return m.containsValue(value);
			}
		}

		public V get(Object key) {
			synchronized (mutex) {
				return m.get(key);
			}
		}

		public V put(K key, V value) {
			synchronized (mutex) {
				return m.put(key, value);
			}
		}

		public V remove(Object key) {
			synchronized (mutex) {
				return m.remove(key);
			}
		}

		public void putAll(Map<? extends K, ? extends V> map) {
			synchronized (mutex) {
				m.putAll(map);
			}
		}

		public void clear() {
			synchronized (mutex) {
				m.clear();
			}
		}

		public Set<K> keySet() {
			synchronized (mutex) {
				return new SynchronizedSet<K>(m.keySet(), mutex);
			}
		}

		public Set<Map.Entry<K, V>> entrySet() {
			synchronized (mutex) {
				return new SynchronizedSet<Map.Entry<K, V>>(m.entrySet(), mutex);
			}
		}

		public Collection<V> values() {
			synchronized (mutex) {
				return new SynchronizedCollection<V>(m.values(), mutex);
			}
		}

		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			synchronized (mutex) {
				return m.equals(o);
			}
		}

		public int hashCode() {
			synchronized (mutex) {
				return m.hashCode();
			}
		}

		public String toString() {
			synchronized (mutex) {
				return m.toString();
			}
		}

		public V getOrDefault(Object key, V defaultValue) {
			synchronized (mutex) {
				return m.getOrDefault(key, defaultValue);
			}
		}

		public void forEach(java.util.function.BiConsumer<? super K, ? super V> action) {
			synchronized (mutex) {
				m.forEach(action);
			}
		}

		public void replaceAll(java.util.function.BiFunction<? super K, ? super V, ? extends V> function) {
			synchronized (mutex) {
				m.replaceAll(function);
			}
		}

		public V putIfAbsent(K key, V value) {
			synchronized (mutex) {
				return m.putIfAbsent(key, value);
			}
		}

		public boolean remove(Object key, Object value) {
			synchronized (mutex) {
				return m.remove(key, value);
			}
		}

		public boolean replace(K key, V oldValue, V newValue) {
			synchronized (mutex) {
				return m.replace(key, oldValue, newValue);
			}
		}

		public V replace(K key, V value) {
			synchronized (mutex) {
				return m.replace(key, value);
			}
		}

		public V computeIfAbsent(K key, java.util.function.Function<? super K, ? extends V> mappingFunction) {
			synchronized (mutex) {
				return m.computeIfAbsent(key, mappingFunction);
			}
		}

		public V computeIfPresent(K key,
				java.util.function.BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
			synchronized (mutex) {
				return m.computeIfPresent(key, remappingFunction);
			}
		}

		public V compute(K key, java.util.function.BiFunction<? super K, ? super V, ? extends V> remappingFunction) {
			synchronized (mutex) {
				return m.compute(key, remappingFunction);
			}
		}

		public V merge(K key, V value,
				java.util.function.BiFunction<? super V, ? super V, ? extends V> remappingFunction) {
			synchronized (mutex) {
				return m.merge(key, value, remappingFunction);
			}
		}
	}
}
