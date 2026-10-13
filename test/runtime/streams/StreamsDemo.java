import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

/** java.util.stream, Optional and the summary statistics, compared with the JVM */
public class StreamsDemo {
	record Person(String name, int age, String city) {
	}

	static final List<Person> PEOPLE = List.of(new Person("Ana", 31, "Lisbon"), new Person("Bruno", 25, "Porto"),
			new Person("Carla", 42, "Lisbon"), new Person("Duarte", 25, "Braga"), new Person("Eva", 37, "Porto"),
			new Person("Filipe", 19, "Lisbon"));

	static int counter;

	static void show(Object o) {
		System.out.println(o);
	}

	public static void main(String[] args) {
		sources();
		intermediate();
		laziness();
		terminal();
		primitives();
		collectors();
		statistics();
		errors();
		other();
	}

	static void sources() {
		System.out.println("== sources");
		System.out.println(Stream.of("a", "b", "c").collect(Collectors.toList()));
		System.out.println(Stream.of("one").toList() + " " + Stream.empty().toList() + " " + Stream.ofNullable(null).count()
				+ " " + Stream.ofNullable("x").count());
		System.out.println(Arrays.stream(new String[] { "x", "y", "z" }).collect(Collectors.joining("|")));
		System.out.println(Arrays.stream(new String[] { "x", "y", "z", "w" }, 1, 3).toList());
		System.out.println(Arrays.toString(Arrays.stream(new int[] { 5, 6, 7, 8 }, 1, 3).toArray()));
		System.out.println(Arrays.stream(new long[] { 1L << 40, 2 }).sum() + " " + Arrays.stream(new double[] { 0.5, 0.25 }).sum());
		System.out.println(Stream.iterate(1, x -> x * 2).limit(10).toList());
		System.out.println(Stream.iterate(1, x -> x < 100, x -> x * 3).toList());
		counter = 0;
		System.out.println(Stream.generate(() -> counter++).limit(5).toList());
		System.out.println(Stream.concat(Stream.of(1, 2), Stream.of(3, 4)).toList());
		Stream.Builder<String> builder = Stream.builder();
		builder.add("b1").add("b2").accept("b3");
		System.out.println(builder.build().toList());
		System.out.println(new ArrayList<>(List.of(3, 1, 2)).stream().map(x -> x * 10).toList());
		System.out.println(new LinkedList<>(List.of("l1", "l2")).stream().toList());
		System.out.println(new ArrayDeque<>(List.of(9, 8)).stream().toList());
		System.out.println(new TreeSet<>(List.of(5, 3, 9)).stream().toList());
		Map<String, Integer> map = new HashMap<>();
		map.put("k1", 1);
		map.put("k2", 2);
		map.put("k3", 3);
		System.out.println(map.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList());
		System.out.println(map.keySet().stream().toList() + " " + map.values().stream().mapToInt(i -> i).sum());
		System.out.println("hello".chars().mapToObj(c -> String.valueOf((char) c)).collect(Collectors.joining(",")));
		System.out.println(new StringBuilder("abc").chars().sum() + " " + "a\uD83D\uDE00b".codePoints().count());
		System.out.println(Optional.of(4).stream().toList() + " " + Optional.empty().stream().count());
		System.out.println(OptionalInt.of(4).stream().sum() + " " + IntStream.of(1, 2, 3).boxed().toList());
		System.out.println(List.of(1, 2, 3).parallelStream().isParallel() + " " + Stream.of(1).parallel().isParallel()
				+ " " + Stream.of(1).parallel().sequential().isParallel());
	}

	static void intermediate() {
		System.out.println("== intermediate");
		List<Integer> numbers = List.of(5, 3, 8, 1, 9, 2, 8, 3, 7);
		System.out.println(numbers.stream().filter(n -> n % 2 == 1).toList());
		System.out.println(numbers.stream().map(n -> "n" + n).toList());
		System.out.println(numbers.stream().distinct().toList());
		System.out.println(numbers.stream().sorted().toList());
		System.out.println(numbers.stream().sorted(Comparator.reverseOrder()).toList());
		System.out.println(numbers.stream().limit(4).toList() + " " + numbers.stream().skip(6).toList() + " "
				+ numbers.stream().skip(20).toList() + " " + numbers.stream().limit(0).toList());
		System.out.println(numbers.stream().takeWhile(n -> n > 2).toList() + " "
				+ numbers.stream().dropWhile(n -> n > 2).toList());
		System.out.println(Stream.of(List.of(1, 2), List.of(3), List.<Integer>of()).flatMap(List::stream).toList());
		System.out.println(Stream.of("a b", "c d e").flatMap(s -> Arrays.stream(s.split(" "))).toList());
		System.out.println(Stream.of(1, 2).flatMap(x -> x == 1 ? null : Stream.of(x, x)).toList());
		System.out.println(Stream.of("ab", "cde").flatMapToInt(String::chars).boxed().toList());
		System.out.println(Stream.of("x", "yy").flatMapToLong(s -> LongStream.of(s.length(), 10)).sum() + " "
				+ Stream.of(1, 2).flatMapToDouble(x -> DoubleStream.of(x / 2.0)).boxed().toList());
		System.out.println(Stream.of("x", "yy", "zzz").mapToInt(String::length).sum() + " "
				+ Stream.of("x", "yy").mapToLong(String::length).max() + " "
				+ Stream.of("x", "yy").mapToDouble(String::length).average());
		// Stable sort: equal keys keep their order
		System.out.println(PEOPLE.stream().sorted(Comparator.comparingInt(Person::age)).map(Person::name).toList());
		System.out.println(PEOPLE.stream().sorted(Comparator.comparing(Person::city).thenComparing(Person::name,
				Comparator.reverseOrder())).map(p -> p.city() + ":" + p.name()).toList());
		System.out.println(Stream.of("b", "a", "c").unordered().sorted().toList());
		List<String> withNull = Arrays.asList("x", null, "y", null);
		System.out.println(withNull.stream().distinct().toList() + " " + withNull.stream().filter(s -> s != null).count());
		System.out.println(Stream.of(1.0, 2.0, 1.0, Double.NaN, Double.NaN).distinct().toList());
	}

	static void laziness() {
		System.out.println("== laziness");
		StringBuilder log = new StringBuilder();
		Stream.of("a", "b", "c").peek(s -> log.append("p:").append(s).append(' ')).map(String::toUpperCase)
				.forEach(s -> log.append("f:").append(s).append(' '));
		System.out.println(log);
		log.setLength(0);
		Stream.of(3, 1, 2).peek(x -> log.append("before" + x + " ")).sorted().peek(x -> log.append("after" + x + " "))
				.forEach(x -> log.append("end" + x + " "));
		System.out.println(log);
		log.setLength(0);
		Optional<Integer> first = Stream.of(1, 2, 3, 4).peek(x -> log.append("saw" + x + " ")).filter(x -> x > 1)
				.findFirst();
		System.out.println(first + " " + log);
		log.setLength(0);
		System.out.println(Stream.iterate(1, x -> {
			log.append("next" + x + " ");
			return x + 1;
		}).limit(3).toList() + " " + log);
		log.setLength(0);
		System.out.println(Stream.iterate(1, x -> x + 1).filter(x -> x % 7 == 0).findFirst() + " "
				+ Stream.generate(() -> "g").anyMatch(s -> s.equals("g")) + " "
				+ IntStream.iterate(0, i -> i + 3).filter(i -> i > 100).findFirst() + " "
				+ Stream.iterate(1, x -> x + 1).takeWhile(x -> x < 5).count());
		System.out.println(Stream.iterate(1, x -> x + 1).flatMap(x -> Stream.of(x, -x)).skip(3).limit(4).toList());
		Stream.of(1, 2, 3).flatMap(x -> Stream.of(x, x * 10).peek(y -> log.append("inner" + y + " "))).limit(3)
				.forEach(y -> log.append("got" + y + " "));
		System.out.println(log);
		log.setLength(0);
		boolean any = Stream.of(1, 2, 3, 4).peek(x -> log.append(x)).anyMatch(x -> x == 2);
		boolean all = Stream.of(1, 2, 3, 4).peek(x -> log.append(x)).allMatch(x -> x < 3);
		boolean none = Stream.of(1, 2, 3, 4).peek(x -> log.append(x)).noneMatch(x -> x == 3);
		System.out.println(any + " " + all + " " + none + " " + log);
		log.setLength(0);
		// count() skips the pipeline when the size is known, as on the JVM
		List<Integer> list = List.of(1, 2, 3);
		long c1 = list.stream().peek(x -> log.append("a" + x)).count();
		long c2 = list.stream().map(x -> x * 2).peek(x -> log.append("b" + x)).count();
		long c3 = list.stream().filter(x -> true).peek(x -> log.append("c" + x)).count();
		long c4 = list.stream().limit(2).peek(x -> log.append("d" + x)).count();
		long c5 = list.stream().skip(1).peek(x -> log.append("e" + x)).count();
		long c6 = list.stream().sorted().peek(x -> log.append("f" + x)).count();
		long c7 = Stream.concat(Stream.of(1), Stream.of(2)).peek(x -> log.append("g" + x)).count();
		long c8 = IntStream.range(0, 3).peek(x -> log.append("h" + x)).count();
		long c9 = Stream.iterate(1, x -> x + 1).limit(3).peek(x -> log.append("i" + x)).count();
		long c10 = list.stream().distinct().peek(x -> log.append("j" + x)).count();
		long c11 = list.stream().flatMap(x -> Stream.of(x)).peek(x -> log.append("k" + x)).count();
		long c12 = new HashSet<>(list).stream().peek(x -> log.append("l" + x)).count();
		long c13 = "abc".chars().peek(x -> log.append("m" + x)).count();
		long c14 = list.stream().mapToInt(x -> x).asLongStream().boxed().peek(x -> log.append("n" + x)).count();
		long total = c1 + c2 + c3 + c4 + c5 + c6 + c7 + c8 + c9 + c10 + c11 + c12 + c13 + c14;
		System.out.println(total + " " + log);
		// late binding: the list is read when the terminal operation runs
		List<String> late = new ArrayList<>(List.of("x"));
		Stream<String> lateStream = late.stream();
		late.add("y");
		System.out.println(lateStream.toList());
		Iterator<Integer> iterator = Stream.iterate(1, x -> x * 10).iterator();
		System.out.println(iterator.next() + " " + iterator.next() + " " + iterator.hasNext() + " " + iterator.next());
		PrimitiveIterator.OfInt ints = IntStream.rangeClosed(1, 3).iterator();
		int sum = 0;
		while (ints.hasNext()) {
			sum += ints.nextInt();
		}
		System.out.println(sum);
	}

	static void terminal() {
		System.out.println("== terminal");
		List<Integer> numbers = List.of(4, 8, 15, 16, 23, 42);
		System.out.println(numbers.stream().reduce(0, Integer::sum) + " " + numbers.stream().reduce(Integer::max) + " "
				+ Stream.<Integer>empty().reduce(Integer::sum) + " "
				+ numbers.stream().reduce(0, (acc, n) -> acc + n * 2, Integer::sum));
		System.out.println(numbers.stream().count() + " " + numbers.stream().min(Comparator.naturalOrder()) + " "
				+ numbers.stream().max(Comparator.naturalOrder()) + " " + Stream.<Integer>empty().max(Integer::compare));
		System.out.println(Stream.of("bb", "aa", "c", "dd").max(Comparator.comparingInt(String::length)) + " "
				+ Stream.of("bb", "aa", "c", "dd").min(Comparator.comparingInt(String::length)) + " "
				+ Stream.of("bb", "aa", "cc").min(Comparator.comparingInt(String::length)));
		System.out.println(numbers.stream().anyMatch(n -> n > 40) + " " + numbers.stream().allMatch(n -> n > 3) + " "
				+ numbers.stream().noneMatch(n -> n == 15) + " " + Stream.empty().allMatch(x -> false) + " "
				+ Stream.empty().anyMatch(x -> true) + " " + Stream.empty().noneMatch(x -> true));
		System.out.println(numbers.stream().findFirst() + " " + numbers.stream().findAny() + " "
				+ Stream.empty().findFirst());
		Object[] objects = Stream.of("p", "q").toArray();
		String[] strings = Stream.of("r", "s", "t").toArray(String[]::new);
		Integer[] boxes = numbers.stream().filter(n -> n % 2 == 0).toArray(Integer[]::new);
		System.out.println(Arrays.toString(objects) + " " + Arrays.toString(strings) + " " + strings.length + " "
				+ Arrays.toString(boxes));
		StringBuilder sb = Stream.of("x", "y", "z").collect(StringBuilder::new, StringBuilder::append,
				StringBuilder::append);
		System.out.println(sb);
		ArrayList<Integer> collected = numbers.stream().collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
		System.out.println(collected);
		List<String> forEach = new ArrayList<>();
		Stream.of("f1", "f2").forEach(forEach::add);
		Stream.of("o1", "o2").forEachOrdered(forEach::add);
		System.out.println(forEach);
		List<Integer> immutable = Stream.of(1, 2).toList();
		try {
			immutable.add(3);
		} catch (UnsupportedOperationException e) {
			System.out.println("toList is unmodifiable");
		}
		System.out.println(Stream.of("a", null).toList());
	}

	static void primitives() {
		System.out.println("== primitives");
		System.out.println(IntStream.range(0, 5).boxed().toList() + " " + IntStream.rangeClosed(1, 5).sum() + " "
				+ IntStream.range(5, 5).count() + " " + IntStream.rangeClosed(3, 1).count());
		System.out.println(IntStream.of(3, 1, 2).sorted().boxed().toList() + " " + IntStream.of(1, 1, 2, 2, 3).distinct().count()
				+ " " + Arrays.toString(IntStream.of(4, 5).map(x -> x * x).toArray()));
		System.out.println(IntStream.of(5, 2, 9).min() + " " + IntStream.of(5, 2, 9).max() + " " + IntStream.empty().min()
				+ " " + IntStream.of(1, 2).average() + " " + IntStream.empty().average());
		System.out.println(IntStream.of(Integer.MAX_VALUE, 1).sum() + " "
				+ IntStream.of(Integer.MAX_VALUE, Integer.MAX_VALUE).average());
		System.out.println(IntStream.of(1, 2, 3).reduce(0, (a, b) -> a * 10 + b) + " "
				+ IntStream.of(1, 2, 3).reduce((a, b) -> a - b) + " " + IntStream.empty().reduce(Integer::sum));
		System.out.println(IntStream.of(1, 2, 3).mapToObj(i -> "#" + i).toList() + " "
				+ IntStream.of(1, 2).mapToLong(i -> i * 10000000000L).sum() + " "
				+ IntStream.of(1, 2).mapToDouble(i -> i / 4.0).sum() + " " + IntStream.of(1, 2).asLongStream().max()
				+ " " + IntStream.of(1, 2).asDoubleStream().boxed().toList());
		System.out.println(IntStream.of(1, 2, 3).flatMap(i -> IntStream.range(0, i)).boxed().toList());
		System.out.println(IntStream.of(5, 6, 7, 8).skip(1).limit(2).boxed().toList() + " "
				+ IntStream.of(1, 2, 3, 1).takeWhile(i -> i < 3).boxed().toList() + " "
				+ IntStream.of(1, 2, 3, 1).dropWhile(i -> i < 3).boxed().toList());
		System.out.println(IntStream.of(1, 2, 3).anyMatch(i -> i == 2) + " " + IntStream.of(1, 2, 3).allMatch(i -> i > 0)
				+ " " + IntStream.of(1, 2, 3).noneMatch(i -> i > 2) + " " + IntStream.of(7, 8).findFirst() + " "
				+ IntStream.empty().findAny());
		System.out.println(IntStream.iterate(1, i -> i * 2).limit(8).boxed().toList() + " "
				+ IntStream.iterate(1, i -> i <= 100, i -> i * 3).boxed().toList() + " "
				+ IntStream.generate(() -> 7).limit(3).sum());
		System.out.println(IntStream.concat(IntStream.of(1), IntStream.of(2, 3)).boxed().toList() + " "
				+ IntStream.builder().add(4).add(5).build().sum());
		ArrayList<Integer> viaCollect = IntStream.of(3, 4).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
		System.out.println(viaCollect);
		StringBuilder peeked = new StringBuilder();
		IntStream.of(1, 2).peek(peeked::append).forEach(i -> peeked.append('.'));
		System.out.println(peeked);
		System.out.println(LongStream.range(0, 4).boxed().toList() + " " + LongStream.rangeClosed(1, 4).sum() + " "
				+ LongStream.of(Long.MAX_VALUE, 1).sum() + " " + LongStream.of(3, 9).average() + " "
				+ LongStream.of(4, 2).min() + " " + LongStream.of(4, 2).max() + " " + LongStream.empty().max());
		System.out.println(LongStream.of(1, 2).mapToInt(l -> (int) l * 3).boxed().toList() + " "
				+ LongStream.of(1, 2).mapToDouble(l -> l / 2.0).sum() + " " + LongStream.of(1, 2).asDoubleStream().sum()
				+ " " + LongStream.of(5, 5, 6).distinct().count() + " "
				+ Arrays.toString(LongStream.of(3, 1, 2).sorted().toArray()) + " "
				+ LongStream.rangeClosed(Long.MAX_VALUE - 1, Long.MAX_VALUE).count());
		System.out.println(DoubleStream.of(1.5, 2.5).sum() + " " + DoubleStream.of(1.5, 2.5).average() + " "
				+ DoubleStream.of(3.0, -1.0).min() + " " + DoubleStream.of(3.0, -1.0).max() + " "
				+ DoubleStream.empty().average() + " " + DoubleStream.of(1.0, Double.NaN).max() + " "
				+ DoubleStream.of(-0.0, 0.0).min() + " " + DoubleStream.of(0.0, -0.0).max());
		System.out.println(DoubleStream.of(0.1, 0.2, 0.3).sum() + " " + DoubleStream.of(1e100, 1.0, -1e100).sum() + " "
				+ DoubleStream.of(1.0, 1e-16).sum() + " " + DoubleStream.of(1e308, 1e308, -1e308).sum() + " "
				+ DoubleStream.of(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).sum() + " "
				+ DoubleStream.of(-0.0).sum() + " " + DoubleStream.empty().sum());
		double[] values = new double[50];
		long seed = 12345;
		for (int i = 0; i < values.length; i++) {
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			values[i] = (seed >>> 11) * 0x1.0p-53 * Math.pow(10, (int) (seed & 15) - 8) * ((seed & 16) == 0 ? 1 : -1);
		}
		double plain = 0;
		for (double v : values) {
			plain += v;
		}
		System.out.println(DoubleStream.of(values).sum() + " " + plain + " " + DoubleStream.of(values).average());
		System.out.println(DoubleStream.of(3.5, 1.5, Double.NaN, -0.0, 0.0).sorted().boxed().toList() + " "
				+ DoubleStream.of(1.0, 1.0, 2.0).distinct().count() + " "
				+ DoubleStream.of(1.6, -1.6).mapToInt(d -> (int) d).boxed().toList() + " "
				+ DoubleStream.of(2.5).mapToLong(d -> (long) (d * 2)).sum() + " "
				+ DoubleStream.iterate(1.0, d -> d / 2).limit(4).boxed().toList() + " "
				+ DoubleStream.generate(() -> 0.5).limit(4).sum());
	}

	static void collectors() {
		System.out.println("== collectors");
		List<String> words = List.of("apple", "banana", "cherry", "avocado", "blueberry", "fig", "apple");
		System.out.println(words.stream().collect(Collectors.toSet()));
		show(words.stream().collect(Collectors.toCollection(TreeSet::new)));
		System.out.println(words.stream().collect(Collectors.toUnmodifiableList()));
		System.out.println(words.stream().collect(Collectors.joining()) + " "
				+ words.stream().collect(Collectors.joining(", ", "[", "]")) + " "
				+ Stream.<String>empty().collect(Collectors.joining(",", "<", ">")));
		System.out.println(words.stream().collect(Collectors.groupingBy(String::length)));
		System.out.println(words.stream().collect(Collectors.groupingBy(w -> w.charAt(0), Collectors.counting())));
		show(words.stream().collect(Collectors.groupingBy(w -> w.charAt(0), TreeMap::new,
				Collectors.mapping(String::toUpperCase, Collectors.toList()))));
		System.out.println(PEOPLE.stream().collect(Collectors.groupingBy(Person::city, Collectors.averagingInt(Person::age))));
		show(PEOPLE.stream().collect(Collectors.groupingBy(Person::city, LinkedHashMap::new,
				Collectors.mapping(Person::name, Collectors.joining("+")))));
		System.out.println(PEOPLE.stream().collect(Collectors.groupingBy(Person::age, Collectors.toSet())).keySet());
		Map<Integer, String> byLength = new HashMap<>();
		for (int i = 0; i < 40; i++) {
			byLength.put(i * 37 % 101, "v" + i);
		}
		System.out.println(byLength.entrySet().stream()
				.collect(Collectors.groupingBy(e -> e.getKey() % 7, Collectors.mapping(Map.Entry::getValue, Collectors.toList()))));
		System.out.println(words.stream().collect(Collectors.partitioningBy(w -> w.length() > 5)));
		System.out.println(words.stream().collect(Collectors.partitioningBy(w -> w.startsWith("a"), Collectors.counting())));
		Map<Boolean, List<String>> parts = Stream.<String>empty().collect(Collectors.partitioningBy(w -> true));
		System.out.println(parts + " " + parts.get(true) + " " + parts.containsKey(false) + " " + parts.size());
		System.out.println(PEOPLE.stream().collect(Collectors.toMap(Person::name, Person::age)));
		System.out.println(PEOPLE.stream().collect(Collectors.toMap(Person::city, p -> 1, Integer::sum)));
		show(PEOPLE.stream().collect(Collectors.toMap(Person::city, Person::name, (a, b) -> a + "&" + b,
				TreeMap::new)));
		System.out.println(words.stream().collect(Collectors.summingInt(String::length)) + " "
				+ words.stream().collect(Collectors.summingLong(String::length)) + " "
				+ words.stream().collect(Collectors.summingDouble(w -> w.length() / 10.0)) + " "
				+ words.stream().collect(Collectors.averagingInt(String::length)) + " "
				+ words.stream().collect(Collectors.averagingLong(String::length)) + " "
				+ words.stream().collect(Collectors.averagingDouble(w -> w.length() / 3.0)) + " "
				+ Stream.<String>empty().collect(Collectors.averagingInt(String::length)));
		System.out.println(words.stream().collect(Collectors.counting()) + " "
				+ words.stream().collect(Collectors.minBy(Comparator.naturalOrder())) + " "
				+ words.stream().collect(Collectors.maxBy(Comparator.comparingInt(String::length))) + " "
				+ Stream.<String>empty().collect(Collectors.maxBy(Comparator.naturalOrder())));
		System.out.println(words.stream().collect(Collectors.reducing(0, String::length, Integer::sum)) + " "
				+ words.stream().map(String::length).collect(Collectors.reducing(Integer::max)) + " "
				+ words.stream().map(String::length).collect(Collectors.reducing(100, Integer::min)));
		System.out.println(words.stream().collect(Collectors.collectingAndThen(Collectors.toList(), List::size)) + " "
				+ words.stream().collect(Collectors.filtering(w -> w.contains("e"), Collectors.toList())) + " "
				+ words.stream().collect(Collectors.flatMapping(w -> w.chars().limit(2).mapToObj(c -> (char) c),
						Collectors.toList())));
		show(words.stream().collect(Collectors.teeing(Collectors.counting(),
				Collectors.joining("/"), (n, s) -> n + ":" + s)));
		System.out.println(words.stream().collect(Collectors.summarizingInt(String::length)));
		System.out.println(words.stream().collect(Collectors.summarizingLong(String::length)));
		System.out.println(words.stream().collect(Collectors.summarizingDouble(w -> w.length() * 1.5)));
		Collector<String, StringJoiner, String> custom = Collector.of(() -> new StringJoiner("-"), StringJoiner::add,
				StringJoiner::merge, StringJoiner::toString);
		System.out.println(words.stream().limit(3).collect(custom) + " "
				+ custom.characteristics().contains(Collector.Characteristics.IDENTITY_FINISH));
		Collector<Integer, List<Integer>, List<Integer>> identity = Collector.of(ArrayList::new, List::add,
				(a, b) -> {
					a.addAll(b);
					return a;
				});
		System.out.println(Stream.of(1, 2).collect(identity) + " "
				+ identity.characteristics().contains(Collector.Characteristics.IDENTITY_FINISH));
		Map<String, Map<Integer, List<String>>> nested = PEOPLE.stream().collect(Collectors.groupingBy(Person::city,
				TreeMap::new, Collectors.groupingBy(Person::age, TreeMap::new, Collectors.mapping(Person::name,
						Collectors.toList()))));
		System.out.println(nested);
		Function<String, Integer> length = String::length;
		System.out.println(Stream.of("aa", "b", "cc").collect(Collectors.toUnmodifiableMap(s -> s, length)).get("aa")
				+ " " + Stream.of(1).collect(Collectors.toUnmodifiableSet()));
	}

	static void statistics() {
		System.out.println("== statistics");
		IntSummaryStatistics ints = IntStream.of(4, -2, 9).summaryStatistics();
		System.out.println(ints + " " + ints.getCount() + " " + ints.getSum() + " " + ints.getMin() + " " + ints.getMax()
				+ " " + ints.getAverage());
		System.out.println(new IntSummaryStatistics());
		LongSummaryStatistics longs = LongStream.of(1L << 40, 3).summaryStatistics();
		System.out.println(longs + " " + new LongSummaryStatistics());
		DoubleSummaryStatistics doubles = DoubleStream.of(0.1, 0.7, 1e-17, 3.3, 1e16, 2.5, -1e16, 0.3)
				.summaryStatistics();
		System.out.println(doubles + " " + doubles.getSum() + " " + doubles.getAverage());
		System.out.println(new DoubleSummaryStatistics());
		DoubleSummaryStatistics withNaN = DoubleStream.of(1, Double.NaN, 3).summaryStatistics();
		System.out.println(withNaN.getMin() + " " + withNaN.getMax() + " " + withNaN.getSum());
		IntSummaryStatistics combined = new IntSummaryStatistics();
		combined.accept(5);
		IntSummaryStatistics other = new IntSummaryStatistics();
		other.accept(-5);
		other.accept(10);
		combined.combine(other);
		System.out.println(combined);
		LongSummaryStatistics fromInts = new LongSummaryStatistics();
		fromInts.accept(7);
		fromInts.accept(7L);
		System.out.println(fromInts.getSum());
		System.out.println(new IntSummaryStatistics(3, 1, 5, 9) + " " + new DoubleSummaryStatistics(2, 0.5, 1.5, 2.0));
		try {
			new IntSummaryStatistics(-1, 0, 0, 0);
		} catch (IllegalArgumentException e) {
			System.out.println(e.getMessage());
		}
		try {
			new LongSummaryStatistics(2, 5, 1, 0);
		} catch (IllegalArgumentException e) {
			System.out.println(e.getMessage());
		}
		try {
			new DoubleSummaryStatistics(2, Double.NaN, 1, 0);
		} catch (IllegalArgumentException e) {
			System.out.println(e.getMessage());
		}
		StringJoiner joiner = new StringJoiner(", ", "{", "}");
		System.out.println(joiner + " " + joiner.length());
		joiner.setEmptyValue("EMPTY");
		System.out.println(joiner + " " + joiner.length());
		joiner.add("one").add("two");
		StringJoiner more = new StringJoiner("-", "(", ")");
		more.add("x").add("y");
		joiner.merge(more);
		System.out.println(joiner + " " + joiner.length());
	}

	static void errors() {
		System.out.println("== errors");
		Stream<Integer> used = Stream.of(1, 2);
		used.map(x -> x);
		try {
			used.map(x -> x);
		} catch (IllegalStateException e) {
			System.out.println("reuse: " + e.getMessage());
		}
		Stream<Integer> consumed = Stream.of(1, 2);
		consumed.count();
		try {
			consumed.forEach(x -> {
			});
		} catch (IllegalStateException e) {
			System.out.println("consumed: " + e.getMessage());
		}
		IntStream ints = IntStream.of(1);
		ints.sum();
		try {
			ints.sum();
		} catch (IllegalStateException e) {
			System.out.println("int reuse: " + e.getMessage());
		}
		Stream<Integer> a = Stream.of(1);
		Stream.concat(a, Stream.of(2));
		try {
			a.toList();
		} catch (IllegalStateException e) {
			System.out.println("concat: " + e.getMessage());
		}
		try {
			Stream.of(1).limit(-1);
		} catch (IllegalArgumentException e) {
			System.out.println("limit: " + e.getMessage());
		}
		try {
			IntStream.of(1).skip(-5);
		} catch (IllegalArgumentException e) {
			System.out.println("skip: " + e.getMessage());
		}
		try {
			Stream.of("a", "b", "a").collect(Collectors.toMap(s -> s, String::length));
		} catch (IllegalStateException e) {
			System.out.println(e.getMessage());
		}
		try {
			Stream.of("a", "b").collect(Collectors.toMap(s -> s, s -> null));
		} catch (NullPointerException e) {
			System.out.println("toMap null value: NullPointerException");
		}
		try {
			Stream.of("a", null).collect(Collectors.groupingBy(s -> s));
		} catch (NullPointerException e) {
			System.out.println(e.getMessage());
		}
		try {
			Stream.of("a", null).findFirst();
			Stream.of((String) null).findFirst();
		} catch (NullPointerException e) {
			System.out.println("findFirst null: NullPointerException");
		}
		try {
			Stream.of(1, null).collect(Collectors.toUnmodifiableList());
		} catch (NullPointerException e) {
			System.out.println("toUnmodifiableList null: NullPointerException");
		}
		try {
			Stream.of(1).iterator().remove();
		} catch (UnsupportedOperationException | IllegalStateException e) {
			System.out.println("iterator remove: " + e.getClass().getName());
		}
		try {
			Iterator<Integer> it = Stream.<Integer>empty().iterator();
			it.next();
		} catch (java.util.NoSuchElementException e) {
			System.out.println("empty iterator: NoSuchElementException");
		}
		try {
			Stream.Builder<Integer> b = Stream.builder();
			b.build();
			b.add(1);
		} catch (IllegalStateException e) {
			System.out.println("builder: IllegalStateException");
		}
		try {
			Arrays.stream(new int[3], 2, 1);
		} catch (RuntimeException e) {
			System.out.println(e.getClass().getName() + ": " + e.getMessage());
		}
		try {
			Arrays.stream(new String[3], -1, 2);
		} catch (RuntimeException e) {
			System.out.println(e.getClass().getName() + ": " + e.getMessage());
		}
		try {
			Arrays.stream(new long[3], 0, 4);
		} catch (RuntimeException e) {
			System.out.println(e.getClass().getName() + ": " + e.getMessage());
		}
		try {
			Stream.of(1, 2).map(x -> 10 / (x - 2)).toList();
		} catch (ArithmeticException e) {
			System.out.println("lambda exception: " + e.getMessage());
		}
	}

	static void other() {
		System.out.println("== other");
		StringBuilder log = new StringBuilder();
		Stream<Integer> closing = Stream.of(1, 2).onClose(() -> log.append("first ")).onClose(() -> log.append("second "));
		System.out.println(closing.map(x -> x + 1).toList());
		closing.close();
		closing.close();
		System.out.println(log);
		log.setLength(0);
		try (Stream<String> s = Stream.of("r").onClose(() -> log.append("closed"))) {
			System.out.println(s.findFirst().get());
		}
		System.out.println(log);
		log.setLength(0);
		Stream<Integer> concatenated = Stream.concat(Stream.of(1).onClose(() -> log.append("a ")),
				Stream.of(2).onClose(() -> log.append("b ")));
		concatenated.close();
		System.out.println(log);
		log.setLength(0);
		Stream.of(1, 2).flatMap(x -> Stream.of(x).onClose(() -> log.append("inner" + x + " "))).forEach(x -> {
		});
		System.out.println(log);
		Stream<Integer> closed = Stream.of(1);
		closed.close();
		try {
			closed.count();
		} catch (IllegalStateException e) {
			System.out.println("closed: " + e.getMessage());
		}
		try {
			Stream.of(1).onClose(() -> {
				throw new IllegalStateException("one");
			}).onClose(() -> {
				throw new IllegalArgumentException("two");
			}).close();
		} catch (IllegalStateException e) {
			System.out.println(e.getMessage() + " suppressed " + e.getSuppressed().length + " "
					+ e.getSuppressed()[0].getMessage());
		}
		Collection<String> words = new ArrayList<>(List.of("delta", "alpha", "charlie", "bravo"));
		System.out.println(words.stream().sorted().collect(Collectors.joining(" ")));
		Set<Character> initials = words.stream().map(w -> w.charAt(0)).collect(Collectors.toCollection(TreeSet::new));
		System.out.println(initials);
		System.out.println(IntStream.rangeClosed(1, 5).mapToObj(i -> "*".repeat(i)).collect(Collectors.joining("\n")));
		System.out.println(IntStream.range(0, 20).filter(i -> i % 3 == 0).mapToObj(Integer::toString)
				.collect(Collectors.joining(",", "{", "}")));
	}
}
