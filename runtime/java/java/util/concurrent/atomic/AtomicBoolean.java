package java.util.concurrent.atomic;

/** AtomicBoolean, written from the Java SE specification; see AtomicInteger */
public class AtomicBoolean implements java.io.Serializable {
	private volatile boolean value;

	public AtomicBoolean(boolean initialValue) {
		value = initialValue;
	}

	public AtomicBoolean() {
	}

	public final boolean get() {
		return value;
	}

	public final void set(boolean newValue) {
		value = newValue;
	}

	public final void lazySet(boolean newValue) {
		value = newValue;
	}

	public final native boolean getAndSet(boolean newValue);

	public final native boolean compareAndSet(boolean expectedValue, boolean newValue);

	public boolean weakCompareAndSetPlain(boolean expectedValue, boolean newValue) {
		return compareAndSet(expectedValue, newValue);
	}

	public boolean weakCompareAndSetVolatile(boolean expectedValue, boolean newValue) {
		return compareAndSet(expectedValue, newValue);
	}

	public String toString() {
		return Boolean.toString(get());
	}

	public final boolean getPlain() {
		return value;
	}

	public final void setPlain(boolean newValue) {
		value = newValue;
	}

	public final boolean getOpaque() {
		return value;
	}

	public final void setOpaque(boolean newValue) {
		value = newValue;
	}

	public final boolean getAcquire() {
		return value;
	}

	public final void setRelease(boolean newValue) {
		value = newValue;
	}
}
