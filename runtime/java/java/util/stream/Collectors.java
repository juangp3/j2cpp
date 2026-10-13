package java.util.stream;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Collectors, written from the Java SE specification. The maps and sets they
 * build are the ones the specification names or the JDK uses (HashMap,
 * HashSet, ArrayList), and the accumulators make the same map calls
 * (computeIfAbsent, putIfAbsent, merge), so iteration order matches the JVM.
 * Double sums and averages use Kahan summation, as the JDK does.
 *
 * Limits: groupingByConcurrent and toConcurrentMap are not provided.
 * toUnmodifiableSet and toUnmodifiableMap iterate in insertion order (see
 * Set.of and Map.of in runtime/README.md).
 */
public final class Collectors {
	private Collectors() {
	}

	static final Set<Collector.Characteristics> CH_ID = characteristics(Collector.Characteristics.IDENTITY_FINISH);
	static final Set<Collector.Characteristics> CH_NOID = characteristics();
	static final Set<Collector.Characteristics> CH_UNORDERED_ID = characteristics(
			Collector.Characteristics.UNORDERED, Collector.Characteristics.IDENTITY_FINISH);
	static final Set<Collector.Characteristics> CH_UNORDERED_NOID = characteristics(
			Collector.Characteristics.UNORDERED);

	private static Set<Collector.Characteristics> characteristics(Collector.Characteristics... values) {
		Set<Collector.Characteristics> set = new HashSet<Collector.Characteristics>();
		for (Collector.Characteristics c : values) {
			set.add(c);
		}
		return Collections.unmodifiableSet(set);
	}

	static final class CollectorImpl<T, A, R> implements Collector<T, A, R> {
		private final Supplier<A> supplier;
		private final BiConsumer<A, T> accumulator;
		private final BinaryOperator<A> combiner;
		private final Function<A, R> finisher;
		private final Set<Characteristics> characteristics;

		/** A null finisher is the identity, for IDENTITY_FINISH collectors */
		CollectorImpl(Supplier<A> supplier, BiConsumer<A, T> accumulator, BinaryOperator<A> combiner,
				Function<A, R> finisher, Set<Characteristics> characteristics) {
			this.supplier = supplier;
			this.accumulator = accumulator;
			this.combiner = combiner;
			this.finisher = finisher;
			this.characteristics = characteristics;
		}

		public Supplier<A> supplier() {
			return supplier;
		}

		public BiConsumer<A, T> accumulator() {
			return accumulator;
		}

		public BinaryOperator<A> combiner() {
			return combiner;
		}

		public Function<A, R> finisher() {
			return finisher != null ? finisher : new CastingIdentity<A, R>();
		}

		public Set<Characteristics> characteristics() {
			return characteristics;
		}
	}

	static final class CastingIdentity<A, R> implements Function<A, R> {
		@SuppressWarnings("unchecked")
		public R apply(A a) {
			return (R) a;
		}
	}

	/** Kahan summation state: sum, compensation, plain sum and count */
	static final class DoubleSum {
		double sum;
		double compensation;
		double simpleSum;
		long count;

		void add(double value) {
			count++;
			simpleSum += value;
			kahan(value);
		}

		private void kahan(double value) {
			double y = value - compensation;
			double t = sum + y;
			compensation = (t - sum) - y;
			sum = t;
		}

		DoubleSum combine(DoubleSum other) {
			count += other.count;
			simpleSum += other.simpleSum;
			kahan(other.sum);
			kahan(-other.compensation);
			return this;
		}

		double result() {
			return AbstractPipeline.finalSum(sum, compensation, simpleSum);
		}
	}

	public static <T, C extends Collection<T>> Collector<T, ?, C> toCollection(Supplier<C> collectionFactory) {
		Objects.requireNonNull(collectionFactory);
		return new CollectorImpl<T, C, C>(collectionFactory, (c, t) -> c.add(t), (r1, r2) -> {
			r1.addAll(r2);
			return r1;
		}, null, CH_ID);
	}

	public static <T> Collector<T, ?, List<T>> toList() {
		return new CollectorImpl<T, List<T>, List<T>>(() -> new ArrayList<T>(), (list, t) -> list.add(t),
				(left, right) -> {
					left.addAll(right);
					return left;
				}, null, CH_ID);
	}

	public static <T> Collector<T, ?, List<T>> toUnmodifiableList() {
		return new CollectorImpl<T, List<T>, List<T>>(() -> new ArrayList<T>(), (list, t) -> list.add(t),
				(left, right) -> {
					left.addAll(right);
					return left;
				}, list -> List.copyOf(list), CH_NOID);
	}

	public static <T> Collector<T, ?, Set<T>> toSet() {
		return new CollectorImpl<T, Set<T>, Set<T>>(() -> new HashSet<T>(), (set, t) -> set.add(t),
				(left, right) -> {
					left.addAll(right);
					return left;
				}, null, CH_UNORDERED_ID);
	}

	public static <T> Collector<T, ?, Set<T>> toUnmodifiableSet() {
		return new CollectorImpl<T, Set<T>, Set<T>>(() -> new HashSet<T>(), (set, t) -> set.add(t),
				(left, right) -> {
					left.addAll(right);
					return left;
				}, set -> Set.copyOf(set), CH_UNORDERED_NOID);
	}

	public static Collector<CharSequence, ?, String> joining() {
		return new CollectorImpl<CharSequence, StringBuilder, String>(() -> new StringBuilder(),
				(sb, s) -> sb.append(s), (r1, r2) -> {
					r1.append(r2);
					return r1;
				}, sb -> sb.toString(), CH_NOID);
	}

	public static Collector<CharSequence, ?, String> joining(CharSequence delimiter) {
		return joining(delimiter, "", "");
	}

	public static Collector<CharSequence, ?, String> joining(CharSequence delimiter, CharSequence prefix,
			CharSequence suffix) {
		Objects.requireNonNull(delimiter, "The delimiter must not be null");
		Objects.requireNonNull(prefix, "The prefix must not be null");
		Objects.requireNonNull(suffix, "The suffix must not be null");
		return new CollectorImpl<CharSequence, StringJoiner, String>(
				() -> new StringJoiner(delimiter, prefix, suffix), (joiner, s) -> joiner.add(s),
				(left, right) -> left.merge(right), joiner -> joiner.toString(), CH_NOID);
	}

	public static <T, U, A, R> Collector<T, ?, R> mapping(Function<? super T, ? extends U> mapper,
			Collector<? super U, A, R> downstream) {
		Objects.requireNonNull(mapper);
		BiConsumer<A, ? super U> downstreamAccumulator = downstream.accumulator();
		return new CollectorImpl<T, A, R>(downstream.supplier(),
				(r, t) -> downstreamAccumulator.accept(r, mapper.apply(t)), downstream.combiner(),
				downstream.finisher(), downstream.characteristics());
	}

	public static <T, U, A, R> Collector<T, ?, R> flatMapping(
			Function<? super T, ? extends Stream<? extends U>> mapper, Collector<? super U, A, R> downstream) {
		Objects.requireNonNull(mapper);
		BiConsumer<A, ? super U> downstreamAccumulator = downstream.accumulator();
		return new CollectorImpl<T, A, R>(downstream.supplier(), (r, t) -> {
			Stream<? extends U> result = mapper.apply(t);
			if (result != null) {
				try {
					result.sequential().forEach(u -> downstreamAccumulator.accept(r, u));
				} finally {
					result.close();
				}
			}
		}, downstream.combiner(), downstream.finisher(), downstream.characteristics());
	}

	public static <T, A, R> Collector<T, ?, R> filtering(Predicate<? super T> predicate,
			Collector<? super T, A, R> downstream) {
		Objects.requireNonNull(predicate);
		BiConsumer<A, ? super T> downstreamAccumulator = downstream.accumulator();
		return new CollectorImpl<T, A, R>(downstream.supplier(), (r, t) -> {
			if (predicate.test(t)) {
				downstreamAccumulator.accept(r, t);
			}
		}, downstream.combiner(), downstream.finisher(), downstream.characteristics());
	}

	public static <T, A, R, RR> Collector<T, A, RR> collectingAndThen(Collector<T, A, R> downstream,
			Function<R, RR> finisher) {
		Objects.requireNonNull(finisher);
		Set<Collector.Characteristics> characteristics = downstream.characteristics();
		if (characteristics.contains(Collector.Characteristics.IDENTITY_FINISH)) {
			Set<Collector.Characteristics> without = new HashSet<Collector.Characteristics>(characteristics);
			without.remove(Collector.Characteristics.IDENTITY_FINISH);
			characteristics = Collections.unmodifiableSet(without);
		}
		return new CollectorImpl<T, A, RR>(downstream.supplier(), downstream.accumulator(), downstream.combiner(),
				downstream.finisher().andThen(finisher), characteristics);
	}

	public static <T> Collector<T, ?, Long> counting() {
		return summingLong(e -> 1L);
	}

	public static <T> Collector<T, ?, Optional<T>> minBy(java.util.Comparator<? super T> comparator) {
		return reducing(BinaryOperator.<T>minBy(comparator));
	}

	public static <T> Collector<T, ?, Optional<T>> maxBy(java.util.Comparator<? super T> comparator) {
		return reducing(BinaryOperator.<T>maxBy(comparator));
	}

	public static <T> Collector<T, ?, Integer> summingInt(ToIntFunction<? super T> mapper) {
		Objects.requireNonNull(mapper);
		return new CollectorImpl<T, int[], Integer>(() -> new int[1], (a, t) -> {
			a[0] += mapper.applyAsInt(t);
		}, (a, b) -> {
			a[0] += b[0];
			return a;
		}, a -> Integer.valueOf(a[0]), CH_NOID);
	}

	public static <T> Collector<T, ?, Long> summingLong(ToLongFunction<? super T> mapper) {
		Objects.requireNonNull(mapper);
		return new CollectorImpl<T, long[], Long>(() -> new long[1], (a, t) -> {
			a[0] += mapper.applyAsLong(t);
		}, (a, b) -> {
			a[0] += b[0];
			return a;
		}, a -> Long.valueOf(a[0]), CH_NOID);
	}

	public static <T> Collector<T, ?, Double> summingDouble(ToDoubleFunction<? super T> mapper) {
		Objects.requireNonNull(mapper);
		return new CollectorImpl<T, DoubleSum, Double>(() -> new DoubleSum(), (a, t) -> a.add(mapper.applyAsDouble(t)),
				(a, b) -> a.combine(b), a -> Double.valueOf(a.result()), CH_NOID);
	}

	public static <T> Collector<T, ?, Double> averagingInt(ToIntFunction<? super T> mapper) {
		Objects.requireNonNull(mapper);
		return new CollectorImpl<T, long[], Double>(() -> new long[2], (a, t) -> {
			a[0] += mapper.applyAsInt(t);
			a[1]++;
		}, (a, b) -> {
			a[0] += b[0];
			a[1] += b[1];
			return a;
		}, a -> Double.valueOf(a[1] == 0 ? 0.0d : (double) a[0] / a[1]), CH_NOID);
	}

	public static <T> Collector<T, ?, Double> averagingLong(ToLongFunction<? super T> mapper) {
		Objects.requireNonNull(mapper);
		return new CollectorImpl<T, long[], Double>(() -> new long[2], (a, t) -> {
			a[0] += mapper.applyAsLong(t);
			a[1]++;
		}, (a, b) -> {
			a[0] += b[0];
			a[1] += b[1];
			return a;
		}, a -> Double.valueOf(a[1] == 0 ? 0.0d : (double) a[0] / a[1]), CH_NOID);
	}

	public static <T> Collector<T, ?, Double> averagingDouble(ToDoubleFunction<? super T> mapper) {
		Objects.requireNonNull(mapper);
		return new CollectorImpl<T, DoubleSum, Double>(() -> new DoubleSum(), (a, t) -> a.add(mapper.applyAsDouble(t)),
				(a, b) -> a.combine(b), a -> Double.valueOf(a.count == 0 ? 0.0d : a.result() / a.count), CH_NOID);
	}

	/** A mutable cell for the reducing collectors */
	static final class Cell<T> {
		T value;
		boolean present;

		Cell(T value) {
			this.value = value;
		}
	}

	public static <T> Collector<T, ?, T> reducing(T identity, BinaryOperator<T> op) {
		Objects.requireNonNull(op);
		return new CollectorImpl<T, Cell<T>, T>(() -> new Cell<T>(identity), (a, t) -> {
			a.value = op.apply(a.value, t);
		}, (a, b) -> {
			a.value = op.apply(a.value, b.value);
			return a;
		}, a -> a.value, CH_NOID);
	}

	public static <T> Collector<T, ?, Optional<T>> reducing(BinaryOperator<T> op) {
		Objects.requireNonNull(op);
		return new CollectorImpl<T, Cell<T>, Optional<T>>(() -> new Cell<T>(null), (a, t) -> {
			if (a.present) {
				a.value = op.apply(a.value, t);
			} else {
				a.value = t;
				a.present = true;
			}
		}, (a, b) -> {
			if (b.present) {
				if (a.present) {
					a.value = op.apply(a.value, b.value);
				} else {
					a.value = b.value;
					a.present = true;
				}
			}
			return a;
		}, a -> Optional.ofNullable(a.value), CH_NOID);
	}

	public static <T, U> Collector<T, ?, U> reducing(U identity, Function<? super T, ? extends U> mapper,
			BinaryOperator<U> op) {
		Objects.requireNonNull(mapper);
		Objects.requireNonNull(op);
		return new CollectorImpl<T, Cell<U>, U>(() -> new Cell<U>(identity), (a, t) -> {
			a.value = op.apply(a.value, mapper.apply(t));
		}, (a, b) -> {
			a.value = op.apply(a.value, b.value);
			return a;
		}, a -> a.value, CH_NOID);
	}

	public static <T, K> Collector<T, ?, Map<K, List<T>>> groupingBy(Function<? super T, ? extends K> classifier) {
		return groupingBy(classifier, Collectors.<T>toList());
	}

	public static <T, K, A, D> Collector<T, ?, Map<K, D>> groupingBy(Function<? super T, ? extends K> classifier,
			Collector<? super T, A, D> downstream) {
		return groupingBy(classifier, () -> new HashMap<K, D>(), downstream);
	}

	@SuppressWarnings("unchecked")
	public static <T, K, D, A, M extends Map<K, D>> Collector<T, ?, M> groupingBy(
			Function<? super T, ? extends K> classifier, Supplier<M> mapFactory,
			Collector<? super T, A, D> downstream) {
		Objects.requireNonNull(classifier);
		Objects.requireNonNull(mapFactory);
		Objects.requireNonNull(downstream);
		Supplier<A> downstreamSupplier = downstream.supplier();
		BiConsumer<A, ? super T> downstreamAccumulator = downstream.accumulator();
		BiConsumer<Map<K, A>, T> accumulator = (m, t) -> {
			K key = Objects.requireNonNull(classifier.apply(t), "element cannot be mapped to a null key");
			A container = m.computeIfAbsent(key, k -> downstreamSupplier.get());
			downstreamAccumulator.accept(container, t);
		};
		BinaryOperator<A> downstreamCombiner = downstream.combiner();
		BinaryOperator<Map<K, A>> merger = (left, right) -> {
			for (Map.Entry<K, A> e : right.entrySet()) {
				left.merge(e.getKey(), e.getValue(), downstreamCombiner);
			}
			return left;
		};
		Supplier<Map<K, A>> factory = (Supplier<Map<K, A>>) (Supplier<?>) mapFactory;
		if (downstream.characteristics().contains(Collector.Characteristics.IDENTITY_FINISH)) {
			return new CollectorImpl<T, Map<K, A>, M>(factory, accumulator, merger, null, CH_ID);
		}
		Function<A, A> downstreamFinisher = (Function<A, A>) (Function<?, ?>) downstream.finisher();
		Function<Map<K, A>, M> finisher = intermediate -> {
			intermediate.replaceAll((k, v) -> downstreamFinisher.apply(v));
			return (M) intermediate;
		};
		return new CollectorImpl<T, Map<K, A>, M>(factory, accumulator, merger, finisher, CH_NOID);
	}

	public static <T> Collector<T, ?, Map<Boolean, List<T>>> partitioningBy(Predicate<? super T> predicate) {
		return partitioningBy(predicate, Collectors.<T>toList());
	}

	public static <T, D, A> Collector<T, ?, Map<Boolean, D>> partitioningBy(Predicate<? super T> predicate,
			Collector<? super T, A, D> downstream) {
		Objects.requireNonNull(predicate);
		Supplier<A> downstreamSupplier = downstream.supplier();
		BiConsumer<A, ? super T> downstreamAccumulator = downstream.accumulator();
		BinaryOperator<A> op = downstream.combiner();
		BiConsumer<Partition<A>, T> accumulator = (result, t) -> downstreamAccumulator
				.accept(predicate.test(t) ? result.forTrue : result.forFalse, t);
		BinaryOperator<Partition<A>> merger = (left, right) -> new Partition<A>(op.apply(left.forTrue, right.forTrue),
				op.apply(left.forFalse, right.forFalse));
		Supplier<Partition<A>> supplier = () -> new Partition<A>(downstreamSupplier.get(), downstreamSupplier.get());
		if (downstream.characteristics().contains(Collector.Characteristics.IDENTITY_FINISH)) {
			return new CollectorImpl<T, Partition<A>, Map<Boolean, D>>(supplier, accumulator, merger, null, CH_ID);
		}
		Function<A, D> downstreamFinisher = downstream.finisher();
		Function<Partition<A>, Map<Boolean, D>> finisher = par -> new Partition<D>(
				downstreamFinisher.apply(par.forTrue), downstreamFinisher.apply(par.forFalse));
		return new CollectorImpl<T, Partition<A>, Map<Boolean, D>>(supplier, accumulator, merger, finisher, CH_NOID);
	}

	/** The two-entry map partitioningBy returns: false, then true */
	static final class Partition<T> extends AbstractMap<Boolean, T> {
		final T forTrue;
		final T forFalse;

		Partition(T forTrue, T forFalse) {
			this.forTrue = forTrue;
			this.forFalse = forFalse;
		}

		public Set<Map.Entry<Boolean, T>> entrySet() {
			return new PartitionEntries<T>(this);
		}

		public boolean containsKey(Object key) {
			return key instanceof Boolean;
		}

		public T get(Object key) {
			if (key instanceof Boolean) {
				return ((Boolean) key).booleanValue() ? forTrue : forFalse;
			}
			return null;
		}

		public int size() {
			return 2;
		}
	}

	static final class PartitionEntries<T> extends AbstractSet<Map.Entry<Boolean, T>> {
		private final Partition<T> partition;

		PartitionEntries(Partition<T> partition) {
			this.partition = partition;
		}

		public Iterator<Map.Entry<Boolean, T>> iterator() {
			List<Map.Entry<Boolean, T>> entries = new ArrayList<Map.Entry<Boolean, T>>(2);
			entries.add(new AbstractMap.SimpleImmutableEntry<Boolean, T>(Boolean.FALSE, partition.forFalse));
			entries.add(new AbstractMap.SimpleImmutableEntry<Boolean, T>(Boolean.TRUE, partition.forTrue));
			return Collections.unmodifiableList(entries).iterator();
		}

		public int size() {
			return 2;
		}
	}

	public static <T, K, U> Collector<T, ?, Map<K, U>> toMap(Function<? super T, ? extends K> keyMapper,
			Function<? super T, ? extends U> valueMapper) {
		Objects.requireNonNull(keyMapper);
		Objects.requireNonNull(valueMapper);
		return new CollectorImpl<T, Map<K, U>, Map<K, U>>(() -> new HashMap<K, U>(), (map, element) -> {
			K k = keyMapper.apply(element);
			U v = Objects.requireNonNull(valueMapper.apply(element));
			U u = map.putIfAbsent(k, v);
			if (u != null) {
				throw duplicateKeyException(k, u, v);
			}
		}, (left, right) -> {
			for (Map.Entry<K, U> e : right.entrySet()) {
				K k = e.getKey();
				U v = Objects.requireNonNull(e.getValue());
				U u = left.putIfAbsent(k, v);
				if (u != null) {
					throw duplicateKeyException(k, u, v);
				}
			}
			return left;
		}, null, CH_ID);
	}

	private static IllegalStateException duplicateKeyException(Object k, Object u, Object v) {
		return new IllegalStateException(
				String.format("Duplicate key %s (attempted merging values %s and %s)", k, u, v));
	}

	public static <T, K, U> Collector<T, ?, Map<K, U>> toMap(Function<? super T, ? extends K> keyMapper,
			Function<? super T, ? extends U> valueMapper, BinaryOperator<U> mergeFunction) {
		return toMap(keyMapper, valueMapper, mergeFunction, () -> new HashMap<K, U>());
	}

	public static <T, K, U, M extends Map<K, U>> Collector<T, ?, M> toMap(Function<? super T, ? extends K> keyMapper,
			Function<? super T, ? extends U> valueMapper, BinaryOperator<U> mergeFunction, Supplier<M> mapFactory) {
		Objects.requireNonNull(keyMapper);
		Objects.requireNonNull(valueMapper);
		Objects.requireNonNull(mergeFunction);
		Objects.requireNonNull(mapFactory);
		return new CollectorImpl<T, M, M>(mapFactory,
				(map, element) -> map.merge(keyMapper.apply(element), valueMapper.apply(element), mergeFunction),
				(left, right) -> {
					for (Map.Entry<K, U> e : right.entrySet()) {
						left.merge(e.getKey(), e.getValue(), mergeFunction);
					}
					return left;
				}, null, CH_ID);
	}

	public static <T, K, U> Collector<T, ?, Map<K, U>> toUnmodifiableMap(Function<? super T, ? extends K> keyMapper,
			Function<? super T, ? extends U> valueMapper) {
		Objects.requireNonNull(keyMapper, "keyMapper");
		Objects.requireNonNull(valueMapper, "valueMapper");
		return collectingAndThen(Collectors.<T, K, U>toMap(keyMapper, valueMapper), map -> Map.copyOf(map));
	}

	public static <T, K, U> Collector<T, ?, Map<K, U>> toUnmodifiableMap(Function<? super T, ? extends K> keyMapper,
			Function<? super T, ? extends U> valueMapper, BinaryOperator<U> mergeFunction) {
		Objects.requireNonNull(keyMapper, "keyMapper");
		Objects.requireNonNull(valueMapper, "valueMapper");
		Objects.requireNonNull(mergeFunction, "mergeFunction");
		return collectingAndThen(Collectors.<T, K, U, HashMap<K, U>>toMap(keyMapper, valueMapper, mergeFunction,
				() -> new HashMap<K, U>()), map -> Map.copyOf(map));
	}

	public static <T> Collector<T, ?, IntSummaryStatistics> summarizingInt(ToIntFunction<? super T> mapper) {
		Objects.requireNonNull(mapper);
		return new CollectorImpl<T, IntSummaryStatistics, IntSummaryStatistics>(() -> new IntSummaryStatistics(),
				(r, t) -> r.accept(mapper.applyAsInt(t)), (l, r) -> {
					l.combine(r);
					return l;
				}, null, CH_ID);
	}

	public static <T> Collector<T, ?, LongSummaryStatistics> summarizingLong(ToLongFunction<? super T> mapper) {
		Objects.requireNonNull(mapper);
		return new CollectorImpl<T, LongSummaryStatistics, LongSummaryStatistics>(() -> new LongSummaryStatistics(),
				(r, t) -> r.accept(mapper.applyAsLong(t)), (l, r) -> {
					l.combine(r);
					return l;
				}, null, CH_ID);
	}

	public static <T> Collector<T, ?, DoubleSummaryStatistics> summarizingDouble(
			ToDoubleFunction<? super T> mapper) {
		Objects.requireNonNull(mapper);
		return new CollectorImpl<T, DoubleSummaryStatistics, DoubleSummaryStatistics>(
				() -> new DoubleSummaryStatistics(), (r, t) -> r.accept(mapper.applyAsDouble(t)), (l, r) -> {
					l.combine(r);
					return l;
				}, null, CH_ID);
	}

	/** The two containers of teeing */
	static final class Pair<A1, A2> {
		final A1 left;
		final A2 right;

		Pair(A1 left, A2 right) {
			this.left = left;
			this.right = right;
		}
	}

	public static <T, R1, R2, R> Collector<T, ?, R> teeing(Collector<? super T, ?, R1> downstream1,
			Collector<? super T, ?, R2> downstream2, BiFunction<? super R1, ? super R2, R> merger) {
		return teeing0(downstream1, downstream2, merger);
	}

	private static <T, A1, A2, R1, R2, R> Collector<T, ?, R> teeing0(Collector<? super T, A1, R1> downstream1,
			Collector<? super T, A2, R2> downstream2, BiFunction<? super R1, ? super R2, R> merger) {
		Objects.requireNonNull(downstream1, "downstream1");
		Objects.requireNonNull(downstream2, "downstream2");
		Objects.requireNonNull(merger, "merger");
		Supplier<A1> s1 = downstream1.supplier();
		Supplier<A2> s2 = downstream2.supplier();
		BiConsumer<A1, ? super T> a1 = downstream1.accumulator();
		BiConsumer<A2, ? super T> a2 = downstream2.accumulator();
		BinaryOperator<A1> c1 = downstream1.combiner();
		BinaryOperator<A2> c2 = downstream2.combiner();
		Function<A1, R1> f1 = downstream1.finisher();
		Function<A2, R2> f2 = downstream2.finisher();
		return new CollectorImpl<T, Pair<A1, A2>, R>(() -> new Pair<A1, A2>(s1.get(), s2.get()), (p, t) -> {
			a1.accept(p.left, t);
			a2.accept(p.right, t);
		}, (x, y) -> new Pair<A1, A2>(c1.apply(x.left, y.left), c2.apply(x.right, y.right)),
				p -> merger.apply(f1.apply(p.left), f2.apply(p.right)), CH_NOID);
	}
}
