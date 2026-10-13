import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.TreeMap;
import java.util.TreeSet;

// R3: queues, deques, priority queues and sorted maps and sets
public class QueuesDemo {
	static void line(String label, Object value) {
		System.out.println(label + ": " + value);
	}

	static void deques() {
		Deque<Integer> deque = new ArrayDeque<>();
		for (int i = 0; i < 20; i++) {
			if (i % 2 == 0) {
				deque.addFirst(i);
			} else {
				deque.addLast(i);
			}
		}
		line("deque", deque + " " + deque.size());
		line("peek poll", deque.peekFirst() + " " + deque.peekLast() + " " + deque.pollFirst() + " "
				+ deque.pollLast() + " " + deque.size());
		Deque<String> stack = new ArrayDeque<>();
		stack.push("a");
		stack.push("b");
		stack.push("c");
		line("stack", stack + " " + stack.peek() + " " + stack.pop() + " " + stack);
		Queue<String> queue = new ArrayDeque<>();
		queue.offer("x");
		queue.add("y");
		line("queue", queue.poll() + " " + queue.remove() + " " + queue.poll() + " " + queue.peek());
		try {
			queue.remove();
		} catch (NoSuchElementException e) {
			line("remove empty", e.getClass().getName());
		}
		try {
			queue.offer(null);
		} catch (NullPointerException e) {
			line("offer null", e.getClass().getName());
		}
		Iterator<Integer> down = deque.descendingIterator();
		StringBuilder sb = new StringBuilder();
		while (down.hasNext()) {
			sb.append(down.next()).append(' ');
		}
		line("descending", sb.toString().trim());
		deque.removeIf(n -> n % 3 == 0);
		line("removeIf", deque + " " + deque.contains(4) + " " + deque.removeFirstOccurrence(4) + " " + deque);
	}

	static void linkedLists() {
		LinkedList<String> list = new LinkedList<>(List.of("b", "c"));
		list.addFirst("a");
		list.addLast("d");
		list.add(2, "x");
		line("linked", list + " " + list.getFirst() + " " + list.getLast() + " " + list.get(2));
		line("remove", list.removeFirst() + " " + list.removeLast() + " " + list.remove(1) + " " + list);
		list.push("p");
		line("deque ops", list.pop() + " " + list.peekLast() + " " + list.indexOf("c") + " " + list);
		Iterator<String> it = list.iterator();
		while (it.hasNext()) {
			if (it.next().equals("b")) {
				it.remove();
			}
		}
		line("iterator remove", list + " " + list.size());
		list.clear();
		line("clear", list + " " + list.peek() + " " + list.poll());
	}

	static void priorityQueues() {
		PriorityQueue<Integer> pq = new PriorityQueue<>();
		int[] values = { 5, 1, 8, 3, 9, 2, 7, 1, 6 };
		for (int v : values) {
			pq.offer(v);
		}
		line("heap order", pq + " " + pq.size() + " " + pq.peek());
		StringBuilder sb = new StringBuilder();
		while (!pq.isEmpty()) {
			sb.append(pq.poll()).append(' ');
		}
		line("polled", sb.toString().trim());
		PriorityQueue<String> words = new PriorityQueue<>(Comparator.comparing(String::length).reversed());
		for (String w : "a quick brown fox jumped over".split(" ")) {
			words.add(w);
		}
		line("by length desc", words);
		words.remove("fox");
		line("remove", words + " " + words.poll() + " " + words);
		PriorityQueue<int[]> dijkstra = new PriorityQueue<>((x, y) -> Integer.compare(x[0], y[0]));
		dijkstra.add(new int[] { 7, 0 });
		dijkstra.add(new int[] { 2, 1 });
		dijkstra.add(new int[] { 4, 2 });
		line("int[] entries", dijkstra.poll()[1] + " " + dijkstra.poll()[1] + " " + dijkstra.poll()[1]);
		PriorityQueue<Integer> fromList = new PriorityQueue<>(List.of(9, 4, 7, 1, 8, 2));
		line("heapify", fromList);
	}

	static void sorted() {
		TreeMap<String, Integer> map = new TreeMap<>();
		for (String w : "the quick brown fox jumps over the lazy dog".split(" ")) {
			map.merge(w, 1, Integer::sum);
		}
		line("tree map", map + " " + map.size());
		line("first last", map.firstKey() + " " + map.lastKey() + " " + map.firstEntry() + " " + map.lastEntry());
		line("navigate", map.floorKey("m") + " " + map.ceilingKey("m") + " " + map.lowerKey("fox") + " "
				+ map.higherKey("fox") + " " + map.floorKey("a") + " " + map.ceilingKey("zz"));
		line("head tail", map.headMap("fox") + " " + map.tailMap("over") + " " + map.subMap("dog", "lazy"));
		line("remove", map.remove("quick") + " " + map.remove("cat") + " " + map.containsKey("quick") + " " + map);
		line("poll", map.pollFirstEntry() + " " + map.pollLastEntry() + " " + map);
		NavigableMap<String, Integer> desc = map.descendingMap();
		line("descending", desc + " " + map.descendingKeySet());
		TreeMap<Integer, String> numbers = new TreeMap<>(Comparator.reverseOrder());
		for (int i = 0; i < 1000; i++) {
			numbers.put(i * 7919 % 1000, "v" + i);
		}
		line("thousand", numbers.size() + " " + numbers.firstKey() + " " + numbers.lastKey() + " " + numbers.get(500));
		for (int i = 0; i < 1000; i += 2) {
			numbers.remove(i);
		}
		int sum = 0;
		for (Map.Entry<Integer, String> e : numbers.entrySet()) {
			sum += e.getKey();
		}
		line("after removes", numbers.size() + " " + sum + " " + numbers.firstKey() + " " + numbers.lastKey());
		TreeSet<String> set = new TreeSet<>(List.of("pear", "apple", "fig", "kiwi"));
		line("tree set", set + " " + set.first() + " " + set.last() + " " + set.ceiling("b") + " " + set.floor("b"));
		line("set ops", set.add("date") + " " + set.add("fig") + " " + set.remove("kiwi") + " " + set + " "
				+ set.headSet("fig") + " " + set.tailSet("fig") + " " + set.descendingSet());
		line("poll set", set.pollFirst() + " " + set.pollLast() + " " + set);
		TreeSet<Integer> ints = new TreeSet<>();
		for (int i = 0; i < 50; i++) {
			ints.add((i * 37) % 23);
		}
		line("int tree set", ints + " " + ints.size() + " " + ints.higher(10) + " " + ints.lower(0));
		Iterator<Integer> it = ints.iterator();
		while (it.hasNext()) {
			if (it.next() % 2 == 0) {
				it.remove();
			}
		}
		line("iterator remove", ints);
	}

	/** Deterministic pseudo-random puts and removes, checked through iteration */
	static void churn() {
		TreeMap<Integer, Integer> tree = new TreeMap<>();
		java.util.HashMap<Integer, Integer> hash = new java.util.HashMap<>();
		PriorityQueue<Integer> heap = new PriorityQueue<>();
		long seed = 12345;
		for (int i = 0; i < 20000; i++) {
			seed = (seed * 6364136223846793005L + 1442695040888963407L);
			int key = (int) ((seed >>> 33) % 2000);
			if ((seed & 3) == 0) {
				tree.remove(key);
				hash.remove(key);
				heap.remove(key);
			} else {
				tree.put(key, i);
				hash.put(key, i);
				heap.offer(key);
			}
		}
		long treeSum = 0;
		int previous = -1;
		boolean ordered = true;
		for (Map.Entry<Integer, Integer> e : tree.entrySet()) {
			treeSum = treeSum * 31 + e.getKey() * 7 + e.getValue();
			ordered &= e.getKey() > previous;
			previous = e.getKey();
		}
		long hashSum = 0;
		for (Map.Entry<Integer, Integer> e : hash.entrySet()) {
			hashSum = hashSum * 31 + e.getKey() * 7 + e.getValue();
		}
		long heapSum = 0;
		while (!heap.isEmpty()) {
			heapSum = heapSum * 31 + heap.poll();
		}
		line("churn", tree.size() + " " + ordered + " " + treeSum + " " + hash.size() + " " + hashSum + " "
				+ heapSum + " " + tree.equals(hash));
	}

	public static void main(String[] args) {
		churn();
		deques();
		linkedLists();
		priorityQueues();
		sorted();
	}
}
