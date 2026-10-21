package java.lang;

/**
 * Thread, written from the Java SE specification, on POSIX threads
 * (Thread-native.cpp) and the monitors of cpp/src/j2c_threads.cpp.
 *
 * Program exit waits for the threads that are not daemons, as the JVM does,
 * and an uncaught exception prints "Exception in thread ..." through the
 * thread's, then the default, handler. sleep and join wait on monitors, so
 * interrupt() ends them, and Object.wait, with InterruptedException; it does
 * not end blocking I/O. Priorities are recorded only.
 *
 * Not supported: thread groups, getState(), stack traces of other threads,
 * stop, suspend and resume (absent), and the uncaught-exception handlers
 * of the main thread (an exception main throws always prints).
 */
public class Thread implements Runnable {
	public static final int MIN_PRIORITY = 1;
	public static final int NORM_PRIORITY = 5;
	public static final int MAX_PRIORITY = 10;

	private static int threadInitNumber;
	private static long threadSeqNumber = 1;
	private static volatile UncaughtExceptionHandler defaultUncaughtExceptionHandler;

	private volatile String name;
	private final long tid;
	private int priority;
	private boolean daemon;
	private final Runnable target;
	private volatile UncaughtExceptionHandler uncaughtExceptionHandler;
	/** 0 new, 1 started, 2 terminated; guarded by this */
	private int state;
	/** Read by the natives through j2c_interrupt_flag */
	volatile boolean interrupted;
	/** For ThreadLocal */
	java.util.HashMap<ThreadLocal<?>, Object> threadLocals;

	public interface UncaughtExceptionHandler {
		void uncaughtException(Thread t, Throwable e);
	}

	public Thread() {
		this(null, null, 0);
	}

	public Thread(Runnable task) {
		this(task, null, 0);
	}

	public Thread(String name) {
		this(null, checkName(name), 0);
	}

	public Thread(Runnable task, String name) {
		this(task, checkName(name), 0);
	}

	private Thread(Runnable task, String name, int unused) {
		Thread parent = currentThread();
		this.name = name == null ? "Thread-" + nextThreadNum() : name;
		this.target = task;
		this.daemon = parent.daemon;
		this.priority = parent.priority;
		this.tid = nextThreadId();
	}

	/** The main thread, which is running */
	private Thread(boolean main) {
		this.name = "main";
		this.target = null;
		this.priority = NORM_PRIORITY;
		this.tid = nextThreadId();
		this.state = 1;
	}

	private static String checkName(String name) {
		if (name == null) {
			throw new NullPointerException("name cannot be null");
		}
		return name;
	}

	private static synchronized int nextThreadNum() {
		return threadInitNumber++;
	}

	private static synchronized long nextThreadId() {
		return threadSeqNumber++;
	}

	/** For currentThread(), on a thread that has no Thread object yet */
	static Thread j2cMainThread() {
		return new Thread(true);
	}

	public static native Thread currentThread();

	public static native void yield();

	public static void onSpinWait() {
	}

	public static void sleep(long millis) throws InterruptedException {
		if (millis < 0) {
			throw new IllegalArgumentException("timeout value is negative");
		}
		Thread t = currentThread();
		if (millis == 0) {
			if (interrupted()) {
				throw new InterruptedException("sleep interrupted");
			}
			return;
		}
		// Nothing notifies this object, so only the time or an interrupt ends the wait
		Object sleeper = new Object();
		long end = System.nanoTime() / 1000000 + millis;
		synchronized (sleeper) {
			long remaining = millis;
			while (remaining > 0) {
				try {
					sleeper.wait(remaining);
				} catch (InterruptedException e) {
					throw new InterruptedException("sleep interrupted");
				}
				remaining = end - System.nanoTime() / 1000000;
			}
		}
	}

	public static void sleep(long millis, int nanos) throws InterruptedException {
		if (millis < 0) {
			throw new IllegalArgumentException("timeout value is negative");
		}
		if (nanos < 0 || nanos > 999999) {
			throw new IllegalArgumentException("nanosecond timeout value out of range");
		}
		if (nanos > 0 && millis < Long.MAX_VALUE) {
			millis++;
		}
		sleep(millis);
	}

	public synchronized void start() {
		if (state != 0) {
			throw new IllegalThreadStateException();
		}
		state = 1;
		if (!start0(daemon)) {
			state = 0;
			throw new OutOfMemoryError("unable to create native thread: possibly out of memory or process/resource limits reached");
		}
	}

	/** Starts a POSIX thread that calls j2cRun; false when it could not */
	private native boolean start0(boolean daemon);

	/** The body of a started thread */
	void j2cRun() {
		try {
			run();
		} catch (Throwable e) {
			dispatchUncaughtException(e);
		} finally {
			threadLocals = null;
			synchronized (this) {
				state = 2;
				notifyAll();
			}
		}
	}

	private void dispatchUncaughtException(Throwable e) {
		try {
			getUncaughtExceptionHandler().uncaughtException(this, e);
		} catch (Throwable ignored) {
			// As the JVM does, an exception from the handler is dropped
		}
	}

	public void run() {
		if (target != null) {
			target.run();
		}
	}

	public final synchronized boolean isAlive() {
		return state == 1;
	}

	public final void join() throws InterruptedException {
		join(0);
	}

	public final synchronized void join(long millis) throws InterruptedException {
		if (millis < 0) {
			throw new IllegalArgumentException("timeout value is negative");
		}
		if (millis == 0) {
			while (state == 1) {
				wait(0);
			}
			return;
		}
		long end = System.nanoTime() / 1000000 + millis;
		long remaining = millis;
		while (state == 1 && remaining > 0) {
			wait(remaining);
			remaining = end - System.nanoTime() / 1000000;
		}
	}

	public final void join(long millis, int nanos) throws InterruptedException {
		if (millis < 0) {
			throw new IllegalArgumentException("timeout value is negative");
		}
		if (nanos < 0 || nanos > 999999) {
			throw new IllegalArgumentException("nanosecond timeout value out of range");
		}
		if (nanos > 0 && millis < Long.MAX_VALUE) {
			millis++;
		}
		join(millis);
	}

	public void interrupt() {
		interrupted = true;
		interrupt0();
	}

	/** Wakes this thread's waits */
	private native void interrupt0();

	public static boolean interrupted() {
		return currentThread().clearInterrupt();
	}

	/** Reads and clears the interrupt flag at once */
	private native boolean clearInterrupt();

	public boolean isInterrupted() {
		return interrupted;
	}

	public static boolean holdsLock(Object obj) {
		if (obj == null) {
			throw new NullPointerException();
		}
		return holdsLock0(obj);
	}

	private static native boolean holdsLock0(Object obj);

	public final String getName() {
		return name;
	}

	public final synchronized void setName(String name) {
		this.name = checkName(name);
	}

	public long getId() {
		return tid;
	}

	public final int getPriority() {
		return priority;
	}

	public final void setPriority(int newPriority) {
		if (newPriority > MAX_PRIORITY || newPriority < MIN_PRIORITY) {
			throw new IllegalArgumentException();
		}
		priority = newPriority;
	}

	public final boolean isDaemon() {
		return daemon;
	}

	public final synchronized void setDaemon(boolean on) {
		if (state == 1) {
			throw new IllegalThreadStateException();
		}
		daemon = on;
	}

	public final void checkAccess() {
	}

	public UncaughtExceptionHandler getUncaughtExceptionHandler() {
		UncaughtExceptionHandler handler = uncaughtExceptionHandler;
		if (handler != null) {
			return handler;
		}
		synchronized (this) {
			if (state == 2) {
				return null;
			}
		}
		return new DefaultHandler();
	}

	public void setUncaughtExceptionHandler(UncaughtExceptionHandler handler) {
		uncaughtExceptionHandler = handler;
	}

	public static UncaughtExceptionHandler getDefaultUncaughtExceptionHandler() {
		return defaultUncaughtExceptionHandler;
	}

	public static void setDefaultUncaughtExceptionHandler(UncaughtExceptionHandler handler) {
		defaultUncaughtExceptionHandler = handler;
	}

	/** As the JVM's ThreadGroup "main": the default handler, else the printout */
	private static final class DefaultHandler implements UncaughtExceptionHandler {
		public void uncaughtException(Thread t, Throwable e) {
			UncaughtExceptionHandler handler = defaultUncaughtExceptionHandler;
			if (handler != null) {
				handler.uncaughtException(t, e);
			} else {
				System.err.print("Exception in thread \"" + t.getName() + "\" ");
				e.printStackTrace(System.err);
			}
		}
	}

	public String toString() {
		boolean ended;
		synchronized (this) {
			ended = state == 2;
		}
		// The thread leaves its group, "main" here, when it ends
		return "Thread[" + getName() + "," + getPriority() + "," + (ended ? "" : "main") + "]";
	}
}
