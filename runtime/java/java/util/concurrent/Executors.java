package java.util.concurrent;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executors, written from the Java SE specification: fixed thread pools and
 * the single-thread executor, whose threads are named and started as the
 * JDK's ThreadPoolExecutor does ("pool-1-thread-1", one new thread per task
 * until the pool is full, a replacement when a task of execute() throws).
 * The cached, scheduled and work-stealing pools are absent.
 */
public class Executors {
	private Executors() {
	}

	public static ExecutorService newFixedThreadPool(int nThreads) {
		return newFixedThreadPool(nThreads, defaultThreadFactory());
	}

	public static ExecutorService newFixedThreadPool(int nThreads, ThreadFactory threadFactory) {
		if (nThreads <= 0) {
			throw new IllegalArgumentException();
		}
		if (threadFactory == null) {
			throw new NullPointerException();
		}
		return new FixedThreadPool(nThreads, threadFactory);
	}

	public static ExecutorService newSingleThreadExecutor() {
		return newFixedThreadPool(1);
	}

	public static ExecutorService newSingleThreadExecutor(ThreadFactory threadFactory) {
		return newFixedThreadPool(1, threadFactory);
	}

	public static ThreadFactory defaultThreadFactory() {
		return new DefaultThreadFactory();
	}

	public static <T> Callable<T> callable(Runnable task, T result) {
		if (task == null) {
			throw new NullPointerException();
		}
		return new RunnableAdapter<T>(task, result);
	}

	public static Callable<Object> callable(Runnable task) {
		return callable(task, null);
	}

	private static final class RunnableAdapter<T> implements Callable<T> {
		private final Runnable task;
		private final T result;

		RunnableAdapter(Runnable task, T result) {
			this.task = task;
			this.result = result;
		}

		public T call() {
			task.run();
			return result;
		}

		public String toString() {
			return super.toString() + "[Wrapped task = " + task + "]";
		}
	}

	private static final class DefaultThreadFactory implements ThreadFactory {
		private static final AtomicInteger poolNumber = new AtomicInteger(1);
		private final AtomicInteger threadNumber = new AtomicInteger(1);
		private final String namePrefix;

		DefaultThreadFactory() {
			namePrefix = "pool-" + poolNumber.getAndIncrement() + "-thread-";
		}

		public Thread newThread(Runnable r) {
			Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
			if (t.isDaemon()) {
				t.setDaemon(false);
			}
			if (t.getPriority() != Thread.NORM_PRIORITY) {
				t.setPriority(Thread.NORM_PRIORITY);
			}
			return t;
		}
	}

	/** A pool of at most size threads over one queue, guarded by lock */
	private static final class FixedThreadPool extends AbstractExecutorService {
		private final Object lock = new Object();
		private final int size;
		private final ThreadFactory factory;
		private final LinkedList<Runnable> queue = new LinkedList<Runnable>();
		private final List<Thread> workers = new ArrayList<Thread>();
		private int active;
		private long completed;
		private boolean shutdown;
		private boolean stopped;

		FixedThreadPool(int size, ThreadFactory factory) {
			this.size = size;
			this.factory = factory;
		}

		public void execute(Runnable command) {
			if (command == null) {
				throw new NullPointerException();
			}
			synchronized (lock) {
				if (shutdown) {
					throw new RejectedExecutionException("Task " + command + " rejected from " + this);
				}
				if (workers.size() < size) {
					addWorker(command);
				} else {
					queue.addLast(command);
					// All: awaitTermination waits on the same monitor
					lock.notifyAll();
				}
			}
		}

		/** With lock held */
		private void addWorker(Runnable first) {
			Worker worker = new Worker(first);
			Thread t = factory.newThread(worker);
			if (t == null) {
				throw new RejectedExecutionException("Thread factory returned null");
			}
			worker.thread = t;
			workers.add(t);
			t.start();
		}

		private final class Worker implements Runnable {
			private Runnable first;
			Thread thread;

			Worker(Runnable first) {
				this.first = first;
			}

			public void run() {
				boolean abrupt = true;
				try {
					Runnable task = first;
					first = null;
					while (task != null || (task = take()) != null) {
						synchronized (lock) {
							active++;
						}
						try {
							task.run();
						} finally {
							synchronized (lock) {
								active--;
								completed++;
							}
						}
						task = null;
					}
					abrupt = false;
				} finally {
					exit(this, abrupt);
				}
			}
		}

		/** The next task, or null when the pool shuts down with an empty queue */
		private Runnable take() {
			synchronized (lock) {
				while (true) {
					if (stopped) {
						return null;
					}
					if (!queue.isEmpty()) {
						return queue.removeFirst();
					}
					if (shutdown) {
						return null;
					}
					try {
						lock.wait();
					} catch (InterruptedException e) {
						// shutdownNow, or a stray interrupt: check again
					}
				}
			}
		}

		private void exit(Worker worker, boolean abrupt) {
			synchronized (lock) {
				workers.remove(worker.thread);
				// A task of execute() threw: the thread dies with it, as in the
				// JDK, and a new one takes its place
				if (abrupt && !stopped && (!shutdown || !queue.isEmpty()) && workers.size() < size) {
					addWorker(null);
				}
				lock.notifyAll();
			}
		}

		public void shutdown() {
			synchronized (lock) {
				shutdown = true;
				lock.notifyAll();
			}
		}

		public List<Runnable> shutdownNow() {
			synchronized (lock) {
				shutdown = true;
				stopped = true;
				List<Runnable> pending = new ArrayList<Runnable>(queue);
				queue.clear();
				for (Thread t : workers) {
					t.interrupt();
				}
				lock.notifyAll();
				return pending;
			}
		}

		public boolean isShutdown() {
			synchronized (lock) {
				return shutdown;
			}
		}

		public boolean isTerminated() {
			synchronized (lock) {
				return shutdown && workers.isEmpty();
			}
		}

		public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
			long end = System.nanoTime() + unit.toNanos(timeout);
			synchronized (lock) {
				while (!(shutdown && workers.isEmpty())) {
					long remaining = end - System.nanoTime();
					if (remaining <= 0) {
						return false;
					}
					lock.wait(TimeUnit.waitMillis(remaining));
				}
				return true;
			}
		}

		public String toString() {
			synchronized (lock) {
				String state = !shutdown ? "Running" : workers.isEmpty() ? "Terminated" : "Shutting down";
				return "java.util.concurrent.ThreadPoolExecutor@" + Integer.toHexString(hashCode()) + "[" + state
						+ ", pool size = " + workers.size() + ", active threads = " + active + ", queued tasks = "
						+ queue.size() + ", completed tasks = " + completed + "]";
			}
		}
	}
}
