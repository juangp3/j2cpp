package java.util.concurrent.atomic;

import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;

/** AtomicLong, written from the Java SE specification; see AtomicInteger */
public class AtomicLong extends Number implements java.io.Serializable {
	private volatile long value;

	public AtomicLong(long initialValue) {
		value = initialValue;
	}

	public AtomicLong() {
	}

	public final long get() {
		return value;
	}

	public final void set(long newValue) {
		value = newValue;
	}

	public final void lazySet(long newValue) {
		value = newValue;
	}

	public final native long getAndSet(long newValue);

	public final native boolean compareAndSet(long expectedValue, long newValue);

	public final boolean weakCompareAndSetPlain(long expectedValue, long newValue) {
		return compareAndSet(expectedValue, newValue);
	}

	public final boolean weakCompareAndSetVolatile(long expectedValue, long newValue) {
		return compareAndSet(expectedValue, newValue);
	}

	public final native long getAndAdd(long delta);

	public final long getAndIncrement() {
		return getAndAdd(1L);
	}

	public final long getAndDecrement() {
		return getAndAdd(-1L);
	}

	public final long incrementAndGet() {
		return getAndAdd(1L) + 1L;
	}

	public final long decrementAndGet() {
		return getAndAdd(-1L) - 1L;
	}

	public final long addAndGet(long delta) {
		return getAndAdd(delta) + delta;
	}

	public final long getAndUpdate(LongUnaryOperator updateFunction) {
		long prev = get();
		while (true) {
			long next = updateFunction.applyAsLong(prev);
			if (compareAndSet(prev, next)) {
				return prev;
			}
			prev = get();
		}
	}

	public final long updateAndGet(LongUnaryOperator updateFunction) {
		long prev = get();
		while (true) {
			long next = updateFunction.applyAsLong(prev);
			if (compareAndSet(prev, next)) {
				return next;
			}
			prev = get();
		}
	}

	public final long getAndAccumulate(long x, LongBinaryOperator accumulatorFunction) {
		long prev = get();
		while (true) {
			long next = accumulatorFunction.applyAsLong(prev, x);
			if (compareAndSet(prev, next)) {
				return prev;
			}
			prev = get();
		}
	}

	public final long accumulateAndGet(long x, LongBinaryOperator accumulatorFunction) {
		long prev = get();
		while (true) {
			long next = accumulatorFunction.applyAsLong(prev, x);
			if (compareAndSet(prev, next)) {
				return next;
			}
			prev = get();
		}
	}

	public String toString() {
		return Long.toString(get());
	}

	public int intValue() {
		return (int) get();
	}

	public long longValue() {
		return get();
	}

	public float floatValue() {
		return (float) get();
	}

	public double doubleValue() {
		return (double) get();
	}

	public final long getPlain() {
		return value;
	}

	public final void setPlain(long newValue) {
		value = newValue;
	}

	public final long getOpaque() {
		return value;
	}

	public final void setOpaque(long newValue) {
		value = newValue;
	}

	public final long getAcquire() {
		return value;
	}

	public final void setRelease(long newValue) {
		value = newValue;
	}
}
