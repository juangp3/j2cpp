package java.util.concurrent.atomic;

import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/**
 * AtomicReference, written from the Java SE specification; see
 * AtomicInteger. compareAndSet compares references (==), as in Java.
 */
public class AtomicReference<V> implements java.io.Serializable {
	private volatile V value;

	public AtomicReference(V initialValue) {
		value = initialValue;
	}

	public AtomicReference() {
	}

	public final V get() {
		return value;
	}

	public final void set(V newValue) {
		value = newValue;
	}

	public final void lazySet(V newValue) {
		value = newValue;
	}

	@SuppressWarnings("unchecked")
	public final V getAndSet(V newValue) {
		return (V) getAndSet0(newValue);
	}

	private native Object getAndSet0(Object newValue);

	public final boolean compareAndSet(V expectedValue, V newValue) {
		return compareAndSet0(expectedValue, newValue);
	}

	private native boolean compareAndSet0(Object expectedValue, Object newValue);

	public final boolean weakCompareAndSetPlain(V expectedValue, V newValue) {
		return compareAndSet(expectedValue, newValue);
	}

	public final boolean weakCompareAndSetVolatile(V expectedValue, V newValue) {
		return compareAndSet(expectedValue, newValue);
	}

	public final V getAndUpdate(UnaryOperator<V> updateFunction) {
		V prev = get();
		while (true) {
			V next = updateFunction.apply(prev);
			if (compareAndSet(prev, next)) {
				return prev;
			}
			prev = get();
		}
	}

	public final V updateAndGet(UnaryOperator<V> updateFunction) {
		V prev = get();
		while (true) {
			V next = updateFunction.apply(prev);
			if (compareAndSet(prev, next)) {
				return next;
			}
			prev = get();
		}
	}

	public final V getAndAccumulate(V x, BinaryOperator<V> accumulatorFunction) {
		V prev = get();
		while (true) {
			V next = accumulatorFunction.apply(prev, x);
			if (compareAndSet(prev, next)) {
				return prev;
			}
			prev = get();
		}
	}

	public final V accumulateAndGet(V x, BinaryOperator<V> accumulatorFunction) {
		V prev = get();
		while (true) {
			V next = accumulatorFunction.apply(prev, x);
			if (compareAndSet(prev, next)) {
				return next;
			}
			prev = get();
		}
	}

	public String toString() {
		return String.valueOf(get());
	}

	public final V getPlain() {
		return value;
	}

	public final void setPlain(V newValue) {
		value = newValue;
	}

	public final V getOpaque() {
		return value;
	}

	public final void setOpaque(V newValue) {
		value = newValue;
	}

	public final V getAcquire() {
		return value;
	}

	public final void setRelease(V newValue) {
		value = newValue;
	}
}
