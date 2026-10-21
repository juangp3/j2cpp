package java.util.concurrent.locks;

import java.util.concurrent.TimeUnit;

/** Condition; awaitUntil(Date) is absent, as there is no java.util.Date */
public interface Condition {
	void await() throws InterruptedException;

	void awaitUninterruptibly();

	long awaitNanos(long nanosTimeout) throws InterruptedException;

	boolean await(long time, TimeUnit unit) throws InterruptedException;

	void signal();

	void signalAll();
}
