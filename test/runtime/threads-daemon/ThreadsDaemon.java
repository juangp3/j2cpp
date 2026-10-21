import java.util.concurrent.CountDownLatch;

/**
 * The program ends when its non-daemon threads end: main returns first, a
 * non-daemon thread finishes later, and a daemon thread that never ends is
 * abandoned.
 */
public class ThreadsDaemon {
	static volatile long spins;

	public static void main(String[] args) throws Exception {
		CountDownLatch running = new CountDownLatch(1);
		Thread daemon = new Thread(() -> {
			running.countDown();
			while (true) {
				spins++;
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					return;
				}
			}
		}, "spinner");
		daemon.setDaemon(true);
		System.out.println("daemon " + daemon.isDaemon());
		daemon.start();
		running.await();
		try {
			daemon.setDaemon(false);
		} catch (IllegalThreadStateException e) {
			System.out.println("setDaemon on a running thread: IllegalThreadStateException");
		}

		// Threads inherit daemon status from the thread that creates them
		Thread[] child = new Thread[1];
		Thread parent = new Thread(() -> child[0] = new Thread(() -> {
		}));
		parent.setDaemon(true);
		parent.start();
		parent.join();
		System.out.println("inherited daemon " + child[0].isDaemon());

		Thread worker = new Thread(() -> {
			try {
				Thread.sleep(300);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			System.out.println("non-daemon worker done, daemon alive " + daemon.isAlive());
		});
		worker.start();
		System.out.println("main returns");
	}
}
