package java.util.stream;

import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.PrimitiveIterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntBinaryOperator;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.IntSupplier;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.LongConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * The runtime's IntStream, written from the Java SE specification: a stage
 * whose source pulls values through the stages before it (see
 * AbstractPipeline). count() does not traverse a stream whose size is known,
 * as on the JVM.
 */
final class IntPipeline extends AbstractPipeline implements IntStream {
	final IntSource source;

	IntPipeline(IntSource source, Shared shared) {
		super(shared);
		this.source = source;
	}

	static IntPipeline head(IntSource source) {
		return new IntPipeline(source, new Shared());
	}

	static IntStream ofArray(int[] array, int from, int to) {
		return head(new ArraySource(array, from, to));
	}

	static IntStream iterate(int seed, IntPredicate hasNext, IntUnaryOperator next) {
		return head(new Iterate(seed, hasNext, next));
	}

	static IntStream generate(IntSupplier supplier) {
		return head(new Generate(supplier));
	}

	static IntStream concat(IntStream a, IntStream b) {
		IntSource first = sourceOf(a);
		IntSource second = sourceOf(b);
		IntPipeline pipeline = head(new Concat(first, second));
		pipeline.shared.parallel = a.isParallel() || b.isParallel();
		pipeline.addCloseHandler(new CloseBoth(a, b));
		return pipeline;
	}

	/** The source of a stream another stream consumes (concat, flatMap) */
	static IntSource sourceOf(IntStream stream) {
		if (stream instanceof IntPipeline) {
			IntPipeline pipeline = (IntPipeline) stream;
			pipeline.link();
			return pipeline.source;
		}
		return new IteratorSource(stream.iterator());
	}

	private IntStream chain(IntSource next) {
		link();
		return new IntPipeline(next, shared);
	}

	private IntSource consume() {
		link();
		return source;
	}

	// BaseStream

	public PrimitiveIterator.OfInt iterator() {
		return new SourceIterator(consume());
	}

	public boolean isParallel() {
		return shared.parallel;
	}

	public IntStream sequential() {
		shared.parallel = false;
		return this;
	}

	public IntStream parallel() {
		shared.parallel = true;
		return this;
	}

	public IntStream unordered() {
		return chain(source);
	}

	public IntStream onClose(Runnable closeHandler) {
		addCloseHandler(closeHandler);
		return this;
	}

	public void close() {
		closePipeline();
	}

	// Intermediate operations

	public IntStream filter(IntPredicate predicate) {
		Objects.requireNonNull(predicate);
		return chain(new Filter(source, predicate));
	}

	public IntStream map(IntUnaryOperator mapper) {
		Objects.requireNonNull(mapper);
		return chain(new MapStage(source, mapper));
	}

	public <U> Stream<U> mapToObj(IntFunction<? extends U> mapper) {
		Objects.requireNonNull(mapper);
		link();
		return new ReferencePipeline<U>(new MapToObj<U>(source, mapper), shared);
	}

	public Stream<Integer> boxed() {
		return mapToObj(new Boxer());
	}

	public LongStream mapToLong(IntToLongFunction mapper) {
		Objects.requireNonNull(mapper);
		link();
		return new LongPipeline(new MapToLong(source, mapper), shared);
	}

	public DoubleStream mapToDouble(IntToDoubleFunction mapper) {
		Objects.requireNonNull(mapper);
		link();
		return new DoublePipeline(new MapToDouble(source, mapper), shared);
	}

	public LongStream asLongStream() {
		link();
		return new LongPipeline(new AsLong(source), shared);
	}

	public DoubleStream asDoubleStream() {
		link();
		return new DoublePipeline(new AsDouble(source), shared);
	}

	public IntStream flatMap(IntFunction<? extends IntStream> mapper) {
		Objects.requireNonNull(mapper);
		return chain(new FlatMap(source, mapper));
	}

	/** Like the JVM: boxed().distinct(), unboxed again */
	public IntStream distinct() {
		return boxed().distinct().mapToInt(new Unboxer());
	}

	public IntStream sorted() {
		return chain(new Sorted(source));
	}

	public IntStream peek(IntConsumer action) {
		Objects.requireNonNull(action);
		return chain(new Peek(source, action));
	}

	public IntStream limit(long maxSize) {
		if (maxSize < 0) {
			throw new IllegalArgumentException(Long.toString(maxSize));
		}
		return chain(new Limit(source, maxSize));
	}

	public IntStream skip(long n) {
		if (n < 0) {
			throw new IllegalArgumentException(Long.toString(n));
		}
		if (n == 0) {
			return chain(source);
		}
		return chain(new Skip(source, n));
	}

	public IntStream takeWhile(IntPredicate predicate) {
		Objects.requireNonNull(predicate);
		return chain(new TakeWhile(source, predicate));
	}

	public IntStream dropWhile(IntPredicate predicate) {
		Objects.requireNonNull(predicate);
		return chain(new DropWhile(source, predicate));
	}

	// Terminal operations

	public void forEach(IntConsumer action) {
		Objects.requireNonNull(action);
		consume().forEachRemaining(action);
	}

	public void forEachOrdered(IntConsumer action) {
		forEach(action);
	}

	public int[] toArray() {
		Collect sink = new Collect();
		consume().forEachRemaining(sink);
		return sink.toArray();
	}

	public int reduce(int identity, IntBinaryOperator op) {
		Objects.requireNonNull(op);
		Reduce sink = new Reduce(op);
		sink.empty = false;
		sink.state = identity;
		consume().forEachRemaining(sink);
		return sink.state;
	}

	public OptionalInt reduce(IntBinaryOperator op) {
		Objects.requireNonNull(op);
		Reduce sink = new Reduce(op);
		consume().forEachRemaining(sink);
		return sink.empty ? OptionalInt.empty() : OptionalInt.of(sink.state);
	}

	public <R> R collect(Supplier<R> supplier, ObjIntConsumer<R> accumulator, BiConsumer<R, R> combiner) {
		Objects.requireNonNull(supplier);
		Objects.requireNonNull(accumulator);
		Objects.requireNonNull(combiner);
		IntSource s = consume();
		CollectInto<R> sink = new CollectInto<R>(supplier.get(), accumulator);
		s.forEachRemaining(sink);
		return sink.container;
	}

	public OptionalInt min() {
		return reduce(new MinMax(true));
	}

	public OptionalInt max() {
		return reduce(new MinMax(false));
	}

	public long count() {
		IntSource s = consume();
		long size = s.exactSize();
		if (size >= 0) {
			return size;
		}
		Statistics sink = new Statistics();
		s.forEachRemaining(sink);
		return sink.count;
	}

	public IntSummaryStatistics summaryStatistics() {
		IntSummaryStatistics statistics = new IntSummaryStatistics();
		consume().forEachRemaining(statistics);
		return statistics;
	}

	public int sum() {
		Statistics sink = new Statistics();
		consume().forEachRemaining(sink);
		return (int) sink.sum;
	}

	public OptionalDouble average() {
		Statistics sink = new Statistics();
		consume().forEachRemaining(sink);
		return sink.count > 0 ? OptionalDouble.of((double) sink.sum / sink.count) : OptionalDouble.empty();
	}

	public boolean anyMatch(IntPredicate predicate) {
		return match(predicate, true);
	}

	public boolean allMatch(IntPredicate predicate) {
		return !match(predicate, false);
	}

	public boolean noneMatch(IntPredicate predicate) {
		return !match(predicate, true);
	}

	/** Whether some value's test result is the given one; stops at the first */
	private boolean match(IntPredicate predicate, boolean result) {
		Objects.requireNonNull(predicate);
		IntSource s = consume();
		Box box = new Box();
		while (s.tryAdvance(box)) {
			if (predicate.test(box.value) == result) {
				return true;
			}
		}
		return false;
	}

	public OptionalInt findFirst() {
		IntSource s = consume();
		Box box = new Box();
		if (s.tryAdvance(box)) {
			return OptionalInt.of(box.value);
		}
		return OptionalInt.empty();
	}

	public OptionalInt findAny() {
		return findFirst();
	}

	// Sinks of the terminal operations

	static final class Box implements IntConsumer {
		int value;

		public void accept(int t) {
			value = t;
		}
	}

	static final class Collect implements IntConsumer {
		private int[] values = new int[16];
		private int size;

		public void accept(int t) {
			if (size == values.length) {
				values = Arrays.copyOf(values, size * 2);
			}
			values[size++] = t;
		}

		int[] toArray() {
			return Arrays.copyOf(values, size);
		}
	}

	static final class Reduce implements IntConsumer {
		boolean empty = true;
		int state;
		private final IntBinaryOperator op;

		Reduce(IntBinaryOperator op) {
			this.op = op;
		}

		public void accept(int t) {
			if (empty) {
				empty = false;
				state = t;
			} else {
				state = op.applyAsInt(state, t);
			}
		}
	}

	static final class MinMax implements IntBinaryOperator {
		private final boolean min;

		MinMax(boolean min) {
			this.min = min;
		}

		public int applyAsInt(int left, int right) {
			return min ? Math.min(left, right) : Math.max(left, right);
		}
	}

	static final class CollectInto<R> implements IntConsumer {
		final R container;
		private final ObjIntConsumer<R> accumulator;

		CollectInto(R container, ObjIntConsumer<R> accumulator) {
			this.container = container;
			this.accumulator = accumulator;
		}

		public void accept(int t) {
			accumulator.accept(container, t);
		}
	}

	/** Count and (wrapping) sum */
	static final class Statistics implements IntConsumer {
		long count;
		long sum;

		public void accept(int t) {
			count++;
			sum += t;
		}
	}

	static final class Boxer implements IntFunction<Integer> {
		public Integer apply(int value) {
			return Integer.valueOf(value);
		}
	}

	static final class Unboxer implements ToIntFunction<Integer> {
		public int applyAsInt(Integer value) {
			return value.intValue();
		}
	}

	static final class SourceIterator implements PrimitiveIterator.OfInt, IntConsumer {
		private final IntSource source;
		private boolean ready;
		private int next;

		SourceIterator(IntSource source) {
			this.source = source;
		}

		public void accept(int t) {
			ready = true;
			next = t;
		}

		public boolean hasNext() {
			if (!ready) {
				source.tryAdvance(this);
			}
			return ready;
		}

		public int nextInt() {
			if (!ready && !hasNext()) {
				throw new NoSuchElementException();
			}
			ready = false;
			return next;
		}

		public Integer next() {
			return Integer.valueOf(nextInt());
		}
	}

	static final class IntBuilder implements IntStream.Builder {
		private final Collect values = new Collect();
		private boolean built;

		public void accept(int t) {
			if (built) {
				throw new IllegalStateException();
			}
			values.accept(t);
		}

		public IntStream.Builder add(int t) {
			accept(t);
			return this;
		}

		public IntStream build() {
			if (built) {
				throw new IllegalStateException();
			}
			built = true;
			int[] array = values.toArray();
			return ofArray(array, 0, array.length);
		}
	}

	// Sources

	static final class ArraySource extends IntSource {
		private final int[] array;
		private int index;
		private final int fence;

		ArraySource(int[] array, int from, int to) {
			this.array = array;
			this.index = from;
			this.fence = to;
		}

		boolean tryAdvance(IntConsumer action) {
			if (index >= fence) {
				return false;
			}
			action.accept(array[index++]);
			return true;
		}

		long exactSize() {
			return fence - index;
		}
	}

	static final class IteratorSource extends IntSource {
		private final PrimitiveIterator.OfInt iterator;

		IteratorSource(PrimitiveIterator.OfInt iterator) {
			this.iterator = iterator;
		}

		boolean tryAdvance(IntConsumer action) {
			if (!iterator.hasNext()) {
				return false;
			}
			action.accept(iterator.nextInt());
			return true;
		}
	}

	/** count values from start; count is at least 1 */
	static IntStream range(int start, long count) {
		return head(new Range(start, count));
	}

	static final class Range extends IntSource {
		private int next;
		private long remaining;

		Range(int start, long count) {
			this.next = start;
			this.remaining = count;
		}

		boolean tryAdvance(IntConsumer action) {
			if (remaining <= 0) {
				return false;
			}
			remaining--;
			action.accept(next++);
			return true;
		}

		long exactSize() {
			return remaining;
		}
	}

	/** iterate: seed, then next applied to the previous value, while hasNext holds */
	static final class Iterate extends IntSource {
		private final int seed;
		private final IntPredicate hasNext;
		private final IntUnaryOperator next;
		private int previous;
		private boolean started;
		private boolean finished;

		Iterate(int seed, IntPredicate hasNext, IntUnaryOperator next) {
			this.seed = seed;
			this.hasNext = hasNext;
			this.next = next;
		}

		boolean tryAdvance(IntConsumer action) {
			if (finished) {
				return false;
			}
			int t;
			if (started) {
				t = next.applyAsInt(previous);
			} else {
				t = seed;
				started = true;
			}
			if (hasNext != null && !hasNext.test(t)) {
				finished = true;
				return false;
			}
			previous = t;
			action.accept(t);
			return true;
		}
	}

	static final class Generate extends IntSource {
		private final IntSupplier supplier;

		Generate(IntSupplier supplier) {
			this.supplier = supplier;
		}

		boolean tryAdvance(IntConsumer action) {
			action.accept(supplier.getAsInt());
			return true;
		}
	}

	static final class Concat extends IntSource {
		private final IntSource first;
		private final IntSource second;
		private boolean inSecond;

		Concat(IntSource first, IntSource second) {
			this.first = first;
			this.second = second;
		}

		boolean tryAdvance(IntConsumer action) {
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

	static final class Filter extends IntSource implements IntConsumer {
		private final IntSource upstream;
		private final IntPredicate predicate;
		private IntConsumer downstream;
		private boolean passed;

		Filter(IntSource upstream, IntPredicate predicate) {
			this.upstream = upstream;
			this.predicate = predicate;
		}

		boolean tryAdvance(IntConsumer action) {
			downstream = action;
			passed = false;
			while (upstream.tryAdvance(this)) {
				if (passed) {
					return true;
				}
			}
			return false;
		}

		public void accept(int t) {
			if (predicate.test(t)) {
				passed = true;
				downstream.accept(t);
			}
		}
	}

	static final class MapStage extends IntSource implements IntConsumer {
		private final IntSource upstream;
		private final IntUnaryOperator mapper;
		private IntConsumer downstream;

		MapStage(IntSource upstream, IntUnaryOperator mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(IntConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(int t) {
			downstream.accept(mapper.applyAsInt(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class MapToObj<U> extends RefSource<U> implements IntConsumer {
		private final IntSource upstream;
		private final IntFunction<? extends U> mapper;
		private Consumer<? super U> downstream;

		MapToObj(IntSource upstream, IntFunction<? extends U> mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(Consumer<? super U> action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(int t) {
			downstream.accept(mapper.apply(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class MapToLong extends LongSource implements IntConsumer {
		private final IntSource upstream;
		private final IntToLongFunction mapper;
		private LongConsumer downstream;

		MapToLong(IntSource upstream, IntToLongFunction mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(LongConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(int t) {
			downstream.accept(mapper.applyAsLong(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class MapToDouble extends DoubleSource implements IntConsumer {
		private final IntSource upstream;
		private final IntToDoubleFunction mapper;
		private DoubleConsumer downstream;

		MapToDouble(IntSource upstream, IntToDoubleFunction mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(DoubleConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(int t) {
			downstream.accept(mapper.applyAsDouble(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class AsLong extends LongSource implements IntConsumer {
		private final IntSource upstream;
		private LongConsumer downstream;

		AsLong(IntSource upstream) {
			this.upstream = upstream;
		}

		boolean tryAdvance(LongConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(int t) {
			downstream.accept((long) t);
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class AsDouble extends DoubleSource implements IntConsumer {
		private final IntSource upstream;
		private DoubleConsumer downstream;

		AsDouble(IntSource upstream) {
			this.upstream = upstream;
		}

		boolean tryAdvance(DoubleConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(int t) {
			downstream.accept((double) t);
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	/** Pulls each mapped stream to its end, then closes it, as the JVM does */
	static final class FlatMap extends IntSource implements IntConsumer {
		private final IntSource upstream;
		private final IntFunction<? extends IntStream> mapper;
		private IntStream innerStream;
		private IntSource inner;

		FlatMap(IntSource upstream, IntFunction<? extends IntStream> mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(IntConsumer action) {
			while (true) {
				if (inner != null) {
					if (inner.tryAdvance(action)) {
						return true;
					}
					IntStream done = innerStream;
					inner = null;
					innerStream = null;
					done.close();
				}
				if (!upstream.tryAdvance(this)) {
					return false;
				}
			}
		}

		public void accept(int t) {
			IntStream stream = mapper.apply(t);
			if (stream != null) {
				inner = sourceOf(stream);
				innerStream = stream;
			}
		}
	}

	/** Pulls every value on the first request, then sorts them */
	static final class Sorted extends IntSource {
		private final IntSource upstream;
		private int[] sorted;
		private int index;

		Sorted(IntSource upstream) {
			this.upstream = upstream;
		}

		boolean tryAdvance(IntConsumer action) {
			if (sorted == null) {
				Collect sink = new Collect();
				upstream.forEachRemaining(sink);
				sorted = sink.toArray();
				Arrays.sort(sorted);
			}
			if (index >= sorted.length) {
				return false;
			}
			action.accept(sorted[index++]);
			return true;
		}

		long exactSize() {
			return sorted == null ? upstream.exactSize() : sorted.length - index;
		}
	}

	static final class Peek extends IntSource implements IntConsumer {
		private final IntSource upstream;
		private final IntConsumer action;
		private IntConsumer downstream;

		Peek(IntSource upstream, IntConsumer action) {
			this.upstream = upstream;
			this.action = action;
		}

		boolean tryAdvance(IntConsumer next) {
			downstream = next;
			return upstream.tryAdvance(this);
		}

		public void accept(int t) {
			action.accept(t);
			downstream.accept(t);
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class Limit extends IntSource {
		private final IntSource upstream;
		private long remaining;

		Limit(IntSource upstream, long maxSize) {
			this.upstream = upstream;
			this.remaining = maxSize;
		}

		boolean tryAdvance(IntConsumer action) {
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

	static final class Skip extends IntSource {
		private final IntSource upstream;
		private long toSkip;

		Skip(IntSource upstream, long n) {
			this.upstream = upstream;
			this.toSkip = n;
		}

		boolean tryAdvance(IntConsumer action) {
			if (toSkip > 0) {
				Box discard = new Box();
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

	static final class TakeWhile extends IntSource implements IntConsumer {
		private final IntSource upstream;
		private final IntPredicate predicate;
		private IntConsumer downstream;
		private boolean taking = true;

		TakeWhile(IntSource upstream, IntPredicate predicate) {
			this.upstream = upstream;
			this.predicate = predicate;
		}

		boolean tryAdvance(IntConsumer action) {
			if (!taking) {
				return false;
			}
			downstream = action;
			if (!upstream.tryAdvance(this)) {
				taking = false;
			}
			return taking;
		}

		public void accept(int t) {
			if (predicate.test(t)) {
				downstream.accept(t);
			} else {
				taking = false;
			}
		}
	}

	static final class DropWhile extends IntSource implements IntConsumer {
		private final IntSource upstream;
		private final IntPredicate predicate;
		private IntConsumer downstream;
		private boolean dropping = true;
		private boolean passed;

		DropWhile(IntSource upstream, IntPredicate predicate) {
			this.upstream = upstream;
			this.predicate = predicate;
		}

		boolean tryAdvance(IntConsumer action) {
			downstream = action;
			passed = false;
			while (upstream.tryAdvance(this)) {
				if (passed) {
					return true;
				}
			}
			return false;
		}

		public void accept(int t) {
			if (dropping && predicate.test(t)) {
				return;
			}
			dropping = false;
			passed = true;
			downstream.accept(t);
		}
	}
}
