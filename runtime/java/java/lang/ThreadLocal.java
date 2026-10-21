package java.lang;

import java.util.HashMap;
import java.util.function.Supplier;

/** ThreadLocal, written from the Java SE specification: a map in each Thread */
public class ThreadLocal<T> {
	public ThreadLocal() {
	}

	public static <S> ThreadLocal<S> withInitial(Supplier<? extends S> supplier) {
		if (supplier == null) {
			throw new NullPointerException();
		}
		return new SuppliedThreadLocal<S>(supplier);
	}

	protected T initialValue() {
		return null;
	}

	@SuppressWarnings("unchecked")
	public T get() {
		HashMap<ThreadLocal<?>, Object> map = map(Thread.currentThread());
		if (map.containsKey(this)) {
			return (T) map.get(this);
		}
		T value = initialValue();
		map.put(this, value);
		return value;
	}

	public void set(T value) {
		map(Thread.currentThread()).put(this, value);
	}

	public void remove() {
		map(Thread.currentThread()).remove(this);
	}

	private static HashMap<ThreadLocal<?>, Object> map(Thread t) {
		if (t.threadLocals == null) {
			t.threadLocals = new HashMap<ThreadLocal<?>, Object>();
		}
		return t.threadLocals;
	}

	private static final class SuppliedThreadLocal<T> extends ThreadLocal<T> {
		private final Supplier<? extends T> supplier;

		SuppliedThreadLocal(Supplier<? extends T> supplier) {
			this.supplier = supplier;
		}

		protected T initialValue() {
			return supplier.get();
		}
	}
}
