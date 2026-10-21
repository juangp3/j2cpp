package java.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * AbstractExecutorService, written from the Java SE specification.
 * invokeAny waits for the tasks in order and returns the first successful
 * result, which the specification allows; it does not return early when a
 * later task finishes first.
 */
public abstract class AbstractExecutorService implements ExecutorService {
	public AbstractExecutorService() {
	}

	protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
		return new FutureTask<T>(runnable, value);
	}

	protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
		return new FutureTask<T>(callable);
	}

	public Future<?> submit(Runnable task) {
		if (task == null) {
			throw new NullPointerException();
		}
		RunnableFuture<Void> f = newTaskFor(task, null);
		execute(f);
		return f;
	}

	public <T> Future<T> submit(Runnable task, T result) {
		if (task == null) {
			throw new NullPointerException();
		}
		RunnableFuture<T> f = newTaskFor(task, result);
		execute(f);
		return f;
	}

	public <T> Future<T> submit(Callable<T> task) {
		if (task == null) {
			throw new NullPointerException();
		}
		RunnableFuture<T> f = newTaskFor(task);
		execute(f);
		return f;
	}

	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		if (tasks == null) {
			throw new NullPointerException();
		}
		List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
		try {
			for (Callable<T> t : tasks) {
				RunnableFuture<T> f = newTaskFor(t);
				futures.add(f);
				execute(f);
			}
			for (Future<T> f : futures) {
				if (!f.isDone()) {
					try {
						f.get();
					} catch (CancellationException | ExecutionException ignore) {
					}
				}
			}
			return futures;
		} catch (Throwable t) {
			cancelAll(futures);
			throw t;
		}
	}

	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException {
		if (tasks == null || unit == null) {
			throw new NullPointerException();
		}
		long end = System.nanoTime() + unit.toNanos(timeout);
		List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
		try {
			for (Callable<T> t : tasks) {
				futures.add(newTaskFor(t));
			}
			for (Future<T> f : futures) {
				if (end - System.nanoTime() <= 0) {
					cancelAll(futures);
					return futures;
				}
				execute((Runnable) f);
			}
			for (Future<T> f : futures) {
				if (!f.isDone()) {
					try {
						f.get(end - System.nanoTime(), TimeUnit.NANOSECONDS);
					} catch (CancellationException | ExecutionException ignore) {
					} catch (TimeoutException timedOut) {
						cancelAll(futures);
						return futures;
					}
				}
			}
			return futures;
		} catch (Throwable t) {
			cancelAll(futures);
			throw t;
		}
	}

	private static <T> void cancelAll(List<Future<T>> futures) {
		for (Future<T> f : futures) {
			f.cancel(true);
		}
	}

	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		try {
			return doInvokeAny(tasks, false, 0);
		} catch (TimeoutException cannotHappen) {
			throw new IllegalStateException(cannotHappen);
		}
	}

	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {
		if (unit == null) {
			throw new NullPointerException();
		}
		return doInvokeAny(tasks, true, unit.toNanos(timeout));
	}

	private <T> T doInvokeAny(Collection<? extends Callable<T>> tasks, boolean timed, long nanos)
			throws InterruptedException, ExecutionException, TimeoutException {
		if (tasks == null) {
			throw new NullPointerException();
		}
		if (tasks.isEmpty()) {
			throw new IllegalArgumentException();
		}
		long end = System.nanoTime() + nanos;
		List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
		try {
			for (Callable<T> t : tasks) {
				futures.add(submit(t));
			}
			ExecutionException last = null;
			for (Future<T> f : futures) {
				try {
					return timed ? f.get(end - System.nanoTime(), TimeUnit.NANOSECONDS) : f.get();
				} catch (ExecutionException e) {
					last = e;
				} catch (CancellationException e) {
					last = new ExecutionException(e);
				}
			}
			throw last;
		} finally {
			cancelAll(futures);
		}
	}
}
