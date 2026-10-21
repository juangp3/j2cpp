package java.util.concurrent.locks;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * ReentrantLock, written from the Java SE specification, on the monitor of
 * a private object: the owner and hold count are fields guarded by it, and
 * threads waiting for the lock or for a Condition wait on it. Fairness is
 * not implemented (isFair() reports the requested policy), and the methods
 * that inspect queued threads are absent.
 */
public class ReentrantLock implements Lock, java.io.Serializable {
	private final Object sync = new Object();
	private final boolean fair;
	/** Guarded by sync */
	private Thread owner;
	private int holds;

	public ReentrantLock() {
		this(false);
	}

	public ReentrantLock(boolean fair) {
		this.fair = fair;
	}

	public void lock() {
		Thread me = Thread.currentThread();
		boolean interrupted = false;
		synchronized (sync) {
			if (owner == me) {
				holds++;
				return;
			}
			while (owner != null) {
				try {
					sync.wait();
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
			owner = me;
			holds = 1;
		}
		if (interrupted) {
			me.interrupt();
		}
	}

	public void lockInterruptibly() throws InterruptedException {
		if (Thread.interrupted()) {
			throw new InterruptedException();
		}
		Thread me = Thread.currentThread();
		synchronized (sync) {
			if (owner == me) {
				holds++;
				return;
			}
			while (owner != null) {
				sync.wait();
			}
			owner = me;
			holds = 1;
		}
	}

	public boolean tryLock() {
		Thread me = Thread.currentThread();
		synchronized (sync) {
			if (owner == null) {
				owner = me;
				holds = 1;
				return true;
			}
			if (owner == me) {
				holds++;
				return true;
			}
			return false;
		}
	}

	public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
		if (Thread.interrupted()) {
			throw new InterruptedException();
		}
		Thread me = Thread.currentThread();
		long end = System.nanoTime() + unit.toNanos(timeout);
		synchronized (sync) {
			if (owner == me) {
				holds++;
				return true;
			}
			while (owner != null) {
				long remaining = end - System.nanoTime();
				if (remaining <= 0) {
					return false;
				}
				sync.wait(waitMillis(remaining));
			}
			owner = me;
			holds = 1;
			return true;
		}
	}

	public void unlock() {
		synchronized (sync) {
			if (owner != Thread.currentThread()) {
				throw new IllegalMonitorStateException();
			}
			if (--holds == 0) {
				owner = null;
				// Lock and condition waiters share the monitor
				sync.notifyAll();
			}
		}
	}

	public Condition newCondition() {
		return new ConditionObject();
	}

	public int getHoldCount() {
		synchronized (sync) {
			return owner == Thread.currentThread() ? holds : 0;
		}
	}

	public boolean isHeldByCurrentThread() {
		synchronized (sync) {
			return owner == Thread.currentThread();
		}
	}

	public boolean isLocked() {
		synchronized (sync) {
			return owner != null;
		}
	}

	public final boolean isFair() {
		return fair;
	}

	public String toString() {
		Thread o;
		synchronized (sync) {
			o = owner;
		}
		return super.toString() + (o == null ? "[Unlocked]" : "[Locked by thread " + o.getName() + "]");
	}

	private static long waitMillis(long nanos) {
		long millis = nanos / 1000000L;
		return nanos % 1000000L == 0 ? millis : millis + 1;
	}

	/** A Condition: each waiter has a flag that signal sets */
	private final class ConditionObject implements Condition {
		/** Guarded by sync, in arrival order */
		private final ArrayList<boolean[]> waiters = new ArrayList<boolean[]>();

		/** Releases every hold; returns the count to restore */
		private int release(Thread me) {
			if (owner != me) {
				throw new IllegalMonitorStateException();
			}
			int saved = holds;
			owner = null;
			holds = 0;
			sync.notifyAll();
			return saved;
		}

		private boolean reacquire(Thread me, int saved) {
			boolean interrupted = false;
			while (owner != null) {
				try {
					sync.wait();
				} catch (InterruptedException e) {
					interrupted = true;
				}
			}
			owner = me;
			holds = saved;
			return interrupted;
		}

		public void await() throws InterruptedException {
			Thread me = Thread.currentThread();
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
			boolean interrupted;
			InterruptedException thrown = null;
			synchronized (sync) {
				int saved = release(me);
				boolean[] signalled = new boolean[1];
				waiters.add(signalled);
				try {
					while (!signalled[0]) {
						sync.wait();
					}
				} catch (InterruptedException e) {
					if (!signalled[0]) {
						waiters.remove(signalled);
						thrown = e;
					}
				}
				interrupted = reacquire(me, saved);
			}
			if (thrown != null) {
				throw thrown;
			}
			if (interrupted) {
				me.interrupt();
			}
		}

		public void awaitUninterruptibly() {
			Thread me = Thread.currentThread();
			boolean interrupted = false;
			synchronized (sync) {
				int saved = release(me);
				boolean[] signalled = new boolean[1];
				waiters.add(signalled);
				while (!signalled[0]) {
					try {
						sync.wait();
					} catch (InterruptedException e) {
						interrupted = true;
					}
				}
				interrupted |= reacquire(me, saved);
			}
			if (interrupted) {
				me.interrupt();
			}
		}

		public long awaitNanos(long nanosTimeout) throws InterruptedException {
			Thread me = Thread.currentThread();
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
			long end = System.nanoTime() + nanosTimeout;
			boolean interrupted;
			InterruptedException thrown = null;
			synchronized (sync) {
				int saved = release(me);
				boolean[] signalled = new boolean[1];
				waiters.add(signalled);
				try {
					while (!signalled[0]) {
						long remaining = end - System.nanoTime();
						if (remaining <= 0) {
							break;
						}
						sync.wait(waitMillis(remaining));
					}
				} catch (InterruptedException e) {
					if (!signalled[0]) {
						thrown = e;
					}
				}
				if (!signalled[0]) {
					waiters.remove(signalled);
				}
				interrupted = reacquire(me, saved);
			}
			if (thrown != null) {
				throw thrown;
			}
			if (interrupted) {
				me.interrupt();
			}
			return end - System.nanoTime();
		}

		public boolean await(long time, TimeUnit unit) throws InterruptedException {
			return awaitNanos(unit.toNanos(time)) > 0;
		}

		public void signal() {
			synchronized (sync) {
				if (owner != Thread.currentThread()) {
					throw new IllegalMonitorStateException();
				}
				if (!waiters.isEmpty()) {
					waiters.remove(0)[0] = true;
					sync.notifyAll();
				}
			}
		}

		public void signalAll() {
			synchronized (sync) {
				if (owner != Thread.currentThread()) {
					throw new IllegalMonitorStateException();
				}
				for (boolean[] signalled : waiters) {
					signalled[0] = true;
				}
				waiters.clear();
				sync.notifyAll();
			}
		}
	}
}
