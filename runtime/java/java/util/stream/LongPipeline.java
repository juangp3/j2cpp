package java.util.stream;

import java.util.Arrays;
import java.util.LongSummaryStatistics;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import java.util.PrimitiveIterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongBinaryOperator;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;
import java.util.function.LongSupplier;
import java.util.function.LongToDoubleFunction;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;
import java.util.function.ObjLongConsumer;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

/**
 * The runtime's LongStream, written from the Java SE specification: a stage
 * whose source pulls values through the stages before it (see
 * AbstractPipeline). count() does not traverse a stream whose size is known,
 * as on the JVM.
 */
final class LongPipeline extends AbstractPipeline implements LongStream {
	final LongSource source;

	LongPipeline(LongSource source, Shared shared) {
		super(shared);
		this.source = source;
	}

	static LongPipeline head(LongSource source) {
		return new LongPipeline(source, new Shared());
	}

	static LongStream ofArray(long[] array, int from, int to) {
		return head(new ArraySource(array, from, to));
	}

	static LongStream iterate(long seed, LongPredicate hasNext, LongUnaryOperator next) {
		return head(new Iterate(seed, hasNext, next));
	}

	static LongStream generate(LongSupplier supplier) {
		return head(new Generate(supplier));
	}

	static LongStream concat(LongStream a, LongStream b) {
		LongSource first = sourceOf(a);
		LongSource second = sourceOf(b);
		LongPipeline pipeline = head(new Concat(first, second));
		pipeline.shared.parallel = a.isParallel() || b.isParallel();
		pipeline.addCloseHandler(new CloseBoth(a, b));
		return pipeline;
	}

	/** The source of a stream another stream consumes (concat, flatMap) */
	static LongSource sourceOf(LongStream stream) {
		if (stream instanceof LongPipeline) {
			LongPipeline pipeline = (LongPipeline) stream;
			pipeline.link();
			return pipeline.source;
		}
		return new IteratorSource(stream.iterator());
	}

	private LongStream chain(LongSource next) {
		link();
		return new LongPipeline(next, shared);
	}

	private LongSource consume() {
		link();
		return source;
	}

	// BaseStream

	public PrimitiveIterator.OfLong iterator() {
		return new SourceIterator(consume());
	}

	public boolean isParallel() {
		return shared.parallel;
	}

	public LongStream sequential() {
		shared.parallel = false;
		return this;
	}

	public LongStream parallel() {
		shared.parallel = true;
		return this;
	}

	public LongStream unordered() {
		return chain(source);
	}

	public LongStream onClose(Runnable closeHandler) {
		addCloseHandler(closeHandler);
		return this;
	}

	public void close() {
		closePipeline();
	}

	// Intermediate operations

	public LongStream filter(LongPredicate predicate) {
		Objects.requireNonNull(predicate);
		return chain(new Filter(source, predicate));
	}

	public LongStream map(LongUnaryOperator mapper) {
		Objects.requireNonNull(mapper);
		return chain(new MapStage(source, mapper));
	}

	public <U> Stream<U> mapToObj(LongFunction<? extends U> mapper) {
		Objects.requireNonNull(mapper);
		link();
		return new ReferencePipeline<U>(new MapToObj<U>(source, mapper), shared);
	}

	public Stream<Long> boxed() {
		return mapToObj(new Boxer());
	}

	public IntStream mapToInt(LongToIntFunction mapper) {
		Objects.requireNonNull(mapper);
		link();
		return new IntPipeline(new MapToInt(source, mapper), shared);
	}

	public DoubleStream mapToDouble(LongToDoubleFunction mapper) {
		Objects.requireNonNull(mapper);
		link();
		return new DoublePipeline(new MapToDouble(source, mapper), shared);
	}

	public DoubleStream asDoubleStream() {
		link();
		return new DoublePipeline(new AsDouble(source), shared);
	}

	public LongStream flatMap(LongFunction<? extends LongStream> mapper) {
		Objects.requireNonNull(mapper);
		return chain(new FlatMap(source, mapper));
	}

	/** Like the JVM: boxed().distinct(), unboxed again */
	public LongStream distinct() {
		return boxed().distinct().mapToLong(new Unboxer());
	}

	public LongStream sorted() {
		return chain(new Sorted(source));
	}

	public LongStream peek(LongConsumer action) {
		Objects.requireNonNull(action);
		return chain(new Peek(source, action));
	}

	public LongStream limit(long maxSize) {
		if (maxSize < 0) {
			throw new IllegalArgumentException(Long.toString(maxSize));
		}
		return chain(new Limit(source, maxSize));
	}

	public LongStream skip(long n) {
		if (n < 0) {
			throw new IllegalArgumentException(Long.toString(n));
		}
		if (n == 0) {
			return chain(source);
		}
		return chain(new Skip(source, n));
	}

	public LongStream takeWhile(LongPredicate predicate) {
		Objects.requireNonNull(predicate);
		return chain(new TakeWhile(source, predicate));
	}

	public LongStream dropWhile(LongPredicate predicate) {
		Objects.requireNonNull(predicate);
		return chain(new DropWhile(source, predicate));
	}

	// Terminal operations

	public void forEach(LongConsumer action) {
		Objects.requireNonNull(action);
		consume().forEachRemaining(action);
	}

	public void forEachOrdered(LongConsumer action) {
		forEach(action);
	}

	public long[] toArray() {
		Collect sink = new Collect();
		consume().forEachRemaining(sink);
		return sink.toArray();
	}

	public long reduce(long identity, LongBinaryOperator op) {
		Objects.requireNonNull(op);
		Reduce sink = new Reduce(op);
		sink.empty = false;
		sink.state = identity;
		consume().forEachRemaining(sink);
		return sink.state;
	}

	public OptionalLong reduce(LongBinaryOperator op) {
		Objects.requireNonNull(op);
		Reduce sink = new Reduce(op);
		consume().forEachRemaining(sink);
		return sink.empty ? OptionalLong.empty() : OptionalLong.of(sink.state);
	}

	public <R> R collect(Supplier<R> supplier, ObjLongConsumer<R> accumulator, BiConsumer<R, R> combiner) {
		Objects.requireNonNull(supplier);
		Objects.requireNonNull(accumulator);
		Objects.requireNonNull(combiner);
		LongSource s = consume();
		CollectInto<R> sink = new CollectInto<R>(supplier.get(), accumulator);
		s.forEachRemaining(sink);
		return sink.container;
	}

	public OptionalLong min() {
		return reduce(new MinMax(true));
	}

	public OptionalLong max() {
		return reduce(new MinMax(false));
	}

	public long count() {
		LongSource s = consume();
		long size = s.exactSize();
		if (size >= 0) {
			return size;
		}
		Statistics sink = new Statistics();
		s.forEachRemaining(sink);
		return sink.count;
	}

	public LongSummaryStatistics summaryStatistics() {
		LongSummaryStatistics statistics = new LongSummaryStatistics();
		consume().forEachRemaining(statistics);
		return statistics;
	}

	public long sum() {
		Statistics sink = new Statistics();
		consume().forEachRemaining(sink);
		return (long) sink.sum;
	}

	public OptionalDouble average() {
		Statistics sink = new Statistics();
		consume().forEachRemaining(sink);
		return sink.count > 0 ? OptionalDouble.of((double) sink.sum / sink.count) : OptionalDouble.empty();
	}

	public boolean anyMatch(LongPredicate predicate) {
		return match(predicate, true);
	}

	public boolean allMatch(LongPredicate predicate) {
		return !match(predicate, false);
	}

	public boolean noneMatch(LongPredicate predicate) {
		return !match(predicate, true);
	}

	/** Whether some value's test result is the given one; stops at the first */
	private boolean match(LongPredicate predicate, boolean result) {
		Objects.requireNonNull(predicate);
		LongSource s = consume();
		Box box = new Box();
		while (s.tryAdvance(box)) {
			if (predicate.test(box.value) == result) {
				return true;
			}
		}
		return false;
	}

	public OptionalLong findFirst() {
		LongSource s = consume();
		Box box = new Box();
		if (s.tryAdvance(box)) {
			return OptionalLong.of(box.value);
		}
		return OptionalLong.empty();
	}

	public OptionalLong findAny() {
		return findFirst();
	}

	// Sinks of the terminal operations

	static final class Box implements LongConsumer {
		long value;

		public void accept(long t) {
			value = t;
		}
	}

	static final class Collect implements LongConsumer {
		private long[] values = new long[16];
		private int size;

		public void accept(long t) {
			if (size == values.length) {
				values = Arrays.copyOf(values, size * 2);
			}
			values[size++] = t;
		}

		long[] toArray() {
			return Arrays.copyOf(values, size);
		}
	}

	static final class Reduce implements LongConsumer {
		boolean empty = true;
		long state;
		private final LongBinaryOperator op;

		Reduce(LongBinaryOperator op) {
			this.op = op;
		}

		public void accept(long t) {
			if (empty) {
				empty = false;
				state = t;
			} else {
				state = op.applyAsLong(state, t);
			}
		}
	}

	static final class MinMax implements LongBinaryOperator {
		private final boolean min;

		MinMax(boolean min) {
			this.min = min;
		}

		public long applyAsLong(long left, long right) {
			return min ? Math.min(left, right) : Math.max(left, right);
		}
	}

	static final class CollectInto<R> implements LongConsumer {
		final R container;
		private final ObjLongConsumer<R> accumulator;

		CollectInto(R container, ObjLongConsumer<R> accumulator) {
			this.container = container;
			this.accumulator = accumulator;
		}

		public void accept(long t) {
			accumulator.accept(container, t);
		}
	}

	/** Count and (wrapping) sum */
	static final class Statistics implements LongConsumer {
		long count;
		long sum;

		public void accept(long t) {
			count++;
			sum += t;
		}
	}

	static final class Boxer implements LongFunction<Long> {
		public Long apply(long value) {
			return Long.valueOf(value);
		}
	}

	static final class Unboxer implements ToLongFunction<Long> {
		public long applyAsLong(Long value) {
			return value.longValue();
		}
	}

	static final class SourceIterator implements PrimitiveIterator.OfLong, LongConsumer {
		private final LongSource source;
		private boolean ready;
		private long next;

		SourceIterator(LongSource source) {
			this.source = source;
		}

		public void accept(long t) {
			ready = true;
			next = t;
		}

		public boolean hasNext() {
			if (!ready) {
				source.tryAdvance(this);
			}
			return ready;
		}

		public long nextLong() {
			if (!ready && !hasNext()) {
				throw new NoSuchElementException();
			}
			ready = false;
			return next;
		}

		public Long next() {
			return Long.valueOf(nextLong());
		}
	}

	static final class LongBuilder implements LongStream.Builder {
		private final Collect values = new Collect();
		private boolean built;

		public void accept(long t) {
			if (built) {
				throw new IllegalStateException();
			}
			values.accept(t);
		}

		public LongStream.Builder add(long t) {
			accept(t);
			return this;
		}

		public LongStream build() {
			if (built) {
				throw new IllegalStateException();
			}
			built = true;
			long[] array = values.toArray();
			return ofArray(array, 0, array.length);
		}
	}

	// Sources

	static final class ArraySource extends LongSource {
		private final long[] array;
		private int index;
		private final int fence;

		ArraySource(long[] array, int from, int to) {
			this.array = array;
			this.index = from;
			this.fence = to;
		}

		boolean tryAdvance(LongConsumer action) {
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

	static final class IteratorSource extends LongSource {
		private final PrimitiveIterator.OfLong iterator;

		IteratorSource(PrimitiveIterator.OfLong iterator) {
			this.iterator = iterator;
		}

		boolean tryAdvance(LongConsumer action) {
			if (!iterator.hasNext()) {
				return false;
			}
			action.accept(iterator.nextLong());
			return true;
		}
	}

	/** The values from start to end inclusive; start <= end */
	static LongStream range(long start, long end) {
		return head(new Range(start, end));
	}

	static final class Range extends LongSource {
		private long next;
		private final long last;
		private boolean done;

		Range(long start, long end) {
			this.next = start;
			this.last = end;
		}

		boolean tryAdvance(LongConsumer action) {
			if (done) {
				return false;
			}
			long value = next;
			if (value == last) {
				done = true;
			} else {
				next++;
			}
			action.accept(value);
			return true;
		}

		long exactSize() {
			if (done) {
				return 0;
			}
			long count = last - next + 1;
			return count > 0 ? count : -1;
		}
	}

	/** iterate: seed, then next applied to the previous value, while hasNext holds */
	static final class Iterate extends LongSource {
		private final long seed;
		private final LongPredicate hasNext;
		private final LongUnaryOperator next;
		private long previous;
		private boolean started;
		private boolean finished;

		Iterate(long seed, LongPredicate hasNext, LongUnaryOperator next) {
			this.seed = seed;
			this.hasNext = hasNext;
			this.next = next;
		}

		boolean tryAdvance(LongConsumer action) {
			if (finished) {
				return false;
			}
			long t;
			if (started) {
				t = next.applyAsLong(previous);
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

	static final class Generate extends LongSource {
		private final LongSupplier supplier;

		Generate(LongSupplier supplier) {
			this.supplier = supplier;
		}

		boolean tryAdvance(LongConsumer action) {
			action.accept(supplier.getAsLong());
			return true;
		}
	}

	static final class Concat extends LongSource {
		private final LongSource first;
		private final LongSource second;
		private boolean inSecond;

		Concat(LongSource first, LongSource second) {
			this.first = first;
			this.second = second;
		}

		boolean tryAdvance(LongConsumer action) {
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

	static final class Filter extends LongSource implements LongConsumer {
		private final LongSource upstream;
		private final LongPredicate predicate;
		private LongConsumer downstream;
		private boolean passed;

		Filter(LongSource upstream, LongPredicate predicate) {
			this.upstream = upstream;
			this.predicate = predicate;
		}

		boolean tryAdvance(LongConsumer action) {
			downstream = action;
			passed = false;
			while (upstream.tryAdvance(this)) {
				if (passed) {
					return true;
				}
			}
			return false;
		}

		public void accept(long t) {
			if (predicate.test(t)) {
				passed = true;
				downstream.accept(t);
			}
		}
	}

	static final class MapStage extends LongSource implements LongConsumer {
		private final LongSource upstream;
		private final LongUnaryOperator mapper;
		private LongConsumer downstream;

		MapStage(LongSource upstream, LongUnaryOperator mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(LongConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(long t) {
			downstream.accept(mapper.applyAsLong(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class MapToObj<U> extends RefSource<U> implements LongConsumer {
		private final LongSource upstream;
		private final LongFunction<? extends U> mapper;
		private Consumer<? super U> downstream;

		MapToObj(LongSource upstream, LongFunction<? extends U> mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(Consumer<? super U> action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(long t) {
			downstream.accept(mapper.apply(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class MapToInt extends IntSource implements LongConsumer {
		private final LongSource upstream;
		private final LongToIntFunction mapper;
		private IntConsumer downstream;

		MapToInt(LongSource upstream, LongToIntFunction mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(IntConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(long t) {
			downstream.accept(mapper.applyAsInt(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class MapToDouble extends DoubleSource implements LongConsumer {
		private final LongSource upstream;
		private final LongToDoubleFunction mapper;
		private DoubleConsumer downstream;

		MapToDouble(LongSource upstream, LongToDoubleFunction mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(DoubleConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(long t) {
			downstream.accept(mapper.applyAsDouble(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class AsDouble extends DoubleSource implements LongConsumer {
		private final LongSource upstream;
		private DoubleConsumer downstream;

		AsDouble(LongSource upstream) {
			this.upstream = upstream;
		}

		boolean tryAdvance(DoubleConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(long t) {
			downstream.accept((double) t);
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	/** Pulls each mapped stream to its end, then closes it, as the JVM does */
	static final class FlatMap extends LongSource implements LongConsumer {
		private final LongSource upstream;
		private final LongFunction<? extends LongStream> mapper;
		private LongStream innerStream;
		private LongSource inner;

		FlatMap(LongSource upstream, LongFunction<? extends LongStream> mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(LongConsumer action) {
			while (true) {
				if (inner != null) {
					if (inner.tryAdvance(action)) {
						return true;
					}
					LongStream done = innerStream;
					inner = null;
					innerStream = null;
					done.close();
				}
				if (!upstream.tryAdvance(this)) {
					return false;
				}
			}
		}

		public void accept(long t) {
			LongStream stream = mapper.apply(t);
			if (stream != null) {
				inner = sourceOf(stream);
				innerStream = stream;
			}
		}
	}

	/** Pulls every value on the first request, then sorts them */
	static final class Sorted extends LongSource {
		private final LongSource upstream;
		private long[] sorted;
		private int index;

		Sorted(LongSource upstream) {
			this.upstream = upstream;
		}

		boolean tryAdvance(LongConsumer action) {
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

	static final class Peek extends LongSource implements LongConsumer {
		private final LongSource upstream;
		private final LongConsumer action;
		private LongConsumer downstream;

		Peek(LongSource upstream, LongConsumer action) {
			this.upstream = upstream;
			this.action = action;
		}

		boolean tryAdvance(LongConsumer next) {
			downstream = next;
			return upstream.tryAdvance(this);
		}

		public void accept(long t) {
			action.accept(t);
			downstream.accept(t);
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class Limit extends LongSource {
		private final LongSource upstream;
		private long remaining;

		Limit(LongSource upstream, long maxSize) {
			this.upstream = upstream;
			this.remaining = maxSize;
		}

		boolean tryAdvance(LongConsumer action) {
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

	static final class Skip extends LongSource {
		private final LongSource upstream;
		private long toSkip;

		Skip(LongSource upstream, long n) {
			this.upstream = upstream;
			this.toSkip = n;
		}

		boolean tryAdvance(LongConsumer action) {
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

	static final class TakeWhile extends LongSource implements LongConsumer {
		private final LongSource upstream;
		private final LongPredicate predicate;
		private LongConsumer downstream;
		private boolean taking = true;

		TakeWhile(LongSource upstream, LongPredicate predicate) {
			this.upstream = upstream;
			this.predicate = predicate;
		}

		boolean tryAdvance(LongConsumer action) {
			if (!taking) {
				return false;
			}
			downstream = action;
			if (!upstream.tryAdvance(this)) {
				taking = false;
			}
			return taking;
		}

		public void accept(long t) {
			if (predicate.test(t)) {
				downstream.accept(t);
			} else {
				taking = false;
			}
		}
	}

	static final class DropWhile extends LongSource implements LongConsumer {
		private final LongSource upstream;
		private final LongPredicate predicate;
		private LongConsumer downstream;
		private boolean dropping = true;
		private boolean passed;

		DropWhile(LongSource upstream, LongPredicate predicate) {
			this.upstream = upstream;
			this.predicate = predicate;
		}

		boolean tryAdvance(LongConsumer action) {
			downstream = action;
			passed = false;
			while (upstream.tryAdvance(this)) {
				if (passed) {
					return true;
				}
			}
			return false;
		}

		public void accept(long t) {
			if (dropping && predicate.test(t)) {
				return;
			}
			dropping = false;
			passed = true;
			downstream.accept(t);
		}
	}
}
