import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

// R3: lists, sets, maps, iteration, sorting and the Collections and Arrays helpers
public class CollectionsDemo {
	static void line(String label, Object value) {
		System.out.println(label + ": " + value);
	}

	record Person(String name, int age) {
	}

	static void lists() {
		List<String> list = new ArrayList<>();
		list.add("pear");
		list.add("apple");
		list.add(0, "fig");
		list.addAll(Arrays.asList("kiwi", "date"));
		line("list", list);
		line("size get", list.size() + " " + list.get(1) + " " + list.indexOf("kiwi") + " " + list.indexOf("plum")
				+ " " + list.contains("date") + " " + list.isEmpty());
		line("set", list.set(1, "lime") + " " + list);
		line("remove index", list.remove(0) + " " + list);
		line("remove object", list.remove("kiwi") + " " + list.remove("plum") + " " + list);
		List<Integer> numbers = new ArrayList<>(Arrays.asList(5, 3, 9, 1, 3));
		numbers.remove(Integer.valueOf(3));
		line("remove boxed", numbers);
		numbers.removeIf(n -> n > 4);
		line("removeIf", numbers);
		List<Integer> big = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			big.add(i * i);
		}
		int sum = 0;
		for (int n : big) {
			sum += n;
		}
		line("grow and iterate", big.size() + " " + sum + " " + big.get(99));
		line("subList", big.subList(2, 6) + " " + big.subList(2, 6).size());
		big.subList(0, 95).clear();
		line("subList clear", big);
		line("equals hash", list.equals(new ArrayList<>(list)) + " " + list.equals(Arrays.asList("x")) + " "
				+ (list.hashCode() == new ArrayList<>(list).hashCode()) + " " + Arrays.asList(1, 2).hashCode());
		line("toArray", Arrays.toString(list.toArray()) + " " + list.toArray(new String[0]).length);
		ListIterator<String> it = list.listIterator();
		while (it.hasNext()) {
			String s = it.next();
			if (s.startsWith("l")) {
				it.set(s.toUpperCase());
			} else if (s.startsWith("a")) {
				it.add("avocado");
			}
		}
		line("listIterator", list);
		while (it.hasPrevious()) {
			System.out.print(it.previous() + (it.hasPrevious() ? " " : "\n"));
		}
		List<Object> self = new ArrayList<>();
		self.add(self);
		line("self", self);
		try {
			list.get(10);
		} catch (IndexOutOfBoundsException e) {
			line("get(10)", e.getMessage());
		}
		try {
			for (String s : list) {
				list.add(s);
			}
		} catch (ConcurrentModificationException e) {
			line("modified while iterating", e.getClass().getName());
		}
		try {
			new ArrayList<String>().iterator().next();
		} catch (NoSuchElementException e) {
			line("next on empty", e.getClass().getName());
		}
		List<String> fixed = Arrays.asList("b", "a");
		try {
			fixed.add("c");
		} catch (UnsupportedOperationException e) {
			line("asList add", e.getClass().getName());
		}
		fixed.set(0, "z");
		line("asList set", fixed);
		List<String> copy = List.of("one", "two");
		line("List.of", copy + " " + copy.size() + " " + List.of().isEmpty());
		try {
			copy.add("three");
		} catch (UnsupportedOperationException e) {
			line("List.of add", e.getClass().getName());
		}
	}

	static void sorting() {
		List<String> words = new ArrayList<>(Arrays.asList("delta", "Alpha", "charlie", "bravo", "echo", "ab"));
		Collections.sort(words);
		line("sort", words);
		words.sort(Comparator.reverseOrder());
		line("reverse order", words);
		words.sort(String.CASE_INSENSITIVE_ORDER);
		line("case insensitive", words);
		words.sort(Comparator.comparing(String::length).thenComparing(Comparator.naturalOrder()));
		line("by length then natural", words);
		words.sort((a, b) -> b.compareTo(a));
		line("lambda", words);
		List<Person> people = new ArrayList<>(List.of(new Person("Ann", 30), new Person("Bob", 25),
				new Person("Cid", 30), new Person("Dee", 25)));
		people.sort(Comparator.comparingInt(Person::age));
		line("stable by age", people);
		people.sort(Comparator.comparing(Person::age).reversed().thenComparing(Person::name, Comparator.reverseOrder()));
		line("age desc, name desc", people);
		List<Integer> nums = new ArrayList<>(Arrays.asList(4, 1, 3, 1, 5, 9, 2, 6));
		Collections.reverse(nums);
		line("reverse", nums);
		line("min max", Collections.min(nums) + " " + Collections.max(nums) + " "
				+ Collections.max(nums, Comparator.reverseOrder()));
		Collections.swap(nums, 0, 7);
		line("swap", nums);
		Collections.sort(nums, null);
		line("sort null comparator", nums);
		line("binarySearch", Collections.binarySearch(nums, 5) + " " + Collections.binarySearch(nums, 7));
		line("frequency nCopies", Collections.frequency(nums, 1) + " " + Collections.nCopies(3, "ab"));
		List<Integer> un = Collections.unmodifiableList(nums);
		try {
			un.set(0, 1);
		} catch (UnsupportedOperationException e) {
			line("unmodifiable", e.getClass().getName() + " " + un);
		}
		line("empty", Collections.emptyList() + " " + Collections.emptyMap() + " " + Collections.emptySet() + " "
				+ Collections.singletonList(7) + " " + Collections.singleton("s"));
		List<Integer> filled = new ArrayList<>(Collections.nCopies(4, 0));
		Collections.fill(filled, 8);
		Collections.addAll(filled, 1, 2);
		line("fill addAll", filled);
	}

	static void arrays() {
		int[] ints = { 5, -2, 9, 0, 3 };
		Arrays.sort(ints);
		line("sort ints", Arrays.toString(ints) + " " + Arrays.binarySearch(ints, 3) + " " + Arrays.binarySearch(ints, 4));
		double[] doubles = { 2.5, -0.0, 0.0, Double.NaN, -1.0 };
		Arrays.sort(doubles);
		line("sort doubles", Arrays.toString(doubles));
		String[] strings = { "c", "a", "b" };
		Arrays.sort(strings);
		line("sort strings", Arrays.toString(strings));
		Arrays.sort(strings, Collections.reverseOrder());
		line("sort comparator", Arrays.toString(strings));
		Integer[] range = { 9, 8, 7, 6, 5 };
		Arrays.sort(range, 1, 4);
		line("sort range", Arrays.toString(range));
		long[] longs = new long[3];
		Arrays.fill(longs, 7L);
		line("fill", Arrays.toString(longs) + " " + Arrays.toString((int[]) null) + " " + Arrays.toString(new char[] { 'h', 'i' }));
		int[] grown = Arrays.copyOf(ints, 7);
		line("copyOf", Arrays.toString(grown) + " " + Arrays.toString(Arrays.copyOfRange(ints, 1, 3)));
		line("equals hashCode", Arrays.equals(ints, new int[] { -2, 0, 3, 5, 9 }) + " " + Arrays.hashCode(ints) + " "
				+ Arrays.hashCode(new String[] { "a", "b" }) + " " + Arrays.deepToString(new int[][] { { 1 }, { 2, 3 } }));
		line("asList", Arrays.asList(strings).indexOf("a"));
	}

	static void sets() {
		Set<String> set = new HashSet<>();
		for (String s : "the quick brown fox jumps over the lazy dog".split(" ")) {
			set.add(s);
		}
		line("hash set", set + " " + set.size() + " " + set.contains("fox") + " " + set.contains("cat"));
		line("add remove", set.add("fox") + " " + set.add("cat") + " " + set.remove("the") + " " + set.remove("the"));
		Set<Integer> numbers = new HashSet<>();
		for (int i = 0; i < 40; i++) {
			numbers.add(i * 37 % 101);
		}
		line("int set", numbers);
		Set<String> linked = new LinkedHashSet<>(Arrays.asList("z", "a", "m", "a"));
		line("linked set", linked);
		line("set equals", set.equals(new HashSet<>(set)) + " " + (set.hashCode() == new HashSet<>(set).hashCode()));
		Set<String> retained = new HashSet<>(set);
		retained.retainAll(Arrays.asList("fox", "dog", "cow"));
		line("retainAll", retained);
		retained.removeAll(List.of("dog"));
		line("removeAll", retained + " " + set.containsAll(retained));
		line("Set.of", Set.of("only") + " " + Set.of().size());
		Iterator<String> it = set.iterator();
		while (it.hasNext()) {
			if (it.next().length() > 4) {
				it.remove();
			}
		}
		line("iterator remove", set);
	}

	static void maps() {
		Map<String, Integer> counts = new HashMap<>();
		for (String w : "a b c a b a d e f g h i j k l m n o p q a".split(" ")) {
			counts.put(w, counts.getOrDefault(w, 0) + 1);
		}
		line("hash map", counts);
		line("get", counts.get("a") + " " + counts.get("zz") + " " + counts.containsKey("b") + " "
				+ counts.containsValue(2) + " " + counts.size());
		counts.merge("a", 10, Integer::sum);
		counts.computeIfAbsent("new", k -> k.length());
		counts.computeIfPresent("b", (k, v) -> v * 100);
		counts.compute("c", (k, v) -> v == null ? -1 : null);
		counts.putIfAbsent("d", 99);
		line("merge compute", counts.get("a") + " " + counts.get("new") + " " + counts.get("b") + " "
				+ counts.containsKey("c") + " " + counts.get("d"));
		line("remove", counts.remove("e") + " " + counts.remove("e") + " " + counts.size());
		int total = 0;
		for (Map.Entry<String, Integer> e : counts.entrySet()) {
			total += e.getValue();
		}
		line("entries", total);
		line("keys values", counts.keySet() + " " + counts.values());
		Map<Integer, String> squares = new HashMap<>();
		for (int i = 0; i < 30; i++) {
			squares.put(i * i, "s" + i);
		}
		line("int keys", squares.keySet());
		squares.keySet().removeIf(k -> k % 2 == 1);
		line("keySet removeIf", squares.size() + " " + squares.get(16));
		squares.put(null, "null key");
		line("null key", squares.get(null) + " " + squares.containsKey(null));
		Map<String, String> linked = new LinkedHashMap<>();
		linked.put("z", "1");
		linked.put("a", "2");
		linked.put("m", "3");
		linked.put("a", "4");
		line("linked map", linked);
		for (Map.Entry<String, String> e : linked.entrySet()) {
			e.setValue(e.getValue() + "!");
		}
		linked.forEach((k, v) -> System.out.print(k + v + " "));
		System.out.println();
		line("map equals", linked.equals(new HashMap<>(linked)) + " " + new HashMap<>(linked) + " "
				+ (linked.hashCode() == new HashMap<>(linked).hashCode()));
		line("Map.of", Map.of("k", 1) + " " + Map.entry("e", 2) + " " + Map.of().isEmpty());
		Map<String, List<Integer>> groups = new HashMap<>();
		for (int i = 0; i < 10; i++) {
			groups.computeIfAbsent(i % 3 == 0 ? "three" : "other", k -> new ArrayList<>()).add(i);
		}
		line("groups", groups);
		Map<String, Integer> replaced = new HashMap<>(Map.of("x", 1));
		replaced.replaceAll((k, v) -> v + 41);
		line("replaceAll", replaced);
		Collection<Integer> values = counts.values();
		values.remove(1);
		line("values remove", counts.size());
	}

	public static void main(String[] args) {
		lists();
		sorting();
		arrays();
		sets();
		maps();
	}
}
