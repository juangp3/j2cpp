package java.util.stream;

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.PrimitiveIterator;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleBinaryOperator;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * The runtime's DoubleStream, written from the Java SE specification: a stage
 * whose source pulls values through the stages before it (see
 * AbstractPipeline). count() does not traverse a stream whose size is known,
 * as on the JVM.
 * Sums and averages use Kahan summation, as the JDK does.
 */
final class DoublePipeline extends AbstractPipeline implements DoubleStream {
	final DoubleSource source;

	DoublePipeline(DoubleSource source, Shared shared) {
		super(shared);
		this.source = source;
	}

	static DoublePipeline head(DoubleSource source) {
		return new DoublePipeline(source, new Shared());
	}

	static DoubleStream ofArray(double[] array, int from, int to) {
		return head(new ArraySource(array, from, to));
	}

	static DoubleStream iterate(double seed, DoublePredicate hasNext, DoubleUnaryOperator next) {
		return head(new Iterate(seed, hasNext, next));
	}

	static DoubleStream generate(DoubleSupplier supplier) {
		return head(new Generate(supplier));
	}

	static DoubleStream concat(DoubleStream a, DoubleStream b) {
		DoubleSource first = sourceOf(a);
		DoubleSource second = sourceOf(b);
		DoublePipeline pipeline = head(new Concat(first, second));
		pipeline.shared.parallel = a.isParallel() || b.isParallel();
		pipeline.addCloseHandler(new CloseBoth(a, b));
		return pipeline;
	}

	/** The source of a stream another stream consumes (concat, flatMap) */
	static DoubleSource sourceOf(DoubleStream stream) {
		if (stream instanceof DoublePipeline) {
			DoublePipeline pipeline = (DoublePipeline) stream;
			pipeline.link();
			return pipeline.source;
		}
		return new IteratorSource(stream.iterator());
	}

	private DoubleStream chain(DoubleSource next) {
		link();
		return new DoublePipeline(next, shared);
	}

	private DoubleSource consume() {
		link();
		return source;
	}

	// BaseStream

	public PrimitiveIterator.OfDouble iterator() {
		return new SourceIterator(consume());
	}

	public boolean isParallel() {
		return shared.parallel;
	}

	public DoubleStream sequential() {
		shared.parallel = false;
		return this;
	}

	public DoubleStream parallel() {
		shared.parallel = true;
		return this;
	}

	public DoubleStream unordered() {
		return chain(source);
	}

	public DoubleStream onClose(Runnable closeHandler) {
		addCloseHandler(closeHandler);
		return this;
	}

	public void close() {
		closePipeline();
	}

	// Intermediate operations

	public DoubleStream filter(DoublePredicate predicate) {
		Objects.requireNonNull(predicate);
		return chain(new Filter(source, predicate));
	}

	public DoubleStream map(DoubleUnaryOperator mapper) {
		Objects.requireNonNull(mapper);
		return chain(new MapStage(source, mapper));
	}

	public <U> Stream<U> mapToObj(DoubleFunction<? extends U> mapper) {
		Objects.requireNonNull(mapper);
		link();
		return new ReferencePipeline<U>(new MapToObj<U>(source, mapper), shared);
	}

	public Stream<Double> boxed() {
		return mapToObj(new Boxer());
	}

	public IntStream mapToInt(DoubleToIntFunction mapper) {
		Objects.requireNonNull(mapper);
		link();
		return new IntPipeline(new MapToInt(source, mapper), shared);
	}

	public LongStream mapToLong(DoubleToLongFunction mapper) {
		Objects.requireNonNull(mapper);
		link();
		return new LongPipeline(new MapToLong(source, mapper), shared);
	}

	public DoubleStream flatMap(DoubleFunction<? extends DoubleStream> mapper) {
		Objects.requireNonNull(mapper);
		return chain(new FlatMap(source, mapper));
	}

	/** Like the JVM: boxed().distinct(), unboxed again */
	public DoubleStream distinct() {
		return boxed().distinct().mapToDouble(new Unboxer());
	}

	public DoubleStream sorted() {
		return chain(new Sorted(source));
	}

	public DoubleStream peek(DoubleConsumer action) {
		Objects.requireNonNull(action);
		return chain(new Peek(source, action));
	}

	public DoubleStream limit(long maxSize) {
		if (maxSize < 0) {
			throw new IllegalArgumentException(Long.toString(maxSize));
		}
		return chain(new Limit(source, maxSize));
	}

	public DoubleStream skip(long n) {
		if (n < 0) {
			throw new IllegalArgumentException(Long.toString(n));
		}
		if (n == 0) {
			return chain(source);
		}
		return chain(new Skip(source, n));
	}

	public DoubleStream takeWhile(DoublePredicate predicate) {
		Objects.requireNonNull(predicate);
		return chain(new TakeWhile(source, predicate));
	}

	public DoubleStream dropWhile(DoublePredicate predicate) {
		Objects.requireNonNull(predicate);
		return chain(new DropWhile(source, predicate));
	}

	// Terminal operations

	public void forEach(DoubleConsumer action) {
		Objects.requireNonNull(action);
		consume().forEachRemaining(action);
	}

	public void forEachOrdered(DoubleConsumer action) {
		forEach(action);
	}

	public double[] toArray() {
		Collect sink = new Collect();
		consume().forEachRemaining(sink);
		return sink.toArray();
	}

	public double reduce(double identity, DoubleBinaryOperator op) {
		Objects.requireNonNull(op);
		Reduce sink = new Reduce(op);
		sink.empty = false;
		sink.state = identity;
		consume().forEachRemaining(sink);
		return sink.state;
	}

	public OptionalDouble reduce(DoubleBinaryOperator op) {
		Objects.requireNonNull(op);
		Reduce sink = new Reduce(op);
		consume().forEachRemaining(sink);
		return sink.empty ? OptionalDouble.empty() : OptionalDouble.of(sink.state);
	}

	public <R> R collect(Supplier<R> supplier, ObjDoubleConsumer<R> accumulator, BiConsumer<R, R> combiner) {
		Objects.requireNonNull(supplier);
		Objects.requireNonNull(accumulator);
		Objects.requireNonNull(combiner);
		DoubleSource s = consume();
		CollectInto<R> sink = new CollectInto<R>(supplier.get(), accumulator);
		s.forEachRemaining(sink);
		return sink.container;
	}

	public OptionalDouble min() {
		return reduce(new MinMax(true));
	}

	public OptionalDouble max() {
		return reduce(new MinMax(false));
	}

	public long count() {
		DoubleSource s = consume();
		long size = s.exactSize();
		if (size >= 0) {
			return size;
		}
		Statistics sink = new Statistics();
		s.forEachRemaining(sink);
		return sink.count;
	}

	public DoubleSummaryStatistics summaryStatistics() {
		DoubleSummaryStatistics statistics = new DoubleSummaryStatistics();
		consume().forEachRemaining(statistics);
		return statistics;
	}

	public double sum() {
		Statistics sink = new Statistics();
		consume().forEachRemaining(sink);
		return sink.sum();
	}

	public OptionalDouble average() {
		Statistics sink = new Statistics();
		consume().forEachRemaining(sink);
		return sink.count > 0 ? OptionalDouble.of(sink.sum() / sink.count) : OptionalDouble.empty();
	}

	public boolean anyMatch(DoublePredicate predicate) {
		return match(predicate, true);
	}

	public boolean allMatch(DoublePredicate predicate) {
		return !match(predicate, false);
	}

	public boolean noneMatch(DoublePredicate predicate) {
		return !match(predicate, true);
	}

	/** Whether some value's test result is the given one; stops at the first */
	private boolean match(DoublePredicate predicate, boolean result) {
		Objects.requireNonNull(predicate);
		DoubleSource s = consume();
		Box box = new Box();
		while (s.tryAdvance(box)) {
			if (predicate.test(box.value) == result) {
				return true;
			}
		}
		return false;
	}

	public OptionalDouble findFirst() {
		DoubleSource s = consume();
		Box box = new Box();
		if (s.tryAdvance(box)) {
			return OptionalDouble.of(box.value);
		}
		return OptionalDouble.empty();
	}

	public OptionalDouble findAny() {
		return findFirst();
	}

	// Sinks of the terminal operations

	static final class Box implements DoubleConsumer {
		double value;

		public void accept(double t) {
			value = t;
		}
	}

	static final class Collect implements DoubleConsumer {
		private double[] values = new double[16];
		private int size;

		public void accept(double t) {
			if (size == values.length) {
				values = Arrays.copyOf(values, size * 2);
			}
			values[size++] = t;
		}

		double[] toArray() {
			return Arrays.copyOf(values, size);
		}
	}

	static final class Reduce implements DoubleConsumer {
		boolean empty = true;
		double state;
		private final DoubleBinaryOperator op;

		Reduce(DoubleBinaryOperator op) {
			this.op = op;
		}

		public void accept(double t) {
			if (empty) {
				empty = false;
				state = t;
			} else {
				state = op.applyAsDouble(state, t);
			}
		}
	}

	static final class MinMax implements DoubleBinaryOperator {
		private final boolean min;

		MinMax(boolean min) {
			this.min = min;
		}

		public double applyAsDouble(double left, double right) {
			return min ? Math.min(left, right) : Math.max(left, right);
		}
	}

	static final class CollectInto<R> implements DoubleConsumer {
		final R container;
		private final ObjDoubleConsumer<R> accumulator;

		CollectInto(R container, ObjDoubleConsumer<R> accumulator) {
			this.container = container;
			this.accumulator = accumulator;
		}

		public void accept(double t) {
			accumulator.accept(container, t);
		}
	}

	/** Count and Kahan (compensated) sum, as the JDK computes them */
	static final class Statistics implements DoubleConsumer {
		long count;
		private double sum;
		private double compensation;
		private double simpleSum;

		public void accept(double t) {
			count++;
			simpleSum += t;
			double y = t - compensation;
			double total = sum + y;
			compensation = (total - sum) - y;
			sum = total;
		}

		double sum() {
			return AbstractPipeline.finalSum(sum, compensation, simpleSum);
		}
	}

	static final class Boxer implements DoubleFunction<Double> {
		public Double apply(double value) {
			return Double.valueOf(value);
		}
	}

	static final class Unboxer implements ToDoubleFunction<Double> {
		public double applyAsDouble(Double value) {
			return value.doubleValue();
		}
	}

	static final class SourceIterator implements PrimitiveIterator.OfDouble, DoubleConsumer {
		private final DoubleSource source;
		private boolean ready;
		private double next;

		SourceIterator(DoubleSource source) {
			this.source = source;
		}

		public void accept(double t) {
			ready = true;
			next = t;
		}

		public boolean hasNext() {
			if (!ready) {
				source.tryAdvance(this);
			}
			return ready;
		}

		public double nextDouble() {
			if (!ready && !hasNext()) {
				throw new NoSuchElementException();
			}
			ready = false;
			return next;
		}

		public Double next() {
			return Double.valueOf(nextDouble());
		}
	}

	static final class DoubleBuilder implements DoubleStream.Builder {
		private final Collect values = new Collect();
		private boolean built;

		public void accept(double t) {
			if (built) {
				throw new IllegalStateException();
			}
			values.accept(t);
		}

		public DoubleStream.Builder add(double t) {
			accept(t);
			return this;
		}

		public DoubleStream build() {
			if (built) {
				throw new IllegalStateException();
			}
			built = true;
			double[] array = values.toArray();
			return ofArray(array, 0, array.length);
		}
	}

	// Sources

	static final class ArraySource extends DoubleSource {
		private final double[] array;
		private int index;
		private final int fence;

		ArraySource(double[] array, int from, int to) {
			this.array = array;
			this.index = from;
			this.fence = to;
		}

		boolean tryAdvance(DoubleConsumer action) {
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

	static final class IteratorSource extends DoubleSource {
		private final PrimitiveIterator.OfDouble iterator;

		IteratorSource(PrimitiveIterator.OfDouble iterator) {
			this.iterator = iterator;
		}

		boolean tryAdvance(DoubleConsumer action) {
			if (!iterator.hasNext()) {
				return false;
			}
			action.accept(iterator.nextDouble());
			return true;
		}
	}

	/** iterate: seed, then next applied to the previous value, while hasNext holds */
	static final class Iterate extends DoubleSource {
		private final double seed;
		private final DoublePredicate hasNext;
		private final DoubleUnaryOperator next;
		private double previous;
		private boolean started;
		private boolean finished;

		Iterate(double seed, DoublePredicate hasNext, DoubleUnaryOperator next) {
			this.seed = seed;
			this.hasNext = hasNext;
			this.next = next;
		}

		boolean tryAdvance(DoubleConsumer action) {
			if (finished) {
				return false;
			}
			double t;
			if (started) {
				t = next.applyAsDouble(previous);
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

	static final class Generate extends DoubleSource {
		private final DoubleSupplier supplier;

		Generate(DoubleSupplier supplier) {
			this.supplier = supplier;
		}

		boolean tryAdvance(DoubleConsumer action) {
			action.accept(supplier.getAsDouble());
			return true;
		}
	}

	static final class Concat extends DoubleSource {
		private final DoubleSource first;
		private final DoubleSource second;
		private boolean inSecond;

		Concat(DoubleSource first, DoubleSource second) {
			this.first = first;
			this.second = second;
		}

		boolean tryAdvance(DoubleConsumer action) {
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

	static final class Filter extends DoubleSource implements DoubleConsumer {
		private final DoubleSource upstream;
		private final DoublePredicate predicate;
		private DoubleConsumer downstream;
		private boolean passed;

		Filter(DoubleSource upstream, DoublePredicate predicate) {
			this.upstream = upstream;
			this.predicate = predicate;
		}

		boolean tryAdvance(DoubleConsumer action) {
			downstream = action;
			passed = false;
			while (upstream.tryAdvance(this)) {
				if (passed) {
					return true;
				}
			}
			return false;
		}

		public void accept(double t) {
			if (predicate.test(t)) {
				passed = true;
				downstream.accept(t);
			}
		}
	}

	static final class MapStage extends DoubleSource implements DoubleConsumer {
		private final DoubleSource upstream;
		private final DoubleUnaryOperator mapper;
		private DoubleConsumer downstream;

		MapStage(DoubleSource upstream, DoubleUnaryOperator mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(DoubleConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(double t) {
			downstream.accept(mapper.applyAsDouble(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class MapToObj<U> extends RefSource<U> implements DoubleConsumer {
		private final DoubleSource upstream;
		private final DoubleFunction<? extends U> mapper;
		private Consumer<? super U> downstream;

		MapToObj(DoubleSource upstream, DoubleFunction<? extends U> mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(Consumer<? super U> action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(double t) {
			downstream.accept(mapper.apply(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class MapToInt extends IntSource implements DoubleConsumer {
		private final DoubleSource upstream;
		private final DoubleToIntFunction mapper;
		private IntConsumer downstream;

		MapToInt(DoubleSource upstream, DoubleToIntFunction mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(IntConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(double t) {
			downstream.accept(mapper.applyAsInt(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class MapToLong extends LongSource implements DoubleConsumer {
		private final DoubleSource upstream;
		private final DoubleToLongFunction mapper;
		private LongConsumer downstream;

		MapToLong(DoubleSource upstream, DoubleToLongFunction mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(LongConsumer action) {
			downstream = action;
			return upstream.tryAdvance(this);
		}

		public void accept(double t) {
			downstream.accept(mapper.applyAsLong(t));
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	/** Pulls each mapped stream to its end, then closes it, as the JVM does */
	static final class FlatMap extends DoubleSource implements DoubleConsumer {
		private final DoubleSource upstream;
		private final DoubleFunction<? extends DoubleStream> mapper;
		private DoubleStream innerStream;
		private DoubleSource inner;

		FlatMap(DoubleSource upstream, DoubleFunction<? extends DoubleStream> mapper) {
			this.upstream = upstream;
			this.mapper = mapper;
		}

		boolean tryAdvance(DoubleConsumer action) {
			while (true) {
				if (inner != null) {
					if (inner.tryAdvance(action)) {
						return true;
					}
					DoubleStream done = innerStream;
					inner = null;
					innerStream = null;
					done.close();
				}
				if (!upstream.tryAdvance(this)) {
					return false;
				}
			}
		}

		public void accept(double t) {
			DoubleStream stream = mapper.apply(t);
			if (stream != null) {
				inner = sourceOf(stream);
				innerStream = stream;
			}
		}
	}

	/** Pulls every value on the first request, then sorts them */
	static final class Sorted extends DoubleSource {
		private final DoubleSource upstream;
		private double[] sorted;
		private int index;

		Sorted(DoubleSource upstream) {
			this.upstream = upstream;
		}

		boolean tryAdvance(DoubleConsumer action) {
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

	static final class Peek extends DoubleSource implements DoubleConsumer {
		private final DoubleSource upstream;
		private final DoubleConsumer action;
		private DoubleConsumer downstream;

		Peek(DoubleSource upstream, DoubleConsumer action) {
			this.upstream = upstream;
			this.action = action;
		}

		boolean tryAdvance(DoubleConsumer next) {
			downstream = next;
			return upstream.tryAdvance(this);
		}

		public void accept(double t) {
			action.accept(t);
			downstream.accept(t);
		}

		long exactSize() {
			return upstream.exactSize();
		}
	}

	static final class Limit extends DoubleSource {
		private final DoubleSource upstream;
		private long remaining;

		Limit(DoubleSource upstream, long maxSize) {
			this.upstream = upstream;
			this.remaining = maxSize;
		}

		boolean tryAdvance(DoubleConsumer action) {
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

	static final class Skip extends DoubleSource {
		private final DoubleSource upstream;
		private long toSkip;

		Skip(DoubleSource upstream, long n) {
			this.upstream = upstream;
			this.toSkip = n;
		}

		boolean tryAdvance(DoubleConsumer action) {
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

	static final class TakeWhile extends DoubleSource implements DoubleConsumer {
		private final DoubleSource upstream;
		private final DoublePredicate predicate;
		private DoubleConsumer downstream;
		private boolean taking = true;

		TakeWhile(DoubleSource upstream, DoublePredicate predicate) {
			this.upstream = upstream;
			this.predicate = predicate;
		}

		boolean tryAdvance(DoubleConsumer action) {
			if (!taking) {
				return false;
			}
			downstream = action;
			if (!upstream.tryAdvance(this)) {
				taking = false;
			}
			return taking;
		}

		public void accept(double t) {
			if (predicate.test(t)) {
				downstream.accept(t);
			} else {
				taking = false;
			}
		}
	}

	static final class DropWhile extends DoubleSource implements DoubleConsumer {
		private final DoubleSource upstream;
		private final DoublePredicate predicate;
		private DoubleConsumer downstream;
		private boolean dropping = true;
		private boolean passed;

		DropWhile(DoubleSource upstream, DoublePredicate predicate) {
			this.upstream = upstream;
			this.predicate = predicate;
		}

		boolean tryAdvance(DoubleConsumer action) {
			downstream = action;
			passed = false;
			while (upstream.tryAdvance(this)) {
				if (passed) {
					return true;
				}
			}
			return false;
		}

		public void accept(double t) {
			if (dropping && predicate.test(t)) {
				return;
			}
			dropping = false;
			passed = true;
			downstream.accept(t);
		}
	}
}
