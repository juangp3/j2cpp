package java.util.concurrent;

/** CountDownLatch, written from the Java SE specification, on a private monitor */
public class CountDownLatch {
	private final Object lock = new Object();
	/** Guarded by lock */
	private long count;

	public CountDownLatch(int count) {
		if (count < 0) {
			throw new IllegalArgumentException("count < 0");
		}
		this.count = count;
	}

	public void await() throws InterruptedException {
		if (Thread.interrupted()) {
			throw new InterruptedException();
		}
		synchronized (lock) {
			while (count > 0) {
				lock.wait();
			}
		}
	}

	public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
		if (Thread.interrupted()) {
			throw new InterruptedException();
		}
		long end = System.nanoTime() + unit.toNanos(timeout);
		synchronized (lock) {
			while (count > 0) {
				long remaining = end - System.nanoTime();
				if (remaining <= 0) {
					return false;
				}
				lock.wait(TimeUnit.waitMillis(remaining));
			}
			return true;
		}
	}

	public void countDown() {
		synchronized (lock) {
			if (count > 0 && --count == 0) {
				lock.notifyAll();
			}
		}
	}

	public long getCount() {
		synchronized (lock) {
			return count;
		}
	}

	public String toString() {
		return super.toString() + "[Count = " + getCount() + "]";
	}
}
