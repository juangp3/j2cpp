package java.util;

/**
 * PriorityQueue, written from the Java SE specification: a binary min-heap
 * in an array. Programs print queues, which shows the array, so the heap
 * moves are the textbook ones: offer sifts up, poll moves the last element
 * to the root and sifts down toward the smaller child, ties staying put.
 */
public class PriorityQueue<E> extends AbstractQueue<E> implements java.io.Serializable {
	private Object[] queue;
	private int size;
	private final Comparator<? super E> comparator;
	private int modCount;

	public PriorityQueue() {
		this(11, null);
	}

	public PriorityQueue(int initialCapacity) {
		this(initialCapacity, null);
	}

	public PriorityQueue(Comparator<? super E> comparator) {
		this(11, comparator);
	}

	public PriorityQueue(int initialCapacity, Comparator<? super E> comparator) {
		if (initialCapacity < 1) {
			throw new IllegalArgumentException();
		}
		this.queue = new Object[initialCapacity];
		this.comparator = comparator;
	}

	@SuppressWarnings("unchecked")
	public PriorityQueue(Collection<? extends E> c) {
		if (c instanceof SortedSet<?>) {
			comparator = (Comparator<? super E>) ((SortedSet<? extends E>) c).comparator();
		} else if (c instanceof PriorityQueue<?>) {
			comparator = (Comparator<? super E>) ((PriorityQueue<? extends E>) c).comparator();
		} else {
			comparator = null;
		}
		Object[] a = c.toArray();
		for (Object e : a) {
			Objects.requireNonNull(e);
		}
		queue = a.length == 0 ? new Object[1] : a;
		size = a.length;
		for (int i = (size >>> 1) - 1; i >= 0; i--) {
			siftDown(i, (E) queue[i]);
		}
	}

	@SuppressWarnings("unchecked")
	private int compare(E a, Object b) {
		return comparator == null ? ((Comparable<? super E>) a).compareTo((E) b) : comparator.compare(a, (E) b);
	}

	public boolean add(E e) {
		return offer(e);
	}

	public boolean offer(E e) {
		Objects.requireNonNull(e);
		modCount++;
		if (size >= queue.length) {
			queue = Arrays.copyOf(queue, queue.length < 64 ? queue.length * 2 + 2 : queue.length + (queue.length >> 1));
		}
		siftUp(size, e);
		size++;
		return true;
	}

	@SuppressWarnings("unchecked")
	public E peek() {
		return (E) (size == 0 ? null : queue[0]);
	}

	private int indexOf(Object o) {
		if (o != null) {
			for (int i = 0; i < size; i++) {
				if (o.equals(queue[i])) {
					return i;
				}
			}
		}
		return -1;
	}

	public boolean remove(Object o) {
		int i = indexOf(o);
		if (i == -1) {
			return false;
		}
		removeAt(i);
		return true;
	}

	public boolean contains(Object o) {
		return indexOf(o) >= 0;
	}

	public Object[] toArray() {
		return Arrays.copyOf(queue, size);
	}

	public Iterator<E> iterator() {
		return new Itr();
	}

	public int size() {
		return size;
	}

	public void clear() {
		modCount++;
		for (int i = 0; i < size; i++) {
			queue[i] = null;
		}
		size = 0;
	}

	@SuppressWarnings("unchecked")
	public E poll() {
		if (size == 0) {
			return null;
		}
		modCount++;
		E result = (E) queue[0];
		int n = --size;
		E x = (E) queue[n];
		queue[n] = null;
		if (n > 0) {
			siftDown(0, x);
		}
		return result;
	}

	/** Removes the element at index i; returns an element moved before i, or null */
	@SuppressWarnings("unchecked")
	E removeAt(int i) {
		modCount++;
		int s = --size;
		if (s == i) {
			queue[i] = null;
		} else {
			E moved = (E) queue[s];
			queue[s] = null;
			siftDown(i, moved);
			if (queue[i] == moved) {
				siftUp(i, moved);
				if (queue[i] != moved) {
					return moved;
				}
			}
		}
		return null;
	}

	private void siftUp(int k, E x) {
		while (k > 0) {
			int parent = (k - 1) >>> 1;
			Object e = queue[parent];
			if (compare(x, e) >= 0) {
				break;
			}
			queue[k] = e;
			k = parent;
		}
		queue[k] = x;
	}

	@SuppressWarnings("unchecked")
	private void siftDown(int k, E x) {
		int half = size >>> 1;
		while (k < half) {
			int child = (k << 1) + 1;
			Object c = queue[child];
			int right = child + 1;
			if (right < size && compare((E) c, queue[right]) > 0) {
				c = queue[child = right];
			}
			if (compare(x, c) <= 0) {
				break;
			}
			queue[k] = c;
			k = child;
		}
		queue[k] = x;
	}

	public Comparator<? super E> comparator() {
		return comparator;
	}

	private final class Itr implements Iterator<E> {
		private int cursor;
		private int lastRet = -1;
		/** Elements moved behind the cursor by removals, still to visit */
		private ArrayDeque<E> forgetMeNot;
		private E lastRetElt;
		private int expectedModCount = modCount;

		public boolean hasNext() {
			return cursor < size || (forgetMeNot != null && !forgetMeNot.isEmpty());
		}

		@SuppressWarnings("unchecked")
		public E next() {
			if (expectedModCount != modCount) {
				throw new ConcurrentModificationException();
			}
			if (cursor < size) {
				return (E) queue[lastRet = cursor++];
			}
			if (forgetMeNot != null) {
				lastRet = -1;
				lastRetElt = forgetMeNot.poll();
				if (lastRetElt != null) {
					return lastRetElt;
				}
			}
			throw new NoSuchElementException();
		}

		public void remove() {
			if (expectedModCount != modCount) {
				throw new ConcurrentModificationException();
			}
			if (lastRet != -1) {
				E moved = removeAt(lastRet);
				lastRet = -1;
				if (moved == null) {
					cursor--;
				} else {
					if (forgetMeNot == null) {
						forgetMeNot = new ArrayDeque<E>();
					}
					forgetMeNot.add(moved);
				}
			} else if (lastRetElt != null) {
				for (int i = 0; i < size; i++) {
					if (queue[i] == lastRetElt) {
						removeAt(i);
						break;
					}
				}
				lastRetElt = null;
			} else {
				throw new IllegalStateException();
			}
			expectedModCount = modCount;
		}
	}
}
