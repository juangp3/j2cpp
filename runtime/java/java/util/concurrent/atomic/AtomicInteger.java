package java.util.concurrent.atomic;

import java.util.function.IntBinaryOperator;
import java.util.function.IntUnaryOperator;

/**
 * AtomicInteger, written from the Java SE specification: a volatile field,
 * which J2C translates to std::atomic, with natives for the read-modify-write
 * operations (AtomicInteger-native.cpp). Every access is sequentially
 * consistent, also the plain, opaque, acquire and release ones.
 */
public class AtomicInteger extends Number implements java.io.Serializable {
	private volatile int value;

	public AtomicInteger(int initialValue) {
		value = initialValue;
	}

	public AtomicInteger() {
	}

	public final int get() {
		return value;
	}

	public final void set(int newValue) {
		value = newValue;
	}

	public final void lazySet(int newValue) {
		value = newValue;
	}

	public final native int getAndSet(int newValue);

	public final native boolean compareAndSet(int expectedValue, int newValue);

	public final boolean weakCompareAndSetPlain(int expectedValue, int newValue) {
		return compareAndSet(expectedValue, newValue);
	}

	public final boolean weakCompareAndSetVolatile(int expectedValue, int newValue) {
		return compareAndSet(expectedValue, newValue);
	}

	public final native int getAndAdd(int delta);

	public final int getAndIncrement() {
		return getAndAdd(1);
	}

	public final int getAndDecrement() {
		return getAndAdd(-1);
	}

	public final int incrementAndGet() {
		return getAndAdd(1) + 1;
	}

	public final int decrementAndGet() {
		return getAndAdd(-1) - 1;
	}

	public final int addAndGet(int delta) {
		return getAndAdd(delta) + delta;
	}

	public final int getAndUpdate(IntUnaryOperator updateFunction) {
		int prev = get();
		while (true) {
			int next = updateFunction.applyAsInt(prev);
			if (compareAndSet(prev, next)) {
				return prev;
			}
			prev = get();
		}
	}

	public final int updateAndGet(IntUnaryOperator updateFunction) {
		int prev = get();
		while (true) {
			int next = updateFunction.applyAsInt(prev);
			if (compareAndSet(prev, next)) {
				return next;
			}
			prev = get();
		}
	}

	public final int getAndAccumulate(int x, IntBinaryOperator accumulatorFunction) {
		int prev = get();
		while (true) {
			int next = accumulatorFunction.applyAsInt(prev, x);
			if (compareAndSet(prev, next)) {
				return prev;
			}
			prev = get();
		}
	}

	public final int accumulateAndGet(int x, IntBinaryOperator accumulatorFunction) {
		int prev = get();
		while (true) {
			int next = accumulatorFunction.applyAsInt(prev, x);
			if (compareAndSet(prev, next)) {
				return next;
			}
			prev = get();
		}
	}

	public String toString() {
		return Integer.toString(get());
	}

	public int intValue() {
		return get();
	}

	public long longValue() {
		return (long) get();
	}

	public float floatValue() {
		return (float) get();
	}

	public double doubleValue() {
		return (double) get();
	}

	public final int getPlain() {
		return value;
	}

	public final void setPlain(int newValue) {
		value = newValue;
	}

	public final int getOpaque() {
		return value;
	}

	public final void setOpaque(int newValue) {
		value = newValue;
	}

	public final int getAcquire() {
		return value;
	}

	public final void setRelease(int newValue) {
		value = newValue;
	}
}
