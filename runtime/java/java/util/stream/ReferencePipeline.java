package java.util.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.function.UnaryOperator;

/**
 * The runtime's Stream, written from the Java SE specification: a stage whose
 * source pulls elements through the stages before it (see AbstractPipeline).
 * count() does not traverse a stream whose size is known, so peek and map
 * actions do not run there, as on the JVM.
 */
final class ReferencePipeline<T> extends AbstractPipeline implements Stream<T> {
	final RefSource<T> source;

	ReferencePipeline(RefSource<T> source, Shared shared) {
		super(shared);
		this.source = source;
	}

	static <T> ReferencePipeline<T> head(RefSource<T> source) {
		return new ReferencePipeline<T>(source, new Shared());
	}

	static <T> Stream<T> ofArray(Object[] array, int from, int to) {
		return head(new ArraySource<T>(array, from, to));
	}

	static <T> Stream<T> ofSpliterator(Spliterator<T> spliterator, boolean parallel) {
		ReferencePipeline<T> pipeline = head(new SpliteratorSource<T>(spliterator));
		pipeline.shared.parallel = parallel;
		return pipeline;
	}

	static <T> Stream<T> iterate(T seed, Predicate<? super T> hasNext, UnaryOperator<T> next) {
		return head(new Iterate<T>(seed, hasNext, next));
	}

	static <T> Stream<T> generate(Supplier<? extends T> supplier) {
		return head(new Generate<T>(supplier));
	}

	static <T> Stream<T> concat(Stream<? extends T> a, Stream<? extends T> b) {
		RefSource<T> first = sourceOf(a);
		RefSource<T> second = sourceOf(b);
		ReferencePipeline<T> pipeline = head(new Concat<T>(first, second));
		pipeline.shared.parallel = a.isParallel() || b.isParallel();
		pipeline.addCloseHandler(new CloseBoth(a, b));
		return pipeline;
	}

	/** The source of a stream another stream consumes (concat, flatMap) */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static <T> RefSource<T> sourceOf(Stream<? extends T> stream) {
		if (stream instanceof ReferencePipeline) {
			ReferencePipeline<T> pipeline = (ReferencePipeline) stream;
			pipeline.link();
			return pipeline.source;
		}
		return new IteratorSource<T>((Iterator<T>) stream.iterator());
	}

	private <R> Stream<R> chain(RefSource<R> next) {
		link();
		return new ReferencePipeline<R>(next, shared);
	}

	private RefSource<T> consume() {
		link();
		return source;
	}

	// BaseStream

	public Iterator<T> iterator() {
		return new SourceIterator<T>(consume());
	}

	public boolean isParallel() {
		return shared.parallel;
	}

	public Stream<T> sequential() {
		shared.parallel = false;
		return this;
	}

	public Stream<T> parallel() {
		shared.parallel = true;
		return this;
	}

	public Stream<T> unordered() {
		return chain(source);
	}

	public Stream<T> onClose(Runnable closeHandler) {
		addCloseHandler(closeHandler);
		return this;
	}

	public void close() {
		closePipeline();
	}

	// Intermediate operations

	public Stream<T> filter(Predicate<? super T> predicate) {
		Objects.requireNonNull(predicate);
		return chain(new Filter<T>(source, predicate));
	}

	public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
		Objects.requireNonNull(mapper);
		return chain(new MapStage<T, R>(source, mapper));
	}

	public IntStream mapToInt(ToIntFunction<? super T> mapper) {
		Objects.requireNonNull(mapper);
		link();
		return new IntPipeline(new MapToInt<T>(source, mapper), shared);
	}

	public LongStream mapToLong(ToLongFunction<? super T> mapper) {
		Objects.requireNonNull(mapper);
		link();
		return new LongPipeline(new MapToLong<T>(source, mapper), shared);
	}

	public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
		Objects.requireNonNull(mapper);
		link();
		return new DoublePipeline(new MapToDouble<T>(source, mapper), shared);
	}

	public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
		Objects.requireNonNull(mapper);
		return chain(new FlatMap<T, R>(source, mapper));
	}

	public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
		Objects.requireNonNull(mapper);
		return map(mapper).flatMap(new IntBoxer()).mapToInt(new IntUnboxer());
	}

	public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
		Objects.requireNonNull(mapper);
		return map(mapper).flatMap(new LongBoxer()).mapToLong(new LongUnboxer());
	}

	public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
		Objects.requireNonNull(mapper);
		return map(mapper).flatMap(new DoubleBoxer()).mapToDouble(new DoubleUnboxer());
	}

	public Stream<T> distinct() {
		return chain(new Distinct<T>(source));
	}

	public Stream<T> sorted() {
		return chain(new Sorted<T>(source, null));
	}

	public Stream<T> sorted(Comparator<? super T> comparator) {
		Objects.requireNonNull(comparator);
		return chain(new Sorted<T>(source, comparator));
	}

	public Stream<T> peek(Consumer<? super T> action) {
		Objects.requireNonNull(action);
		return chain(new Peek<T>(source, action));
	}

	public Stream<T> limit(long maxSize) {
		if (maxSize < 0) {
			throw new IllegalArgumentException(Long.toString(maxSize));
		}
		return chain(new Limit<T>(source, maxSize));
	}

	public Stream<T> skip(long n) {
		if (n < 0) {
			throw new IllegalArgumentException(Long.toString(n));
		}
		if (n == 0) {
			return chain(source);
		}
		return chain(new Skip<T>(source, n));
	}

	public Stream<T> takeWhile(Predicate<? super T> predicate) {
		Objects.requireNonNull(predicate);
		return chain(new TakeWhile<T>(source, predicate));
	}

	public Stream<T> dropWhile(Predicate<? super T> predicate) {
		Objects.requireNonNull(predicate);
		return chain(new DropWhile<T>(source, predicate));
	}

	// Terminal operations

	public void forEach(Consumer<? super T> action) {
		Objects.requireNonNull(action);
		consume().forEachRemaining(action);
	}

	public void forEachOrdered(Consumer<? super T> action) {
		forEach(action);
	}

	private ArrayList<T> drain() {
		Collect<T> sink = new Collect<T>();
		consume().forEachRemaining(sink);
		return sink.elements;
	}

	public Object[] toArray() {
		return drain().toArray();
	}

	public <A> A[] toArray(IntFunction<A[]> generator) {
		Objects.requireNonNull(generator);
		Object[] elements = drain().toArray();
		A[] array = generator.apply(elements.length);
		if (array.length != elements.length) {
			throw new IllegalStateException("Begin size " + elements.length + " is not equal to fixed size "
					+ array.length);
		}
		System.arraycopy(elements, 0, array, 0, elements.length);
		return array;
	}

	public T reduce(T identity, BinaryOperator<T> accumulator) {
		Objects.requireNonNull(accumulator);
		Fold<T, T> sink = new Fold<T, T>(identity, accumulator);
		consume().forEachRemaining(sink);
		return sink.state;
	}

	public Optional<T> reduce(BinaryOperator<T> accumulator) {
		Objects.requireNonNull(accumulator);
		Reduce<T> sink = new Reduce<T>(accumulator);
		consume().forEachRemaining(sink);
		return sink.empty ? Optional.<T>empty() : Optional.of(sink.state);
	}

	public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
		Objects.requireNonNull(accumulator);
		Objects.requireNonNull(combiner);
		Fold<T, U> sink = new Fold<T, U>(identity, accumulator);
		consume().forEachRemaining(sink);
		return sink.state;
	}

	public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
		Objects.requireNonNull(supplier);
		Objects.requireNonNull(accumulator);
		Objects.requireNonNull(combiner);
		RefSource<T> s = consume();
		CollectInto<T, R> sink = new CollectInto<T, R>(supplier.get(), accumulator);
		s.forEachRemaining(sink);
		return sink.container;
	}

	@SuppressWarnings("unchecked")
	public <R, A> R collect(Collector<? super T, A, R> collector) {
		Objects.requireNonNull(collector);
		A container = collector.supplier().get();
		BiConsumer<A, ? super T> accumulator = collector.accumulator();
		CollectInto<T, A> sink = new CollectInto<T, A>(container, accumulator);
		consume().forEachRemaining(sink);
		if (collector.characteristics().contains(Collector.Characteristics.IDENTITY_FINISH)) {
			return (R) sink.container;
		}
		return collector.finisher().apply(sink.container);
	}

	public List<T> toList() {
		return Collections.unmodifiableList(drain());
	}

	public Optional<T> min(Comparator<? super T> comparator) {
		return reduce(BinaryOperator.<T>minBy(comparator));
	}

	public Optional<T> max(Comparator<? super T> comparator) {
		return reduce(BinaryOperator.<T>maxBy(comparator));
	}

	public long count() {
		RefSource<T> s = consume();
		long size = s.exactSize();
		if (size >= 0) {
			return size;
		}
		Collect<T> sink = new Collect<T>();
		sink.countOnly = true;
		s.forEachRemaining(sink);
		return sink.count;
	}

	public boolean anyMatch(Predicate<? super T> predicate) {
		return match(predicate, true);
	}

	public boolean allMatch(Predicate<? super T> predicate) {
		return !match(predicate, false);
	}

	public boolean noneMatch(Predicate<? super T> predicate) {
		return !match(predicate, true);
	}

	/** Whether some element's test result is the given one; stops at the first */
	private boolean match(Predicate<? super T> predicate, boolean result) {
		Objects.requireNonNull(predicate);
		RefSource<T> s = consume();
		Box<T> box = new Box<T>();
		while (s.tryAdvance(box)) {
			if (predicate.test(box.value) == result) {
				return true;
			}
		}
		return false;
	}

	public Optional<T> findFirst() {
		RefSource<T> s = consume();
		Box<T> box = new Box<T>();
		if (s.tryAdvance(box)) {
			return Optional.of(box.value);
		}
		return Optional.empty();
	}

	public Optional<T> findAny() {
		return findFirst();
	}

	// Sinks of the terminal operations

	static final class Box<T> implements Consumer<T> {
		T value;

		public void accept(T t) {
			value = t;
		}
	}

	static final class Collect<T> implements Consumer<T> {
		final ArrayList<T> elements = new ArrayList<T>();
		boolean countOnly;
		long count;

		public void accept(T t) {
			if (countOnly) {
				count++;
			} else {
				elements.add(t);
			}
		}
	}

	static final class Fold<T, U> implements Consumer<T> {
		U state;
		private final BiFunction<U, ? super T, U> accumulator;

		Fold(U identity, BiFunction<U, ? super T, U> accumulator) {
			this.state = identity;
			this.accumulator = accumulator;
		}

		public void accept(T t) {
			state = accumulator.apply(state, t);
		}
	}

	static final class Reduce<T> implements Consumer<T> {
		boolean empty = true;
		T state;
		private final BinaryOperator<T> accumulator;

		Reduce(BinaryOperator<T> accumulator) {
			this.accumulator = accumulator;
		}

		public void accept(T t) {
			if (empty) {
				empty = false;
				state = t;
			} else {
				state = accumulator.apply(state, t);
			}
		}
	}

	static final class CollectInto<T, R> implements Consumer<T> {
		final R container;
		private final BiConsumer<R, ? super T> accumulator;

		CollectInto(R container, BiConsumer<R, ? super T> accumulator) {
			this.container = container;
			this.accumulator = accumulator;
		}

		public void accept(T t) {
			accumulator.accept(container, t);
		}
	}

	static final class SourceIterator<T> implements Iterator<T>, Consumer<T> {
		private final RefSource<T> source;
		private boolean ready;
		private T next;

		SourceIterator(RefSource<T> source) {
			this.source = source;
		}

		public void accept(T t) {
			ready = true;
			next = t;
		}

		public boolean hasNext() {
			if (!ready) {
				source.tryAdvance(this);
			}
			return ready;
		}

		public T next() {
			if (!ready && !hasNext()) {
				throw new NoSuchElementException();
			}
			ready = false;
			T t = next;
			next = null;
			return t;
		}
	}

	static final class StreamBuilder<T> implements Stream.Builder<T> {
		private final ArrayList<T> elements = new ArrayList<T>();
		private boolean built;

		public void accept(T t) {
			if (built) {
				throw new IllegalStateException();
			}
			elements.add(t);
		}

		public Stream.Builder<T> add(T t) {
			accept(t);
			return this;
		}

		public Stream<T> build() {
			if (built) {
				throw new IllegalStateException();
			}
			built = true;
			Object[] array = elements.toArray();
			return ofArray(array, 0, array.length);
		}
	}

	// Sources

	static final class ArraySource<T> extends RefSource<T> {
		private final Object[] array;
		private int index;
		private final int fence;

		ArraySource(Object[] array, int from, int to) {
			this.array = array;
			this.index = from;
			this.fence = to;
		}

		@SuppressWarnings("unchecked")
		boolean tryAdvance(Consumer<? super T> action) {
			if (index >= fence) {
				return false;
			}
			action.accept((T) array[index++]);
			return true;
		}

		long exactSize() {
			return fence - index;
		}
	}

	static final class SpliteratorSource<T> extends RefSource<T> {
		private final Spliterator<T> spliterator;

		SpliteratorSource(Spliterator<T> spliterator) {
			this.spliterator = spliterator;
		}

		boolean tryAdvance(Consumer<? super T> action) {
			return spliterator.tryAdvance(action);
		}

		void forEachRemaining(Consumer<? super T> action) {
			spliterator.forEachRemaining(action);
		}

		long exactSize() {
			return spliterator.getExactSizeIfKnown();
		}
	}

	static final class IteratorSource<T> extends RefSource<T> {
		private final Iterator<T> iterator;

		IteratorSource(Iterator<T> iterator) {
			this.iterator = iterator;
		}

		boolean tryAdvance(Consumer<? super T> action) {
			if (!iterator.hasNext()) {
				return false;
			}
			action.accept(iterator.next());
			return true;
		}
	}

	/** Stream.iterate: seed, then next applied to the previous element, while hasNext holds */
	static final class Iterate<T> extends RefSource<T> {
		private final T seed;
		private final Predicate<? super T> hasNext;
		private final UnaryOperator<T> next;
		private T previous;
		private boolean started;
		private boolean finished;

		Iterate(T seed, Predicate<? super T> hasNext, UnaryOperator<T> next) {
			this.seed = seed;
			this.hasNext = hasNext;
			this.next = next;
		}

		boolean tryAdvance(Consumer<? super T> action) {
			if (finished) {
				return false;
			}
			T t;
			if (started) {
				t = next.apply(previous);
			} else {
				t = seed;
				started = true;
			}
			if (hasNext != null && !hasNext.test(t)) {
				previous = null;
				finished = true;
				return false;
			}
			previous = t;
			action.accept(t);
			return true;
		}
	}

	static final class Generate<T> extends RefSource<T> {
		private final Supplier<? extends T> supplier;

		Generate(Supplier<? extends T> supplier) {
			this.supplier = supplier;
		}

		boolean tryAdvance(Consumer<? super T> action) {
			action.accept(supplier.get());
			return true;
		}
	}

	static final class Concat<T> extends RefSource<T> {
		private final RefSource<T> first;
		private final RefSource<T> second;
		private boolean inSecond;

		Concat(RefSource<T> first, RefSource<T> second) {
			this.first = first;
			this.second = second;
		}

		boolean tryAdvance(Consumer<? super T> action) {
			if (!inSecond) {
				if (first.tryAdvance(action)) {
					return true;
				}
				inSecond = true;
			}
			return second.tryAdvance(action);
		}

		long exactSize() {
			return inSecond ? second.exactSize() : addSizes(first.exactSize(), second.exactSize());
		}
	}

	// Stages

	static final class Filter<T> extends RefSource<T> implements Consumer<T> {
		private final RefSource<T> upstream;
		private final Predicate<? super T> predicate;
		private Consumer<? super T> downstream;
		private boolean passed;

		Filter(RefSource<T> upstream, Predicate<? super T> predicate) {
			this.upstream = upstream;
			this.predicate = predicate;
		}

		boolean tryAdvance(Consumer<? super T> action) {
			downstream = action;
			passed = false;
			while (upstream.tryAdvance(this)) {
				if (passed) {
					return true;
				}
			}
			return false;
		}

		public void accept(T t) {
			if (predicate.test(t)) {
				passed = true;
				downstream.accept(t);
			}
		}
	}

	static final class MapStage<T, R> extends RefSource<R> implements Consumer<T> {
		private final RefSource<T> upstream;
		private final Function<? super T, ? extends R> mapper;
		private Consumer<? super R> downstream;

		MapStage(RefSource<T> upstream, Function<? super T, ? extends R> mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(Consumer<? super R> action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		void forEachRemaining(Consumer<? super R> action) {
			downstream = action;
			upstream.forEachRemaining(this);
		}

		public void accept(T t) {
			downstream.accept(mapper.apply(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class MapToInt<T> extends IntSource implements Consumer<T> {
		private final RefSource<T> upstream;
		private final ToIntFunction<? super T> mapper;
		private IntConsumer downstream;

		MapToInt(RefSource<T> upstream, ToIntFunction<? super T> mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(IntConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(T t) {
			downstream.accept(mapper.applyAsInt(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class MapToLong<T> extends LongSource implements Consumer<T> {
		private final RefSource<T> upstream;
		private final ToLongFunction<? super T> mapper;
		private LongConsumer downstream;

		MapToLong(RefSource<T> upstream, ToLongFunction<? super T> mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(LongConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(T t) {
			downstream.accept(mapper.applyAsLong(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class MapToDouble<T> extends DoubleSource implements Consumer<T> {
		private final RefSource<T> upstream;
		private final ToDoubleFunction<? super T> mapper;
		private DoubleConsumer downstream;

		MapToDouble(RefSource<T> upstream, ToDoubleFunction<? super T> mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(DoubleConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(T t) {
			downstream.accept(mapper.applyAsDouble(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	/** Pulls each mapped stream to its end, then closes it, as the JVM does */
	static final class FlatMap<T, R> extends RefSource<R> implements Consumer<T> {
		private final RefSource<T> upstream;
		private final Function<? super T, ? extends Stream<? extends R>> mapper;
		private Stream<? extends R> innerStream;
		private RefSource<R> inner;

		FlatMap(RefSource<T> upstream, Function<? super T, ? extends Stream<? extends R>> mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(Consumer<? super R> action) {
			while (true) {
				if (inner != null) {
					if (inner.tryAdvance(action)) {
						return true;
					}
					Stream<? extends R> done = innerStream;
					inner = null;
					innerStream = null;
					done.close();
				}
				if (!upstream.tryAdvance(this)) {
					return false;
				}
			}
		}

		public void accept(T t) {
			Stream<? extends R> stream = mapper.apply(t);
			if (stream != null) {
				inner = sourceOf(stream);
				innerStream = stream;
			}
		}
	}

	static final class IntBoxer implements Function<IntStream, Stream<Integer>> {
		public Stream<Integer> apply(IntStream s) {
			return s == null ? null : s.boxed();
		}
	}

	static final class IntUnboxer implements ToIntFunction<Integer> {
		public int applyAsInt(Integer value) {
			return value.intValue();
		}
	}

	static final class LongBoxer implements Function<LongStream, Stream<Long>> {
		public Stream<Long> apply(LongStream s) {
			return s == null ? null : s.boxed();
		}
	}

	static final class LongUnboxer implements ToLongFunction<Long> {
		public long applyAsLong(Long value) {
			return value.longValue();
		}
	}

	static final class DoubleBoxer implements Function<DoubleStream, Stream<Double>> {
		public Stream<Double> apply(DoubleStream s) {
			return s == null ? null : s.boxed();
		}
	}

	static final class DoubleUnboxer implements ToDoubleFunction<Double> {
		public double applyAsDouble(Double value) {
			return value.doubleValue();
		}
	}

	static final class Distinct<T> extends RefSource<T> implements Consumer<T> {
		private final RefSource<T> upstream;
		private final HashSet<T> seen = new HashSet<T>();
		private Consumer<? super T> downstream;
		private boolean passed;

		Distinct(RefSource<T> upstream) {
			this.upstream = upstream;
		}

		boolean tryAdvance(Consumer<? super T> action) {
			downstream = action;
			passed = false;
			while (upstream.tryAdvance(this)) {
				if (passed) {
					return true;
				}
			}
			return false;
		}

		public void accept(T t) {
			if (seen.add(t)) {
				passed = true;
				downstream.accept(t);
			}
		}
	}

	/** Pulls every element on the first request, then sorts them (stably) */
	static final class Sorted<T> extends RefSource<T> {
		private final RefSource<T> upstream;
		private final Comparator<? super T> comparator;
		private Object[] sorted;
		private int index;

		Sorted(RefSource<T> upstream, Comparator<? super T> comparator) {
			this.upstream = upstream;
			this.comparator = comparator;
		}

		@SuppressWarnings("unchecked")
		boolean tryAdvance(Consumer<? super T> action) {
			if (sorted == null) {
				Collect<T> sink = new Collect<T>();
				upstream.forEachRemaining(sink);
				sorted = sink.elements.toArray();
				Arrays.sort((T[]) sorted, comparator);
			}
			if (index >= sorted.length) {
				return false;
			}
			T t = (T) sorted[index];
			sorted[index++] = null;
			action.accept(t);
			return true;
		}

		long exactSize() {
			return sorted == null ? upstream.exactSize() : sorted.length - index;
		}
	}

	static final class Peek<T> extends RefSource<T> implements Consumer<T> {
		private final RefSource<T> upstream;
		private final Consumer<? super T> action;
		private Consumer<? super T> downstream;

		Peek(RefSource<T> upstream, Consumer<? super T> action) {
			this.upstream = upstream;
			this.action = action;
		}

		boolean tryAdvance(Consumer<? super T> next) {
			downstream = next;
			return upstream.tryAdvance(this);
		}

		void forEachRemaining(Consumer<? super T> next) {
			downstream = next;
			upstream.forEachRemaining(this);
		}

		public void accept(T t) {
			action.accept(t);
			downstream.accept(t);
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class Limit<T> extends RefSource<T> {
		private final RefSource<T> upstream;
		private long remaining;

		Limit(RefSource<T> upstream, long maxSize) {
			this.upstream = upstream;
			this.remaining = maxSize;
		}

		boolean tryAdvance(Consumer<? super T> action) {
			if (remaining <= 0) {
				return false;
			}
			remaining--;
			if (upstream.tryAdvance(action)) {
				return true;
			}
			remaining = 0;
			return false;
		}

		long exactSize() {
			return limitSize(upstream.exactSize(), remaining);
		}
	}

	static final class Skip<T> extends RefSource<T> {
		private final RefSource<T> upstream;
		private long toSkip;

		Skip(RefSource<T> upstream, long n) {
			this.upstream = upstream;
			this.toSkip = n;
		}

		boolean tryAdvance(Consumer<? super T> action) {
			if (toSkip > 0) {
				Box<T> discard = new Box<T>();
				while (toSkip > 0) {
					toSkip--;
					if (!upstream.tryAdvance(discard)) {
						toSkip = 0;
						return false;
					}
				}
			}
			return upstream.tryAdvance(action);
		}

		long exactSize() {
			return skipSize(upstream.exactSize(), toSkip);
		}
	}

	static final class TakeWhile<T> extends RefSource<T> implements Consumer<T> {
		private final RefSource<T> upstream;
		private final Predicate<? super T> predicate;
		private Consumer<? super T> downstream;
		private boolean taking = true;

		TakeWhile(RefSource<T> upstream, Predicate<? super T> predicate) {
			this.upstream = upstream;
			this.predicate = predicate;
		}

		boolean tryAdvance(Consumer<? super T> action) {
			if (!taking) {
				return false;
			}
			downstream = action;
			if (!upstream.tryAdvance(this)) {
				taking = false;
			}
			return taking;
		}

		public void accept(T t) {
			if (predicate.test(t)) {
				downstream.accept(t);
			} else {
				taking = false;
			}
		}
	}

	static final class DropWhile<T> extends RefSource<T> implements Consumer<T> {
		private final RefSource<T> upstream;
		private final Predicate<? super T> predicate;
		private Consumer<? super T> downstream;
		private boolean dropping = true;
		private boolean passed;

		DropWhile(RefSource<T> upstream, Predicate<? super T> predicate) {
			this.upstream = upstream;
			this.predicate = predicate;
		}

		boolean tryAdvance(Consumer<? super T> action) {
			downstream = action;
			passed = false;
			while (upstream.tryAdvance(this)) {
				if (passed) {
					return true;
				}
			}
			return false;
		}

		public void accept(T t) {
			if (dropping && predicate.test(t)) {
				return;
			}
			dropping = false;
			passed = true;
			downstream.accept(t);
		}
	}
}
