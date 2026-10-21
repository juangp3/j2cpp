package java.lang;

public class Object {
	public Object() {
	}

	public final native Class<?> getClass();

	public boolean equals(Object obj) {
		return this == obj;
	}

	public native int hashCode();

	protected native Object clone() throws CloneNotSupportedException;

	public String toString() {
		return getClass().getName() + "@" + Integer.toHexString(hashCode());
	}

	// Monitors (cpp/src/j2c_threads.cpp); the messages are the JVM's

	public final void notify() {
		if (!notify0(false)) {
			throw new IllegalMonitorStateException("current thread is not owner");
		}
	}

	public final void notifyAll() {
		if (!notify0(true)) {
			throw new IllegalMonitorStateException("current thread is not owner");
		}
	}

	public final void wait() throws InterruptedException {
		wait(0L);
	}

	public final void wait(long timeoutMillis) throws InterruptedException {
		if (timeoutMillis < 0) {
			throw new IllegalArgumentException("timeout value is negative");
		}
		int result = wait0(timeoutMillis);
		if (result == 1) {
			throw new IllegalMonitorStateException("current thread is not owner");
		}
		if (result == 2) {
			throw new InterruptedException();
		}
	}

	public final void wait(long timeoutMillis, int nanos) throws InterruptedException {
		if (timeoutMillis < 0) {
			throw new IllegalArgumentException("timeout value is negative");
		}
		if (nanos < 0 || nanos > 999999) {
			throw new IllegalArgumentException("nanosecond timeout value out of range");
		}
		if (nanos > 0 && timeoutMillis < Long.MAX_VALUE) {
			timeoutMillis++;
		}
		wait(timeoutMillis);
	}

	/** false when the current thread does not hold the monitor */
	private native boolean notify0(boolean all);

	/** 0 when notified or timed out, 1 without the monitor, 2 when interrupted */
	private native int wait0(long timeoutMillis);
}
