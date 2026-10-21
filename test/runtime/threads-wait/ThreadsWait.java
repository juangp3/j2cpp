import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Producers and consumers over a bounded buffer with wait and notifyAll */
public class ThreadsWait {
	static final class Buffer {
		private final ArrayDeque<Integer> items = new ArrayDeque<>();
		private final int capacity;
		int maxSeen;

		Buffer(int capacity) {
			this.capacity = capacity;
		}

		synchronized void put(int item) throws InterruptedException {
			while (items.size() == capacity) {
				wait();
			}
			items.addLast(item);
			maxSeen = Math.max(maxSeen, items.size());
			notifyAll();
		}

		synchronized int take() throws InterruptedException {
			while (items.isEmpty()) {
				wait();
			}
			int item = items.removeFirst();
			notifyAll();
			return item;
		}
	}

	/** A one-slot handoff with notify: strict alternation of two threads */
	static final class PingPong {
		private boolean pingTurn = true;
		final StringBuilder log = new StringBuilder();

		synchronized void play(boolean ping, int rounds) throws InterruptedException {
			for (int i = 0; i < rounds; i++) {
				while (pingTurn != ping) {
					wait();
				}
				log.append(ping ? "ping " : "pong ");
				pingTurn = !ping;
				notify();
			}
		}
	}

	public static void main(String[] args) throws Exception {
		Buffer buffer = new Buffer(4);
		int producers = 3;
		int perProducer = 2000;
		List<Thread> threads = new ArrayList<>();
		for (int p = 0; p < producers; p++) {
			final int base = p * perProducer;
			threads.add(new Thread(() -> {
				try {
					for (int i = 0; i < perProducer; i++) {
						buffer.put(base + i);
					}
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}));
		}
		long[] sums = new long[2];
		int[] counts = new int[2];
		for (int c = 0; c < 2; c++) {
			final int consumer = c;
			threads.add(new Thread(() -> {
				try {
					for (int i = 0; i < producers * perProducer / 2; i++) {
						sums[consumer] += buffer.take();
						counts[consumer]++;
					}
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}));
		}
		for (Thread t : threads) {
			t.start();
		}
		for (Thread t : threads) {
			t.join();
		}
		int n = producers * perProducer;
		System.out.println("consumed " + (counts[0] + counts[1]) + " sum " + (sums[0] + sums[1]) + " expected "
				+ ((long) n * (n - 1) / 2) + " bounded " + (buffer.maxSeen <= 4));

		PingPong game = new PingPong();
		Thread ping = new Thread(() -> {
			try {
				game.play(true, 5);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		});
		Thread pong = new Thread(() -> {
			try {
				game.play(false, 5);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		});
		pong.start();
		ping.start();
		ping.join();
		pong.join();
		System.out.println(game.log.toString().trim());

		// A waiting thread interrupted: InterruptedException, flag cleared
		Object lock = new Object();
		boolean[] ready = new boolean[1];
		Thread waiter = new Thread(() -> {
			synchronized (lock) {
				ready[0] = true;
				lock.notifyAll();
				try {
					lock.wait();
					System.out.println("woke normally");
				} catch (InterruptedException e) {
					System.out.println("wait interrupted, flag " + Thread.currentThread().isInterrupted()
							+ ", holds lock " + Thread.holdsLock(lock));
				}
			}
		});
		waiter.start();
		synchronized (lock) {
			while (!ready[0]) {
				lock.wait();
			}
		}
		// The waiter released the monitor in wait(), so this lock succeeds
		synchronized (lock) {
			waiter.interrupt();
		}
		waiter.join();

		// Timed waits return after the timeout without a notify
		long start = System.nanoTime();
		synchronized (lock) {
			lock.wait(30);
		}
		System.out.println("timed wait returned " + ((System.nanoTime() - start) >= 30000000L));

		// join with a timeout on a thread that keeps running, then interrupt it
		Thread sleeper = new Thread(() -> {
			try {
				Thread.sleep(60000);
			} catch (InterruptedException e) {
				System.out.println("sleeper interrupted");
			}
		});
		sleeper.start();
		sleeper.join(20);
		System.out.println("still alive " + sleeper.isAlive());
		sleeper.interrupt();
		sleeper.join();
		System.out.println("alive after join " + sleeper.isAlive());

		// Recursive monitors: wait releases every hold and restores them
		Object nested = new Object();
		Thread notifier = new Thread(() -> {
			synchronized (nested) {
				nested.notifyAll();
			}
		});
		synchronized (nested) {
			synchronized (nested) {
				notifier.start();
				nested.wait();
				System.out.println("nested holds after wait " + Thread.holdsLock(nested));
			}
			System.out.println("outer still held " + Thread.holdsLock(nested));
		}
		notifier.join();
	}
}
