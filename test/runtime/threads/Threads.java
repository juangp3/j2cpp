import java.util.ArrayList;
import java.util.List;

/** Threads, synchronized methods and blocks, and their exceptions */
public class Threads {
	static int staticCount;
	int count;
	final Object lock = new Object();
	int blockCount;

	synchronized void increment() {
		count++;
	}

	static synchronized void incrementStatic() {
		staticCount++;
	}

	void incrementBlock() {
		synchronized (lock) {
			blockCount++;
		}
	}

	/** Reentrant: a synchronized method calling another on the same object */
	synchronized int nested(int depth) {
		return depth == 0 ? count : nested(depth - 1);
	}

	/** The monitor is released when the body throws */
	synchronized void fail() {
		throw new IllegalStateException("failed inside synchronized");
	}

	static int squareWork(int n) {
		int sum = 0;
		for (int i = 0; i < 1000; i++) {
			sum += n;
		}
		return sum / 1000 * n;
	}

	public static void main(String[] args) throws Exception {
		Thread main = Thread.currentThread();
		System.out.println(main.getName() + " " + main.isDaemon() + " " + main.isAlive() + " " + main);

		// Results collected in join order
		int[] results = new int[8];
		List<Thread> workers = new ArrayList<>();
		for (int i = 0; i < results.length; i++) {
			final int n = i;
			Thread t = new Thread(() -> results[n] = squareWork(n));
			workers.add(t);
		}
		System.out.println(workers.get(0).getName() + " alive " + workers.get(0).isAlive());
		for (Thread t : workers) {
			t.start();
		}
		for (Thread t : workers) {
			t.join();
		}
		StringBuilder sb = new StringBuilder();
		for (int r : results) {
			sb.append(r).append(' ');
		}
		System.out.println("squares " + sb.toString().trim());
		System.out.println(workers.get(7).getName() + " alive " + workers.get(7).isAlive() + " " + workers.get(7));

		// Counters under synchronized methods, static methods and blocks
		Threads counter = new Threads();
		List<Thread> incrementers = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			incrementers.add(new Thread(() -> {
				for (int j = 0; j < 20000; j++) {
					counter.increment();
					incrementStatic();
					counter.incrementBlock();
				}
			}, "incrementer-" + i));
		}
		for (Thread t : incrementers) {
			t.start();
		}
		for (Thread t : incrementers) {
			t.join();
		}
		System.out.println("count " + counter.count + " static " + staticCount + " block " + counter.blockCount);
		System.out.println("nested " + counter.nested(10));
		try {
			counter.fail();
		} catch (IllegalStateException e) {
			System.out.println("caught " + e.getMessage());
		}
		// Another thread can take the monitor after the exception released it
		Thread after = new Thread(counter::increment);
		after.start();
		after.join();
		System.out.println("count after " + counter.count);

		// A subclass overriding run, with its own name
		Thread named = new Thread("worker") {
			public void run() {
				System.out.println("running in " + Thread.currentThread().getName());
			}
		};
		named.start();
		named.join();

		// Sleep, interrupt and the interrupt flag
		Thread sleeper = new Thread(() -> {
			try {
				Thread.sleep(60000);
				System.out.println("slept");
			} catch (InterruptedException e) {
				System.out.println("sleep: " + e.getMessage() + " " + Thread.currentThread().isInterrupted());
			}
		});
		sleeper.start();
		Thread.sleep(50);
		sleeper.interrupt();
		sleeper.join();

		Thread.currentThread().interrupt();
		System.out.println("interrupted " + Thread.currentThread().isInterrupted());
		System.out.println("cleared " + Thread.interrupted() + " " + Thread.interrupted());
		Thread.currentThread().interrupt();
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {
			System.out.println("pending interrupt: " + e.getMessage());
		}
		long start = System.nanoTime();
		Thread.sleep(20);
		System.out.println("slept at least 20 ms " + ((System.nanoTime() - start) >= 20000000L));

		// Monitor errors
		Object o = new Object();
		try {
			o.notify();
		} catch (IllegalMonitorStateException e) {
			System.out.println("notify: " + e.getMessage());
		}
		try {
			o.wait();
		} catch (IllegalMonitorStateException e) {
			System.out.println("wait: " + e.getMessage());
		}
		try {
			o.wait(-1);
		} catch (IllegalArgumentException e) {
			System.out.println("wait(-1): " + e.getMessage());
		}
		synchronized (o) {
			System.out.println("holdsLock " + Thread.holdsLock(o));
			o.wait(5);
			o.notifyAll();
		}
		System.out.println("holdsLock after " + Thread.holdsLock(o));
		try {
			Object nothing = null;
			synchronized (nothing) {
				System.out.println("not reached");
			}
		} catch (NullPointerException e) {
			System.out.println("synchronized (null): NullPointerException");
		}
		try {
			named.start();
		} catch (IllegalThreadStateException e) {
			System.out.println("start twice: " + e);
		}
		try {
			Thread.sleep(-1);
		} catch (IllegalArgumentException e) {
			System.out.println("sleep(-1): " + e.getMessage());
		}

		// ThreadLocal: each thread sees its own value
		ThreadLocal<Integer> local = ThreadLocal.withInitial(() -> 7);
		local.set(1);
		int[] seen = new int[1];
		Thread other = new Thread(() -> {
			seen[0] = local.get();
			local.set(99);
		});
		other.start();
		other.join();
		System.out.println("thread locals " + local.get() + " " + seen[0]);
	}
}
