package java.util.concurrent;

/**
 * FutureTask, written from the Java SE specification, on the monitor of a
 * private lock: get() waits on it until the task completes or is cancelled.
 */
public class FutureTask<V> implements RunnableFuture<V> {
	private static final int NEW = 0;
	private static final int NORMAL = 1;
	private static final int EXCEPTIONAL = 2;
	private static final int CANCELLED = 3;

	private final Object lock = new Object();
	private Callable<V> callable;
	/** Guarded by lock */
	private int state = NEW;
	private Object outcome;
	private Thread runner;

	public FutureTask(Callable<V> callable) {
		if (callable == null) {
			throw new NullPointerException();
		}
		this.callable = callable;
	}

	public FutureTask(Runnable runnable, V result) {
		this.callable = Executors.callable(runnable, result);
	}

	public boolean isCancelled() {
		synchronized (lock) {
			return state == CANCELLED;
		}
	}

	public boolean isDone() {
		synchronized (lock) {
			return state != NEW;
		}
	}

	public boolean cancel(boolean mayInterruptIfRunning) {
		synchronized (lock) {
			if (state != NEW) {
				return false;
			}
			state = CANCELLED;
			if (mayInterruptIfRunning && runner != null) {
				runner.interrupt();
			}
			lock.notifyAll();
		}
		finish();
		return true;
	}

	public V get() throws InterruptedException, ExecutionException {
		synchronized (lock) {
			while (state == NEW) {
				lock.wait();
			}
			return report();
		}
	}

	public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		if (unit == null) {
			throw new NullPointerException();
		}
		long end = System.nanoTime() + unit.toNanos(timeout);
		synchronized (lock) {
			while (state == NEW) {
				long remaining = end - System.nanoTime();
				if (remaining <= 0) {
					throw new TimeoutException();
				}
				lock.wait(TimeUnit.waitMillis(remaining));
			}
			return report();
		}
	}

	@SuppressWarnings("unchecked")
	private V report() throws ExecutionException {
		if (state == NORMAL) {
			return (V) outcome;
		}
		if (state == CANCELLED) {
			throw new CancellationException();
		}
		throw new ExecutionException((Throwable) outcome);
	}

	protected void done() {
	}

	private void finish() {
		done();
		callable = null;
	}

	protected void set(V v) {
		synchronized (lock) {
			if (state != NEW) {
				return;
			}
			outcome = v;
			state = NORMAL;
			lock.notifyAll();
		}
		finish();
	}

	protected void setException(Throwable t) {
		synchronized (lock) {
			if (state != NEW) {
				return;
			}
			outcome = t;
			state = EXCEPTIONAL;
			lock.notifyAll();
		}
		finish();
	}

	public void run() {
		Callable<V> c;
		synchronized (lock) {
			if (state != NEW || runner != null) {
				return;
			}
			runner = Thread.currentThread();
			c = callable;
		}
		try {
			V result;
			try {
				result = c.call();
			} catch (Throwable ex) {
				setException(ex);
				return;
			}
			set(result);
		} finally {
			synchronized (lock) {
				runner = null;
			}
		}
	}

	public String toString() {
		String status;
		synchronized (lock) {
			switch (state) {
			case NORMAL:
				status = "[Completed normally]";
				break;
			case EXCEPTIONAL:
				status = "[Completed exceptionally: " + outcome + "]";
				break;
			case CANCELLED:
				status = "[Cancelled]";
				break;
			default:
				status = callable == null ? "[Not completed]" : "[Not completed, task = " + callable + "]";
			}
		}
		return super.toString() + status;
	}
}
